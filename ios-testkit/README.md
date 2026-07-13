# iOS test automation kit

Two scripts for iterating on a physical iPhone from an Xcode/macOS setup.

> ⚠️ These run on the **Mac**, not on Windows. Xcode and `devicectl`/`cfgutil`
> are macOS-only. Copy this folder to the Mac to use it.

## 1. `build-and-deploy.sh` — everyday loop (use this constantly)

Build → uninstall old app → install fresh → launch. About 5 seconds, fully
scriptable, gives you a **clean app sandbox** (no old defaults/cache/keychain)
every run. This is the right "clean state" for almost all app testing.

Setup:
1. Open the script, set `SCHEME`, `BUNDLE_ID`, `PROJECT`, `CONFIG`.
2. `chmod +x build-and-deploy.sh`
3. Run it, or wire it into Xcode: **Scheme ▸ Edit Scheme ▸ Build ▸ Post-actions
   ▸ + ▸ New Run Script Action**, and paste the script path. Now every build
   auto-redeploys a clean install.
   (VS Code equivalent: add it as a task in `.vscode/tasks.json`.)

Note: Xcode hasn't supported binary "plugins" since Xcode 8 (SIP). Run Script
build phases / scheme actions are the supported way to hook automation.

## 2. `factory-reset-device.sh` — deliberate full wipe (rare)

A **true** factory erase of the physical phone. Minutes-long; run by hand
between test passes, **not** on every build.

- `./factory-reset-device.sh cfgutil` — fast cryptographic erase. **Requires
  supervising the phone once** with Apple Configurator 2. Add a Prepare
  blueprint to auto-run through Setup Assistant.
- `./factory-reset-device.sh restore` — full iOS reinstall via
  `idevicerestore`. No supervision needed, but slower and Setup Assistant is
  manual.

### The Setup Assistant wall
After any erase the phone boots to "Hello" and needs manual taps unless it's
**supervised** with a Prepare blueprint. There is no fully-hands-free factory
loop on an unsupervised personal device — Apple designed it that way.

### Heads-up on using your Pro Max
Supervising + repeatedly wiping turns your daily phone into a lab device
(supervision banner, data erased each cycle). Consider a **dedicated test
device** if this becomes routine.
