#!/usr/bin/env bash
set -euo pipefail
cd /workspace/bt-connect
source scripts/cloud-env.sh
task_downloads=$(mktemp -d /tmp/bt-connect-setup.XXXXXX)
trap 'rm -rf "$task_downloads"' EXIT
mkdir -p /workspace/.tools

if ! [ -x "$JAVA_HOME/bin/javac" ]; then
    task_jdk_url='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.20.1_1.tar.gz'
    curl --fail --silent --show-error --location "$task_jdk_url" -o "$task_downloads/jdk.tar.gz"
    curl --fail --silent --show-error --location "$task_jdk_url.sha256.txt" -o "$task_downloads/jdk.sha256"
    python3 - "$task_downloads" <<'PY'
import hashlib, pathlib, sys
p=pathlib.Path(sys.argv[1])
if hashlib.sha256((p/'jdk.tar.gz').read_bytes()).hexdigest() != (p/'jdk.sha256').read_text().split()[0].lower():
    raise SystemExit('JDK checksum verification failed')
print('JDK checksum verified')
PY
    mkdir -p "$JAVA_HOME"
    tar -xzf "$task_downloads/jdk.tar.gz" --strip-components=1 -C "$JAVA_HOME"
fi

if ! [ -x "$DOTNET_ROOT/dotnet" ] || ! "$DOTNET_ROOT/dotnet" --list-sdks | rg -q '^8\.0\.425 '; then
    curl --fail --silent --show-error --location https://builds.dotnet.microsoft.com/dotnet/Sdk/8.0.425/dotnet-sdk-8.0.425-linux-x64.tar.gz -o "$task_downloads/dotnet.tar.gz"
    printf '%s  %s\n' 934b8060a7190e5909ad1fd0785db542f487b3bbf6cdd14826b02095fdd0d0394298b1634085eff302928fccc33f7c1a7253e9b87df555fc36fce819bcd2e798 "$task_downloads/dotnet.tar.gz" | sha512sum -c -
    mkdir -p "$DOTNET_ROOT"
    tar -xzf "$task_downloads/dotnet.tar.gz" -C "$DOTNET_ROOT"
fi
if ! [ -x /workspace/.tools/gradle-8.11.1/bin/gradle ]; then
    curl --fail --silent --show-error --location https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o "$task_downloads/gradle.zip"
    printf '%s  %s\n' f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6 "$task_downloads/gradle.zip" | sha256sum -c -
    unzip -q "$task_downloads/gradle.zip" -d /workspace/.tools
fi
if ! [ -x "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" ]; then
    curl --fail --silent --show-error --location https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$task_downloads/android.zip"
    # Checksum from Google's signed-over-TLS repository2-1.xml metadata.
    printf '%s  %s\n' 5fdcc763663eefb86a5b8879697aa6088b041e70 "$task_downloads/android.zip" | sha1sum -c -
    unzip -q "$task_downloads/android.zip" -d "$task_downloads/android"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    mv "$task_downloads/android/cmdline-tools" "$ANDROID_HOME/cmdline-tools/19.0"
fi
# Reproducible noninteractive license acceptance (sdkmanager returns the status).
python3 - <<'PY'
import subprocess, os
command=[os.environ['ANDROID_HOME']+'/cmdline-tools/19.0/bin/sdkmanager','--sdk_root='+os.environ['ANDROID_HOME'],'--licenses']
subprocess.run(command,input='y\n'*100,text=True,check=True)
PY
sdkmanager --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
dotnet restore desktop/BtConnect.Core.Tests/BtConnect.Core.Tests.csproj --locked-mode
dotnet restore desktop/BtConnect.Server/BtConnect.Server.csproj --locked-mode
dotnet test desktop/BtConnect.Core.Tests/BtConnect.Core.Tests.csproj -c Release --no-restore
dotnet build desktop/BtConnect.Server/BtConnect.Server.csproj -c Release --no-restore
cd android
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
