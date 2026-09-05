# S3 Vision — Product Ideas Backlog

A single brainstorming session's worth of ideas for where "S3" (Surina 3 — RaceHacker's
in-app name, see `LoginActivity`'s launch greetings) could go, gathered in one sitting.
This is a **raw idea backlog**, not a spec — nothing here is scoped, prioritized against
the rest of `FEATURE_IDEAS.md`, or committed to. Cross-referenced against
[FEATURE_IDEAS.md](FEATURE_IDEAS.md), [SENSOR_DIAGNOSTICS.md](SENSOR_DIAGNOSTICS.md), and
[DIAGNOSTIC_PLATFORM_VISION.md](DIAGNOSTIC_PLATFORM_VISION.md) where an idea overlaps
something already tracked there, so effort isn't duplicated across docs.

One item from this session was actually built rather than just recorded — see
"Response learning" in `DIAGNOSTIC_PLATFORM_VISION.md`, now implemented as
`carhackerkit`'s `com.carhacker.kit.knowledge` package (Room-persisted PID/ECU/service
discovery, surfaced via CarHackerKit's "View Knowledge" button).

## 0. The signature feature — S3 Diagnostic Intelligence workflow

> "That is the difference between an OBD reader and a vehicle diagnostic platform."

The unifying pipeline everything else in this doc is a piece of:

```
SCAN → IDENTIFY → DISCOVER MODULES → READ CODES → COLLECT LIVE DATA
     → CORRELATE DATA → DIAGNOSE → TEST → VERIFY REPAIR → GENERATE REPORT
```

Where each stage maps to something already in this doc (or already in the codebase):

| Stage | Maps to |
|---|---|
| SCAN / IDENTIFY | VIN read (already exists) + response-learning lookup (built this session) |
| DISCOVER MODULES | Module tree, §5/§6 |
| READ CODES | Existing DTC read/clear, extended with pending/permanent/freeze-frame |
| COLLECT LIVE DATA | Synchronized multi-signal timeline, §1 |
| CORRELATE DATA | The relationship engine already sketched in `DIAGNOSTIC_PLATFORM_VISION.md` |
| DIAGNOSE | AI diagnostic intelligence, §4 |
| TEST | Active Tests (bidirectional), §5 |
| VERIFY REPAIR | Post-repair verification, §4 |
| GENERATE REPORT | Vehicle Health Report, §9 |

Worked example from the same message, worth keeping verbatim as the north star for what
"diagnose" should actually produce instead of a bare code:

> Instead of the app merely saying **P0302**, S3 says: *"Cylinder 2 Misfire Detected.
> Misfire counter increased 0 → 18 during 2,800–3,400 RPM. Fuel trims remained normal.
> Ignition timing dropped during the event. Recommended next tests: ignition coil →
> plug → injector. [Start Diagnostic Test]"*

Every individual stage already has a home elsewhere in this doc — what's new here is
naming the end-to-end pipeline as the thing to actually build toward, rather than
treating each stage as an independent feature.

## 1. Synchronized multi-signal timeline + tap-to-inspect

> "Then provide a timeline where everything is synchronized... User taps a point on the
> graph: RPM: 6,421, Throttle: 97%, Lambda: 0.89... That turns the app into an actual
> diagnostic analysis platform, not just a gauge app."

What exists today: all the core OBD signals (RPM, throttle, MAP, MAF, STFT, LTFT,
AFR/lambda, timing, coolant, IAT, speed) are already read and CSV-logged by
`DataLogger`, and G-force is already read from the phone accelerometer (displayed live,
not logged). What doesn't exist: GPS speed/elevation capture (needs only location
permission, already granted — **no Maps API key required**, that's only needed to
*render* a map, not to log GPS data), a "gear" signal, a wide/synchronized-format log
(today's CSV is long-format: one row per `(timestamp, parameter, value)`, not one row
per instant with every signal as a column), and — the actual "killer interface" — any
multi-signal chart overlay with tap-to-inspect. `HistoryFragment` today only charts one
gauge at a time. MPAndroidChart already supports point-tap callbacks
(`OnChartValueSelectedListener`) — the library is there, just unused for this.

## 2. Advanced graphing / oscilloscope mode

> "Think oscilloscope meets datalogger... unlimited channels, synchronized cursor,
> zoom/pan, event markers, RPM-triggered capture, threshold triggers, pre-trigger
> recording, post-trigger recording, overlay multiple runs, compare healthy vs faulty
> vehicle, export CSV/JSON/diagnostic report."

A significant extension of #1 above — same underlying data pipeline, much more
sophisticated capture/comparison semantics (triggers, pre/post-trigger buffers,
multi-run overlay). Worth treating as #1's "v2," not a separate data pipeline.

## 3. Performance / track mode

> "0–30, 0–60, 0–100, 60–130, 1/8 mile, 1/4 mile, 1/2 mile, braking distance, lateral G,
> longitudinal G, launch analysis, shift analysis, RPM drop during shifts, boost
> response, throttle response. Combine GPS + accelerometer + OBD."

Extends FEATURE_IDEAS.md's existing "Performance testing" entry (0-60/quarter-mile/
braking) with a full metric list and the same GPS+accelerometer+OBD fusion as #1 — all
three of #1/#2/#3 want the same underlying synchronized-capture engine underneath
different presentation layers (timeline, oscilloscope, performance-test summary).

## 4. Automated diagnostic intelligence (AI layer)

> "Instead of: P0171 — give: P0171 — System Too Lean. Then analyze correlated data...
> and produce: Likely causes... Then tell the user what to test next."

This is exactly the seam `NarrationEngine`'s own doc comment already anticipated:
*"v1 (RuleBasedNarrationEngine) is threshold-driven... this interface is the seam where
an on-device LLM can later be swapped in as a richer narrator."* CLAUDE.md's existing
llama.cpp + Gemma pipeline (this DGX box) is a real candidate backend. Related ideas
from the same discussion:

- **Voice-driven diagnosis** — "why is my check engine light on" through Ace triggers
  this instead of just reciting the code.
- **Freeze-frame-aware diagnosis** — needs Mode 02 freeze-frame capture (currently a
  total gap) so the AI reasons from the conditions that *caused* the fault, not
  whatever the gauges show now.
- **Confidence-ranked causes**, scored against how well live data fits each hypothesis
  — literally the relationship-engine example already in `DIAGNOSTIC_PLATFORM_VISION.md`
  (`MAF/MAP relationship: abnormal → possible unmetered air`).
- **"What changed" diffing** against the response-learning knowledge base (§ built this
  session) — "this used to read X on your last 3 drives, now it's Y."
- **Guided next-test walkthroughs** — Ace talks you through a physical test step by
  step, using the two-way voice interface that already exists.
- **Post-repair verification** — after a DTC clear, watch correlated signals over the
  next drive and confirm or flag whether the fix actually worked.
- Running this against a local model rather than a cloud API keeps vehicle diagnostic
  data private by default — a real, worth-stating advantage over cloud-based
  competitors, distinct from the response-learning *sync* idea (§ below) which does
  need real consent/anonymization design.

## 5. Module tree — Active Tests (bidirectional controls)

> "Cooling fan activation, fuel pump activation, injector tests, EVAP tests, EGR tests,
> throttle actuator tests, ABS actuator tests, HVAC actuator tests, relay activation,
> solenoid activation, transmission tests, steering tests."

Fleshes out the module-tree idea already logged in `DIAGNOSTIC_PLATFORM_VISION.md`'s
professional-scan-tool backlog (`Module → DTCs → live data → tests → service
functions`) with the actual "Active Tests" branch contents and UI shape:

```
ECM
 ├─ Live Data
 ├─ DTCs
 ├─ Freeze Frame
 ├─ Active Tests
 │    ├─ Cooling Fan
 │    ├─ Fuel Pump
 │    ├─ Injector
 │    └─ EVAP
 └─ Special Functions
```

**Safety note, stated plainly because it matters:** bidirectional actuator tests are a
materially different risk tier than passive reading — firing one at the wrong moment
can actually do something to the car (activate a fuel pump, move a steering actuator).
Whenever this gets built, it needs the same prominent confirmation/warning treatment
the existing ECU flash feature already has (README's Safety Disclaimer), not a bare
button.

## 6. Module tree — Special Functions (service resets/relearns/calibrations)

> "Oil service reset, battery registration, BMS reset, throttle relearn, idle relearn,
> steering-angle calibration, TPMS relearn, injector coding, DPF regeneration, EPB
> service mode, ABS bleeding, transmission adaptations, HVAC calibration, maintenance
> reset, immobilizer-related service functions where legitimately supported."

The other branch of the same module-tree idea (#5). Same safety-tier note applies.
Immobilizer-related functions sit under the same "your own vehicle, authorized use"
framing the rest of the security-testing code already uses (`SecurityTester.kt`'s own
header: *"For authorized security research only. NEVER test on vehicles without
explicit permission."*) — not a new category of concern, just another item in the same
bucket.

## 7. Custom PID laboratory

> "Name: Calculated Boost, Formula: MAP - BARO, Units: PSI... Support: PID equations,
> scaling, offsets, units, byte extraction, bit extraction, CAN signals, conditional
> logic, lookup tables. Then users can save and share PID packs."

This is FEATURE_IDEAS.md's existing "user-definable custom PIDs/formulas" entry
(already flagged there as *"the single biggest idea here"*) — now with an actual
formula-language spec. Worth digging into Torque's "TorqueScan" PID-discovery plugin
as prior art before designing this, per that doc's existing note.

### Monetization ideas for PID packs (asked for explicitly)

- **One-time paid packs** per vehicle/manufacturer (Play Billing one-time products) —
  e.g. "Harley-Davidson Extended PID Pack — $4.99."
- **Free community packs, paid "Verified & Tested" tier** — anyone can share a pack
  free; a validated-against-real-hardware badge is the paid differentiator, not the
  format itself.
- **"S3 Pro" subscription** bundling curated packs + cross-device sync (§ 10) + early
  access to new vehicle profiles.
- **Revenue share with pack creators** if community members can sell their own —
  gives contributors a reason to build packs, not just consume them.

Worth treating as its own project (Play Billing integration, pack hosting/server,
content moderation for community-submitted formulas) once the underlying formula
engine itself exists — not something to bolt on simultaneously.

## 8. Vehicle packs / community content (whole-vehicle scope)

> "Make every vehicle a profile... allow Community Vehicle Packs. Users could
> contribute validated: PIDs, dashboards, DTC definitions, CAN mappings, service
> procedures, sensor definitions, logging templates."

Same shape as #7, scaled up to the whole vehicle rather than individual PIDs — ties
directly into `DIAGNOSTIC_PLATFORM_VISION.md`'s existing `Vehicle → Engine → ECU → PID
→ ...` schema and per-vehicle-class profile tables. A vehicle pack is essentially "a
populated instance of that schema, shared":

```
2021 Harley-Davidson Road Glide
        │
        ├── ECU
        ├── TCM
        ├── ABS
        ├── BCM
        ├── Sensors
        ├── DTC database
        ├── PIDs
        ├── Service functions
        └── Known diagnostics
```

## 9. Vehicle Health Report (one-button PDF)

> "Generate Vehicle Health Report... Export to PDF. This would be excellent for shops
> and vehicle sellers."

A single-button rollup of data the app would already have by this point: vehicle
identity (VIN/mileage), ECUs scanned, active/pending DTCs, readiness monitor status,
battery voltage, and a priority recommendation — essentially a formatted view over the
response-learning knowledge base (§ built this session) plus a live DTC/readiness scan,
exported as PDF instead of read off-screen. Real commercial angle: pre-purchase
inspection reports, shop customer handouts.

## 10. Data recording + cloud (optional account layer)

> "Vehicle history, previous scans, DTC history, maintenance history, log storage,
> dashboard backups, custom PID library, vehicle profiles, cross-device
> synchronization."

The umbrella that response-learning's Phase 2 (§ `DIAGNOSTIC_PLATFORM_VISION.md`),
PID-pack sync (§ 7), and cross-device custom-PID sync would all eventually live under
— an opt-in account layer on top of the existing `ProfileSyncService` pattern
(`race.surina.xyz`). Same consent/privacy design questions already flagged for
response-learning's sync phase apply here across the board, at larger scope.

## 11. Motorcycle mode as a first-class mode

> "Since you're supporting motorcycles, I'd make this a first-class mode rather than
> simply adding them to the car database... create Bike Dash specifically optimized
> for riding."

Signals called out: RPM, throttle position, engine temp, intake temp, AFR/lambda,
battery voltage, ignition timing, gear, vehicle speed, engine load, fuel trims,
injector data, diagnostic codes, sensor monitoring, ride logging. Nearly all of this
already exists as generic Mode 01 PIDs in `:app` (this session added STFT/LTFT/O2 on
top of the existing set) — the actual ask is a **dedicated UI mode**, not new sensor
support: a bike-specific dashboard layout/gauge selection distinct from car Racing
mode, rather than motorcycles being just another `VehicleProfile` entry sharing the
car dashboard layout. `VehicleProfile` already has motorcycle profiles (Harley,
sportbikes per `DIAGNOSTIC_PLATFORM_VISION.md`'s per-vehicle-class table) — this is
about the UI/UX layer treating them differently, not the data layer.

## 12. ECU calibration/tuning layer — "S3 Diagnostic" vs. "S3 Advanced/Tuning"

> "The critical distinction is that reading/analyzing ECU data is much broader than
> safely writing modified calibration data. Writing should be heavily vehicle-specific,
> authenticated where required, and protected against interrupted programming."

A proposed product-tier split, not just a feature list — separating passive/safe
capability from active/risky capability at the product level, not just the UI level:

**Read/analyze side** (broad, safer): read calibration information, compare
calibration versions, calibration identification, map visualization, table
visualization, binary comparison, hex viewer, checksum validation, calibration
backup.

**Write side** (narrow, genuinely risky): restore, parameter editing where supported,
logging-assisted tuning, tune revision management.

This is a direct extension of what already exists — `EcuFlashManager` and
`TuningFragment` already do ROM backup/restore and Stage 1-4 parameter tuning (this is
also, per the earlier Car Hacker's Handbook chapter check, the feature area that
already covers Ch. 13, "Performance Tuning"). The user's own framing here is worth
preserving as a design principle for that existing code, not just new code: keep
raising the bar on what "write" requires (vehicle-specific validation, authentication
where the ECU supports it, protection against an interrupted flash bricking the ECU)
as more vehicles/calibration formats get added, rather than let a growing feature list
quietly erode how carefully the write path is guarded. The existing README Safety
Disclaimer (back up before flashing, simulator-first, no responsibility for damage) is
the floor here, not the ceiling.
