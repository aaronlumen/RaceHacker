# Sensor Diagnostics Reference

This is the design reference for Ace's diagnostic narration — what each sensor
normally looks like, what's actually worth flagging, and how to avoid false
alarms. It's a knowledge base to implement *against*, not a description of
what's implemented today — see the **Status** column below.

## Design principles (read before adding a new rule)

Ace narrates safety-relevant conditions out loud while someone may be driving.
A false alarm isn't neutral — it erodes trust and can be a real distraction.
So every rule in here should default toward **under-reacting, not
over-reacting**:

- **Require sustained conditions, not single samples.** OBD polling happens
  every ~500ms; a single noisy reading is not a trend. Rate-of-change checks
  need a real time window (seconds, not one poll tick); "still not warmed up"
  needs minutes of run time, not one low reading right after startup.
- **Prefer correlation over isolation.** A sensor being individually "out of
  range" is much less informative than two related sensors moving together in
  a way that only makes sense if something's actually wrong (lean AFR *and*
  boost; low oil pressure *and* high RPM). Where a real correlation exists,
  require it before escalating severity.
- **Only implement a rule against a sensor that's actually wired up.** Don't
  add narration logic for a PID `ObdConnectionService` doesn't poll — it's
  either dead code or, worse, something that could misfire against stale/
  default data.
- **Debounce everything.** See `Ace.checkForProactiveAlert()` — the same
  message won't repeat more than once every 20 seconds, and "nothing wrong
  anymore" always resets the debounce state.
- **When genuinely unsure, say less.** A rule that might be wrong should stay
  out until it can be validated against a real vehicle (see: this whole file
  is currently untested on real hardware).

## Status legend

- ✅ **Implemented & narrated** — real PID data flows in, and
  `RuleBasedNarrationEngine` has a rule for it.
- 🔶 **Read, not narrated** — `ObdConnectionService` polls it, but no
  narration rule exists yet.
- 🔲 **Not read at all** — no PID polling/parsing exists; narration for this
  would be speculative until that's built.

## Sensors

| PID | What it measures | Normal / what to watch | Diagnostic notes | Status |
|---|---|---|---|---|
| RPM | Engine speed | Idle should be relatively stable | Hunting/unexplained oscillation can point to air/fuel/ignition issues | ✅ (static thresholds; oil-pressure-at-high-RPM correlation) |
| Speed | Vehicle speed | — | Useful for correlating drivetrain/transmission behavior | 🔶 |
| Boost | Manifold pressure above atmospheric (forced induction) | — | See AFR correlation below | ✅ (static thresholds + lean-under-boost correlation) |
| AFR / Lambda | Air-fuel ratio | ~1.00 lambda (~14.7:1 gasoline) is stoichiometric | Deviations under load/acceleration are informative; lean **and** boosted together is a real detonation risk, more urgent than either alone | ✅ (static thresholds + lean-under-boost correlation) |
| ECT — Engine Coolant Temp | Temperature, warm-up rate | Cold start near ambient; ~185–220°F fully warm | Slow/absent warm-up can indicate a stuck-open thermostat; unusually fast rise (even while still "in range") is an early overheat signal | ✅ (static thresholds + rate-of-change + slow-warmup) |
| Oil Temp | Oil temperature | — | — | ✅ static thresholds, but 🔲 no real PID data (always NaN today) |
| Oil Pressure | Oil pressure | — | Low pressure **and** high RPM together risks bearing damage faster than either alone | ✅ static thresholds + correlation, but 🔲 no real PID data (always NaN today) |
| Battery / Control Module Voltage | Voltage | Running: alternator should hold ~13.5–14.5V. At rest: healthy battery ~12.6–12.8V | Under 13V while running points at the charging system, not the battery; under 12.8V at rest is an early weak-battery sign. Watch for abnormal drops during starting or when electrical loads switch on | ✅ (real-world thresholds, not GaugeData's generic ones) |
| Fuel Level | Tank level % | — | — | ✅ (currently piggybacks on a `FUEL_PRESSURE`-typed gauge — see code note) |
| IAT — Intake Air Temp | Temperature vs. ambient | Should generally be plausible relative to outside air | Extremely high readings can indicate heat soak or a sensor problem | 🔶 |
| Throttle Position (TPS) | % and rate of change | Should move smoothly, no unexplained jumps/dropouts | — | 🔶 |
| Ignition Timing Advance | °BTDC and response | — | Watch for abnormal retard, particularly under load | 🔶 |
| Exhaust Temp | Temperature | — | — | 🔲 |
| MAF — Mass Air Flow | g/s, changes with RPM/load | Should rise smoothly with throttle/RPM | Erratic or disproportionately low readings can indicate intake/MAF problems | 🔲 |
| MAP — Manifold Absolute Pressure | kPa/inHg, response to throttle | At idle, substantially below atmospheric on a naturally aspirated engine; should respond quickly to throttle | — | 🔲 |
| APP — Accelerator Pedal Position | % and correlation with throttle | Should track commanded throttle smoothly | — | 🔲 |
| O2 Sensor — upstream | Voltage or equivalence ratio | Narrowband sensors switch rapidly once warm | Don't interpret a fixed voltage without knowing sensor type | 🔲 |
| O2 Sensor — downstream | Voltage/trend | Mainly for catalytic-converter monitoring | A downstream signal closely mimicking upstream can indicate catalyst efficiency problems | 🔲 |
| STFT — Short-Term Fuel Trim | % | Immediate ECU fuel correction | Persistent large values matter more than momentary fluctuations | 🔲 |
| LTFT — Long-Term Fuel Trim | % | Long-term correction | Persistent large positive values suggest unmetered air/fuel-delivery issues; large negative values suggest excessive fueling | 🔲 |
| Engine Load | % | — | Useful alongside MAF/MAP, fuel trims, and timing | 🔲 |
| Knock Retard / Ignition Retard | Degrees of retard | — | Repeated significant retard under similar conditions matters; PID availability varies a lot by vehicle | 🔲 |
| Fuel Rail Pressure | Pressure and stability | — | Compare against manufacturer-expected pressure, especially during acceleration | 🔲 |
| Commanded Equivalence Ratio | Target mixture | — | Compare commanded vs. actual lambda where supported | 🔲 |
| EVAP Purge Command | % | — | Watch when purge begins and whether fuel trims shift when it does | 🔲 |
| EGR Command / Position | Command vs. actual | — | Look for proper response, not just a specific percentage | 🔲 |
| VVT/VCT Command & Actual | Cam angle correlation | Commanded and actual should track each other | One of the best live-data diagnostics for variable-valve-timing problems | 🔲 |
| Transmission Temperature | Temperature trend | — | Particularly useful under towing/heavy-load conditions, if available | 🔲 |

## Suggested build order

Roughly easiest-and-most-valuable first, once there's a real device to
validate PID parsing against:

1. **MAF, MAP** — standard Mode 01 PIDs (`0x10`, `0x0B`), single values, no
   correlation logic needed to be useful on their own.
2. **STFT/LTFT** — standard PIDs (`0x06`–`0x09`), genuinely diagnostic
   (persistent large trim values are a real "something's wrong" signal), but
   needs the "persistent, not momentary" rule from day one.
3. **Upstream O2 / commanded equivalence ratio** — more valuable paired with
   AFR, which is already implemented.
4. Everything else (knock retard, EGR, VVT, transmission temp) — lower
   priority; PID availability varies a lot by vehicle, so these need
   per-vehicle-profile awareness before they're reliable.
