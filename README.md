# 🏁 Pro Racing OBD

![Carbon Fiber](app/src/main/res/drawable/carbon_fiber_bg.png)

> **Real-time OBD2 racing dashboard for Android** — ELM327 Bluetooth · Live gauges · ECU flash · Social login · VIN auto-detect
>
> *Package:* `xyz.surina.proracingobd` · *Min SDK:* 24 (Android 7+) · *Target:* SDK 34

---

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ▓▓░░▓▓░░▓▓░░  PRO RACING OBD  ░░▓▓░░▓▓░░▓▓░░▓▓
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
              S U R I N A
```

---

## What We Built Tonight

### ✅ Bluetooth Device Search
Replaced the fixed 5-device list with a live **search filter** — type any part of the device name to narrow down 20+ paired adapters instantly.

### ✅ Real OBD2 Gauge Data
Removed the fake `simulateGaugeUpdates()` loop entirely. Gauges now show:
- **`--`** (muted gray) when not connected
- **Live sensor values** polled every 500 ms via real ELM327 PID commands when connected
- Color-coded: 🟢 Normal · 🟠 Warning · 🔴 Critical

**PIDs polled:**

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
| AFR | `0106/0107` | fuel trim calc |

### ✅ Visual Upgrade — Carbon Fiber + Checkered Flag

| Asset | Usage |
|-------|-------|
| ![flag](app/src/main/res/drawable/checkered_flag_bg.png) | Dashboard header · Login screen logo |
| Carbon fiber texture | Gauge card background overlay (α 18%) |

- Racing-black theme (`#0A0A0A` background, `#CC0000` red accents)
- Gauge values: 30sp monospace bold
- 3dp red top accent bar on each gauge card
- "**SURINA**" italic silver watermark — always visible, never intrusive

### ✅ Vehicle Expansion — 50+ Profiles + VIN Auto-Detect

App reads the VIN via Mode 09 PID 02, parses the WMI (first 3 chars), and auto-selects the matching profile. Falls back to fetching a **plugin pack** from:

```
GET https://race.surina.xyz/api/v1/profiles/{vin8}
    https://race.e164.cloud/api/v1/profiles/{vin8}   ← fallback
```

**Supported makes:**

| Make | Models |
|------|--------|
| BMW | N54 · N55 · S58 · B58 |
| Ford | F-150 · Powerstroke · Mustang · Explorer · Raptor |
| Chevy / GMC | Silverado · Duramax · Camaro · Corvette · Acadia · Sierra |
| Dodge / Jeep | Hemi · RAM 1500 · Cummins · Challenger · Charger · Cherokee · Grand Cherokee · Wrangler |
| Toyota / Lexus | Tacoma · Tundra · 4Runner · Camry · Supra · IS · GS · RX · RC-F |
| Nissan / Infiniti | GT-R · 370Z · Titan · Frontier · Q50 · Q60 |
| Honda / Acura | Civic Si · Accord · Ridgeline · TLX · NSX · Integra |
| Mitsubishi | Evo · Eclipse · Outlander |
| Subaru | WRX |
| VW / Audi | VAG platform |
| Generic | OBD2 Generic · Diesel Generic · Plugin Pack |

### ✅ Social Login (Google-first)

```
┌─────────────────────────────────────┐
│   🏁  PRO RACING OBD               │
│        SURINA                       │
│                                     │
│  [ Sign in with Google          ]   │  ← primary, full-width
│                                     │
│  ── or try something else ──        │
│                                     │
│  [ f Facebook ] [ 𝕏 X ] [ 👻 Snap ]│  ← mosaic
│  [ 📸 Insta  ] [ a Amazon] [AZ AZ ]│
└─────────────────────────────────────┘
```

- **Google Sign-In** via Firebase Auth (full OAuth, ID token → profile sync)
- **Facebook Login** via Facebook SDK
- X, Snapchat, Instagram, Amazon, AutoZone — tiles wired, coming soon
- **Skip option** for local-only use (no sync)
- VIN linked to Google account; profile synced to backend with Bearer token auth

### ✅ Package Renamed

`shop.surina.proracingobd` → **`xyz.surina.proracingobd`** — all 20 source files + Gradle + Manifest updated.

---

## Setup

### 1. Firebase (required for Google login)
1. [console.firebase.google.com](https://console.firebase.google.com) → create project
2. Add Android app → package: `xyz.surina.proracingobd`
3. Download `google-services.json` → place in `app/`
4. Authentication → enable **Google** sign-in
5. Copy **Web Client ID** → `app/src/main/res/values/strings.xml` → `google_web_client_id`

### 2. Facebook (optional)
Fill `facebook_app_id`, `facebook_client_token`, `fb_login_protocol_scheme` in `strings.xml`.

### 3. Build
```bash
./gradlew assembleDebug
# or open in Android Studio and hit Run
```

---

## Profile Sync API

```
POST https://race.surina.xyz/api/v1/profile
POST https://race.e164.cloud/api/v1/profile     ← auto-fallback

Authorization: Bearer <Firebase ID token>
Content-Type: application/json

{ "vin": "1FTZX17...", "profile": { ... } }
```

---

## Project Structure

```
app/src/main/java/xyz/surina/proracingobd/
├── activities/
│   ├── LoginActivity.java       # Google-first social login
│   └── MainActivity.java        # Bottom nav host + auth guard
├── auth/
│   ├── AuthManager.java         # Firebase auth singleton + SharedPrefs cache
│   └── ProfileSyncService.java  # OkHttp → race.surina.xyz / race.e164.cloud
├── fragments/
│   ├── DashboardFragment.java   # Live gauge grid (real PID data)
│   ├── DiagnosticsFragment.java # DTC read/clear
│   ├── EcuFlashFragment.java    # ROM backup + flash
│   ├── TuningFragment.java      # AFR, timing, boost, launch control
│   └── SettingsFragment.java    # BT search filter + VIN auto-detect
├── services/
│   └── ObdConnectionService.java  # ELM327 RFCOMM + PID polling
├── models/
│   └── GaugeData.java             # NaN = no data sentinel
├── adapters/
│   ├── GaugeAdapter.java          # Color-coded gauge cards
│   └── DtcAdapter.java
├── vehicles/
│   └── VehicleProfile.java        # 50+ profiles + detectFromVin() + fetchPluginProfile()
└── utils/
    └── DataLogger.java
```

---

## Warnings

> **ECU flashing can damage your vehicle.** Always backup your ROM first.
> Many tuning parameters are for **race use only** and may not be street legal.
> The developers are not responsible for damage resulting from use of this application.

---

## Version History

### v1.1.0 — Tonight's session
- Real OBD2 PID polling — fake data removed
- Bluetooth device search filter
- Carbon fiber + checkered flag UI
- SURINA watermark
- 50+ vehicle profiles with VIN auto-detection
- Plugin pack fetch from race.surina.xyz / race.e164.cloud
- Google + Facebook + social mosaic login
- Package renamed to `xyz.surina.proracingobd`

### v1.0.0 — Initial release
- 10-vehicle profiles, ELM327 connection, basic gauges, ECU flash, tuning presets

---

*SURINA · Aaron Lumen · [race.surina.xyz](https://race.surina.xyz)*  🏁
