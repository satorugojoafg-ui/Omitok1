import os
import re
import tempfile
import uuid
from typing import Optional, Tuple

from flask import Blueprint, Response, current_app, g, jsonify, request

from auth import _serialize_user, get_current_user, login_required
from database import db_session
from storage import StorageProvider

videos_bp = Blueprint("videos", __name__)

MAX_VIDEO_BYTES = 50 * 1024 * 1024  # 50 MB cap suited for PythonAnywhere free tier
ALLOWED_EXTENSIONS = {".mp4", ".webm", ".mov"}
RANGE_RE = re.compile(r"bytes=(\d*)-(\d*)")


def _is_valid_video_magic_bytes(file_path: str) -> Tuple[bool, str]:
    """Inspect real file header magic bytes instead of trusting file extension alone."""
    with open(file_path, "rb") as f:
        header = f.read(32)
    if len(header) < 12:
        return False, ""
    # MP4 / MOV ISO Base Media File Format ('ftyp' at byte 4..8)
    if header[4:8] == b"ftyp":
        brand = header[8:12]
        if brand in (b"qt  ",):
            return True, "video/quicktime"
        return True, "video/mp4"
    # WebM / Matroska EBML header (0x1A 0x45 0xDF 0xA3)
    if header[:4] == b"\x1a\x45\xdf\xa3":
        return True, "video/webm"
    return False, ""


def _serialize_video(row) -> dict:
    vid = str(row["id"])
    return {
        "id": vid,
        "owner_id": str(row["owner_id"]),
        "username": row["username"],
        "title": row["title"],
        "description": row["description"],
        "file_url": f"/videos/{vid}/stream",
        "stream_url": f"/videos/{vid}/stream",
        "published_at": row["published_at"],
        "views": int(row["views"]),
        "status": row["status"],
    }


def _get_storage() -> StorageProvider:
    return current_app.config["STORAGE_PROVIDER"]


@videos_bp.get("/videos")
def list_videos():
    with db_session() as conn:
        rows = conn.execute(
            """
            SELECT v.*, u.username
            FROM videos v
            JOIN users u ON u.id = v.owner_id
            WHERE v.status = 'published'
            ORDER BY v.id DESC
            LIMIT 100
            """
        ).fetchall()
    return jsonify({"videos": [_serialize_video(r) for r in rows]}), 200


@videos_bp.post("/upload")
@login_required
def upload_video():
    if "video" not in request.files:
        return jsonify({"error": "No video file field provided."}), 400

    file_storage = request.files["video"]
    orig_name = os.path.basename(file_storage.filename or "video.mp4")
    _, ext = os.path.splitext(orig_name.lower())
    if ext not in ALLOWED_EXTENSIONS:
        ext = ".mp4"

    title = str(request.form.get("title", "")).strip()[:120]
    description = str(request.form.get("description", "")).strip()[:600]
    status = str(request.form.get("status", "published")).strip().lower()
    if status not in ("published", "draft"):
        status = "published"
    if not title:
        return jsonify({"error": "Video title is required."}), 400

    fd, tmp_path = tempfile.mkstemp(suffix=ext)
    os.close(fd)
    size_bytes = 0
    try:
        with open(tmp_path, "wb") as out:
            while True:
                chunk = file_storage.stream.read(1024 * 256)
                if not chunk:
                    break
                size_bytes += len(chunk)
                if size_bytes > MAX_VIDEO_BYTES:
                    raise ValueError("Video exceeds 50 MB size limit.")
                out.write(chunk)

        is_valid, detected_mime = _is_valid_video_magic_bytes(tmp_path)
        if not is_valid:
            return jsonify({"error": "Invalid or potentially unsafe video file signature."}), 415

        storage = _get_storage()
        object_key = f"{uuid.uuid4().hex}{ext}"
        stored_key = storage.save_file(tmp_path, object_key)

        user = g.current_user
        with db_session() as conn:
            cursor = conn.execute(
                """
                INSERT INTO videos (owner_id, title, description, storage_provider, storage_key, mime_type, size_bytes, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    user["id"],
                    title,
                    description,
                    storage.provider_name,
                    stored_key,
                    detected_mime,
                    size_bytes,
                    status,
                ),
            )
            vid_id = cursor.lastrowid
            row = conn.execute(
                "SELECT v.*, u.username FROM videos v JOIN users u ON u.id = v.owner_id WHERE v.id = ?",
                (vid_id,),
            ).fetchone()

        return jsonify({"video": _serialize_video(row)}), 201
    except ValueError as ve:
        return jsonify({"error": str(ve)}), 413
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


@videos_bp.get("/videos/<int:video_id>")
def get_video(video_id: int):
    with db_session() as conn:
        row = conn.execute(
            "SELECT v.*, u.username FROM videos v JOIN users u ON u.id = v.owner_id WHERE v.id = ?",
            (video_id,),
        ).fetchone()
    if not row:
        return jsonify({"error": "Video not found."}), 404
    return jsonify({"video": _serialize_video(row)}), 200


@videos_bp.get("/videos/<int:video_id>/stream")
def stream_video(video_id: int):
    with db_session() as conn:
        row = conn.execute("SELECT * FROM videos WHERE id = ?", (video_id,)).fetchone()
        if not row:
            return jsonify({"error": "Video not found."}), 404
        range_header = request.headers.get("Range")
        if not range_header or range_header.startswith("bytes=0-"):
            conn.execute("UPDATE videos SET views = views + 1 WHERE id = ?", (video_id,))

    try:
        file_path = _get_storage().ensure_local_readable(row["storage_key"])
    except FileNotFoundError:
        return jsonify({"error": "Media file missing from storage."}), 404

    file_size = os.path.getsize(file_path)
    mime_type = row["mime_type"] or "video/mp4"

    start = 0
    end = file_size - 1
    status_code = 200

    if range_header:
        match = RANGE_RE.match(range_header.strip())
        if match:
            s_str, e_str = match.groups()
            if s_str:
                start = int(s_str)
            if e_str:
                end = min(int(e_str), file_size - 1)
            if start >= file_size or start > end:
                return Response(status=416, headers={"Content-Range": f"bytes */{file_size}"})
            status_code = 206

    length = (end - start) + 1

    def generate():
        with open(file_path, "rb") as f:
            f.seek(start)
            remaining = length
            while remaining > 0:
                chunk = f.read(min(1024 * 256, remaining))
                if not chunk:
                    break
                remaining -= len(chunk)
                yield chunk

    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(length),
        "Content-Type": mime_type,
        "Cache-Control": "public, max-age=3600",
    }
    if status_code == 206:
        headers["Content-Range"] = f"bytes {start}-{end}/{file_size}"

    return Response(generate(), status=status_code, headers=headers, direct_passthrough=True)


@videos_bp.delete("/videos/<int:video_id>")
@login_required
def delete_video(video_id: int):
    user = g.current_user
    with db_session() as conn:
        row = conn.execute("SELECT * FROM videos WHERE id = ?", (video_id,)).fetchone()
        if not row:
            return jsonify({"error": "Video not found."}), 404
        if int(row["owner_id"]) != int(user["id"]):
            return jsonify({"error": "Forbidden: you do not own this video."}), 403
        storage_key = row["storage_key"]
        conn.execute("DELETE FROM videos WHERE id = ?", (video_id,))

    _get_storage().delete_file(storage_key)
    return jsonify({"message": "Video deleted."}), 200


@videos_bp.post("/videos/<int:video_id>/report")
def report_video(video_id: int):
    data = request.get_json(silent=True) or {}
    reason = str(data.get("reason", "")).strip()[:200]
    if not reason:
        return jsonify({"error": "Report reason is required."}), 400
    reporter = get_current_user()
    with db_session() as conn:
        exists = conn.execute("SELECT id FROM videos WHERE id = ?", (video_id,)).fetchone()
        if not exists:
            return jsonify({"error": "Video not found."}), 404
        conn.execute(
            "INSERT INTO reports (video_id, reporter_user_id, reason) VALUES (?, ?, ?)",
            (video_id, reporter["id"] if reporter else None, reason),
        )
    return jsonify({"message": "Report submitted."}), 201


@videos_bp.get("/profile/<string:username>")
def get_profile(username: str):
    with db_session() as conn:
        user_row = conn.execute(
            "SELECT id, username, display_name, bio, created_at FROM users WHERE username = ?",
            (username.strip(),),
        ).fetchone()
        if not user_row:
            return jsonify({"error": "User not found."}), 404
        stats = conn.execute(
            "SELECT COUNT(*) AS cnt, COALESCE(SUM(views), 0) AS total_views FROM videos WHERE owner_id = ? AND status = 'published'",
            (user_row["id"],),
        ).fetchone()
    return jsonify(
        {
            "user": _serialize_user(
                user_row,
                video_count=int(stats["cnt"]),
                total_views=int(stats["total_views"]),
            )
        }
    ), 200


@videos_bp.get("/profile/<string:username>/videos")
def get_profile_videos(username: str):
    with db_session() as conn:
        user_row = conn.execute(
            "SELECT id FROM users WHERE username = ?", (username.strip(),)
        ).fetchone()
        if not user_row:
            return jsonify({"error": "User not found."}), 404
        rows = conn.execute(
            """
            SELECT v.*, u.username
            FROM videos v
            JOIN users u ON u.id = v.owner_id
            WHERE v.owner_id = ? AND v.status = 'published'
            ORDER BY v.id DESC
            """,
            (user_row["id"],),
        ).fetchall()
    return jsonify({"videos": [_serialize_video(r) for r in rows]}), 200
