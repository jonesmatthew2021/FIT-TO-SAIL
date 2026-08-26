#!/usr/bin/env bash
#
# Start the local development stack: Quarkus dev mode on :8080 and the admin-web Vite
# server on :5173, which proxies /api to it.
#
#   ./scripts/dev-start.sh                        both
#   ./scripts/dev-start.sh backend                just the backend
#   ./scripts/dev-start.sh web                    just the SPA
#   ./scripts/dev-start.sh --dataset extracted    seed the POC's workbook extracts (REAL crew
#                                                 data, read from ~/shipping by default —
#                                                 override with --extract-root PATH)
#   ./scripts/dev-start.sh --dataset portal       seed the Coolibah portal snapshot (REAL crew
#                                                 data, read from ~/coolibah-portal/latest —
#                                                 override with --portal-root PATH; refresh the
#                                                 snapshot with ./scripts/portal-snapshot.sh)
#
# The dataset only takes effect against an empty database: run ./scripts/dev-stop.sh first so
# the database container is reaped, or the previous dataset is still what you will see.
#
# Both run in the background; logs land in .dev/. Stop them with ./scripts/dev-stop.sh —
# in particular stop the backend before `./mvnw verify`, because dev mode and a Maven build
# fight over target/.
#
# This is a development-only convenience. It sets no production configuration and its MCP
# token is a local credential for a server that production disables by default (§13.1).
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-common.sh"

what="all"
dataset="${CREWCOMP_DEV_SEED_DATASET:-synthetic}"
extract_root="${CREWCOMP_DEV_SEED_EXTRACT_ROOT:-$HOME/shipping}"
portal_root="${CREWCOMP_DEV_SEED_PORTAL_ROOT:-$HOME/coolibah-portal/latest}"
while (( $# )); do
  case "$1" in
    all|backend|web) what="$1" ;;
    --dataset) dataset="${2:?--dataset needs a value: synthetic | extracted | portal}"; shift ;;
    --extract-root) extract_root="${2:?--extract-root needs a directory}"; shift ;;
    --portal-root) portal_root="${2:?--portal-root needs a directory}"; shift ;;
    -h|--help) sed -n '2,24p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown argument '$1'. Use: all | backend | web [--dataset synthetic|extracted|portal] [--extract-root PATH] [--portal-root PATH]" ;;
  esac
  shift
done

case "$dataset" in
  synthetic|extracted|portal) ;;
  *) die "Unknown dataset '$dataset'. Use: synthetic | extracted | portal" ;;
esac

# MicroProfile maps these onto crewcomp.dev-seed.dataset / .extract-root / .portal-root. The
# extracts and the portal snapshot are real crew data living outside the repository; the backend
# refuses either dataset without its root.
export CREWCOMP_DEV_SEED_DATASET="$dataset"
if [[ "$dataset" == extracted ]]; then
  [[ -f "$extract_root/exceptions.csv" && -d "$extract_root/seed" ]] ||
    die "--dataset extracted: '$extract_root' does not look like the POC extract directory
(expected seed/*.csv beside exceptions.csv). Point --extract-root at the POC checkout."
  export CREWCOMP_DEV_SEED_EXTRACT_ROOT="$extract_root"
fi
if [[ "$dataset" == portal ]]; then
  [[ -f "$portal_root/portal-state.json" ]] ||
    die "--dataset portal: '$portal_root' holds no portal-state.json. Run
./scripts/portal-snapshot.sh first, or point --portal-root at a snapshot directory."
  export CREWCOMP_DEV_SEED_PORTAL_ROOT="$portal_root"
fi

mkdir -p "$run_dir"

# ── The container runtime ────────────────────────────────────────────────────────────────
# Quarkus Dev Services starts PostgreSQL 16 in a container via Testcontainers. Testcontainers
# does not discover Colima's Docker context on its own, so point it at Colima's socket when
# that is what is here and the environment has not already chosen a runtime (Docker Desktop
# users, and CI, need neither variable).
setup_docker() {
  if [[ -z "${DOCKER_HOST:-}" && -S "$HOME/.colima/default/docker.sock" ]]; then
    export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
    # Ryuk, the Testcontainers reaper, bind-mounts the socket at its conventional path.
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
  fi

  if ! docker info >/dev/null 2>&1; then
    die "No reachable container runtime — Dev Services cannot start PostgreSQL.

  Colima:  colima start --cpu 2 --memory 2 --disk 20
  or start Docker Desktop.

If you use something else, set DOCKER_HOST yourself and re-run."
  fi
}

# ── The MCP token ────────────────────────────────────────────────────────────────────────
# Start-up fails while MCP is enabled if this is unset (MCP-4), and the check also enforces a
# 32-character minimum — a short token fails start-up just as hard as a missing one. Generate
# a long one once and reuse it, so a client configured against this checkout keeps working.
ensure_mcp_token() {
  if [[ -n "${CREWCOMP_MCP_TOKEN:-}" ]]; then
    (( ${#CREWCOMP_MCP_TOKEN} >= 32 )) ||
      die "CREWCOMP_MCP_TOKEN is set but only ${#CREWCOMP_MCP_TOKEN} characters; the backend requires at least 32.
Unset it to have this script generate one."
    return
  fi

  if [[ ! -s "$mcp_token_file" ]]; then
    ( umask 077; openssl rand -hex 24 > "$mcp_token_file" )
    say "${dim}Generated a development MCP token in .dev/mcp-token${off}"
  fi
  chmod 600 "$mcp_token_file" 2>/dev/null || true
  export CREWCOMP_MCP_TOKEN="$(cat "$mcp_token_file")"
}

# Wait for a URL to answer, failing fast if the process we started has already died.
# Polling the socket beats grepping the log for a ready-line: the log wording is not a
# contract, and a "started" line from a Testcontainers sidecar reads exactly like the
# application's own.
await_http() {
  local name="$1" url="$2" pid="$3" log="$4" tries="${5:-240}" i
  for (( i = 0; i < tries; i++ )); do
    if curl -fsS -o /dev/null --max-time 2 "$url" 2>/dev/null; then
      return 0
    fi
    if ! running "$pid"; then
      warn "$name exited during start-up. Last 30 lines of $log:"
      tail -30 "$log" >&2
      return 1
    fi
    sleep 1
  done
  warn "$name did not answer $url within ${tries}s. Last 30 lines of $log:"
  tail -30 "$log" >&2
  return 1
}

port_guard() {
  local name="$1" port="$2" pids
  pids="$(port_pids "$port")"
  [[ -z "$pids" ]] && return 0
  die "Port $port is already in use (PID: $(tr '\n' ' ' <<<"$pids")) — $name cannot start.
Run ./scripts/dev-stop.sh, or stop that process yourself."
}

start_backend() {
  if pid="$(read_pidfile "$backend_pidfile")"; then
    say "${green}Backend already running${off} (PID $pid) on :$backend_port"
    return 0
  fi
  port_guard backend "$backend_port"
  setup_docker
  ensure_mcp_token

  say "Starting backend… ${dim}(Dev Services pulls PostgreSQL 16 on a cold run)${off}"
  ( cd "$backend_dir" && exec ./mvnw quarkus:dev ) > "$backend_log" 2>&1 &
  local pid=$!
  echo "$pid" > "$backend_pidfile"

  # /api/v1/session is unauthenticated under the dev shim and is the SPA's own first call,
  # so a 200 here means the thing the browser needs is genuinely ready — not merely that a
  # port is open.
  if await_http "Backend" "http://localhost:$backend_port/api/v1/session" "$pid" "$backend_log"; then
    say "${green}Backend ready${off}  http://localhost:$backend_port"
  else
    rm -f "$backend_pidfile"
    kill_tree "$pid" TERM 2>/dev/null || true
    return 1
  fi
}

start_web() {
  if pid="$(read_pidfile "$web_pidfile")"; then
    say "${green}SPA already running${off} (PID $pid) on :$web_port"
    return 0
  fi
  port_guard admin-web "$web_port"

  if [[ ! -d "$web_dir/node_modules" ]]; then
    say "Installing admin-web dependencies…"
    ( cd "$web_dir" && npm install )
  fi

  say "Starting admin-web…"
  ( cd "$web_dir" && exec npm run dev ) > "$web_log" 2>&1 &
  local pid=$!
  echo "$pid" > "$web_pidfile"

  if await_http "admin-web" "http://localhost:$web_port/" "$pid" "$web_log" 60; then
    say "${green}SPA ready${off}      http://localhost:$web_port"
  else
    rm -f "$web_pidfile"
    kill_tree "$pid" TERM 2>/dev/null || true
    return 1
  fi
}

[[ "$what" == all || "$what" == backend ]] && start_backend
[[ "$what" == all || "$what" == web ]] && start_web

say ""
say "${bold}Development stack${off}"
[[ "$what" == all || "$what" == web ]] &&
  say "  Admin SPA    http://localhost:$web_port"
if [[ "$what" == all || "$what" == backend ]]; then
  say "  API          http://localhost:$backend_port/api/v1"
  say "  Swagger UI   http://localhost:$backend_port/q/swagger-ui"
  say "  MCP          http://localhost:$backend_port/mcp  ${dim}(token in .dev/mcp-token)${off}"
fi
say ""
say "${dim}Logs:  tail -f .dev/*.log${off}"
say "${dim}Stop:  ./scripts/dev-stop.sh${off}"
say ""
say "${yellow}Development mode:${off} the auth shim authenticates any request and grants all four"
if [[ "$dataset" == extracted ]]; then
  say "roles by default. ${yellow}Dataset: EXTRACTED — real crew names, Sam numbers and expiry data"
  say "from $extract_root. Do not expose or screen-share this environment beyond its audience.${off}"
elif [[ "$dataset" == portal ]]; then
  say "roles by default. ${yellow}Dataset: PORTAL — real crew names, employee ids and expiry data"
  say "from $portal_root. Do not expose or screen-share this environment beyond its audience.${off}"
else
  say "roles by default; the seeded crew, vessels and holdings are invented, not real data."
fi
