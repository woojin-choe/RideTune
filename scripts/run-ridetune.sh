#!/bin/sh
# Build and run RideTune on an already booted Pleos emulator.
set -eu
task_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$task_dir/android-env.sh"
"$task_dir/../vehicle-app/gradlew" -p "$task_dir/../vehicle-app" :app:installDebug
ridetune_user=$("$ANDROID_HOME/platform-tools/adb" -e shell am get-current-user | tr -d '\r')
"$ANDROID_HOME/platform-tools/adb" -e install --user "$ridetune_user" -r "$task_dir/../vehicle-app/app/build/outputs/apk/debug/app-debug.apk"
exec "$ANDROID_HOME/platform-tools/adb" -e shell am start -n com.humanfirst.ridetune.vehicle/.MainActivity
