import abc
import os
import shutil
from typing import Optional


class StorageProvider(abc.ABC):
    """Swappable storage abstraction so MEGA can be replaced with S3/R2/GCS anytime."""

    provider_name: str

    @abc.abstractmethod
    def save_file(self, local_temp_path: str, object_key: str) -> str:
        """Persist file and return canonical storage key."""

    @abc.abstractmethod
    def ensure_local_readable(self, storage_key: str) -> str:
        """Return a local readable file path (downloading/caching if remote) for HTTP Range streaming."""

    @abc.abstractmethod
    def delete_file(self, storage_key: str) -> None:
        """Remove file from storage backend."""


class LocalDiskStorageProvider(StorageProvider):
    provider_name = "local"

    def __init__(self, base_dir: str) -> None:
        self.base_dir = os.path.abspath(base_dir)
        os.makedirs(self.base_dir, exist_ok=True)

    def save_file(self, local_temp_path: str, object_key: str) -> str:
        safe_name = os.path.basename(object_key)
        dest_path = os.path.join(self.base_dir, safe_name)
        shutil.move(local_temp_path, dest_path)
        return safe_name

    def ensure_local_readable(self, storage_key: str) -> str:
        safe_name = os.path.basename(storage_key)
        path = os.path.join(self.base_dir, safe_name)
        if not os.path.exists(path):
            raise FileNotFoundError(f"Video file missing: {safe_name}")
        return path

    def delete_file(self, storage_key: str) -> None:
        safe_name = os.path.basename(storage_key)
        path = os.path.join(self.base_dir, safe_name)
        if os.path.exists(path):
            os.remove(path)


class MegaStorageProvider(StorageProvider):
    """
    MEGA adapter using server-side environment variables ONLY (MEGA_EMAIL, MEGA_PASSWORD, MEGA_FOLDER).
    Note: mega.py uses an unofficial reverse-engineered client without native HTTP Range streaming.
    To guarantee smooth mobile ExoPlayer seeking on PythonAnywhere, uploaded files are stored in the
    OMITOK folder on MEGA and cached locally for byte-range streaming.
    """

    provider_name = "mega"

    def __init__(self, cache_dir: str, folder_name: str = "OMITOK") -> None:
        self.cache_dir = os.path.abspath(cache_dir)
        self.folder_name = folder_name
        os.makedirs(self.cache_dir, exist_ok=True)
        self._mega_client = None

    def _get_client(self):
        if self._mega_client is not None:
            return self._mega_client
        email = os.environ.get("MEGA_EMAIL", "").strip()
        password = os.environ.get("MEGA_PASSWORD", "").strip()
        if not email or not password:
            raise RuntimeError("MEGA_EMAIL and MEGA_PASSWORD environment variables are required.")
        from mega import Mega  # Lazy import so app boots even if mega.py is unused

        mega = Mega()
        self._mega_client = mega.login(email, password)
        return self._mega_client

    def save_file(self, local_temp_path: str, object_key: str) -> str:
        safe_name = os.path.basename(object_key)
        cache_path = os.path.join(self.cache_dir, safe_name)
        shutil.copy2(local_temp_path, cache_path)
        try:
            client = self._get_client()
            folder = client.find(self.folder_name, exclude_deleted=True)
            folder_id = folder[0] if folder else None
            uploaded = client.upload(local_temp_path, dest=folder_id, dest_filename=safe_name)
            return safe_name if uploaded else safe_name
        finally:
            if os.path.exists(local_temp_path):
                os.remove(local_temp_path)

    def ensure_local_readable(self, storage_key: str) -> str:
        safe_name = os.path.basename(storage_key)
        cache_path = os.path.join(self.cache_dir, safe_name)
        if os.path.exists(cache_path):
            return cache_path
        client = self._get_client()
        file_node = client.find(safe_name, exclude_deleted=True)
        if not file_node:
            raise FileNotFoundError(f"MEGA object not found: {safe_name}")
        client.download(file_node, dest_path=self.cache_dir, dest_filename=safe_name)
        return cache_path

    def delete_file(self, storage_key: str) -> None:
        safe_name = os.path.basename(storage_key)
        cache_path = os.path.join(self.cache_dir, safe_name)
        if os.path.exists(cache_path):
            os.remove(cache_path)
        try:
            client = self._get_client()
            file_node = client.find(safe_name, exclude_deleted=True)
            if file_node:
                client.destroy(file_node[0])
        except Exception:
            pass


def build_storage_provider(base_dir: Optional[str] = None) -> StorageProvider:
    root = base_dir or os.path.join(os.path.dirname(__file__), "media_store")
    backend = os.environ.get("OMITOK_STORAGE_BACKEND", "local").strip().lower()
    if backend == "mega":
        return MegaStorageProvider(
            cache_dir=os.path.join(root, "mega_cache"),
            folder_name=os.environ.get("MEGA_FOLDER", "OMITOK"),
        )
    return LocalDiskStorageProvider(base_dir=root)
