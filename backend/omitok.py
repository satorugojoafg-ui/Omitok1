from flask import Flask, jsonify

from auth import auth_bp
from database import init_db
from storage import build_storage_provider
from videos import MAX_VIDEO_BYTES, videos_bp


def create_app() -> Flask:
    app = Flask(__name__)
    app.config["MAX_CONTENT_LENGTH"] = MAX_VIDEO_BYTES + (1024 * 1024)
    app.config["STORAGE_PROVIDER"] = build_storage_provider()

    init_db()

    app.register_blueprint(auth_bp)
    app.register_blueprint(videos_bp)

    @app.get("/")
    def health():
        return jsonify(
            {
                "app": "OMITOK",
                "status": "ok",
                "storage_backend": app.config["STORAGE_PROVIDER"].provider_name,
            }
        ), 200

    @app.after_request
    def apply_security_headers(response):
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        return response

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
