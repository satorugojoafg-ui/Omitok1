import re
import secrets
import time
from datetime import datetime, timedelta, timezone
from functools import wraps
from typing import Any, Callable, Optional

from flask import Blueprint, g, jsonify, request
from werkzeug.security import check_password_hash, generate_password_hash

from database import db_session

auth_bp = Blueprint("auth", __name__)

USERNAME_RE = re.compile(r"^[a-zA-Z0-9_]{3,24}$")
MAX_LOGIN_ATTEMPTS = 5
LOGIN_WINDOW_SECONDS = 300
SESSION_TTL_DAYS = 14


def _serialize_user(row: Any, video_count: int = 0, total_views: int = 0) -> dict:
    return {
        "id": str(row["id"]),
        "username": row["username"],
        "display_name": row["display_name"] or row["username"],
        "bio": row["bio"] or "",
        "created_at": row["created_at"],
        "video_count": video_count,
        "total_views": total_views,
    }


def _extract_token() -> Optional[str]:
    header = request.headers.get("Authorization", "")
    if header.lower().startswith("bearer "):
        return header[7:].strip()
    return request.cookies.get("omitok_session")


def get_current_user() -> Optional[dict]:
    token = _extract_token()
    if not token:
        return None
    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    with db_session() as conn:
        row = conn.execute(
            """
            SELECT u.id, u.username, u.display_name, u.bio, u.created_at
            FROM sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.token = ? AND s.expires_at > ?
            """,
            (token, now_iso),
        ).fetchone()
        if not row:
            return None
        return dict(row)


def login_required(fn: Callable) -> Callable:
    @wraps(fn)
    def wrapper(*args, **kwargs):
        user = get_current_user()
        if not user:
            return jsonify({"error": "Authentication required"}), 401
        g.current_user = user
        return fn(*args, **kwargs)

    return wrapper


def _is_rate_limited(client_key: str) -> bool:
    cutoff = int(time.time()) - LOGIN_WINDOW_SECONDS
    with db_session() as conn:
        conn.execute("DELETE FROM login_attempts WHERE attempted_at < ?", (cutoff,))
        row = conn.execute(
            "SELECT COUNT(*) AS cnt FROM login_attempts WHERE client_key = ?",
            (client_key,),
        ).fetchone()
        return (row["cnt"] if row else 0) >= MAX_LOGIN_ATTEMPTS


def _record_login_failure(client_key: str) -> None:
    with db_session() as conn:
        conn.execute(
            "INSERT INTO login_attempts (client_key, attempted_at) VALUES (?, ?)",
            (client_key, int(time.time())),
        )


def _clear_login_failures(client_key: str) -> None:
    with db_session() as conn:
        conn.execute("DELETE FROM login_attempts WHERE client_key = ?", (client_key,))


def _create_session(user_id: int) -> str:
    token = secrets.token_urlsafe(36)
    expires = (datetime.now(timezone.utc) + timedelta(days=SESSION_TTL_DAYS)).strftime(
        "%Y-%m-%d %H:%M:%S"
    )
    with db_session() as conn:
        conn.execute(
            "INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, ?)",
            (token, user_id, expires),
        )
    return token


@auth_bp.post("/register")
def register():
    data = request.get_json(silent=True) or {}
    username = str(data.get("username", "")).strip()
    password = str(data.get("password", ""))

    if not USERNAME_RE.match(username):
        return (
            jsonify({"error": "Username must be 3-24 letters, digits, or underscores."}),
            400,
        )
    if len(password) < 6 or len(password) > 128:
        return jsonify({"error": "Password must be between 6 and 128 characters."}), 400

    pw_hash = generate_password_hash(password, method="pbkdf2:sha256")
    with db_session() as conn:
        existing = conn.execute(
            "SELECT id FROM users WHERE username = ?", (username,)
        ).fetchone()
        if existing:
            return jsonify({"error": "Username is already taken."}), 409

        cursor = conn.execute(
            "INSERT INTO users (username, password_hash, display_name) VALUES (?, ?, ?)",
            (username, pw_hash, username),
        )
        user_id = cursor.lastrowid
        user_row = conn.execute(
            "SELECT id, username, display_name, bio, created_at FROM users WHERE id = ?",
            (user_id,),
        ).fetchone()

    token = _create_session(int(user_id))
    return jsonify({"token": token, "user": _serialize_user(user_row)}), 201


@auth_bp.post("/login")
def login():
    data = request.get_json(silent=True) or {}
    username = str(data.get("username", "")).strip()
    password = str(data.get("password", ""))
    client_ip = request.headers.get("X-Forwarded-For", request.remote_addr or "unknown").split(",")[0].strip()
    rate_key = f"{client_ip}:{username.lower()}"

    if _is_rate_limited(rate_key):
        resp = jsonify({"error": "Too many failed login attempts. Please try again later."})
        resp.headers["Retry-After"] = str(LOGIN_WINDOW_SECONDS)
        return resp, 429

    if not username or not password:
        return jsonify({"error": "Username and password are required."}), 400

    with db_session() as conn:
        user_row = conn.execute(
            "SELECT id, username, password_hash, display_name, bio, created_at FROM users WHERE username = ?",
            (username,),
        ).fetchone()

    if not user_row or not check_password_hash(user_row["password_hash"], password):
        _record_login_failure(rate_key)
        return jsonify({"error": "Invalid username or password."}), 401

    _clear_login_failures(rate_key)
    token = _create_session(int(user_row["id"]))
    return jsonify({"token": token, "user": _serialize_user(user_row)}), 200


@auth_bp.get("/me")
@login_required
def me():
    user = g.current_user
    with db_session() as conn:
        stats = conn.execute(
            "SELECT COUNT(*) AS cnt, COALESCE(SUM(views), 0) AS total_views FROM videos WHERE owner_id = ?",
            (user["id"],),
        ).fetchone()
    return jsonify(
        {
            "user": _serialize_user(
                user,
                video_count=int(stats["cnt"]),
                total_views=int(stats["total_views"]),
            )
        }
    )


@auth_bp.post("/logout")
def logout():
    token = _extract_token()
    if token:
        with db_session() as conn:
            conn.execute("DELETE FROM sessions WHERE token = ?", (token,))
    return jsonify({"message": "Logged out"}), 200


@auth_bp.put("/profile")
@login_required
def update_profile():
    data = request.get_json(silent=True) or {}
    display_name = str(data.get("display_name", "")).strip()[:40]
    bio = str(data.get("bio", "")).strip()[:200]
    user = g.current_user

    with db_session() as conn:
        conn.execute(
            "UPDATE users SET display_name = ?, bio = ? WHERE id = ?",
            (display_name or user["username"], bio, user["id"]),
        )
        updated = conn.execute(
            "SELECT id, username, display_name, bio, created_at FROM users WHERE id = ?",
            (user["id"],),
        ).fetchone()
    return jsonify({"user": _serialize_user(updated)}), 200
