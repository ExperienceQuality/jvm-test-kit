#!/usr/bin/env python3
"""Promote exact staged Maven bytes and verify remote immutability."""

from __future__ import annotations

import argparse
import base64
from dataclasses import dataclass
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class RemoteRepository:
    base_url: str
    username: str
    token: str

    def url(self, relative: Path) -> str:
        encoded = "/".join(quote(part, safe="._-") for part in relative.parts)
        return f"{self.base_url.rstrip('/')}/{encoded}"

    def request(self, relative: Path, method: str, data: bytes | None = None) -> tuple[int, bytes]:
        credential = base64.b64encode(f"{self.username}:{self.token}".encode()).decode()
        request = Request(
            self.url(relative),
            data=data,
            method=method,
            headers={"Authorization": f"Basic {credential}"},
        )
        try:
            with urlopen(request, timeout=30) as response:
                return response.status, response.read()
        except HTTPError as error:
            return error.code, error.read()


def publication_files(repository: Path) -> list[Path]:
    files = []
    for path in repository.rglob("*"):
        if not path.is_file() or path.name.startswith("maven-metadata.xml"):
            continue
        files.append(path)
    return sorted(files)


def promote(repository: Path, remote: RemoteRepository, dry_run: bool) -> None:
    files = publication_files(repository)
    if not files:
        raise ValueError(f"staged Maven repository contains no immutable files: {repository}")
    for path in files:
        relative = path.relative_to(repository)
        local_bytes = path.read_bytes()
        if dry_run:
            print(f"would promote {relative.as_posix()}")
            continue
        status, remote_bytes = remote.request(relative, "GET")
        if status == 200:
            if remote_bytes != local_bytes:
                raise ValueError(f"remote artifact differs from staged bytes: {relative}")
            print(f"verified existing {relative.as_posix()}")
            continue
        if status != 404:
            raise ValueError(f"remote preflight returned HTTP {status}: {relative}")
        upload_status, _ = remote.request(relative, "PUT", local_bytes)
        if upload_status not in {200, 201, 204}:
            raise ValueError(f"remote upload returned HTTP {upload_status}: {relative}")
        verify_status, uploaded_bytes = remote.request(relative, "GET")
        if verify_status != 200 or uploaded_bytes != local_bytes:
            raise ValueError(f"remote verification failed after upload: {relative}")
        print(f"promoted {relative.as_posix()}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("repository", type=Path)
    parser.add_argument("base_url")
    parser.add_argument("--username", default="")
    parser.add_argument("--token", default="")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.dry_run and (not args.username or not args.token):
        print("promotion requires username and token", file=sys.stderr)
        return 2
    try:
        promote(
            args.repository,
            RemoteRepository(args.base_url, args.username, args.token),
            args.dry_run,
        )
    except (OSError, URLError, ValueError) as error:
        print(f"Maven promotion error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
