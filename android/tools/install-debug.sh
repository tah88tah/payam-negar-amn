#!/usr/bin/env sh
set -eu
if [ "$#" -ne 1 ]; then
  echo "Usage: tools/install-debug.sh path/to/app-debug.apk" >&2
  exit 2
fi
adb devices
adb install -r -g "$1"
echo "Installed. Open the app and verify SMS and notification permissions on this phone."
