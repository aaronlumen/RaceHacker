# 🏁 Pro Racing OBD

![Carbon Fiber](app/src/main/res/drawable/carbon_fiber_bg.png)

> **Dual-mode Android OBD2 toolkit** — a real-time ELM327 racing dashboard *and* an in-vehicle
> diagnostics/security workshop, in one app.
>
> [![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
> ![Min SDK 24](https://img.shields.io/badge/minSdk-24-informational)
> ![Target SDK 34](https://img.shields.io/badge/targetSdk-34-informational)
> ![Kotlin%20%2B%20Java](https://img.shields.io/badge/Kotlin%20%2B%20Java-11-orange)

---

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ▓▓░░▓▓░░▓▓░░  PRO RACING OBD  ░░▓▓░░▓▓░░▓▓░░▓▓
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
              S U R I N A
```

---

## ⚠️ Safety Disclaimer

**This app can read, write, and flash your vehicle's ECU.** Modifying tuning parameters,
disabling safety limiters, or flashing a ROM can damage your engine, void your warranty, or
violate emissions law — and mistakes made through the CAN bus / UDS tooling in Workshop mode can
affect the vehicle's control units directly. Before touching real hardware:

- **Try Simulator mode first** (Workshop → Simulator) — no vehicle or adapter required.
- **Always back up your ECU ROM** before flashing.
- Only use tuning/flashing features on a vehicle in a safe location, engine off unless testing
  requires it, battery voltage 13.5–14.5V.
- The developers are not responsible for damage resulting from use of this application.

## Overview

Pro Racing OBD is one Android Studio project with two apps merged into it, selected at launch:

```
LoginActivity → ModeSelectActivity → ┬─ MainActivity        (Racing mode)
                                      └─ WorkshopActivity  →  CarHackerKit UI (Workshop mode)
```

- **Racing mode** (`:app`) — a live carbon-fiber-themed OBD2 dashboard, DTC diagnostics, ECU
  flashing, and tuning, built around 50+ auto-detected vehicle profiles.
- **Workshop mode** (`:carhackerkit`, run in-process inside the same app) — a deeper diagnostics
  and security toolkit: CAN bus monitoring, PID browsing, UDS/ECU discovery, and seed-key testing.

Login uses Google/Facebook via Firebase Auth when configured, but **fails forward to a local
guest session** if Firebase isn't set up — so the app is fully usable without any backend
configuration.

## Features

### Racing mode (`:app`)

- **Live gauge dashboard** — RPM, speed, coolant/intake temp, throttle, boost, battery, timing,
  fuel level, AFR — polled every ~500 ms over real ELM327 PID commands (no simulated data once
  connected):

  | Gauge | PID | Formula |
  |-------|-----|---------|
  | RPM | `010C` | `(A*256+B)/4` |
  | Speed | `010D` | `A * 0.621371` mph |
  | Coolant Temp | `0105` | `(A-40)*9/5+32` °F |
  | Intake Temp | `010F` | `(A-40)*9/5+32` °F |
  | Throttle | `0111` | `A*100/255` % |
  | Boost | `010B`+`0133` | MAP vs baro, PSI |
  | Battery | `ATRV` | volts |
  | Timing | `010E` | `A/2 - 64` deg |
  | Fuel Level | `012F` | `A*100/255` % |
  | AFR | `0106`/`0107` | fuel trim calc |

  Color-coded 🟢 Normal · 🟠 Warning · 🔴 Critical, `--` shown when not connected.
- **DTC diagnostics** — read/clear active, pending, and permanent codes; MIL status.
- **ECU flash** — backup and restore ROM, with live flash progress.
- **Tuning** — AFR, timing, boost, rev limiter, launch control (presets: Conservative, Street,
  Aggressive, Race).
- **50+ vehicle profiles with VIN auto-detect** — reads the VIN via Mode 09 PID 02, parses the
  WMI, and auto-selects a matching profile (BMW, Ford, Chevy/GMC, Dodge/Jeep, Toyota/Lexus,
  Nissan/Infiniti, Honda/Acura, Mitsubishi, Subaru, VW/Audi, and generic OBD2/diesel). Falls back
  to fetching a remote "plugin pack" profile by VIN prefix.
- **Bluetooth device search** — live filter across paired adapters instead of a fixed list.
- **Social login** — Google Sign-In (Firebase Auth) and Facebook Login, with a Skip option for
  local-only use; falls back to a guest session if unconfigured.
- Carbon-fiber / checkered-flag racing UI theme.

### Workshop mode (CarHackerKit, `:carhackerkit`)

An in-vehicle diagnostics and security toolkit, launched from `WorkshopActivity` and run
in-process (no separate app install required):

- **CAN bus monitor** — live ISO-TP frame capture, replay, and fuzzing.
- **PID browser** — SAE J1979 Mode 01/09 PID enumeration.
- **ECU info** — VIN, ECU name, calibration ID.
- **DTC reader/clearer.**
- **Security Tester** — UDS/ECU discovery and seed-key testing, for authorized diagnostic/security
  work on vehicles you own or are engaged to test.
- **Simulator mode** — exercise every feature above with no adapter or vehicle attached.
- Connects over Bluetooth, USB, or Wi-Fi.

### Ace — voice copilot

Fully on-device (Android's built-in `TextToSpeech` + `SpeechRecognizer` — no network calls, no
new accounts), available from a mic FAB on every screen:

- **Tap** the FAB for a spoken status narration of the live gauges.
- **Long-press** to speak a command — Ace only ever listens in response to this explicit action,
  never on its own.
- **Anything a button can do, by voice** — navigate any tab, scan/auto-detect the vehicle,
  read/clear codes, back up the ECU, load tuning presets. Consequential actions (flashing the
  ECU, applying tuning) require a spoken "yes" before running.
- **"What can you do?"** — lists navigable screens and the current screen's own commands, built
  directly from what's actually registered so it can't drift out of sync.
- **Two vocabulary levels** — BASIC (default, plain/jargon-free — "your fuel mixture's running a
  bit thin") and ENTHUSIAST (real gauge names/units/numbers), toggled by a switch on Settings.
- **Debounced proactive narration** — speaks up on its own for a real condition (e.g. "your
  engine's running a little warm"), but a message identical to the last one spoken is suppressed
  for 20 seconds so it can't turn into repeating itself every ~500ms poll tick.
- **Diagnostic reasoning beyond static thresholds** — real-world battery-voltage interpretation
  (running vs. resting), coolant rate-of-change and slow-warmup (possible stuck thermostat)
  detection, and cross-sensor correlations (lean AFR + boost, low oil pressure + high RPM) that
  escalate severity even when no single gauge alone has crossed its threshold. See
  [SENSOR_DIAGNOSTICS.md](SENSOR_DIAGNOSTICS.md) for the full reasoning behind these, including an
  explicit design principle: prefer under-reacting over false alarms.
- **Launch greeting** — speaks one of several intro scripts on app open (20% faster than Ace's
  normal speech rate), rotating so it isn't the same line every time.

Ace's rule-based "brain" today (`RuleBasedNarrationEngine`, `RuleBasedCommandHandler`) is an
explicit seam — see [DIAGNOSTIC_PLATFORM_VISION.md](DIAGNOSTIC_PLATFORM_VISION.md) for where this
is headed (an on-device LLM, a real multi-vehicle diagnostic platform).

## Architecture

Two Gradle modules in one project:

| Module | Type | Package | Role |
|---|---|---|---|
| `:app` | `com.android.application` | `xyz.surina.racehacker` | Racing dashboard, login, ECU flash/tuning — the installed app |
| `:carhackerkit` | `com.android.library` | `com.carhacker.kit` | Workshop diagnostics/security toolkit, consumed in-process by `:app` via `implementation project(':carhackerkit')` |

`WorkshopActivity` in `:app` launches `com.carhacker.kit.ui.MainActivity` from the library
directly — Workshop mode runs inside the same process and APK as Racing mode, not as a separate
installed app.

## Tech Stack

| | |
|---|---|
| Language | Kotlin + Java 11 |
| Build | Gradle / AGP 8.13.2 |
| SDK | compileSdk 34 · minSdk 24 (Android 7+) · targetSdk 34 |
| Auth | Firebase Auth + BOM, `play-services-auth` (Google), Facebook Login SDK |
| OBD | [`com.github.pires:obd-java-api`](PIRES_API_INTEGRATION.md) (via JitPack) |
| Connectivity | `usb-serial-for-android` (USB ELM327), `androidx.bluetooth` (alpha) |
| Storage | Room + `androidx.security:security-crypto` (`:carhackerkit`) |
| Networking | OkHttp (profile sync), Gson |
| Charts | MPAndroidChart |
| Concurrency | Kotlin coroutines |

## Project Structure

```
app/src/main/java/xyz/surina/racehacker/
├── activities/   (LoginActivity, ModeSelectActivity, MainActivity, WorkshopActivity)
├── adapters/     (DtcAdapter, GaugeAdapter, BluetoothDeviceAdapter)
├── auth/         (AuthManager, ProfileSyncService)
├── ecu/          (EcuFlashManager, TuningParameters)
├── fragments/    (DashboardFragment, DiagnosticsFragment, EcuFlashFragment, TuningFragment, SettingsFragment)
├── models/       (GaugeData)
├── services/     (DtcManager, EnhancedObdService, ObdConnectionService, PiresObdManager)
├── utils/        (DataLogger)
├── vehicles/     (VehicleProfile)
└── voice/        (Ace, ActionRegistry, NarrationEngine + RuleBasedNarrationEngine,
                    CommandHandler + RuleBasedCommandHandler, GaugeVocabulary,
                    VocabularyLevel, VocabularyPrefs)

carhackerkit/src/main/java/com/carhacker/kit/
├── CarHackerApp.kt
├── can/          (CANProtocol.kt)
├── obd/          (OBDConnection.kt, OBDProtocol.kt, PIDDefinitions.kt)
├── security/     (SecurityTester.kt)
└── ui/           (MainActivity.kt, LogAdapter.kt)
```

## Getting Started

### Prerequisites

- Android Studio (latest)
- JDK 11+
- Android SDK 34

### Build & run

```bash
git clone https://github.com/aaronlumen/RaceHacker.git
cd RaceHacker
./gradlew :app:installDebug     # build + install on a connected device/emulator
# or open the project in Android Studio and hit Run
```

The app builds and runs with **no backend configuration required** — login falls back to a local
guest session automatically.

### Optional: social login setup

1. [console.firebase.google.com](https://console.firebase.google.com) → create a project → add an
   Android app with package `xyz.surina.racehacker`.
2. Download `google-services.json` → place in `app/`.
3. Authentication → enable **Google** sign-in.
4. Copy the **Web Client ID** → `app/src/main/res/values/strings.xml` → `google_web_client_id`.
5. (Optional) Fill in `facebook_app_id`, `facebook_client_token`, `fb_login_protocol_scheme` in
   `strings.xml` for Facebook Login.

## Usage

### No hardware needed (Simulator / demo)

- **Workshop mode** → Simulator — exercises PID browsing, DTC read/clear, CAN capture/replay, and
  UDS discovery against synthetic data.
- **Racing mode** dashboard shows `--` on each gauge until an adapter is connected.

### With a real ELM327 adapter

1. Pair your Bluetooth ELM327 adapter in Android settings (or connect via USB/Wi-Fi in Workshop
   mode).
2. Plug the adapter into the vehicle's OBD2 port, ignition ON.
3. In-app: Settings tab (Racing) or Connect (Workshop) → search/select your adapter → Connect.

Common AT/PID commands used under the hood:

```
ATZ      - Reset adapter
ATE0     - Echo off
ATSP6    - Set protocol (ISO 15765-4 CAN)
0100     - Request supported PIDs
03       - Request DTCs
04       - Clear DTCs
```

**Troubleshooting**: no data on gauges → confirm engine is running and protocol is correct, try a
generic OBD2 adapter first. Can't connect → re-check Bluetooth pairing and that the adapter is
seated in the OBD2 port with ignition on.

## Permissions

| Permission | Why |
|---|---|
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | Pair/connect to ELM327 Bluetooth adapters |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Required by Android for Bluetooth device scanning |
| USB host (`android.hardware.usb.host`) | USB-serial ELM327 adapters |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | Data logging (CSV), ECU ROM backup files |
| `INTERNET` | Firebase Auth, profile sync, plugin-pack profile fetch |
| `FOREGROUND_SERVICE`, `WAKE_LOCK` | Keep CAN/OBD monitoring alive during Workshop sessions |
| `RECORD_AUDIO` | Ace's voice commands (only requested when you first long-press the mic FAB) |

## Documentation

- **[PIRES_API_INTEGRATION.md](PIRES_API_INTEGRATION.md)** — the standard OBD2 PID reference (80+
  PIDs by category via the Pires OBD-Java API). This is a living document — proprietary
  manufacturer PID sets (starting with Harley-Davidson) are actively being added.
- **[SENSOR_DIAGNOSTICS.md](SENSOR_DIAGNOSTICS.md)** — the design reference for Ace's diagnostic
  narration: normal ranges, rate-of-change/correlation reasoning, and an explicit "avoid false
  alarms" design principle, with a status column showing what's actually implemented vs. still
  just documented.
- **[DIAGNOSTIC_PLATFORM_VISION.md](DIAGNOSTIC_PLATFORM_VISION.md)** — the long-term vision for a
  full multi-vehicle diagnostic platform (a real schema, a relationship-inference engine,
  per-vehicle-class PID profiles). Not built — recorded so the vision survives as a spec.
- **[ios-testkit/README.md](ios-testkit/README.md)** — unrelated macOS/Xcode shell scripts for
  iterating on a physical iPhone; not part of the Android app.

## Contributing

Single-developer project currently — issues and PRs are welcome, but there's no formal
CONTRIBUTING guide yet.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Known Gaps

- X, Snapchat, Instagram, Amazon, AutoZone login tiles are present in the UI but not yet wired up
  (Google and Facebook are functional).
- Ace's narration/thresholds (battery voltage, coolant behavior) use generic constants, not
  per-vehicle normal ranges — `VehicleProfile` has no threshold fields yet. See the "vehicle-aware
  thresholds" gap in [SENSOR_DIAGNOSTICS.md](SENSOR_DIAGNOSTICS.md).
- Oil temperature and oil pressure have gauge slots and narration rules, but no real PID data
  behind them yet (`ObdConnectionService` doesn't poll them) — always show `--`.

---

*SURINA · Aaron Lumen · [race.surina.xyz](https://race.surina.xyz)* 🏁
