# OMITOK Backend (PythonAnywhere + Flask + SQLite + Swappable Storage)

## Structure
- `omitok.py`: Flask WSGI entrypoint (`app`) and `GET /` health endpoint
- `database.py`: SQLite WAL schema (`users`, `sessions`, `login_attempts`, `videos`, `reports`)
- `auth.py`: `/register`, `/login` (with sliding-window rate limiting), `/me`, `/logout`, `PUT /profile`
- `videos.py`: `/videos`, `POST /upload` (magic-byte check + 50MB limit), `/videos/<id>`, `/videos/<id>/stream` (`HTTP 206` Range), `DELETE /videos/<id>` (owner-only), `/videos/<id>/report`, `/profile/<username>`, `/profile/<username>/videos`
- `storage.py`: Swappable `StorageProvider` (`local` or `mega` via server env vars `MEGA_EMAIL`, `MEGA_PASSWORD`, `MEGA_FOLDER=OMITOK`)

## PythonAnywhere Deployment
1. Upload `backend/` to `/home/omitok07090/OMITOK/backend`.
2. Install dependencies: `pip install --user -r requirements.txt`.
3. In PythonAnywhere Web tab -> WSGI configuration file:
   ```python
   import sys
   sys.path.insert(0, '/home/omitok07090/OMITOK/backend')
   from omitok import app as application
   ```
4. Set environment variables in WSGI / `.env` (never in Android client):
   - `OMITOK_STORAGE_BACKEND=local` (or `mega`)
   - `MEGA_EMAIL=...`
   - `MEGA_PASSWORD=...`
   - `MEGA_FOLDER=OMITOK`
5. Reload the web app in PythonAnywhere and verify endpoints inside the OMITOK Android app -> Settings -> Probe API Endpoints.
