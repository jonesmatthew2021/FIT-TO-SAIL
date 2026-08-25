#!/usr/bin/env bash
#
# Rebuild the tailnet instance from the current checkout, on the box itself.
#
#   ./deploy/rebuild.sh              pull, rebuild both images, restart, wait until it answers
#   ./deploy/rebuild.sh --no-pull    rebuild what is already checked out
#   ./deploy/rebuild.sh --backend    just the backend image
#   ./deploy/rebuild.sh --web        just the SPA image
#
# What this deliberately does NOT do is touch the database. New code runs its Flyway migrations
# against the data that is already there, which is the entire point of the box — and the first
# place the forward-only, expand/contract rule is enforced rather than asserted. A migration that
# breaks it fails start-up here, loudly, before it can do the same in production.
#
# To start over instead: ./deploy/reset.sh
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

pull=1
services=()
while (( $# )); do
  case "$1" in
    --no-pull) pull=0 ;;
    --backend) services+=(backend) ;;
    --web)     services+=(web) ;;
    -h|--help) sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument '$1'" >&2; exit 1 ;;
  esac
  shift
done

[[ -f .env ]] || { echo "deploy/.env is missing — copy .env.example and fill it in." >&2; exit 1; }

if (( pull )); then
  echo "==> Pulling"
  git -C .. pull --ff-only
fi
echo "==> Building $(git -C .. rev-parse --short HEAD) — $(git -C .. log -1 --format=%s)"

docker compose build "${services[@]}"

# --wait blocks on the backend's healthcheck, so this command returning means the API is actually
# answering /health/ready — not merely that a container started. A failed migration or a bad
# config shows up as a timeout here rather than as a screen that half loads ten minutes later.
echo "==> Starting"
if ! docker compose up --detach --wait --wait-timeout 300; then
  echo
  echo "!! The stack did not come up. Last 60 lines from the backend:" >&2
  docker compose logs --tail 60 backend >&2
  exit 1
fi

echo
docker compose ps

# The stack reaches the tailnet through its own node, not through the host's — so the thing to
# report is the sidecar's serve config, and its absence is a failure of this stack rather than
# something for the operator to go and fix by hand on the host.
echo
echo "Serving:"
docker compose exec -T ts-attest tailscale serve status 2>/dev/null \
  || echo "  !! the ts-attest sidecar is not serving — check: docker compose logs ts-attest" >&2
