#!/bin/sh
# Profiles upgrade and downgrade: installs a build from before profiles, loads a data set into it, upgrades and
# checks Default kept everything, then adds a second profile, downgrades and checks the old build still opens
# Default's data. Needs python3, two debug APKs (the new one must carry ProfileProbeReceiver) and a tar of an app
# data directory from before profiles. It clears the app, so it only runs on an emulator:
# ANDROID_SERIAL=emulator-5554 sh scripts/profiles/upgrade_downgrade.sh old.apk new.apk data.tar
set -eu
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the emulator to run on}"
export ANDROID_SERIAL
OLD_APK=$1
NEW_APK=$2
DATA_TAR=$3
PKG=${PKG:-io.github.aedev.flow.debug}
RECEIVER=io.github.aedev.flow.data.profile.ProfileProbeReceiver
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
FAILURES=0
# Rewritten by something other than the app's own stores, so never compared: WebView's storage (the token WebView
# runs only on some launches), ProfileInstaller after a new APK, WorkManager's database, the engine's Home snapshots,
# the device-level widget theme signature, and the Room files, whose tables are compared row by row instead.
VOLATILE='^\./(app_webview/|files/profileInstalled$|no_backup/androidx\.work\.workdb|no_backup/neuro/home_snapshots/|files/datastore/widget_theme\.preferences_pb$|databases/flow_database)'

if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ] && [ "$(adb shell getprop ro.boot.qemu | tr -d '\r')" != 1 ]; then
    echo "$ANDROID_SERIAL is not an emulator; the run clears the app, so it stops here." >&2
    exit 2
fi

cat >"$WORK/prefs.py" <<'PY'
def varint(b, i):
    n = s = 0
    while True:
        c = b[i]
        i += 1
        n |= (c & 0x7F) << s
        s += 7
        if c < 0x80:
            return n, i


def fields(b):
    i = 0
    while i < len(b):
        tag, i = varint(b, i)
        size, i = varint(b, i)
        yield tag >> 3, b[i:i + size]
        i += size


def prefs(path):
    out = {}
    for _, entry in fields(open(path, "rb").read()):
        parts = dict(fields(entry))
        out[parts[1].decode()] = parts.get(2, b"")
    return out
PY
cat >"$WORK/pbdiff.py" <<'PY'
import sys
from prefs import prefs
a, b = prefs(sys.argv[1]), prefs(sys.argv[2])
for key in sorted(set(a) | set(b)):
    if a.get(key) != b.get(key):
        print(key, (a.get(key) or b"-")[:48], "->", (b.get(key) or b"-")[:48])
PY

pass() { echo "  ok    $*"; }
fail() {
    echo "  FAIL  $*"
    FAILURES=$((FAILURES + 1))
}

launch() {
    adb shell am start -n "$PKG/io.github.aedev.flow.MainActivity" >/dev/null
    sleep 20
    if [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then pass "$1 is running"; else fail "$1 is not running"; fi
    adb shell am force-stop "$PKG"
}

# sha256 and path of every file outside the caches, and a copy of every preferences file to name changed keys.
hashes() {
    adb shell run-as "$PKG" sh -c "'find . -type f ! -path ./cache/\* ! -path ./code_cache/\* -exec sha256sum {} +'" | tr -d '\r' | sort -k2 >"$WORK/$1"
    mkdir -p "$WORK/$1.prefs"
    for f in $(adb shell run-as "$PKG" ls files/datastore | tr -d '\r' | grep 'preferences_pb$'); do
        adb exec-out run-as "$PKG" cat "files/datastore/$f" >"$WORK/$1.prefs/$f"
    done
}

# Row count and a digest of the contents of every Room table that isn't a cache, one line per table.
rows() {
    for part in "" -wal -shm; do
        adb exec-out run-as "$PKG" cat "databases/flow_database$part" >"$WORK/db$part" 2>/dev/null || true
    done
    python3 - "$WORK/db" >"$WORK/$1" <<'PY'
import hashlib, sqlite3, sys
db = sqlite3.connect(sys.argv[1])
for (name,) in db.execute("select name from sqlite_master where type = 'table' and name not like 'sqlite_%' and name not like '%cache%' and name not in ('android_metadata', 'room_master_table') order by name"):
    rows = sorted(repr(r) for r in db.execute(f'select * from "{name}"'))
    print(name, len(rows), hashlib.sha256("\n".join(rows).encode()).hexdigest()[:16])
PY
}

# Paths whose hash differs between snapshots $1 and $2, or that only one of them has.
changed() { python3 - "$WORK/$1" "$WORK/$2" <<'PY'
import sys
def load(p):
    return {line.split(None, 1)[1].strip(): line.split()[0] for line in open(p) if line.strip()}
a, b = load(sys.argv[1]), load(sys.argv[2])
for path in sorted(set(a) | set(b)):
    if a.get(path) != b.get(path):
        print(path)
PY
}

registry_field() { adb shell run-as "$PKG" cat files/datastore/profiles_registry.json | python3 -c "import sys, json; d = json.load(sys.stdin); print($1)"; }

check_crashes() {
    crashes=$(adb logcat -d -b crash | grep -c "Process: $PKG" || true)
    if [ "$crashes" = 0 ]; then pass "no crash"; else fail "$crashes crash(es):" && adb logcat -d -b crash | grep -A8 "Process: $PKG" | head -20; fi
    adb logcat -b crash -c
}

check_rows() {
    if diff -q "$WORK/$1" "$WORK/$2" >/dev/null; then
        pass "every Room table kept its rows ($(awk '{s += $2} END {print s}' "$WORK/$2") rows in $(wc -l <"$WORK/$2" | tr -d ' ') tables, caches left out)"
    else
        fail "Room tables differ:" && diff "$WORK/$1" "$WORK/$2" | sed 's/^/          /'
    fi
}

# Fails on any change between $1 and $2 that isn't launch churn or one of the paths in $3 (a grep -E pattern).
check_untouched() {
    unexpected=$(changed "$1" "$2" | grep -vxF -f "$WORK/churn" | grep -vE "$VOLATILE" | grep -vE "$3" || true)
    if [ -z "$unexpected" ]; then
        pass "Default's files are unchanged apart from launch churn"
        return
    fi
    fail "files changed:"
    for path in $unexpected; do
        echo "          $path"
        name=${path##*/}
        if [ -f "$WORK/$1.prefs/$name" ] && [ -f "$WORK/$2.prefs/$name" ]; then
            python3 "$WORK/pbdiff.py" "$WORK/$1.prefs/$name" "$WORK/$2.prefs/$name" | sed 's/^/            /'
        fi
    done
}

echo "Old build with the data set"
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r "$OLD_APK" >/dev/null
adb logcat -b crash -c
launch "old build, first start"
adb push -q "$DATA_TAR" /data/local/tmp/flow-data.tar 2>/dev/null || adb push "$DATA_TAR" /data/local/tmp/flow-data.tar >/dev/null
adb shell run-as "$PKG" tar xf /data/local/tmp/flow-data.tar -C "/data/data/$PKG"
adb shell rm /data/local/tmp/flow-data.tar
launch "old build with the data"
hashes old1
launch "old build again"
hashes old2
rows old_rows
changed old1 old2 >"$WORK/churn"
echo "  ..    $(wc -l <"$WORK/churn" | tr -d ' ') file(s) change on every launch and are left out of the comparisons"
check_crashes

echo "Upgrade"
adb install -r "$NEW_APK" >/dev/null
launch "new build"
hashes new1
rows new_rows
check_rows old_rows new_rows
check_untouched old2 new1 '^\./files/datastore/(device_preferences\.preferences_pb|profiles_registry\.json)$'
if [ "$(registry_field 'len(d["profiles"])')" = 1 ] && [ "$(registry_field 'd["activeId"] == d["defaultId"]')" = True ]; then pass "one profile, Default, is active"; else fail "the registry isn't a lone active Default"; fi
adb exec-out run-as "$PKG" cat files/datastore/player_preferences.preferences_pb >"$WORK/player.pb"
adb exec-out run-as "$PKG" cat files/datastore/device_preferences.preferences_pb >"$WORK/device.pb"
copy=$(cd "$WORK" && python3 - "$WORK/player.pb" "$WORK/device.pb" <<'PY'
import sys
from prefs import prefs
player, device = prefs(sys.argv[1]), prefs(sys.argv[2])
shared = [k for k in device if k in player]
differ = [k for k in shared if device[k] != player[k]]
print(len(shared), len(differ), " ".join(differ))
PY
)
set -- $copy
if [ "$1" -gt 0 ] && [ "$2" = 0 ]; then pass "$1 device settings copied with the same values, and still in player_preferences for an older build"; else fail "device settings copy: $copy"; fi
check_crashes

echo "Second profile, then downgrade while it is active"
adb shell am start -n "$PKG/io.github.aedev.flow.MainActivity" >/dev/null
sleep 10
adb shell am broadcast -n "$PKG/$RECEIVER" --es op create --es name Second >/dev/null
sleep 3
SECOND=$(registry_field '[p["id"] for p in d["profiles"] if p["name"] == "Second"][0]')
adb shell am broadcast -n "$PKG/$RECEIVER" --es op switch --es id "$SECOND" >/dev/null
sleep 15
adb shell am broadcast -n "$PKG/$RECEIVER" --es op mark --es tag zzupdown >/dev/null
sleep 10
adb shell am force-stop "$PKG"
if [ "$(registry_field 'd["activeId"]')" = "$SECOND" ]; then pass "Second is active"; else fail "Second isn't active"; fi
hashes before_down
rows before_down_rows
adb install -r -d "$OLD_APK" >/dev/null
launch "old build after the downgrade"
hashes after_down
rows after_down_rows
check_rows before_down_rows after_down_rows
check_untouched before_down after_down '^$'
if adb shell run-as "$PKG" ls "files/profile_library/$SECOND" >/dev/null 2>&1; then pass "Second's folders are still on disk"; else fail "Second's folders are gone"; fi
check_crashes

echo "Upgrade again"
adb install -r "$NEW_APK" >/dev/null
adb shell am start -n "$PKG/io.github.aedev.flow.MainActivity" >/dev/null
sleep 20
if [ "$(registry_field 'd["activeId"]')" = "$SECOND" ] && [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then pass "the new build opens Second again"; else fail "the new build didn't come back to Second"; fi
check_crashes

echo
if [ "$FAILURES" = 0 ]; then echo "Upgrade and downgrade passed"; else echo "Upgrade and downgrade: $FAILURES failure(s)"; fi
[ "$FAILURES" = 0 ]
