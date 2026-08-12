#!/usr/bin/env bash
#
# DGX Spark -> Android development bootstrap.
# Brings a fresh aarch64 DGX Spark (Ubuntu 24.04) to "build & deploy Android APKs" ready.
# Idempotent — safe to re-run. Run as root:  sudo ./dgx-spark/setup.sh
#
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then echo "Run as root: sudo $0"; exit 1; fi
HERE="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-/usr/lib/android-sdk}"
JDK17="/usr/lib/jvm/java-17-openjdk-arm64"

echo "############################################################"
echo "# [1/5] Packages: JDK 17, Android SDK + platform-tools, qemu"
echo "############################################################"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y --no-install-recommends \
  openjdk-17-jdk-headless \
  android-sdk android-sdk-platform-tools \
  qemu-user-static binfmt-support \
  unzip curl git

echo "############################################################"
echo "# [2/5] x86-64 emulation so the x86-only aapt2 runs on ARM"
echo "############################################################"
bash "$HERE/install-x86-aapt2.sh"

echo "############################################################"
echo "# [3/5] Permanent loopback-NAT fix (Gradle daemon / Studio)"
echo "############################################################"
install -m0644 "$HERE/systemd/dgx-loopback-nat-fix.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now dgx-loopback-nat-fix.service
iptables -t nat -S POSTROUTING | grep -q '127.0.0.0/8 -d 127.0.0.0/8 -j RETURN' \
  && echo "   loopback NAT exemption active." || echo "   !! rule not found — check the service"

echo "############################################################"
echo "# [4/5] Make the Debian platform-tools adb the system adb"
echo "############################################################"
DEB_ADB="$SDK_DIR/platform-tools/adb"
if [ -x "$DEB_ADB" ]; then
  if [ -e /usr/local/bin/adb ] && [ ! -L /usr/local/bin/adb ]; then
    mv -f /usr/local/bin/adb /usr/local/bin/adb.snap-shim.bak
    echo "   backed up snap shim -> /usr/local/bin/adb.snap-shim.bak"
  fi
  ln -sf "$DEB_ADB" /usr/local/bin/adb
  echo "   adb -> $(readlink -f /usr/local/bin/adb)"
else
  echo "   !! $DEB_ADB missing — is android-sdk-platform-tools installed?"
fi

echo "############################################################"
echo "# [5/5] SDK components (platform + build-tools) & licenses"
echo "############################################################"
SDKMGR="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMGR" ]; then
  export ANDROID_SDK_ROOT="$SDK_DIR" JAVA_HOME="$JDK17"
  yes | "$SDKMGR" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
  "$SDKMGR" --sdk_root="$SDK_DIR" "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null 2>&1 \
    && echo "   platform android-34 + build-tools 34.0.0 present." \
    || echo "   (install platforms/build-tools you need via: $SDKMGR --sdk_root=$SDK_DIR \"platforms;android-XX\" \"build-tools;XX.Y.Z\")"
else
  echo "   (cmdline-tools not found; install desired platforms/build-tools from Android Studio's SDK Manager)"
fi

cat <<EOF

============================================================
 DONE. This machine is Android-dev ready.

 Per project:
   • local.properties:  sdk.dir=$SDK_DIR   (see dgx-spark/templates/)
   • If a module uses the Firebase google-services plugin, add
     app/google-services.json (placeholder in dgx-spark/templates/).

 Build (note JDK 17):
   JAVA_HOME=$JDK17 ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk

 Verify:
   adb devices        # your phone should show 'device' (tap Allow on the phone)
============================================================
EOF
