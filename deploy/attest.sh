#!/usr/bin/env bash
#
# The tailnet instance's whole operator surface, in one command, runnable over ssh from anywhere:
#
#   ssh chris-hp attest status                    what is running, on what code, with whose data
#   ssh chris-hp attest update                    pull main, rebuild, restart, wait until it answers
#   ssh chris-hp attest dataset portal --refresh --yes
#   ssh chris-hp attest reset --yes               destroy the database, re-seed the same dataset
#
# Every verb runs to completion without a terminal, which is the difference between this and
# calling rebuild.sh/reset.sh directly: `read -p "type destroy"` cannot be answered by a one-shot
# ssh command, so anything destructive here takes --yes instead and says what it is about to do.
#
# It delegates rather than duplicates — rebuild.sh, reset.sh, backup.sh and
# scripts/portal-snapshot.sh are still the implementations. What lives here is the argument
# handling those scripts cannot have (they are also run by hand), the environment reconciliation,
# and the tailnet-identity preflight that must happen before any compose command runs.
#
# Verbs:
#
#   status                 code, containers, health, dataset, tailnet identity, backups, disk
#   update [--backend|--web|--no-pull]
#   up                     start what is stopped, without rebuilding
#   reset --yes            destroy the database volume only, re-seed from .env's dataset
#   dataset NAME [--refresh] --yes
#                          synthetic | extracted | portal; --refresh re-snapshots the portal first
#   snapshot [--documents] refresh the Coolibah portal snapshot on this box
#   backup                 database dump + the sidecar's tailnet identity
#   restore FILE.dump      pg_restore a dump over the running database
#   identity [--backup|--restore FILE]
#                          assert this box still owns the `attest` node; save or put back its state
#   logs [SERVICE]         follow (ctrl-c to stop); no service means all of them
#   roles CSV              what an unheadered request is granted, then restart the backend
#   env [--fix]            reconcile deploy/.env against .env.example
#   install                put this script on PATH as `attest`
set -euo pipefail

# Resolved through symlinks: the installed entry point is ~/bin/attest pointing here, and every
# path below is relative to the checkout rather than to the caller's working directory.
self="$(readlink -f "${BASH_SOURCE[0]}")"
here="$(cd "$(dirname "$self")" && pwd)"
repo="$(cd "$here/.." && pwd)"
project=crewcomp
# Where the caller was standing. Everything below runs from deploy/, so a file named relatively on
# the command line — `attest restore deploy/backups/x.dump` from the checkout root — has to be
# resolved against the caller's directory before that cd, not silently looked for under deploy/.
caller_pwd="$PWD"
cd "$here"

resolve_path() {
  local given="${1:?}"
  case "$given" in
    /*) printf '%s\n' "$given"; return 0 ;;
  esac
  local candidate
  for candidate in "$caller_pwd/$given" "$here/$given"; do
    if [[ -e "$candidate" ]]; then printf '%s\n' "$(readlink -f "$candidate")"; return 0; fi
  done
  printf '%s\n' "$caller_pwd/$given"      # does not exist; report the path the caller meant
}

say()  { printf '==> %s\n' "$*"; }
warn() { printf '!!  %s\n' "$*" >&2; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }
rule() { printf -- '\n--- %s ---\n' "$*"; }

yes=0
assume_yes() {
  (( yes )) && return 0
  die "this destroys data and cannot ask — pass --yes if that is what you mean"
}

# ---------------------------------------------------------------------------- environment

env_keys()  { grep -oE '^[A-Z_]+=' "$1" | tr -d '='; }

# Every key the example names must be present, because a key that is merely absent gets
# docker-compose's default silently — which is how real crew data ended up mounted from inside the
# repository for a fortnight (CREWCOMP_PORTAL_ROOT was never added to .env, so `./portal` won).
# A missing key is therefore an error with a one-command fix, not a warning nobody reads.
env_check() {
  [[ -f .env ]] || die "deploy/.env is missing — cp .env.example .env and fill it in"
  local missing=() key
  while read -r key; do
    grep -qE "^${key}=" .env || missing+=("$key")
  done < <(env_keys .env.example)
  (( ${#missing[@]} == 0 )) && return 0
  printf 'deploy/.env is missing %d key(s) the example defines:\n' "${#missing[@]}" >&2
  printf '  %s\n' "${missing[@]}" >&2
  return 1
}

env_fix() {
  local missing=() key
  while read -r key; do
    grep -qE "^${key}=" .env || missing+=("$key")
  done < <(env_keys .env.example)
  if (( ${#missing[@]} == 0 )); then say "deploy/.env has every key .env.example defines."; return 0; fi
  say "Appending ${#missing[@]} missing key(s) with the example's values — check them"
  { printf '\n# Appended by `attest env --fix` — values copied from .env.example, verify them.\n'
    for key in "${missing[@]}"; do grep -E "^${key}=" .env.example; done
  } >> .env
  printf '  %s\n' "${missing[@]}"
  warn "a value copied from the example is a default, not a decision — read them before a reset"
}

# Sourced by every verb that talks to compose. The dataset roots are resolved here rather than
# left to compose, because both are symlinks in practice (~/coolibah-portal/latest points at the
# newest snapshot) and a bind mount pins whatever the path resolved to at container-creation time.
# Resolving it here means the mount is the snapshot directory itself and `latest` moving under it
# cannot silently leave the container serving the previous revision.
load_env() {
  env_check || die "run: attest env --fix"
  set -a; . ./.env; set +a
  # Tells reset.sh not to re-source .env and undo the path resolution below.
  export ATTEST_ENV_LOADED=1
  local root
  case "${CREWCOMP_DEV_SEED_DATASET:-synthetic}" in
    portal)
      root="$(readlink -f "${CREWCOMP_PORTAL_ROOT:-./portal}" 2>/dev/null || true)"
      [[ -n "$root" && -f "$root/portal-state.json" ]] ||
        die "dataset is 'portal' but no portal-state.json under ${CREWCOMP_PORTAL_ROOT:-./portal} — run: attest snapshot"
      export CREWCOMP_PORTAL_ROOT="$root"
      ;;
    extracted)
      root="$(readlink -f "${CREWCOMP_EXTRACT_ROOT:-./extracts}" 2>/dev/null || true)"
      [[ -n "$root" && -d "$root/seed" ]] ||
        die "dataset is 'extracted' but no seed/ under ${CREWCOMP_EXTRACT_ROOT:-./extracts}"
      export CREWCOMP_EXTRACT_ROOT="$root"
      ;;
  esac
}

# ---------------------------------------------------------------------------- tailnet identity

volume_exists() { docker volume inspect "${project}_$1" >/dev/null 2>&1; }

# The one preflight that runs before anything starts containers.
#
# The sidecar's identity lives entirely in the ts-state volume. Lose it and tailscaled registers a
# NEW device, which takes the name `attest-1` — so the stable hostname stops resolving, the Let's
# Encrypt name changes, and any machine share pointing at the old device is orphaned. It has
# happened once (reset.sh used to `down --volumes`). The database volume existing while ts-state
# does not is exactly that state, and it is worth refusing to compound rather than starting a
# stack that quietly renames itself.
identity_preflight() {
  if volume_exists db-data && ! volume_exists ts-state; then
    warn "${project}_ts-state is gone but the database volume is not."
    warn "Starting now would register a NEW tailnet device as 'attest-1' and orphan any share."
    warn "Put the identity back first:  attest identity --restore backups/ts-state-<date>.tar.gz"
    die  "refusing to start and rename the node"
  fi
}

ts_hostname() {
  docker compose exec -T ts-attest tailscale status --json 2>/dev/null |
    sed -n 's/.*"HostName": *"\([^"]*\)".*/\1/p' | head -1
}

identity_report() {
  rule "tailnet identity"
  if ! volume_exists ts-state; then
    warn "no ${project}_ts-state volume — this box does not currently hold a tailnet identity"
    return 0
  fi
  local host dns
  host="$(ts_hostname || true)"
  dns="$(docker compose exec -T ts-attest tailscale status --json 2>/dev/null |
           sed -n 's/.*"DNSName": *"\([^"]*\)".*/\1/p' | head -1)"
  if [[ -z "$host" ]]; then
    warn "the sidecar is not answering — check: attest logs ts-attest"
  elif [[ "$host" != attest ]]; then
    warn "this node calls itself '$host', not 'attest'."
    warn "It re-registered at some point: the stable name is gone and so is any share on the old"
    warn "device. Restore a saved identity (attest identity --restore ...) or repair the share in"
    warn "the admin console, then delete the stray device."
  else
    printf '  node        %s (%s)\n' "$host" "${dns%.}"
  fi
  # The auth key matters only when there is no state to reuse — but that is the moment it is
  # needed most, and a reusable key's ceiling is 90 days. A key that quietly expired turns
  # "restore the box" into "re-register by hand from the admin console".
  if [[ -n "${TS_AUTHKEY_ISSUED:-}" ]]; then
    local age
    age=$(( ( $(date +%s) - $(date -d "$TS_AUTHKEY_ISSUED" +%s) ) / 86400 ))
    printf '  auth key    issued %s (%s days ago)\n' "$TS_AUTHKEY_ISSUED" "$age"
    if (( age > 80 )); then
      warn "that key is near or past its 90-day ceiling — mint a new reusable, UNTAGGED one"
    fi
  else
    warn "TS_AUTHKEY_ISSUED is not set in .env, so nothing here can tell you when the key lapses"
  fi
  # Key expiry and tagging are deliberately not checked over the API. This node is untagged on
  # purpose — Tailscale is explicit that a tagged machine cannot be shared, and sharing strips
  # tags — and its key expiry was disabled in the admin console on 27 August 2026. If that ever
  # needs asserting from here, it is POST /api/v2/device/{id}/key with keyExpiryDisabled, which
  # needs an API credential this box does not otherwise want.
  return 0
}

identity_backup() {
  volume_exists ts-state || die "no ${project}_ts-state volume to save"
  mkdir -p backups
  local out="backups/ts-state-$(date +%Y%m%d-%H%M%S).tar.gz"
  # Through a container because the volume is root-owned and its contents must stay that way:
  # tailscaled.state is 0600 and tailscaled refuses to use it if that changes. The tarball is
  # handed back to the operator inside the same container — written as root it would be a file
  # this account can delete but not chmod, and it holds the box's tailnet credentials.
  docker run --rm \
    -v "${project}_ts-state:/state:ro" \
    -v "$here/backups:/out" \
    alpine:3 sh -c "tar czf '/out/$(basename "$out")' -C /state . &&
                    chown $(id -u):$(id -g) '/out/$(basename "$out")' &&
                    chmod 600 '/out/$(basename "$out")'" 2>/dev/null
  find backups -name 'ts-state-*.tar.gz' -type f -mtime "+${CREWCOMP_BACKUP_KEEP_DAYS:-14}" -delete
  say "$out ($(du -h "$out" | cut -f1)) — the only copy of this node's identity outside the volume"
}

identity_restore() {
  local tarball; tarball="$(resolve_path "${1:?attest identity --restore FILE}")"
  [[ -f "$tarball" ]] || die "no such file: $tarball"
  # Check the tarball is what it claims BEFORE anything is removed. This function empties the
  # volume, so a tarball without a node key in it would turn a recovery into the loss it exists
  # to undo.
  tar tzf "$tarball" 2>/dev/null | grep -qx './tailscaled.state' ||
    die "$tarball has no ./tailscaled.state in it — that is not a tailnet identity"
  assume_yes
  # And keep a way back from the recovery itself, if there is anything to keep.
  if volume_exists ts-state; then
    say "Saving the identity that is there now, before replacing it"
    identity_backup
  fi
  say "Replacing the sidecar's state from $(basename "$tarball")"
  docker compose stop ts-attest web >/dev/null
  docker volume create "${project}_ts-state" >/dev/null
  docker run --rm \
    -v "${project}_ts-state:/state" \
    -v "$(cd "$(dirname "$tarball")" && pwd):/in:ro" \
    alpine:3 sh -c "rm -rf /state/* /state/.[!.]* 2>/dev/null; tar xzf /in/$(basename "$tarball") -C /state"
  docker compose up --detach --wait --wait-timeout 300
  identity_report
}

# ---------------------------------------------------------------------------- verbs

v_status() {
  load_env
  rule "code"
  git -C "$repo" fetch --quiet origin 2>/dev/null || warn "cannot reach origin — is the deploy key still on the repo?"
  printf '  HEAD        %s  %s\n' "$(git -C "$repo" rev-parse --short HEAD)" "$(git -C "$repo" log -1 --format=%s)"
  local behind
  behind="$(git -C "$repo" rev-list --count HEAD..origin/main 2>/dev/null || echo '?')"
  if [[ "$behind" == 0 ]]; then printf '  origin/main up to date\n'
  else printf '  origin/main %s commit(s) ahead — attest update\n' "$behind"; fi
  [[ -n "$(git -C "$repo" status --porcelain)" ]] && warn "the working tree is dirty; a pull will refuse to fast-forward"

  rule "containers"
  docker compose ps --format 'table {{.Service}}\t{{.Status}}' 2>/dev/null || true

  rule "data"
  printf '  dataset     %s\n' "${CREWCOMP_DEV_SEED_DATASET:-synthetic}"
  case "${CREWCOMP_DEV_SEED_DATASET:-synthetic}" in
    portal)    printf '  source      %s\n' "${CREWCOMP_PORTAL_ROOT}" ;;
    extracted) printf '  source      %s\n' "${CREWCOMP_EXTRACT_ROOT}" ;;
  esac
  local people
  people="$(docker compose exec -T db psql -tAqU crewcomp -d crewcomp \
              -c 'select count(*) from person' 2>/dev/null | tr -d '[:space:]')"
  printf '  people      %s in the database\n' "${people:-unknown}"
  printf '  roles       %s\n' "${CREWCOMP_DEV_AUTH_DEFAULT_ROLES:-<compose default>}"
  local last
  last="$(ls -1t backups/crewcomp-*.dump 2>/dev/null | head -1 || true)"
  printf '  last backup %s\n' "${last:-none — attest backup}"

  identity_report

  rule "box"
  printf '  disk        %s\n' "$(df -h . | tail -1 | awk '{print $4" free of "$2}')"
  printf '  uptime      %s\n' "$(uptime -p 2>/dev/null || true)"
}

v_update() {
  load_env
  identity_preflight
  "$here/rebuild.sh" "$@"
}

v_up() {
  load_env
  identity_preflight
  docker compose up --detach --wait --wait-timeout 300
  docker compose ps
}

v_reset() {
  load_env
  identity_preflight
  assume_yes
  "$here/reset.sh" --yes
}

v_dataset() {
  local want="${1:?attest dataset synthetic|extracted|portal}"; shift || true
  local refresh=0
  while (( $# )); do
    case "$1" in
      --refresh) refresh=1 ;;
      *) die "unknown argument '$1' to dataset" ;;
    esac
    shift
  done
  case "$want" in synthetic|extracted|portal) ;; *) die "dataset must be synthetic, extracted or portal" ;; esac
  env_check || die "run: attest env --fix"
  assume_yes

  if (( refresh )); then
    [[ "$want" == portal ]] || die "--refresh only means anything for the portal dataset"
    v_snapshot
  fi

  sed -i "s|^CREWCOMP_DEV_SEED_DATASET=.*|CREWCOMP_DEV_SEED_DATASET=$want|" .env
  say "dataset is now '$want' in deploy/.env"
  load_env                     # validates the source exists BEFORE the database is destroyed
  identity_preflight
  "$here/reset.sh" --yes
}

# The snapshot script is the Mac's and the box's alike: it needs curl and python3 and nothing else,
# and it refuses a root inside this repository because what it fetches is real crew data. Running
# it here is what makes loading fresh portal data a remote command rather than a copy from a laptop.
v_snapshot() {
  local root="${COOLIBAH_PORTAL_ROOT:-$HOME/coolibah-portal}"
  local base="${COOLIBAH_PORTAL_URL:-https://matt.tail029157.ts.net}"
  # The portal is a laptop, so it is often simply off. The snapshot script's own timeout is 120s
  # per request, which is a long time to wait to be told that — and over a tailnet the symptom of
  # an offline peer is a connect that hangs rather than one that is refused. Ask cheaply first.
  if ! curl -fsS --connect-timeout 10 -m 25 -o /dev/null "$base/portal-state.json"; then
    die "$base is not answering — Matt's machine is probably off. Nothing was changed; the snapshot already under $root is untouched."
  fi
  say "Snapshotting the Coolibah portal into $root"
  COOLIBAH_PORTAL_ROOT="$root" "$repo/scripts/portal-snapshot.sh" "$@"
  printf '  latest -> %s\n' "$(readlink -f "$root/latest")"
}

v_backup() {
  load_env
  "$here/backup.sh"
  identity_backup
}

v_restore() {
  local dump; dump="$(resolve_path "${1:?attest restore FILE.dump}")"
  [[ -f "$dump" ]] || die "no such file: $dump"
  load_env
  assume_yes
  # The backend comes down first. pg_restore --clean drops every table, which an application
  # holding connections would either block with its locks or observe half-applied.
  say "Restoring $(basename "$dump") — stopping the backend while the schema is replaced"
  docker compose stop backend >/dev/null
  docker compose exec -T db pg_restore -U crewcomp -d crewcomp --clean --if-exists < "$dump"
  docker compose up --detach --wait --wait-timeout 300 backend
  docker compose ps
}

v_identity() {
  load_env
  case "${1:-}" in
    --backup)  identity_backup ;;
    --restore) shift; identity_restore "${1:-}" ;;
    "")        identity_report ;;
    *)         die "unknown argument '$1' to identity" ;;
  esac
}

v_logs() { load_env; docker compose logs --tail 100 --follow ${1:+"$1"}; }

v_roles() {
  local roles="${1:?attest roles crew_coordinator,data_steward}"
  env_check || die "run: attest env --fix"
  [[ -n "$roles" ]] || die "never empty: the SPA would render a sign-in screen pointing at a BFF that does not exist"
  sed -i "s|^CREWCOMP_DEV_AUTH_DEFAULT_ROLES=.*|CREWCOMP_DEV_AUTH_DEFAULT_ROLES=$roles|" .env
  load_env
  docker compose up --detach --wait --wait-timeout 120 backend
  say "an unheadered request is now: $roles"
}

v_env() {
  case "${1:-}" in
    --fix) env_fix ;;
    "")    if env_check; then say "deploy/.env has every key .env.example defines."; fi ;;
    *)     die "unknown argument '$1' to env" ;;
  esac
}

# ~/bin rather than /usr/local/bin because this box has no passwordless sudo. `ssh host cmd` runs a
# non-login shell, so the PATH line goes at the TOP of .bashrc — above the guard that returns early
# when the shell is not interactive, which is precisely the case for a one-shot ssh command.
v_install() {
  mkdir -p "$HOME/bin"
  ln -sfn "$self" "$HOME/bin/attest"
  say "$HOME/bin/attest -> $self"
  local marker='# attest: PATH for one-shot `ssh <box> attest ...` commands (must precede the'
  if grep -qF "$marker" "$HOME/.bashrc" 2>/dev/null; then
    say ".bashrc already exports the PATH non-interactively"
  else
    local tmp; tmp="$(mktemp)"
    { printf '%s\n' "$marker"
      printf '%s\n' '# non-interactive guard below, which a remote command hits).'
      printf '%s\n\n' 'PATH="$HOME/bin:$PATH"'
      cat "$HOME/.bashrc" 2>/dev/null
    } > "$tmp"
    mv "$tmp" "$HOME/.bashrc"
    say "prepended a PATH line to ~/.bashrc"
  fi
  printf '\nFrom anywhere with ssh access:\n  ssh %s attest status\n' "$(hostname -s)"
}

usage() { sed -n '2,45p' "$self" | sed 's/^# \{0,1\}//'; }

# ---------------------------------------------------------------------------- dispatch

verb="${1:-status}"; shift || true
args=()
while (( $# )); do
  case "$1" in
    --yes|-y) yes=1 ;;
    *) args+=("$1") ;;
  esac
  shift
done
set -- ${args+"${args[@]}"}

case "$verb" in
  status)     v_status "$@" ;;
  update)     v_update "$@" ;;
  up)         v_up "$@" ;;
  reset)      v_reset "$@" ;;
  dataset)    v_dataset "$@" ;;
  snapshot)   v_snapshot "$@" ;;
  backup)     v_backup "$@" ;;
  restore)    v_restore "$@" ;;
  identity)   v_identity "$@" ;;
  logs)       v_logs "$@" ;;
  roles)      v_roles "$@" ;;
  env)        v_env "$@" ;;
  install)    v_install "$@" ;;
  help|-h|--help) usage ;;
  *) die "unknown verb '$verb' — attest help" ;;
esac
