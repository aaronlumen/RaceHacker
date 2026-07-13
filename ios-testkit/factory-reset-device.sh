#!/usr/bin/env bash
#
# factory-reset-device.sh — the DELIBERATE full-wipe command.
# Truly erases the physical iPhone back to factory state.
# This is minutes-long and is NOT meant to run on every build — call it by hand
# between test passes when you genuinely need first-boot / provisioning state.
#
# Two backends. Pick with the first argument:
#
#   ./factory-reset-device.sh cfgutil    # fast cryptographic erase (EACS)
#                                         #   REQUIRES the device be SUPERVISED
#                                         #   via Apple Configurator 2 first.
#                                         #   Can auto-run Setup Assistant if you
#                                         #   attach a Prepare blueprint (below).
#
#   ./factory-reset-device.sh restore    # full IPSW reinstall of iOS.
#                                         #   No supervision needed, but SLOWER
#                                         #   and Setup Assistant stays MANUAL.
#                                         #   Needs libimobiledevice's
#                                         #   `idevicerestore` (brew install).
#
# ⚠️  Either path ERASES ALL DATA on the phone. There is no undo.

set -euo pipefail

MODE="${1:-}"

case "$MODE" in
  cfgutil)
    CFGUTIL="/Applications/Apple Configurator.app/Contents/MacOS/cfgutil"
    if [[ ! -x "$CFGUTIL" ]]; then
      echo "!! Apple Configurator 2 not found. Install it from the Mac App Store," >&2
      echo "   then Configurator menu ▸ Install Automation Tools, and supervise" >&2
      echo "   the device once (this erases it and marks it supervised)." >&2
      exit 1
    fi
    echo "==> Erasing supervised device (cryptographic EACS, fast)…"
    "$CFGUTIL" erase
    # If you built a Prepare blueprint that skips setup panes + sets Wi-Fi, run it
    # here to make the device usable again hands-free. Example:
    #   "$CFGUTIL" prepare --blueprint "DevTest"
    echo "==> Erase issued. Device will reboot to Setup Assistant."
    echo "    (Attach a Prepare blueprint above to auto-run through setup.)"
    ;;

  restore)
    if ! command -v idevicerestore >/dev/null 2>&1; then
      echo "!! idevicerestore not found. Install with:  brew install libimobiledevice idevicerestore" >&2
      exit 1
    fi
    echo "==> Full erase + restore to the latest signed iOS…"
    echo "    (-l fetches the latest IPSW; -e erases.) This takes several minutes."
    idevicerestore -l -e
    echo "==> Restore done. Finish Setup Assistant manually on the device."
    ;;

  *)
    echo "Usage: $0 {cfgutil|restore}" >&2
    echo "  cfgutil  — fast EACS erase (needs a supervised device)" >&2
    echo "  restore  — full IPSW reinstall (no supervision, slower, manual setup)" >&2
    exit 2
    ;;
esac
