# Anti-backdating anchor for a lockfile

A lockfile pins *what* was built. It does not establish *when* it existed. Whoever
can rewrite the lockfile can also rewrite its recorded date, so a lockfile alone
cannot rule out that a dependency set was constructed after the fact and
presented as if it had always been there.

An OpenTimestamps proof closes that gap. It commits the lockfile's canonical hash
into the Bitcoin block chain, which gives an upper bound on the lockfile's age
that no one operates and no one can move. It is additive to the Sigstore
attestation this project already publishes, and it is not a replacement for it.

## What it does and does not prove

| | |
|---|---|
| Proves | this exact dependency set existed at or before block *N*'s time |
| Does not prove | the build is reproducible, the artifacts are genuine, or the lockfile is *correct* |

Existence and ordering only. Everything about *what* was built remains the job of
the lockfile, the Sigstore attestation, and the rebuild tooling.

## Why not rely on the Sigstore timestamp

Sigstore's timestamp is asserted by the transparency log operator. If the log
operator is inside your threat model, the timestamp inherits exactly the trust
you were trying to remove. Bitcoin's header chain is a clock nobody runs; the
proof is checked offline against the chain itself, with no server and no key
belonging to any third party, including whoever produced the proof.

The honest framing is that these are complementary. Sigstore says *who* signed
and *what* they signed. The anchor says *by when it existed*, and it survives the
log operator being wrong.

## The root is over canonical bytes, not file bytes

`lockfile.json` is JSON, so the anchor root is SHA-256 over its **RFC 8785 (JCS)**
canonical form, not over the raw file. Reformatting, reordering keys or changing
whitespace then does not change the root, and the root is a property of the
pinned dependency set rather than of one serialization of it.

A maven-lockfile contains only strings and integers. JCS number serialization is
therefore unambiguous here, and `scripts/anchor/canonicalize.py` refuses to
proceed if a non-integer number ever appears, rather than emit a root that a
different JCS implementation might not reproduce.

## Where the proof file lives

The proof is a single small binary, roughly 1.5 KB, one per lockfile:

```
lockfile.json          # already committed
lockfile.json.ots      # the proof, committed next to it
```

For releases, the natural home is beside the `.sigstore.json` that the release
job already uploads to Maven Central. That reuses a pipeline this project already
runs and adds no new distribution channel.

## How it is verified

```
pip install -r scripts/anchor/requirements.txt
python3 scripts/anchor/verify_anchor.py lockfile.json lockfile.json.ots
```

which recomputes the canonical root, confirms the proof commits to that exact
root, reports the Bitcoin block if the proof is confirmed, and shows that
mutating one field breaks the bind.

None of that needs a network or a Bitcoin node, so it runs under a blocked-egress
runner. It checks the binding, not the chain.

`scripts/anchor/anchor-verify.yml.example` is a ready-to-use workflow doing
exactly that, pinned to the same action SHAs this repository already uses. It is
left as an example rather than dropped into `.github/workflows/` so that CI
remains yours to opt into.

Confirming that the named block is really on the Bitcoin chain is a separate,
deliberately independent step, and it should not trust this repository either:

```
ots verify lockfile.json.ots        # against your own Bitcoin node
```

## Reproducing the committed proof

`scripts/anchor/testdata/lockfile-8e6d7ab.jcs.json.ots` anchors this project's
own lockfile, dogfooded rather than synthetic:

| | |
|---|---|
| Commit | `8e6d7ab3900135b32ce85a8ade2464d3c9d05dee` |
| Raw `lockfile.json` SHA-256 | `c4476eaa6edc8011975c5d36bfd9d6a88c5c0a749451a647065a16e055c64aad` |
| JCS anchor root | `f7f931b34e8d3869ad2e942b252952a93670e4f1f1e588ee826b8a009c189012` |
| Bitcoin block | 957400 |

Reproduce the root from the repository's own file:

```
python3 scripts/anchor/canonicalize.py lockfile.json
# anchor root : f7f931b34e8d3869ad2e942b252952a93670e4f1f1e588ee826b8a009c189012
```

## Stamping a new lockfile

```
pip install -r scripts/anchor/requirements.txt
python3 scripts/anchor/canonicalize.py lockfile.json --write
ots stamp lockfile.jcs.json
```

Stamping contacts the OpenTimestamps calendars, so it needs egress to them; a
release job hardened with `egress-policy: block` must allow those four hosts, as
`scripts/anchor/anchor-verify.yml.example` documents. The calendars aggregate many
submissions into one Bitcoin transaction, so the marginal cost of an anchor is
zero and there is no wallet, no coin and no chain dependency introduced into the
build itself.

A fresh proof is `PENDING` until a block confirms it, typically within a few
hours. `ots upgrade` then rewrites the proof with the Bitcoin attestation, and it
is that upgraded proof which is worth committing.
