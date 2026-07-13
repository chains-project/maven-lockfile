#!/usr/bin/env python3
"""Compute the anchor root of a maven-lockfile.

The root is SHA-256 over the RFC 8785 (JCS) canonical form of lockfile.json, not
over the file bytes. Canonicalizing first means the root survives reformatting,
key reordering and whitespace changes, so it is a property of the pinned
dependency set rather than of one particular serialization.

    python3 canonicalize.py lockfile.json

Prints the raw-bytes SHA-256 and the JCS anchor root, and writes the canonical
bytes next to the input as <name>.jcs.json when --write is passed.
"""
import argparse
import hashlib
import json
import sys

import jsoncanon


def anchor_root(lockfile_bytes: bytes) -> tuple[str, bytes]:
    """Return (hex root, canonical bytes) for the given lockfile.json bytes."""
    doc = json.loads(lockfile_bytes)
    reject_non_integer_numbers(doc)
    canonical = jsoncanon.canonicalize(doc)
    return hashlib.sha256(canonical).hexdigest(), canonical


def reject_non_integer_numbers(node) -> None:
    """JCS serializes floats via ECMAScript Number-to-String, which is exact but
    easy to get wrong in a reimplementation. A maven-lockfile contains only
    strings and integers, so refuse to canonicalize anything else rather than
    emit a root another implementation might not reproduce."""
    if isinstance(node, bool):
        return
    if isinstance(node, float):
        raise SystemExit("non-integer number in lockfile: %r (see comment above)" % node)
    if isinstance(node, dict):
        for value in node.values():
            reject_non_integer_numbers(value)
    elif isinstance(node, list):
        for value in node:
            reject_non_integer_numbers(value)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lockfile", help="path to lockfile.json")
    parser.add_argument("--write", action="store_true", help="write <name>.jcs.json")
    args = parser.parse_args()

    raw = open(args.lockfile, "rb").read()
    root, canonical = anchor_root(raw)

    print("raw bytes        : %d" % len(raw))
    print("raw sha256       : %s" % hashlib.sha256(raw).hexdigest())
    print("canonical bytes  : %d" % len(canonical))
    print("anchor root      : %s" % root)

    if args.write:
        out = args.lockfile.removesuffix(".json") + ".jcs.json"
        open(out, "wb").write(canonical)
        print("wrote            : %s" % out)


if __name__ == "__main__":
    sys.exit(main())
