#!/usr/bin/env bash
# Source this file; never change HOME. All tool state belongs under /workspace.
export DOTNET_ROOT=/workspace/.tools/dotnet
export JAVA_HOME=/workspace/.tools/jdk-17
export DOTNET_CLI_HOME=/workspace/.cache/dotnet
export DOTNET_CLI_TELEMETRY_OPTOUT=1
export NUGET_PACKAGES=/workspace/.cache/nuget
export GRADLE_USER_HOME=/workspace/.cache/gradle
export ANDROID_HOME=/workspace/.tools/android
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME=/workspace/.cache/android/.android
export ANDROID_PREFS_ROOT=/workspace/.cache/android
export SDKMANAGER_OPTS="${SDKMANAGER_OPTS:-} -Duser.home=/workspace/.cache/android"
export PATH="$JAVA_HOME/bin:$DOTNET_ROOT:/workspace/.tools/gradle-8.11.1/bin:$ANDROID_HOME/cmdline-tools/19.0/bin:$ANDROID_HOME/platform-tools:$PATH"
if [ -f /etc/ssl/certs/java/cacerts ]; then
    export SDKMANAGER_OPTS="$SDKMANAGER_OPTS -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
mkdir -p "$DOTNET_CLI_HOME" "$NUGET_PACKAGES" "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"
# Gradle/JVM do not automatically use HTTPS_PROXY. Refresh host/port per instance,
# preserving unrelated properties. No proxy credentials or verification overrides.
python3 - <<'PY'
import os, urllib.parse
from pathlib import Path
p=Path(os.environ['GRADLE_USER_HOME'])/'gradle.properties'
keys={'systemProp.https.proxyHost','systemProp.https.proxyPort','systemProp.http.proxyHost','systemProp.http.proxyPort'}
lines=p.read_text().splitlines() if p.exists() else []
lines=[line for line in lines if line.split('=',1)[0] not in keys]
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:
    if proxy.username or proxy.password:
        raise SystemExit('Authenticated proxy requires supported platform configuration; credentials will not be written.')
    for scheme in ('http','https'):
        lines += [f'systemProp.{scheme}.proxyHost={proxy.hostname}',f'systemProp.{scheme}.proxyPort={proxy.port or 80}']
if Path('/etc/ssl/certs/java/cacerts').is_file() and not any(line.startswith('systemProp.javax.net.ssl.trustStore=') for line in lines):
    lines.append('systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
p.write_text('\n'.join(lines)+'\n')
PY
