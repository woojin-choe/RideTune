#!/bin/sh
# Build and run RideTune on an already booted Pleos emulator.
set -eu
task_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$task_dir/android-env.sh"
"$task_dir/../vehicle-app/gradlew" -p "$task_dir/../vehicle-app" :app:installDebug
ridetune_user=$("$ANDROID_HOME/platform-tools/adb" -e shell am get-current-user | tr -d '\r')
"$ANDROID_HOME/platform-tools/adb" -e install --user "$ridetune_user" -r "$task_dir/../vehicle-app/app/build/outputs/apk/debug/app-debug.apk"
"$ANDROID_HOME/platform-tools/adb" -e forward tcp:48732 tcp:48732
if ! lsof -iTCP:48733 -sTCP:LISTEN >/dev/null 2>&1; then
  nohup python3 -c '
import socket, threading
def pipe(a, b):
    try:
        while data := a.recv(4096): b.sendall(data)
    except Exception: pass
    finally:
        try: a.close()
        except Exception: pass
        try: b.close()
        except Exception: pass
def handle(c):
    try:
        u = socket.create_connection(("127.0.0.1", 48732), timeout=3)
        threading.Thread(target=pipe, args=(c, u), daemon=True).start()
        threading.Thread(target=pipe, args=(u, c), daemon=True).start()
    except Exception: c.close()
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("0.0.0.0", 48733))
s.listen(16)
while True:
    c, _ = s.accept()
    threading.Thread(target=handle, args=(c,), daemon=True).start()
' </dev/null >/dev/null 2>&1 &
fi
exec "$ANDROID_HOME/platform-tools/adb" -e shell am start -n com.humanfirst.ridetune.vehicle/.MainActivity
