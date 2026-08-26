#!/usr/bin/env bash
#
# Destroy the instance's database and start again from a fresh seed.
#
#   ./deploy/reset.sh
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
set -a; [[ -f .env ]] && . ./.env; set +a

dataset="${CREWCOMP_DEV_SEED_DATASET:-synthetic}"

echo "This deletes the CREWCOMP database volume on $(hostname) and re-seeds it as '$dataset'."
if [[ "$dataset" == extracted || "$dataset" == portal ]]; then
  echo "'$dataset' is REAL crew data. It will be at rest on this box, readable by anybody the"
  echo "tailnet ACL lets in, behind an authentication shim that trusts every request."
fi
read -r -p "Type the word 'destroy' to continue: " confirm
[[ "$confirm" == destroy ]] || { echo "Nothing done."; exit 1; }

docker compose down --volumes
docker compose up --detach --wait --wait-timeout 300
docker compose ps
