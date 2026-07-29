#!/usr/bin/env bash
#
# Run the crew self-service app on an Android emulator against the local backend.
#
#   ./scripts/android-start.sh                     boot the AVD, then run the app as crew member 2
#   ./scripts/android-start.sh --person 3          a different seeded crew member
#   ./scripts/android-start.sh --supervisor        also claim vessel_master, for MOB-11
#   ./scripts/android-start.sh --boot-only         boot the emulator and leave it running
#   ./scripts/android-start.sh --keep-data         keep the on-device store (see below)
#   ./scripts/android-start.sh --avd <name>        a different AVD
#   ./scripts/android-start.sh --list              the AVDs available here
#
# The app runs in the foreground, because flutter's console is the point: r hot reload,
# R hot restart, q quit. The emulator does not — it is started detached and survives quitting
# the app, which is what makes iterating quick. ./scripts/android-stop.sh stops both.
#
# The backend must already be up — ./scripts/dev-start.sh backend.
#
# Why the API base is 10.0.2.2 and not 127.0.0.1
# ---------------------------------------------
# An Android emulator is a separate machine. Its 127.0.0.1 is its own loopback, not the host's,
# so the default CREWCOMP_API that is correct on an iOS simulator silently reaches nothing here.
# 10.0.2.2 is the address the emulator's NAT maps to the host. The failure it prevents is not an
# error dialog: every screen renders from an empty local store under a "couldn't sync" banner,
# which is indistinguishable from a backend that is down (mobile/CLAUDE.md §Traps).
#
# Why this reinstalls the app by default
# -------------------------------------
# Same reason as the iOS runner, and it is worth repeating because the symptom is so misleading.
# The encrypted store on the device outlives the backend. Stopping the backend lets Ryuk reap the
# PostgreSQL container, so the next start re-migrates and re-seeds — and the fixture's rows come
# back holding the same primary keys they had before. Delta sync only carries rows the server
# reports as changed, so a store left over from the previous database keeps its own read-marks and
# stale fields sitting on top of the new generation's ids, and the app renders a state no server
# ever sent. It looks exactly like an app bug, and it is not one.
#
# Pass --keep-data when the persisted store is the thing under test — offline mode across a
# relaunch, or a delta arriving on top of an existing snapshot.
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-common.sh"

# The Android applicationId, which is *not* the iOS bundle id — `au.crewcomp.crewcompCrew` there,
# snake_case here, because Gradle took it from the Dart package name.
app_id="au.crewcomp.crewcomp_crew"

avd="${CREWCOMP_AVD:-crewcomp_api36}"
person=2
fresh=1
supervisor=false
partnership=1
boot_only=0

# The SDK root. `flutter config --android-sdk` writes this into flutter's own settings, and
# Homebrew's cask puts a *second*, unpopulated root in its prefix — so prefer the environment and
# fall back to the conventional location Android Studio uses rather than to Homebrew's.
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

while (( $# )); do
  case "$1" in
    --avd) avd="${2:?--avd needs an AVD name}"; shift 2 ;;
    --person) person="${2:?--person needs a person id}"; shift 2 ;;
    --supervisor) supervisor=true; shift ;;
    --partnership) partnership="${2:?--partnership needs a partnership id}"; shift 2 ;;
    --keep-data) fresh=0; shift ;;
    --fresh) fresh=1; shift ;;
    --boot-only) boot_only=1; shift ;;
    --list) "$sdk/emulator/emulator" -list-avds; exit 0 ;;
    -h|--help) sed -n '2,38p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "Unknown option '$1'. Try --help." ;;
  esac
done

[[ "$person" =~ ^[0-9]+$ ]] || die "--person takes a numeric person id, not '$person'."

mkdir -p "$run_dir"

# ── Toolchain ────────────────────────────────────────────────────────────────────────────
# Flutter's own bin goes first on PATH, for the same reason as the iOS runner: a standalone
# Homebrew Dart earlier in the path is a different SDK version, and when it wins the failure
# names a kernel binary version rather than anything that says "wrong dart" (mobile/CLAUDE.md).
[[ -d /opt/homebrew/share/flutter/bin ]] && PATH="/opt/homebrew/share/flutter/bin:$PATH"
export PATH

command -v flutter >/dev/null 2>&1 ||
  die "No flutter on PATH. Install the Homebrew cask: brew install --cask flutter"

adb="$sdk/platform-tools/adb"
emulator_bin="$sdk/emulator/emulator"

[[ -x "$adb" ]] || die "No adb at $adb.

Set ANDROID_HOME, or install the SDK platform-tools:
  sdkmanager --install platform-tools"
[[ -x "$emulator_bin" ]] || die "No emulator at $emulator_bin.

  sdkmanager --install emulator"

# The NDK is not needed to *run* a built app, but it is needed to build one, and AGP will not
# fetch it the way it fetches build-tools and CMake. Checking here turns a confusing Gradle
# failure several minutes in into a sentence (mobile/CLAUDE.md §"What the Android run proved").
if ! compgen -G "$sdk/ndk/*" >/dev/null; then
  warn "No NDK under $sdk/ndk. The build compiles libsqlite3mc.so, so it will fail.
Install the revision the build pins:
  $sdk/cmdline-tools/latest/bin/sdkmanager --install \"ndk;28.2.13676358\""
fi

"$emulator_bin" -list-avds 2>/dev/null | grep -qxF "$avd" || die "No AVD named '$avd'.

What is here:
$("$emulator_bin" -list-avds 2>/dev/null | sed 's/^/  /')

Create one with:
  $sdk/cmdline-tools/latest/bin/avdmanager create avd -n $avd \\
    -k 'system-images;android-36;google_apis;arm64-v8a' -d pixel_7"

# ── One run at a time ────────────────────────────────────────────────────────────────────
# Two flutter invocations against this directory deadlock on the build directory and neither
# makes progress, which presents as a hang rather than as a clash (mobile/CLAUDE.md). The
# pidfile is shared with mobile-start.sh, so this also catches an iOS run.
if (( ! boot_only )) && pid="$(read_pidfile "$mobile_pidfile")"; then
  die "A flutter run for mobile/ is already going (PID $pid).
Quit it with q in its console, or: ./scripts/android-stop.sh app"
fi

# ── The emulator ─────────────────────────────────────────────────────────────────────────
# Ask each attached emulator which AVD it is running, rather than assuming emulator-5554 is
# ours. `adb emu avd name` prints the name and then OK, hence the first line only.
serial_for_avd() {
  local line s name
  while read -r line; do
    s="${line%%$'\t'*}"
    [[ "$s" == emulator-* ]] || continue
    name="$("$adb" -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
    if [[ "$name" == "$avd" ]]; then printf '%s' "$s"; return 0; fi
  done < <("$adb" devices 2>/dev/null | tail -n +2)
  return 1
}

if serial="$(serial_for_avd)"; then
  say "${dim}$avd is already running ($serial)${off}"
else
  say "Booting ${bold}$avd${off}…"
  # Detached, so it outlives this script — both when --boot-only returns and when the exec
  # below replaces this shell with flutter. Its output goes to a log because a stray emulator
  # writing to the terminal would tangle with flutter's console.
  nohup "$emulator_bin" -avd "$avd" -no-boot-anim >"$emulator_log" 2>&1 &
  echo "$!" > "$emulator_pidfile"

  "$adb" start-server >/dev/null 2>&1 || true
  for (( i = 0; i < 180; i++ )); do
    serial="$(serial_for_avd || true)"
    [[ -n "$serial" ]] && break
    sleep 1
  done
  [[ -n "${serial:-}" ]] || die "$avd never appeared in \`adb devices\`. Its log:
  $emulator_log"
fi

# `wait-for-device` returns as soon as adb can talk to it, which is long before Android is up.
# sys.boot_completed is the property that actually means the system is ready to take an install.
say "${dim}Waiting for Android to finish booting…${off}"
"$adb" -s "$serial" wait-for-device
for (( i = 0; i < 180; i++ )); do
  [[ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
  sleep 2
done
[[ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] ||
  die "$avd booted adb but never reported sys.boot_completed. Its log:
  $emulator_log"

api_base="${CREWCOMP_API:-http://10.0.2.2:$backend_port}"

if (( boot_only )); then
  say ""
  say "${green}$avd is up${off} ${dim}($serial)${off}"
  say "  Run the app with   ./scripts/android-start.sh"
  say "  Stop it with       ./scripts/android-stop.sh emulator"
  exit 0
fi

# ── The backend ──────────────────────────────────────────────────────────────────────────
# Probed from *this* machine, so the emulator's 10.0.2.2 has to be translated back to the
# host's own loopback first — curl here cannot use the address the app will use.
# /api/v1/session is unauthenticated under the dev shim, so a 200 means the API is genuinely
# answering rather than merely holding the port.
probe_base="${api_base//10.0.2.2/127.0.0.1}"
if ! curl -fsS -o /dev/null --max-time 3 "$probe_base/api/v1/session" 2>/dev/null; then
  die "No backend answering at $probe_base.

  ./scripts/dev-start.sh backend

The app would still launch and render whatever its local store already holds — which is the
offline path working as designed, and a confusing thing to debug a screen against."
fi

if (( fresh )); then
  say "Uninstalling $app_id ${dim}(so the store matches this database — --keep-data skips)${off}"
  "$adb" -s "$serial" uninstall "$app_id" >/dev/null 2>&1 || true
else
  warn "Keeping the on-device store. If the backend has restarted since it was written, the
app may show read-marks and fields from the previous database's rows."
fi

say ""
say "${bold}Crew app${off}"
say "  Emulator     $avd ${dim}($serial)${off}"
say "  API          $api_base ${dim}(10.0.2.2 is this machine, seen from the emulator)${off}"
say "  Signed in as ${dim}development shim,${off} person $person"
say ""
say "${dim}r hot reload · R hot restart · q quit${off}"
say "${dim}Screens can be driven from here, unlike on an iOS simulator:${off}"
say "${dim}  $adb -s $serial shell input tap X Y${off}"
say "${dim}  $adb -s $serial exec-out screencap -p > /tmp/shot.png${off}"
say ""
say "${yellow}Development mode:${off} the sign-in shim asserts a person id in a header; the crew,"
say "vessels and holdings behind it are invented, not real data."
say ""

# Foreground, so the console stays interactive and ^C reaches flutter rather than a wrapper.
#
# exec means $$ goes on being the right pid after the replacement, which is what the guard above
# reads. Nothing deletes this file: an EXIT trap cannot run in a process that has been exec'd
# over, and it does not need to, because read_pidfile treats a dead pid as absent.
echo "$$" > "$mobile_pidfile"

cd "$mobile_dir"
exec flutter run -d "$serial" \
  --dart-define=CREWCOMP_API="$api_base" \
  --dart-define=CREWCOMP_DEV_PERSON="$person" \
  --dart-define=CREWCOMP_DEV_SUPERVISOR="$supervisor" \
  --dart-define=CREWCOMP_DEV_PARTNERSHIP="$partnership"
