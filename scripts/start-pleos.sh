#!/bin/sh
set -eu
task_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$task_dir/android-env.sh"
exec "$ANDROID_HOME/emulator/emulator" -avd RideTune_Pleos_API34 -no-snapshot -memory 4096 "$@"
