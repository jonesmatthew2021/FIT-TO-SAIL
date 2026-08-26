#!/usr/bin/env bash
#
# Destroy the instance's database and start again from a fresh seed.
#
#   ./deploy/reset.sh              asks for confirmation
#   ./deploy/reset.sh --yes        does not — for `attest reset --yes` over ssh, which has no
#                                  terminal to answer a prompt with
#
# This is how the dataset switch takes effect: the seeder refuses a database that already holds
# people (§11 — the synthetic and extracted catalogues must never mix), so `synthetic` and
# `extracted` are only ever chosen at the moment a database is created. It is also the way back
# from a Flyway checksum failure caused by editing a migration that has already run here.
#
# Everything anybody entered through the UI is deleted. There is no undo; take a backup first if
# the contents matter (./deploy/backup.sh).
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

assume_yes=0
while (( $# )); do
  case "$1" in
    --yes|-y) assume_yes=1 ;;
    -h|--help) sed -n '2,16p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument '$1'" >&2; exit 1 ;;
  esac
  shift
done

# attest.sh has already loaded .env and resolved the dataset roots to real directories; re-reading
# the file here would put the unresolved values back and undo that. Run by hand, this is still the
# only thing that loads them.
if [[ -z "${ATTEST_ENV_LOADED:-}" ]]; then
  set -a; [[ -f .env ]] && . ./.env; set +a
fi

dataset="${CREWCOMP_DEV_SEED_DATASET:-synthetic}"

echo "This deletes the CREWCOMP database volume on $(hostname) and re-seeds it as '$dataset'."
if [[ "$dataset" == extracted || "$dataset" == portal ]]; then
  echo "'$dataset' is REAL crew data. It will be at rest on this box, readable by anybody the"
  echo "tailnet ACL lets in, behind an authentication shim that trusts every request."
fi
if (( assume_yes )); then
  echo "--yes given: not asking."
else
  read -r -p "Type the word 'destroy' to continue: " confirm
  [[ "$confirm" == destroy ]] || { echo "Nothing done."; exit 1; }
fi

# Only the database volume. `down --volumes` would also destroy ts-state — the sidecar's tailnet
# identity — and a re-registered node comes back as `attest-2`, breaking the stable name and any
# machine share on the old device (this happened; the admin-console cleanup is no fun).
docker compose down
docker volume rm crewcomp_db-data 2>/dev/null || echo "(no database volume to remove)"
docker compose up --detach --wait --wait-timeout 300
docker compose ps
