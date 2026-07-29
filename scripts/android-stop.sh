#!/usr/bin/env bash
#
# Stop what android-start.sh started.
#
#   ./scripts/android-stop.sh              the app, then the emulator
#   ./scripts/android-stop.sh app          just the flutter run — leaves the emulator up
#   ./scripts/android-stop.sh emulator     just the emulator
#
# Stopping the emulator does not lose the app's data: an AVD keeps its userdata image on disk, so
# the encrypted store survives and the next start syncs a delta onto it. That is usually not what
# you want after a backend restart — see android-start.sh on why it reinstalls by default.
#
# There is no equivalent for iOS because there is nothing to stop: `q` in flutter's console ends
# the run, and the simulator is left alone.
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-common.sh"

what="${1:-all}"
case "$what" in
  all|app|emulator) ;;
  -h|--help) sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) die "Unknown target '$what'. Use: all | app | emulator" ;;
esac

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
adb="$sdk/platform-tools/adb"

stop_app() {
  local pid i
  if pid="$(read_pidfile "$mobile_pidfile")"; then
    say "Stopping the flutter run (PID $pid)…"
    # kill_tree because `flutter run` forks a Dart process that holds the device connection;
    # killing only the wrapper leaves it attached and the next run reports the device is busy.
    kill_tree "$pid" TERM
    for (( i = 0; i < 15; i++ )); do
      running "$pid" || break
      sleep 1
    done
    running "$pid" && { warn "It did not exit on SIGTERM; sending SIGKILL."; kill_tree "$pid" KILL; sleep 1; }
    rm -f "$mobile_pidfile"
    say "${green}flutter run stopped${off}"
  else
    rm -f "$mobile_pidfile"
    say "${dim}No flutter run was going${off}"
  fi
}

stop_emulator() {
  local pid i stopped=0 serial line

  # `adb emu kill` first, and by serial rather than by pid: it asks the emulator to shut down the
  # way its own window's close button would, so the userdata image is flushed rather than torn
  # away mid-write. A SIGKILL'd emulator can come back needing a cold boot.
  if [[ -x "$adb" ]]; then
    while read -r line; do
      serial="${line%%$'\t'*}"
      [[ "$serial" == emulator-* ]] || continue
      say "Asking $serial to shut down…"
      "$adb" -s "$serial" emu kill >/dev/null 2>&1 || true
      stopped=1
    done < <("$adb" devices 2>/dev/null | tail -n +2)
  fi

  # Then the pidfile, as the backstop for one that ignored the request or never registered with
  # adb at all — an emulator that failed mid-boot is still a process holding the AVD's lock, and
  # the next start fails with "the AVD is already in use" pointing at nothing useful.
  if pid="$(read_pidfile "$emulator_pidfile")"; then
    for (( i = 0; i < 20; i++ )); do
      running "$pid" || break
      sleep 1
    done
    if running "$pid"; then
      warn "The emulator did not exit on request; sending SIGTERM."
      kill_tree "$pid" TERM
      for (( i = 0; i < 10; i++ )); do
        running "$pid" || break
        sleep 1
      done
      running "$pid" && { warn "Still up; sending SIGKILL."; kill_tree "$pid" KILL; }
    fi
    stopped=1
  fi
  rm -f "$emulator_pidfile"

  if (( stopped )); then
    say "${green}emulator stopped${off}"
  else
    say "${dim}No emulator was running${off}"
  fi
}

[[ "$what" == all || "$what" == app ]]      && stop_app
[[ "$what" == all || "$what" == emulator ]] && stop_emulator

exit 0
