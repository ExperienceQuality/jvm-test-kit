#!/usr/bin/env python3
"""Copy bounded, non-sensitive JUnit XML evidence. Fail closed on bad input."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

MAX_TEXT = 8192
SENSITIVE = {"system-out", "system-err"}

def scrub(element: ET.Element) -> None:
    for key in list(element.attrib):
        if key.lower() in {"hostname", "timestamp"}:
            element.attrib.pop(key)
    if element.text and len(element.text) > MAX_TEXT:
        element.text = element.text[:MAX_TEXT] + "...[truncated]"
    if element.tail and len(element.tail) > MAX_TEXT:
        element.tail = element.tail[:MAX_TEXT] + "...[truncated]"
    for child in list(element):
        if child.tag.rsplit("}", 1)[-1] in SENSITIVE:
            element.remove(child)
        else:
            scrub(child)

def main() -> int:
    if len(sys.argv) != 3:
        print("usage: sanitize_junit.py INPUT OUTPUT", file=sys.stderr)
        return 2
    source, target = map(Path, sys.argv[1:])
    files = sorted(source.rglob("*.xml")) if source.exists() else []
    if not files:
        print(f"No JUnit XML found under {source}", file=sys.stderr)
        return 1
    target.mkdir(parents=True, exist_ok=True)
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as exc:
            print(f"Cannot sanitize {path}: {exc}", file=sys.stderr)
            return 1
        scrub(root)
        destination = target / path.relative_to(source)
        destination.parent.mkdir(parents=True, exist_ok=True)
        ET.ElementTree(root).write(destination, encoding="utf-8", xml_declaration=True)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
