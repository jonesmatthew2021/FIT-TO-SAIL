#!/usr/bin/env bash
#
# Dump the instance's database to deploy/backups/. From cron on the box, go through `attest`
# rather than this script directly:
#
#   15 2 * * *  /home/<you>/bin/attest backup >/dev/null 2>&1
#
# — because `attest backup` runs this and then saves the sidecar's tailnet identity beside the
# dump. The database can be re-seeded and the identity cannot: lose ts-state and the node comes
# back as `attest-1`, orphaning the stable name and every machine share pointing at the old one.
#
# The dev stack has nothing worth backing up by design — it re-seeds on every start. This box is
# the opposite: it accumulates whatever anybody does on it, which is what makes a demonstration
# build on the last one instead of starting from the fixture every time.
#
# Custom format (-Fc), so a restore can be selective:
#   docker compose exec -T db pg_restore -U crewcomp -d crewcomp --clean < backups/<file>.dump
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

keep_days="${CREWCOMP_BACKUP_KEEP_DAYS:-14}"
# 700: a dump of the extracted or portal dataset is real crew data, and the identity tarball
# beside it is this box's tailnet credentials.
mkdir -p backups && chmod 700 backups
out="backups/crewcomp-$(date +%Y%m%d-%H%M%S).dump"

# -T because cron has no TTY. Written to a temporary name first so an interrupted dump is never
# left looking like a good one.
docker compose exec -T db pg_dump -U crewcomp -Fc crewcomp > "$out.partial"
mv "$out.partial" "$out"
chmod 600 "$out"

find backups -name 'crewcomp-*.dump' -type f -mtime "+$keep_days" -delete
echo "$out ($(du -h "$out" | cut -f1))"
