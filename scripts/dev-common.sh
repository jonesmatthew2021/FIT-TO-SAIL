#!/usr/bin/env bash
#
# Shared configuration and helpers for dev-start.sh / dev-stop.sh.
# Sourced, never executed directly.
#
# Everything mutable lives under .dev/ (gitignored): pidfiles, logs, and the generated
# MCP token. Nothing here writes inside a component's tree, so `mvnw verify` and
# `vite build` see exactly the directory they would without these scripts.

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
run_dir="$root/.dev"

backend_dir="$root/backend"
web_dir="$root/admin-web"
mobile_dir="$root/mobile"

backend_log="$run_dir/backend.log"
web_log="$run_dir/web.log"
backend_pidfile="$run_dir/backend.pid"
web_pidfile="$run_dir/web.pid"
mobile_pidfile="$run_dir/mobile.pid"

# The Android emulator, which unlike an iOS simulator is a process these scripts own: it is
# started detached and outlives the flutter run in front of it, so it needs a pidfile and a log
# of its own. `mobile_pidfile` is deliberately shared between the iOS and Android runners —
# the constraint it guards is one `flutter run` per mobile/, which is not per platform.
emulator_log="$run_dir/emulator.log"
emulator_pidfile="$run_dir/emulator.pid"

# Ports are fixed by the components: Quarkus defaults to 8080, and admin-web's Vite config
# proxies /api to it from 5173. Changing either means changing vite.config.ts too.
backend_port=8080
web_port=5173

# The MCP token is generated once and reused, so an MCP client configured against this
# checkout keeps working across restarts. It is a local development credential for a server
# that is disabled in production by default (§13.1) — but it is still a credential, so it
# lives in a gitignored file with owner-only permissions and is never echoed in full.
mcp_token_file="$run_dir/mcp-token"

bold=$'\033[1m'; dim=$'\033[2m'; red=$'\033[31m'; green=$'\033[32m'; yellow=$'\033[33m'; off=$'\033[0m'
[[ -t 1 ]] || { bold=''; dim=''; red=''; green=''; yellow=''; off=''; }

say()  { printf '%s\n' "$*"; }
warn() { printf '%s%s%s\n' "$yellow" "$*" "$off" >&2; }
die()  { printf '%s%s%s\n' "$red" "$*" "$off" >&2; exit 1; }

# Kill a process and everything it spawned, children first.
#
# This matters more than it looks: `mvnw quarkus:dev` is a shell wrapper that forks a Maven
# JVM, which forks the application JVM. Killing only the PID we recorded orphans the JVM that
# actually holds port 8080, and the next start fails on a port clash with no visible culprit.
kill_tree() {
  local pid="$1" sig="${2:-TERM}" child
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do
    kill_tree "$child" "$sig"
  done
  kill "-$sig" "$pid" 2>/dev/null || true
}

# PIDs listening on a TCP port — the backstop when a pidfile is stale or missing.
port_pids() {
  lsof -nP -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null || true
}

running() { [[ -n "${1:-}" ]] && kill -0 "$1" 2>/dev/null; }

read_pidfile() {
  local f="$1" pid
  [[ -f "$f" ]] || return 1
  pid="$(cat "$f" 2>/dev/null || true)"
  running "$pid" || return 1
  printf '%s' "$pid"
}
