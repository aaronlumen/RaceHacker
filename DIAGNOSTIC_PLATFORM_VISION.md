# Diagnostic Platform Vision

This is the long-term architecture for RaceHacker's diagnostics — a real
multi-vehicle diagnostic platform, not just a fixed set of gauges. It's a
**vision document**: none of this is built yet. See
[SENSOR_DIAGNOSTICS.md](SENSOR_DIAGNOSTICS.md) for the narrower, currently-
being-implemented reference on individual PID normal ranges and the specific
narration rules that exist today.

Current state, for contrast: one hardcoded vehicle profile's worth of
threshold logic, 14 fixed `GaugeType` values, no concept of "this vehicle
doesn't support this PID" distinct from "this PID reads zero." Everything
below is the target this should grow toward, not a description of what runs
today.

## The core principle: "unsupported" is a state, not a zero

Not every vehicle supports every standardized PID, and manufacturer-specific
PIDs extend well beyond the generic OBD-II set. A gauge with no data must be
distinguishable from a gauge legitimately reading zero — conflating the two
is actively misleading (e.g. showing "0 PSI boost" for a car that simply
doesn't report a boost PID at all). This has to be a first-class state
throughout the data model, not an afterthought.

## Core database structure

```
Vehicle → Engine → ECU → PID → Sensor → Unit → Operating State → Expected Range → Relationship → Alert
```

This is what lets one common dashboard/voice layer accommodate all of:
Standard OBD-II, CAN, J1979 PIDs, manufacturer-specific PIDs, diesel PIDs,
performance ECU parameters, motorcycle ECUs, ABS/TC modules, transmission
modules, and aftermarket systems (e.g. Bully Dog) — without assuming every
PID exists on every vehicle. The target scale discussed: an architecture
that works the same way across ~56 vehicle profiles.

## The relationship engine

The single most important idea here: **don't treat individual sensors as
independent numbers — treat them as a system.** A sensor reading in isolation
is much less informative than whether it's behaving consistently with the
sensors causally related to it.

> TPS increases → MAP changes → MAF increases → calculated load increases →
> injector demand changes → lambda responds → RPM accelerates.
>
> If one link in that chain breaks, that's far more diagnostic than simply
> reporting "MAF = 18 g/s."

Instead of reporting a raw value, the engine should classify each
relationship and synthesize a hypothesis:

```
MAF/RPM relationship: normal
MAF/TPS relationship: normal
MAF/MAP relationship: abnormal
Fuel trim response: +14%
Lambda response: lean
Diagnostic hypothesis: possible unmetered air / insufficient fuel /
                        airflow measurement error
```

```
Commanded boost: 21 psi
Actual boost: 14 psi
VGT command: 92%
MAF: below expected
Fuel pressure: normal
Diagnostic hypothesis: boost-generation/control problem
```

### General relationship categories (cross-vehicle-type)

| Relationship | Monitor together | What you're looking for |
|---|---|---|
| Airflow | RPM + MAF + MAP + TPS + Load | Does airflow make sense for engine speed/load? |
| Throttle | APP + TPS + commanded throttle + actual throttle | Pedal → command → throttle response |
| Fuel correction | STFT + LTFT + O2/Lambda + MAF | Why is the ECU adding/removing fuel? |
| Fuel delivery | Commanded rail pressure + actual rail pressure + RPM + load | Can the fuel system maintain requested pressure? |
| Boost | MAP + BARO + MAF + throttle + RPM | Actual boost/airflow response to demand |
| Ignition | RPM + load + spark advance + knock retard | Is the ECU pulling timing? |
| VVT | RPM + commanded cam + actual cam + load | Is the camshaft following the ECU command? |
| Temperature | ECT + IAT + oil temp + EGT | Thermal behavior under load |
| Charging | Battery voltage + alternator command + RPM | Charging-system behavior |
| Catalyst | Lambda/O2 upstream + downstream + catalyst temp | Catalyst response |
| EGR | EGR command + EGR actual + MAP + MAF + fuel trims | Does EGR actually affect airflow? |
| Transmission | RPM + vehicle speed + gear + TCC slip + trans temp | Shift/converter behavior |
| ABS | Four wheel speeds + vehicle speed | Wheel-speed agreement |
| Diesel combustion | Rail pressure + injection quantity + timing + boost + EGT | Fuel/air/combustion relationship |
| Diesel emissions | DPF pressure + DPF temp + soot load + regen state | DPF loading/regeneration |
| Turbo diesel | MAP + BARO + commanded boost + actual boost + VGT | Turbo control |
| Motorcycle | RPM + TPS + MAP + IAT + ECT + lambda + ignition | Throttle/load/mixture/ignition |

## Per-vehicle-class profiles

Vehicle-class-specific screens, each surfacing the PIDs and relationships
that actually matter for that class — this is the "vehicle/profile layer"
the whole platform hinges on, rather than assuming every PID exists
everywhere.

### Diesel (Cummins/Powerstroke-type) — dedicated screen

| PID | Monitor | Important relationship |
|---|---|---|
| Engine RPM | RPM | Baseline for everything |
| MAP | Manifold pressure | Boost system |
| BARO | Atmospheric pressure | Needed to interpret MAP |
| Boost | Calculated/actual | Compare with commanded |
| MAF | Air mass | Compare with boost/load |
| Fuel rail pressure | Actual | Critical |
| Rail pressure desired | Commanded | Compare actual vs. desired |
| Injection quantity | Fuel quantity | Compare with load |
| Injection timing | Timing | Compare RPM/load |
| EGR command | Command | Airflow/emissions |
| EGR actual | Actual | Command vs. response |
| VGT command | Turbo vane command | Boost response |
| VGT position | Actual vane position | Command vs. actual |
| EGT 1–4 | Exhaust temperatures | Cylinder/turbo/DPF clues |
| DPF differential pressure | Pressure across DPF | Loading/restriction |
| DPF soot load | Calculated soot | Regeneration requirement |
| DPF regen status | Active/inactive | Interpret EGT/pressure |
| DPF inlet temp | Temperature | Regen |
| DPF outlet temp | Temperature | Regen/catalyst behavior |
| SCR temp | Exhaust temp | Aftertreatment |
| DEF level | Fluid level | Emissions system |
| DEF pressure | System pressure | Injection system |
| NOx upstream | NOx | Combustion/aftertreatment |
| NOx downstream | NOx | SCR effectiveness |
| Transmission temp | Fluid temp | Towing/load |
| Torque converter slip | RPM difference | Transmission health |

Some newer standardized OBD data includes diesel-specific rail pressure and
exhaust/DPF-related PIDs, but many of the genuinely useful diesel parameters
are manufacturer-specific.

### Honda/BMW/Toyota performance/racing

| PID | Why |
|---|---|
| RPM | Primary engine reference |
| TPS | Driver demand |
| APP | Driver demand |
| MAP/Boost | Engine load |
| MAF | Airflow |
| IAT | Charge temperature |
| ECT | Engine temperature |
| Oil temperature | Thermal condition |
| Oil pressure | Engine protection |
| Fuel pressure | Fuel delivery |
| Lambda | Mixture |
| AFR commanded | ECU target |
| STFT/LTFT | Fuel correction |
| Ignition advance | Timing |
| Knock retard | Detonation protection |
| Per-cylinder knock | Identify individual-cylinder problems |
| VVT/VANOS/VTEC position | Valve timing |
| VVT/VANOS/VTEC command | ECU request |
| Cam actual vs. commanded | Timing tracking |
| EGT | Exhaust/combustion |
| Turbo wastegate/VGT | Boost control |
| Boost target | ECU request |
| Boost actual | Actual pressure |
| Wastegate duty | Turbo control |
| Gear | Drivetrain |
| Clutch status | Launch/shift analysis |
| Wheel speeds | Traction |
| Steering angle | Driver/traction correlation |

### Harley-Davidson and sportbikes

Same concepts, normalized for motorcycle ECUs:

| PID | Watch |
|---|---|
| RPM | Engine speed |
| TPS | Throttle |
| APP | If electronic throttle |
| MAP | Engine load |
| MAF | If equipped |
| IAT | Intake temperature |
| ECT / CHT | Engine temperature |
| Lambda/O2 | Mixture |
| STFT/LTFT | Fuel correction where supported |
| Injector pulse width | Fuel delivery |
| Fuel pressure | Fuel system |
| Ignition advance | Timing |
| Knock retard | Detonation |
| VVT | If equipped |
| Gear | Transmission |
| Vehicle speed | Speed |
| Wheel speed | Traction/ABS |
| Engine oil temp | Thermal |
| Battery voltage | Charging |
| Charging current | Electrical |
| Ride mode | ECU mode |
| Traction-control intervention | Performance |
| Throttle commanded | ECU demand |
| Throttle actual | Actual throttle |
| Lean/rich lambda | Combustion |

## Why this isn't being built tonight

This is a genuinely large platform — a real schema, a relationship-inference
engine, and per-vehicle-class PID profiles across dozens of vehicles. Building
any meaningful slice of it requires real PID data flowing from real hardware
to validate against (parsing correctness, actual normal ranges, whether a
given vehicle even reports a given PID at all), which isn't available in this
session. Recorded here in full so the vision survives and the next concrete
step — probably: add the `Vehicle → ... → Alert` schema as real data
structures, starting with the vehicle/profile layer already flagged as
missing in SENSOR_DIAGNOSTICS.md — has a complete spec to build against.
