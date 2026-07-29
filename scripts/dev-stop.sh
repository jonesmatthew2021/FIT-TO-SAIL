#!/usr/bin/env bash
#
# Stop what dev-start.sh started.
#
#   ./scripts/dev-stop.sh              both
#   ./scripts/dev-stop.sh backend      just the backend — do this before `./mvnw verify`,
#                                      which fights dev mode over target/
#   ./scripts/dev-stop.sh web          just the SPA
#
# Stopping the backend also disposes of its PostgreSQL container: Ryuk reaps it when the JVM
# that requested it goes away. The next start therefore gets a clean database and re-runs the
# migrations and the development seed. That is a feature — it is the same cold path CI takes —
# but it does mean local edits made through the UI do not survive a restart.
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-common.sh"

what="${1:-all}"
case "$what" in
  all|backend|web) ;;
  -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) die "Unknown target '$what'. Use: all | backend | web" ;;
esac

# Stop by pidfile, then sweep the port.
#
# The sweep is not redundant: a pidfile goes stale whenever a process was started outside
# these scripts or the machine was rebooted mid-run, and the symptom of missing it — a port
# clash on the next start — points at nothing useful. Whatever is on the port is what stops
# the next start, so that is what has to go.
stop_one() {
  local name="$1" pidfile="$2" port="$3"
  local stopped=0 pid pids leftover i

  if pid="$(read_pidfile "$pidfile")"; then
    say "Stopping $name (PID $pid)…"
    kill_tree "$pid" TERM
    for (( i = 0; i < 15; i++ )); do
      running "$pid" || break
      sleep 1
    done
    if running "$pid"; then
      warn "$name did not exit on SIGTERM; sending SIGKILL."
      kill_tree "$pid" KILL
      sleep 1
    fi
    stopped=1
  fi
  rm -f "$pidfile"

  leftover="$(port_pids "$port")"
  if [[ -n "$leftover" ]]; then
    (( stopped )) || say "Stopping $name on :$port ${dim}(no pidfile — started outside these scripts?)${off}"
    for pid in $leftover; do kill_tree "$pid" TERM; done
    for (( i = 0; i < 10; i++ )); do
      [[ -z "$(port_pids "$port")" ]] && break
      sleep 1
    done
    for pid in $(port_pids "$port"); do kill_tree "$pid" KILL; done
    stopped=1
  fi

  if (( stopped )); then
    if [[ -n "$(port_pids "$port")" ]]; then
      warn "$name: port $port is still held. Investigate with: lsof -nP -iTCP:$port -sTCP:LISTEN"
      return 1
    fi
    say "${green}$name stopped${off}"
  else
    say "${dim}$name was not running${off}"
  fi
}

rc=0
[[ "$what" == all || "$what" == web ]]     && { stop_one "admin-web" "$web_pidfile" "$web_port" || rc=1; }
[[ "$what" == all || "$what" == backend ]] && { stop_one "backend" "$backend_pidfile" "$backend_port" || rc=1; }

exit "$rc"
