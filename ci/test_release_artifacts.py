#!/usr/bin/env python3

from __future__ import annotations

import tempfile
from pathlib import Path
import unittest

from promote_maven_repository import RemoteRepository, promote
from staging_manifest import create, verify


class MemoryRepository(RemoteRepository):
    def __init__(self) -> None:
        super().__init__("https://packages.example.invalid", "actor", "token")
        self.files: dict[str, bytes] = {}

    def request(self, relative: Path, method: str, data: bytes | None = None) -> tuple[int, bytes]:
        key = relative.as_posix()
        if method == "GET":
            return (200, self.files[key]) if key in self.files else (404, b"")
        if method == "PUT" and data is not None:
            self.files[key] = data
            return 201, b""
        return 405, b""


class ReleaseArtifactsTest(unittest.TestCase):
    def test_manifest_detects_changed_staged_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "repository/com/xq/example/1.0.0/example-1.0.0.jar"
            artifact.parent.mkdir(parents=True)
            artifact.write_bytes(b"original")
            create(root, "library", "1.0.0", "abc123")
            verify(root, "library", "1.0.0", "abc123")

            artifact.write_bytes(b"changed")

            with self.assertRaisesRegex(ValueError, "changed after build"):
                verify(root, "library", "1.0.0", "abc123")

    def test_promotion_is_byte_idempotent_and_rejects_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            artifact = repository / "com/xq/example/1.0.0/example-1.0.0.jar"
            artifact.parent.mkdir(parents=True)
            artifact.write_bytes(b"immutable")
            remote = MemoryRepository()

            promote(repository, remote, dry_run=False)
            promote(repository, remote, dry_run=False)
            remote.files[artifact.relative_to(repository).as_posix()] = b"different"

            with self.assertRaisesRegex(ValueError, "differs from staged bytes"):
                promote(repository, remote, dry_run=False)


if __name__ == "__main__":
    unittest.main()
