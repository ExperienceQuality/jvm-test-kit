#!/usr/bin/env python3
"""Copy JUnit XML while removing output streams and property values."""

from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET


def sanitize(source_root: Path, destination_root: Path) -> None:
    destination_root.mkdir(parents=True, exist_ok=True)
    for source in source_root.rglob("*.xml"):
        tree = ET.parse(source)
        root = tree.getroot()
        for parent in root.iter():
            for child in list(parent):
                if child.tag in {"system-out", "system-err"}:
                    parent.remove(child)
        for prop in root.iter("property"):
            if "value" in prop.attrib:
                prop.set("value", "<redacted>")
            prop.text = None
        destination = destination_root / source.relative_to(source_root)
        destination.parent.mkdir(parents=True, exist_ok=True)
        tree.write(destination, encoding="utf-8", xml_declaration=True)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: sanitize_junit.py SOURCE_DIR DESTINATION_DIR", file=sys.stderr)
        return 2
    source = Path(sys.argv[1])
    destination = Path(sys.argv[2])
    if not source.is_dir():
        print(f"JUnit source directory does not exist: {source}", file=sys.stderr)
        return 1
    if destination.exists():
        shutil.rmtree(destination)
    sanitize(source, destination)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
