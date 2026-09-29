#!/usr/bin/env python3
"""Create and verify immutable Maven staging manifests."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys


MANIFEST_NAME = "manifest.json"
CHECKSUM_NAME = "SHA256SUMS"


def staged_files(root: Path) -> list[Path]:
    excluded = {root / MANIFEST_NAME, root / CHECKSUM_NAME}
    return sorted(path for path in root.rglob("*") if path.is_file() and path not in excluded)


def digest(path: Path) -> str:
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def snapshot(root: Path) -> dict[str, str]:
    return {path.relative_to(root).as_posix(): digest(path) for path in staged_files(root)}


def create(root: Path, product: str, version: str, commit: str) -> None:
    files = snapshot(root)
    if not files:
        raise ValueError(f"staging directory contains no artifacts: {root}")
    manifest = {
        "commit": commit,
        "files": files,
        "formatVersion": 1,
        "product": product,
        "version": version,
    }
    (root / MANIFEST_NAME).write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    checksum_lines = [f"{file_digest}  {name}" for name, file_digest in sorted(files.items())]
    (root / CHECKSUM_NAME).write_text("\n".join(checksum_lines) + "\n", encoding="utf-8")


def verify(root: Path, product: str, version: str, commit: str) -> None:
    manifest_path = root / MANIFEST_NAME
    checksum_path = root / CHECKSUM_NAME
    if not manifest_path.is_file() or not checksum_path.is_file():
        raise ValueError("staging manifest or checksum file is missing")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected_identity = {"product": product, "version": version, "commit": commit}
    actual_identity = {key: manifest.get(key) for key in expected_identity}
    if actual_identity != expected_identity or manifest.get("formatVersion") != 1:
        raise ValueError(
            f"staging identity mismatch: expected {expected_identity}, found {actual_identity}"
        )
    actual_files = snapshot(root)
    if manifest.get("files") != actual_files:
        raise ValueError("staged file set or SHA-256 digest changed after build")
    expected_lines = [
        f"{file_digest}  {name}" for name, file_digest in sorted(actual_files.items())
    ]
    actual_lines = checksum_path.read_text(encoding="utf-8").splitlines()
    if actual_lines != expected_lines:
        raise ValueError("SHA256SUMS does not match manifest")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("create", "verify"))
    parser.add_argument("root", type=Path)
    parser.add_argument("product", choices=("all", "library", "plugin"))
    parser.add_argument("version")
    parser.add_argument("commit")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if args.command == "create":
            create(args.root, args.product, args.version, args.commit)
        else:
            verify(args.root, args.product, args.version, args.commit)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"staging manifest error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
