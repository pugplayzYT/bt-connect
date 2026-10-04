#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
# Python handles a closed input pipe without masking sdkmanager's exit status.
# License prompts are accepted explicitly; failures retain their diagnostic output.
python3 - <<'PY'
import os, subprocess
result = subprocess.run(
    ['sdkmanager', '--sdk_root=' + os.environ['ANDROID_HOME'], '--licenses'],
    input='y\n' * 100, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
)
if result.returncode:
    print(result.stdout)
    raise SystemExit(result.returncode)
print('Android SDK licenses accepted.')
PY
sdkmanager --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
