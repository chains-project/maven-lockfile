#!/usr/bin/env python3
"""Verify that an OpenTimestamps proof anchors a given maven-lockfile.

    python3 verify_anchor.py lockfile.json lockfile.jcs.json.ots

Checks, in order:

  1. recompute the JCS anchor root from lockfile.json, independently;
  2. confirm the .ots proof commits to exactly that root;
  3. report the OpenTimestamps status honestly: the Bitcoin block height if the
     proof is confirmed, PENDING if the calendars have it but no block does yet;
  4. negative check: mutating one field must change the root and break the bind.

Step 1-2 and 4 need no network and no Bitcoin node, which is why this is safe to
run under a blocked-egress CI runner. Step 3 only reads what is already inside
the .ots file; it does not ask anyone whether the block is real. Confirming that
block 957400 is on the real Bitcoin chain is the verifier's job, with `ots
verify` against their own node, and it deliberately trusts nobody here.

Exit status is 0 only if the root binds and the negative check holds.
"""
import copy
import hashlib
import json
import re
import subprocess
import sys

import jsoncanon


def jcs_root(doc) -> str:
    return hashlib.sha256(jsoncanon.canonicalize(doc)).hexdigest()


def read_ots(ots_path: str) -> tuple[str | None, str]:
    """Return (hash the proof commits to, human status) by reading the proof."""
    info = subprocess.run(
        ["ots", "info", ots_path], capture_output=True, text=True
    ).stdout

    committed = re.search(r"File sha256 hash:\s*([0-9a-f]{64})", info)
    block = re.search(r"BitcoinBlockHeaderAttestation\((\d+)\)", info)

    if block:
        status = "CONFIRMED in Bitcoin block %s" % block.group(1)
    elif "PendingAttestation" in info:
        status = "PENDING (held by calendars, not yet in a block)"
    else:
        status = "UNKNOWN"

    return (committed.group(1) if committed else None), status


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    lockfile_path, ots_path = sys.argv[1], sys.argv[2]

    doc = json.loads(open(lockfile_path, "rb").read())
    root = jcs_root(doc)
    committed, status = read_ots(ots_path)
    binds = committed == root

    mutated = copy.deepcopy(doc)
    mutated["lockFileVersion"] = 999999
    mutation_detected = jcs_root(mutated) != committed

    print("recomputed anchor root : %s" % root)
    print("root committed by .ots : %s" % committed)
    print("root binds to proof    : %s" % ("YES" if binds else "NO"))
    print("OpenTimestamps status  : %s" % status)
    print("mutation breaks bind   : %s" % ("YES" if mutation_detected else "NO"))

    if binds and mutation_detected:
        print("\nPASS: this lockfile is the one the proof commits to.")
        return 0
    print("\nFAIL: the proof does not anchor this lockfile.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
