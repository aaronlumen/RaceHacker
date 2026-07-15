#!/usr/bin/env bash
#
# Make the x86-64-only `aapt2` (Android resource compiler) run on this aarch64 DGX box.
#
# Android ships no ARM64 aapt2, so AGP downloads an x86-64 ELF that can't exec natively.
# We register x86-64 emulation (qemu-user-static) and install the x86-64 glibc/libstdc++/zlib
# runtime at the standard multiarch paths (which are empty on arm64, so nothing conflicts).
#
# Safe to re-run. Run as root.
set -euo pipefail

if [ "$(dpkg --print-architecture)" != "arm64" ]; then
  echo "Not arm64 — native aapt2 works, nothing to do."; exit 0
fi

echo "==> Ensuring qemu-user-static + binfmt are present"
export DEBIAN_FRONTEND=noninteractive
if ! [ -x /usr/bin/qemu-x86_64-static ]; then
  apt-get update -y
  apt-get install -y --no-install-recommends qemu-user-static binfmt-support
fi
# Re-register binfmt handlers (idempotent) so x86-64 ELFs are auto-emulated.
systemctl restart systemd-binfmt.service 2>/dev/null || /usr/sbin/update-binfmts --enable qemu-x86_64 2>/dev/null || true

if [ -e /lib64/ld-linux-x86-64.so.2 ] && /usr/bin/qemu-x86_64-static /usr/lib/x86_64-linux-gnu/libc.so.6 >/dev/null 2>&1; then
  echo "==> x86-64 runtime already installed."
else
  echo "==> Fetching x86-64 runtime libraries (libc6, libgcc-s1, libstdc++6, zlib1g) from Ubuntu amd64"
  WORK="$(mktemp -d)"
  BASE="http://archive.ubuntu.com/ubuntu"
  SUITE="$(. /etc/os-release; echo "${VERSION_CODENAME:-noble}")"
  cd "$WORK"
  : > allpkgs
  for s in "$SUITE" "${SUITE}-updates"; do
    if curl -fsSL "$BASE/dists/$s/main/binary-amd64/Packages.gz" -o "p.gz"; then
      gunzip -c p.gz >> allpkgs || true
    fi
  done
  get_filename() { awk -v pkg="$1" '$1=="Package:"{c=$2} $1=="Filename:"&&c==pkg{f=$2} END{print f}' allpkgs; }
  mkdir -p stage
  for pkg in libc6 libgcc-s1 libstdc++6 zlib1g; do
    fn="$(get_filename "$pkg")"
    [ -n "$fn" ] || { echo "!! could not resolve $pkg"; exit 1; }
    echo "   - $pkg"
    curl -fsSL "$BASE/$fn" -o "$pkg.deb"
    dpkg-deb -x "$pkg.deb" stage
  done
  echo "==> Installing libs to standard multiarch paths"
  mkdir -p /lib/x86_64-linux-gnu /usr/lib/x86_64-linux-gnu /lib64
  cp -a stage/usr/lib/x86_64-linux-gnu/. /usr/lib/x86_64-linux-gnu/ 2>/dev/null || true
  cp -a stage/lib/x86_64-linux-gnu/.     /lib/x86_64-linux-gnu/     2>/dev/null || true
  # The ELF interpreter is hardcoded to /lib64/ld-linux-x86-64.so.2 (noble ships it under usr).
  ln -sf /usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2 /lib64/ld-linux-x86-64.so.2
  ldconfig || true
  rm -rf "$WORK"
fi

echo "==> Verifying aapt2 can run under emulation"
AAPT2="$(find /usr/lib/android-sdk /root/.gradle /home/*/.gradle -path '*aapt2*' -name aapt2 -type f 2>/dev/null | head -1)"
if [ -n "${AAPT2:-}" ]; then
  "$AAPT2" version && echo "OK: aapt2 runs." || { echo "!! aapt2 still failing"; exit 1; }
else
  echo "   (no aapt2 found yet — it will work once the SDK build-tools are installed)"
fi
