#!/usr/bin/env bash
#
# build-and-deploy.sh — the FAST everyday loop.
# Build the app, wipe its old sandbox off the device, install fresh, launch.
# This is the ~5-second "clean app state" you want 99% of the time.
# Runs on macOS with Xcode 15+ (uses `xcrun devicectl`).
#
# Usage:
#   ./build-and-deploy.sh
#
# Configure the four variables below for your project, then wire this into
# Xcode: Scheme ▸ Edit Scheme ▸ Build ▸ Post-actions ▸ New Run Script Action,
# OR run it from a VS Code task / the terminal on ⌘-a-keypress.

set -euo pipefail

# ---- CONFIGURE ME ------------------------------------------------------------
SCHEME="MyApp"                       # your Xcode scheme
BUNDLE_ID="com.example.MyApp"        # your app's bundle identifier
PROJECT="MyApp.xcodeproj"            # or use WORKSPACE="MyApp.xcworkspace"
CONFIG="Debug"
# ------------------------------------------------------------------------------

DERIVED="$(pwd)/.build"
PRODUCT_DIR="$DERIVED/Build/Products/${CONFIG}-iphoneos"

echo "==> Finding a connected device…"
# Grab the UDID of the first connected physical device.
UDID="$(xcrun devicectl list devices 2>/dev/null \
  | awk 'NR>2 && $0 !~ /Simulator/ {print $(NF-1); exit}')"
if [[ -z "${UDID:-}" ]]; then
  echo "!! No physical device found. Plug in the iPhone and trust this Mac." >&2
  exit 1
fi
echo "    device: $UDID"

echo "==> Building $SCHEME ($CONFIG)…"
xcodebuild \
  -project "$PROJECT" \
  -scheme "$SCHEME" \
  -configuration "$CONFIG" \
  -destination "id=$UDID" \
  -derivedDataPath "$DERIVED" \
  build

APP_PATH="$(/usr/bin/find "$PRODUCT_DIR" -maxdepth 1 -name '*.app' | head -n1)"
if [[ -z "$APP_PATH" ]]; then
  echo "!! No .app produced in $PRODUCT_DIR" >&2
  exit 1
fi

echo "==> Uninstalling old app (wipes its sandbox: defaults, cache, keychain)…"
# `|| true` so a first-ever install (nothing to remove) doesn't abort.
xcrun devicectl device uninstall app --device "$UDID" "$BUNDLE_ID" || true

echo "==> Installing fresh build…"
xcrun devicectl device install app --device "$UDID" "$APP_PATH"

echo "==> Launching…"
xcrun devicectl device process launch --device "$UDID" "$BUNDLE_ID"

echo "==> Done. Fresh app, clean sandbox."
