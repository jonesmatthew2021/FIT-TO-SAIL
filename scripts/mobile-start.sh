#!/usr/bin/env bash
#
# Run the crew self-service app on an iOS simulator against the local backend.
#
#   ./scripts/mobile-start.sh                          iPhone 17 Pro, crew member 2, fresh install
#   ./scripts/mobile-start.sh --person 3               a different seeded crew member
#   ./scripts/mobile-start.sh --device 'iPhone 17 Pro Max'
#   ./scripts/mobile-start.sh --supervisor             also claim vessel_master, for MOB-11
#   ./scripts/mobile-start.sh --keep-data              keep the on-device store (see below)
#   ./scripts/mobile-start.sh --list                   the simulators available here
#
# Runs in the foreground, because flutter's console is the point: r hot reload, R hot restart,
# q quit. The backend must already be up — ./scripts/dev-start.sh backend.
#
# Why this reinstalls the app by default
# --------------------------------------
# The encrypted store on the device outlives the backend. Stopping the backend lets Ryuk reap
# the PostgreSQL container, so the next start re-migrates and re-seeds — and the fixture's rows
# come back holding the same primary keys they had before. Delta sync only carries rows the
# server reports as changed, so a store left over from the previous database keeps its own
# read-marks and stale fields sitting on top of the new generation's ids, and the app renders a
# state no server ever sent. It looks exactly like an app bug, and it is not one.
#
# Uninstalling first costs a few seconds and makes what is on screen answerable to what is in
# the database. Pass --keep-data when the persisted store is the thing under test — offline mode
# across a relaunch, or a delta arriving on top of an existing snapshot.
#
# iOS only. There is no Android SDK on this machine, so ADR 0002's parity mandate is enforced
# by CI's Android build and by nothing here.
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-common.sh"

bundle_id="au.crewcomp.crewcompCrew"
device="${CREWCOMP_SIM_DEVICE:-iPhone 17 Pro}"
person=2
fresh=1
supervisor=false
partnership=1

while (( $# )); do
  case "$1" in
    --device) device="${2:?--device needs a simulator name or UDID}"; shift 2 ;;
    --person) person="${2:?--person needs a person id}"; shift 2 ;;
    --supervisor) supervisor=true; shift ;;
    --partnership) partnership="${2:?--partnership needs a partnership id}"; shift 2 ;;
    --keep-data) fresh=0; shift ;;
    --fresh) fresh=1; shift ;;
    --list) xcrun simctl list devices available; exit 0 ;;
    -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown option '$1'. Try --help." ;;
  esac
done

[[ "$person" =~ ^[0-9]+$ ]] || die "--person takes a numeric person id, not '$person'."

mkdir -p "$run_dir"

# ── Toolchain ────────────────────────────────────────────────────────────────────────────
# Flutter's own bin goes first on PATH. A standalone Homebrew Dart earlier in the path is a
# different SDK version, and when it wins the failure names a kernel binary version rather than
# anything that says "wrong dart" (mobile/CLAUDE.md).
[[ -d /opt/homebrew/share/flutter/bin ]] && PATH="/opt/homebrew/share/flutter/bin:$PATH"
export PATH

command -v flutter >/dev/null 2>&1 ||
  die "No flutter on PATH. Install the Homebrew cask: brew install --cask flutter"

xcode_dev="$(xcode-select -p 2>/dev/null || true)"
[[ -n "$xcode_dev" && -d "$xcode_dev" ]] ||
  die "No Xcode developer directory. Install Xcode, then: sudo xcode-select -s /Applications/Xcode.app"

simulator_app="$xcode_dev/Applications/Simulator.app"
[[ -d "$simulator_app" ]] ||
  die "Simulator.app is not at $simulator_app — is this a full Xcode install rather than the CLT?"

# ── One run at a time ────────────────────────────────────────────────────────────────────
# Two flutter invocations against this directory deadlock on the build directory and neither
# makes progress, which presents as a hang rather than as a clash (mobile/CLAUDE.md).
if pid="$(read_pidfile "$mobile_pidfile")"; then
  die "A flutter run for mobile/ is already going (PID $pid).
Quit it with q in its console, or: kill $pid"
fi

# ── The backend ──────────────────────────────────────────────────────────────────────────
# /api/v1/session is unauthenticated under the dev shim, so a 200 means the API is genuinely
# answering rather than merely holding the port.
api_base="${CREWCOMP_API:-http://127.0.0.1:$backend_port}"
if ! curl -fsS -o /dev/null --max-time 3 "$api_base/api/v1/session" 2>/dev/null; then
  die "No backend answering at $api_base.

  ./scripts/dev-start.sh backend

The app would still launch and render whatever its local store already holds — which is the
offline path working as designed, and a confusing thing to debug a screen against."
fi

# ── The simulator ────────────────────────────────────────────────────────────────────────
# A name resolves to the first available device that carries it. The trailing " (" in the
# pattern is what stops 'iPhone 17 Pro' from matching 'iPhone 17 Pro Max'.
if [[ "$device" =~ ^[0-9A-Fa-f-]{36}$ ]]; then
  udid="$device"
else
  udid="$(xcrun simctl list devices available |
    sed -n "s/^[[:space:]]*${device} (\([0-9A-Fa-f-]\{36\}\)).*/\1/p" | head -1)"
  [[ -n "$udid" ]] || die "No available simulator named '$device'.
See what is here with: ./scripts/mobile-start.sh --list"
fi

say "Booting ${bold}$device${off} ${dim}($udid)${off}…"
xcrun simctl boot "$udid" 2>/dev/null || true   # already booted is success here
xcrun simctl bootstatus "$udid" -b >/dev/null

# `open -a Simulator` does not resolve; the app lives inside Xcode.
open "$simulator_app"

if (( fresh )); then
  say "Uninstalling $bundle_id ${dim}(so the store matches this database — --keep-data skips)${off}"
  xcrun simctl uninstall "$udid" "$bundle_id" 2>/dev/null || true
else
  warn "Keeping the on-device store. If the backend has restarted since it was written, the
app may show read-marks and fields from the previous database's rows."
fi

say ""
say "${bold}Crew app${off}"
say "  Device       $device"
say "  API          $api_base"
say "  Signed in as ${dim}development shim,${off} person $person"
say ""
say "${dim}r hot reload · R hot restart · q quit${off}"
say ""
say "${yellow}Development mode:${off} the sign-in shim asserts a person id in a header; the crew,"
say "vessels and holdings behind it are invented, not real data."
say ""

# Foreground, so the console stays interactive and ^C reaches flutter rather than a wrapper.
#
# exec means $$ goes on being the right pid after the replacement, which is what the guard
# above reads. Nothing deletes this file: an EXIT trap cannot run in a process that has been
# exec'd over, and it does not need to, because read_pidfile treats a pid that is no longer
# alive as absent.
echo "$$" > "$mobile_pidfile"

cd "$mobile_dir"
exec flutter run -d "$udid" \
  --dart-define=CREWCOMP_API="$api_base" \
  --dart-define=CREWCOMP_DEV_PERSON="$person" \
  --dart-define=CREWCOMP_DEV_SUPERVISOR="$supervisor" \
  --dart-define=CREWCOMP_DEV_PARTNERSHIP="$partnership"
