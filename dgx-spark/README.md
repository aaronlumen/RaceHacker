# DGX Spark → Android development bootstrap

Config + scripts to make a **fresh NVIDIA DGX Spark (aarch64, Ubuntu 24.04)** build and
deploy Android apps (RaceHacker / ProRacingOBD / CarHackerKit) out of the box.

Android's toolchain assumes x86-64 Linux, and the DGX has a couple of network quirks that
silently break Gradle. This branch captures every fix as reproducible config so you don't
have to rediscover them.

## TL;DR

```bash
sudo ./dgx-spark/setup.sh
# then, once per machine:
yes | /usr/lib/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
```

Build with JDK 17:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## The four problems this fixes

1. **Loopback traffic is source-NAT'd, so the Gradle daemon rejects its own client.**
   A stray catch-all `iptables -t nat -A POSTROUTING -j MASQUERADE` rewrites even
   `127.0.0.1 ↔ 127.0.0.1` connections to the box's LAN IP. Gradle's daemon only accepts
   loopback peers, so every build (CLI *and* Android Studio) fails with
   *"A new daemon was started but could not be connected to."*
   → `systemd/dgx-loopback-nat-fix.service` inserts a loopback NAT exemption at boot.

2. **`aapt2` ships x86-64 only.** Every `aapt2` (SDK and AGP-downloaded) is an x86-64 ELF,
   so resource linking dies with *"Failed to start AAPT2 / Exec format error"* on ARM.
   → `install-x86-aapt2.sh` installs `qemu-user-static` + the x86-64 glibc/libstdc++/zlib
   runtime so the emulated `aapt2` runs. (Slower than native, fine for app builds.)

3. **The `adb` on `PATH` is a snap and is AppArmor-confined** — it can't read USB sysfs,
   so `adb devices` is always empty even with a phone attached.
   → `setup.sh` points `/usr/local/bin/adb` at the unconfined Debian
   `platform-tools/adb`.

4. **Wrong JDK + Firebase config.** Gradle must run on **JDK 17** (default is 21), and the
   `com.google.gms.google-services` plugin needs an `app/google-services.json`.
   → `setup.sh` installs JDK 17; `templates/google-services.json.example` is a placeholder
   that lets the project build. Real Google Sign-In needs a real Firebase project (register
   the app package + debug SHA-1, then drop in the real `google-services.json`). Until then
   use the app's **"Skip for now — use locally"** option.

## Files

| Path | Purpose |
|------|---------|
| `setup.sh` | One-shot, idempotent bootstrap. Run as root. |
| `install-x86-aapt2.sh` | qemu + x86-64 runtime libs so `aapt2` runs on ARM. |
| `systemd/dgx-loopback-nat-fix.service` | Permanent, boot-time loopback NAT exemption. |
| `templates/local.properties.example` | `sdk.dir` for the Debian Android SDK. |
| `templates/google-services.json.example` | Placeholder Firebase config (build-only). |

## Reverting

```bash
sudo systemctl disable --now dgx-loopback-nat-fix.service
sudo rm /etc/systemd/system/dgx-loopback-nat-fix.service
# restore the original snap adb shim if desired:
sudo mv /usr/local/bin/adb.snap-shim.bak /usr/local/bin/adb
```
