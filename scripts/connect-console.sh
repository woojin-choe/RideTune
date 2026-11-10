#!/bin/sh
set -eu
task_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$task_dir/android-env.sh"
adb "$@" forward tcp:8088 tcp:8088
printf '%s\n' 'Pleos Virtual Console: http://127.0.0.1:8088/'
