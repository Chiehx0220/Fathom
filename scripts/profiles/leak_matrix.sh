#!/bin/sh
# Profiles leak matrix: writes a marker into every profile-bound store of one profile, switches, and fails when
# the marker turns up in a file outside that profile's folders, in live WorkManager work or in the shade.
# Needs a debug build (it carries ProfileProbeReceiver) and python3. It clears the app's data, so it only runs on
# an emulator: ANDROID_SERIAL=emulator-5554 sh scripts/profiles/leak_matrix.sh
set -eu
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the emulator to run on}"
export ANDROID_SERIAL
PKG=${PKG:-io.github.aedev.flow.debug}
RECEIVER=io.github.aedev.flow.data.profile.ProfileProbeReceiver
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
FAILURES=0

if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] && [ "$(adb shell getprop ro.boot.qemu | tr -d '\r')" != 1 ]; then
    echo "$ANDROID_SERIAL is not an emulator; the run clears the app's data, so it stops here." >&2
    exit 2
fi

pass() { echo "  ok    $*"; }
fail() {
    echo "  FAIL  $*"
    FAILURES=$((FAILURES + 1))
}

registry() { adb shell run-as "$PKG" cat files/datastore/profiles_registry.json; }
active() { registry | python3 -c 'import sys, json; print(json.load(sys.stdin)["activeId"])'; }
default_id() { registry | python3 -c 'import sys, json; print(json.load(sys.stdin)["defaultId"])'; }

send() { adb shell am broadcast -n "$PKG/$RECEIVER" "$@" >/dev/null; }

# Sends one op and waits for the receiver's log line, which it prints.
probe() {
    adb logcat -c
    send "$@"
    i=0
    while [ $i -lt 90 ]; do
        line=$(adb logcat -d -s ProfileProbe:I | grep "ProfileProbe" | tail -1 || true)
        if [ -n "$line" ]; then
            echo "${line#*ProfileProbe: }"
            return
        fi
        sleep 1
        i=$((i + 1))
    done
    echo "no answer from the probe" >&2
    exit 1
}

create() { probe --es op create --es name "$1" ${2:+--ez copySettings true} | sed -n 's/.*created \([0-9a-f-]*\).*/\1/p'; }

switch_to() {
    old=$(adb shell pidof "$PKG" | tr -d '\r' || true)
    # A switch that goes through restarts the process before the receiver can answer.
    send --es op switch --es id "$1"
    i=0
    while [ $i -lt 60 ]; do
        pid=$(adb shell pidof "$PKG" | tr -d '\r' || true)
        if [ -n "$pid" ] && [ "$pid" != "$old" ] && [ "$(active)" = "$1" ]; then
            sleep 6
            return
        fi
        sleep 1
        i=$((i + 1))
    done
    echo "the switch to $1 did not restart the app" >&2
    exit 1
}

# Files under the app's data holding $1, one path per line.
files_with() {
    adb shell run-as "$PKG" sh -c "'grep -rlia $1 . 2>/dev/null'" | tr -d '\r' | grep -v "androidx.work.workdb" || true
}

# Enqueued, running or blocked work whose input holds $1.
live_work_with() {
    for part in "" -wal -shm; do
        adb exec-out run-as "$PKG" cat "no_backup/androidx.work.workdb$part" >"$WORK/workdb$part" 2>/dev/null || true
    done
    python3 - "$WORK/workdb" "$1" <<'PY'
import sqlite3, sys
marker = sys.argv[2].lower().encode()
db = sqlite3.connect(sys.argv[1])
print(sum(1 for state, data in db.execute("select state, input from workspec") if state in (0, 1, 4) and marker in bytes(data).lower()))
PY
}

# Fails when marker $1, owned by profile $2, shows anywhere $2's id doesn't name.
check_contained() {
    leaks=$(files_with "$1" | grep -v "$2" || true)
    if [ -z "$leaks" ]; then pass "$1 stays in its profile's files"; else fail "$1 found outside its profile:" && echo "$leaks" | sed 's/^/          /'; fi
    work=$(live_work_with "$1")
    if [ "$work" = 0 ]; then pass "no live work carries $1"; else fail "$work live work item(s) carry $1"; fi
    shade=$(adb shell dumpsys notification --noredact | grep -ci "$1" || true)
    if [ "$shade" = 0 ]; then pass "no notification shows $1"; else fail "a notification shows $1"; fi
}

# The marker reached the running profile's stores and its reminder is queued, so the containment checks mean something.
check_written() {
    owned=$(files_with "$1" | grep "$2" || true)
    count=$(echo "$owned" | grep -c . || true)
    if [ "$count" -ge 5 ]; then pass "$1 written to $count files of its profile"; else fail "$1 written to only $count files:"; fi
    echo "$owned" | sed 's/^/          /'
    work=$(live_work_with "$1")
    if [ "$work" -ge 1 ]; then pass "$1's reminder is queued"; else fail "$1's reminder is not queued"; fi
}

echo "Clearing $PKG on $ANDROID_SERIAL"
adb shell pm clear "$PKG" >/dev/null
adb shell am start -n "$PKG/io.github.aedev.flow.MainActivity" >/dev/null
sleep 8

A=$(create LeakA)
B=$(create LeakB)
DEFAULT=$(default_id)
echo "Default $DEFAULT, A $A, B $B"

echo "In A"
switch_to "$A"
probe --es op mark --es tag zzleakA
check_written zzleakA "$A"

echo "In B"
switch_to "$B"
if files_with zzleakA | grep "$A" | grep -q neuro_brain; then pass "A's engine saved zzleakA when A was left"; else fail "A's engine never saved zzleakA"; fi
check_contained zzleakA "$A"
probe --es op mark --es tag zzleakB
check_written zzleakB "$B"

echo "Back in A"
switch_to "$A"
check_contained zzleakB "$B"
work=$(live_work_with zzleakA)
if [ "$work" -ge 1 ]; then pass "A's reminder is queued again"; else fail "A's reminder was not queued again"; fi

echo "In C, copied from A with settings only"
C=$(create LeakC copy)
switch_to "$C"
check_contained zzleakA "$A"
check_contained zzleakB "$B"
copied=$(files_with zzsetA | grep "$C" || true)
if [ -n "$copied" ]; then pass "A's subtitle language was copied to C"; else fail "A's subtitle language did not reach C"; fi

echo "In Default"
switch_to "$DEFAULT"
check_contained zzleakA "$A"
check_contained zzleakB "$B"

echo
if [ "$FAILURES" = 0 ]; then echo "Leak matrix passed"; else echo "Leak matrix: $FAILURES failure(s)"; fi
[ "$FAILURES" = 0 ]
