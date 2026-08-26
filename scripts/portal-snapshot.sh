#!/usr/bin/env bash
#
# Snapshot the TSV Coolibah crew portal (Matt's proof-of-concept, shared over Tailscale)
# into a local directory OUTSIDE this repository, so it can be loaded as a dev dataset.
#
#   ./scripts/portal-snapshot.sh                 portal-state.json + documents/index.json
#   ./scripts/portal-snapshot.sh --documents     ... plus every uploaded file (~460 MB first
#                                                run; later runs fetch only what changed)
#   ./scripts/portal-snapshot.sh --root PATH     where to keep snapshots (default ~/coolibah-portal)
#   ./scripts/portal-snapshot.sh --url URL       the portal share (default https://matt.tail029157.ts.net)
#
# Layout it maintains:
#
#   $root/snapshots/<savedAt>-rev<rev>/portal-state.json     one directory per portal revision
#   $root/snapshots/<savedAt>-rev<rev>/documents-index.json
#   $root/latest -> snapshots/<newest>                       what a loader should read
#   $root/documents/<path as the portal files it>            shared across snapshots; files are
#                                                            content-addressed by the index's
#                                                            SHA-256 so re-runs skip what is
#                                                            already here and verify what isn't
#
# EVERYTHING under $root is real crew data (names, certificate expiries, medical dates,
# the certificates themselves). It must never be committed to this repository — same rule
# as the POC extracts in ~/shipping. docs/handoff/coolibah-portal-dataset.md is the map of
# what is in here and how it becomes the `portal` dev dataset.
set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

base="${COOLIBAH_PORTAL_URL:-https://matt.tail029157.ts.net}"
root="${COOLIBAH_PORTAL_ROOT:-$HOME/coolibah-portal}"
with_documents=0
while (( $# )); do
  case "$1" in
    --documents) with_documents=1 ;;
    --root) root="${2:?--root needs a directory}"; shift ;;
    --url) base="${2:?--url needs the portal base URL}"; shift ;;
    -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown argument '$1'. Use: [--documents] [--root PATH] [--url URL]" ;;
  esac
  shift
done

command -v python3 >/dev/null || die "needs python3"
case "$root" in
  "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"*) die "--root must be outside this repository — the snapshot is real crew data" ;;
esac

mkdir -p "$root/snapshots"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

echo "Fetching portal state from $base ..."
curl -fsS -m 120 -o "$tmp/portal-state.json" "$base/portal-state.json" ||
  die "cannot reach $base — is this machine on the tailnet, and is the share up?"
curl -fsS -m 120 -o "$tmp/documents-index.json" "$base/documents/index.json"

read -r rev saved < <(python3 - "$tmp/portal-state.json" <<'EOF'
import json, sys
s = json.load(open(sys.argv[1]))
print(s["rev"], s["savedAt"].replace(":", "").replace("-", "")[:13])
EOF
)

snap="$root/snapshots/${saved}-rev${rev}"
if [[ -d "$snap" ]]; then
  echo "Already have revision $rev ($snap) — state unchanged."
else
  mkdir -p "$snap"
  mv "$tmp/portal-state.json" "$tmp/documents-index.json" "$snap/"
  echo "New snapshot: revision $rev -> $snap"
fi
ln -sfn "snapshots/$(basename "$snap")" "$root/latest"

if (( with_documents )); then
  echo "Mirroring uploaded files into $root/documents ..."
  python3 - "$snap/documents-index.json" "$base" "$root/documents" <<'EOF'
import hashlib, json, pathlib, sys, urllib.parse, urllib.request

index_path, base, dest = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
entries = json.load(open(index_path))
fetched = skipped = 0
failures = []
for e in entries:
    target = dest / e["path"]
    if target.exists():
        if e["checksum"]:
            if hashlib.sha256(target.read_bytes()).hexdigest() == e["checksum"]:
                skipped += 1
                continue
        elif target.stat().st_size == e["sizeBytes"]:
            skipped += 1
            continue
    target.parent.mkdir(parents=True, exist_ok=True)
    url = base + "/documents/" + urllib.parse.quote(e["path"])
    try:
        data = urllib.request.urlopen(url, timeout=60).read()
    except Exception as exc:
        failures.append(f"{e['path']}: {exc}")
        continue
    if e["checksum"] and hashlib.sha256(data).hexdigest() != e["checksum"]:
        failures.append(f"{e['path']}: checksum mismatch — not kept")
        continue
    target.write_bytes(data)
    fetched += 1
print(f"{fetched} fetched, {skipped} already here, {len(failures)} failed of {len(entries)}")
for f in failures:
    print(f"  FAILED {f}", file=sys.stderr)
sys.exit(1 if failures else 0)
EOF
fi

echo "Done. Loaders should read $root/latest"
