# Feature Ideas

Gathered by watching Torque (a mature, long-standing OBD2 app) side by side with
RaceHacker, screen by screen. Organized by status so it's clear what's actually
been built vs. still just an idea.

## Built this session

- **Ace: master speech mute, pitch control, "test speech" button** — Settings.
- **Shift lights** — RPM-driven, color-escalating indicator on the Dashboard.
- **"Reached operating temperature" narration** — positive counterpart to the
  existing slow-warmup/stuck-thermostat check.
- **Cold-engine + aggressive-driving correlation** — sharper than the old
  purely time-based slow-warmup check; only warns if you're actually pushing
  the engine (high throttle) while it's still cold, not just idling.
- **"Connect to X" disambiguation** — if more than one paired device matches
  the spoken name, Ace asks which one instead of silently grabbing the first.
- **G-force gauge** — phone accelerometer (`TYPE_LINEAR_ACCELERATION`), no OBD
  connection required at all.
- **MAF + MAP gauges** — `SENSOR_DIAGNOSTICS.md`'s own suggested build order
  #1: standard Mode 01 PIDs, read and displayed, no narration rule yet.
- **Remove-adapter reminder** — a toast on app exit if the OBD adapter is
  still connected, so it doesn't get left plugged in draining the battery.
- **STFT/LTFT gauges + persistent-trim narration** — bank 1 short/long-term
  fuel trim, `SENSOR_DIAGNOSTICS.md` suggested build order #2. Both were
  already being queried internally to derive the AFR estimate; now also
  exposed as their own gauges, with a 30-second sustained-value rule before
  narrating (a single out-of-range poll is normal, not a signal).
- **Upstream O2 sensor gauge + stuck-sensor narration** — bank1/sensor1
  voltage (PID 0114), build order #3. Narrated via "hasn't moved in 30s+ once
  warmed up" rather than any absolute-voltage threshold, since a fixed O2
  voltage isn't inherently meaningful without knowing sensor type.
- **AMOLED burn-in reduction** — the Dashboard now nudges itself a few dp in
  a slow four-corner rotation once a minute.
- **User-configurable alarm thresholds** — Settings → Alarm Thresholds:
  pick any gauge, set your own warning/critical numbers, persisted across
  restarts (`GaugeThresholdPrefs`). Global per-type overrides, not yet
  per-`VehicleProfile` — see the vehicle-aware-thresholds gap in
  [SENSOR_DIAGNOSTICS.md](SENSOR_DIAGNOSTICS.md).
- **Historical graphing** — new History tab, MPAndroidChart line chart of any
  one gauge's last 5 minutes (`GaugeHistoryStore`, in-memory, resets on
  restart — `DataLogger`'s CSV export is still the durable record).

## Not built — next up, roughly in priority order

- **Units toggle (imperial/metric)** — currently hardcoded (MPH, °F, PSI)
  throughout `MainActivity.setupLiveGauges()` and `GaugeData`. Real effort:
  touches every gauge's display/formatting, not just a single setting.
- **Performance testing** — 0-60, quarter mile, braking distance. Needs
  speed+time tracking and a dedicated screen; a real scope, not a quick add.
- **GPS logging + map view** — location-tagged log rows and a route map.
  Needs `ACCESS_FINE_LOCATION` (already granted for BT scan) wired to
  `FusedLocationProviderClient`, plus a maps SDK.
- **User-definable custom PIDs/formulas** — the single biggest idea here,
  ties directly into the Harley-Davidson proprietary-PID work in
  [PIRES_API_INTEGRATION.md](PIRES_API_INTEGRATION.md) and the vehicle/profile
  layer described in [DIAGNOSTIC_PLATFORM_VISION.md](DIAGNOSTIC_PLATFORM_VISION.md).
  Worth digging into Torque's "TorqueScan" plugin (a PID-discovery tool "to
  find where extended PIDs are located in an ECU") as a reference before
  building this.
- **Share diagnostics with a trusted mechanic** — inspired by Torque's
  AutoTalky plugin (owners share live engine data with a registered
  mechanic). RaceHacker already has the right shape of infrastructure for
  this in `ProfileSyncService` (OkHttp → race.surina.xyz) — a natural
  template rather than new infrastructure.
- **Homescreen widgets** — gauge dials outside the app (Torque's "Widgets for
  Torque" plugin). Needs an Android App Widget provider — separate from
  anything currently in the app.
- **Reduce AMOLED burn** — shift the dashboard display slightly over time,
  for long dash-mounted sessions.

## Explicitly not pursuing

- **WiFi Tracker**-style wardriving — unrelated to OBD/vehicle diagnostics,
  outside RaceHacker's scope.
