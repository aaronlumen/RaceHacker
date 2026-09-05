package xyz.surina.racehacker.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Threshold-based narration: turns gauge readings into short, human sentences.
 * No AI involved — this is the "always works, zero dependencies" narrator the
 * app ships with today, while on-device LLM narration is still being
 * integrated (see the llama.cpp-on-Android work).
 *
 * Two vocabulary levels (see {@link VocabularyLevel}): BASIC is plain,
 * jargon-free language aimed at someone with no car background; ENTHUSIAST
 * uses real terms, units and numbers for people who already know their car.
 *
 * Beyond simple "is this one gauge out of range" checks, this engine also
 * looks at:
 * <ul>
 *   <li><b>Rate of change</b> — coolant temp climbing unusually fast is worth
 *       an early heads-up even before it crosses the static warning threshold.
 *       Conversely, coolant that stays low for too long after startup (slow/no
 *       warm-up) can mean a stuck-open thermostat.</li>
 *   <li><b>Cross-sensor correlation</b> — a single gauge in isolation can look
 *       merely "in warning" while the combination with another gauge is
 *       actually serious: lean AFR + high boost is a detonation risk, and low
 *       oil pressure + high RPM is a bearing-damage risk, even when neither
 *       gauge alone has crossed its own critical threshold.</li>
 * </ul>
 * This instance keeps small bits of state (last reading, engine-start time)
 * across calls to support this — it's meant to be reused for the lifetime of
 * the app (see {@link Ace}, which owns one instance), not recreated per call.
 *
 * Battery voltage is special-cased with real automotive thresholds instead of
 * {@link GaugeData}'s generic ones (11.5V/11.0V, which only catch a battery
 * that's already nearly dead). With the engine running, the alternator should
 * be holding ~13.5–14.5V, so under 13V while running points at the *charging
 * system*; with the engine off, a healthy fully-charged battery rests around
 * 12.6–12.8V, so under 12.8V at rest is an early sign of a weakening battery.
 * Engine-running is inferred from the RPM gauge.
 *
 * These thresholds and heuristics are a reasonable first pass grounded in
 * common automotive knowledge, not a substitute for a real diagnostic tool —
 * they haven't been validated against a real vehicle yet.
 */
public class RuleBasedNarrationEngine implements NarrationEngine {

    private static final float BATTERY_RUNNING_WARNING_V = 13.0f;
    private static final float BATTERY_RUNNING_CRITICAL_V = 12.0f;
    private static final float BATTERY_RESTING_WARNING_V = 12.8f;
    private static final float BATTERY_RESTING_CRITICAL_V = 11.8f;
    private static final float ENGINE_RUNNING_RPM_THRESHOLD = 300f;

    // Lean-under-boost: independent of AFR's own gauge thresholds (11.5/11.0)
    // because the danger threshold shifts once real boost is present.
    private static final float LEAN_UNDER_BOOST_AFR = 12.5f;
    private static final float MEANINGFUL_BOOST_PSI = 5.0f;
    // Oil-starvation-at-high-RPM: only escalate once oil pressure is already
    // at least in its own warning zone AND revs are high.
    private static final float HIGH_RPM_FOR_OIL_RISK = 4000f;

    // Coolant rising faster than this (even while still under the normal
    // warning threshold) is an early heads-up heuristic, not a hard rule.
    private static final float RAPID_COOLANT_RISE_PER_MIN_F = 5.0f;
    private static final long MIN_SAMPLE_INTERVAL_MS = 5_000;
    // ECT normally reaches ~185-220°F fully warm; a car still well under that
    // long after startup can mean a stuck-open thermostat.
    private static final float SLOW_WARMUP_THRESHOLD_F = 160f;
    private static final long WARMUP_GRACE_PERIOD_MS = 10 * 60 * 1000; // 10 min
    // Bottom of the normal fully-warm operating band — crossing this is the
    // positive counterpart to the slow-warmup check, not a warning.
    private static final float OPERATING_TEMP_F = 185f;

    // Cold-engine + aggressive-driving: a real risk right after startup, not
    // something that needs the 10-minute grace period above — pushing a cold
    // engine hard causes real wear regardless of whether it'll warm up fine
    // given time.
    private static final float COLD_ENGINE_THRESHOLD_F = 120f;
    private static final float AGGRESSIVE_THROTTLE_PCT = 60f;

    // Persistent fuel trim: SENSOR_DIAGNOSTICS.md calls this out by name as
    // needing "the persistent, not momentary, rule from day one" — a single
    // poll reading combined STFT+LTFT out of range is normal and common
    // during a throttle transient, not a real signal on its own.
    private static final float FUEL_TRIM_WARNING_PCT = 10f;
    private static final float FUEL_TRIM_CRITICAL_PCT = 20f;
    private static final long FUEL_TRIM_SUSTAIN_MS = 30_000; // 30s of continuous out-of-range before narrating

    // Stuck/lazy O2 sensor: a healthy narrowband sensor swings across a wide
    // range as the ECU's closed loop hunts around stoichiometric — a voltage
    // that just sits still for a long stretch (once warmed up) is the tell,
    // not any particular absolute value (see SENSOR_DIAGNOSTICS.md: "don't
    // interpret a fixed voltage without knowing sensor type").
    private static final float O2_SWITCH_DELTA_V = 0.08f;
    private static final long O2_STUCK_DURATION_MS = 30_000;

    private final Map<GaugeData.GaugeType, HistoryPoint> history = new HashMap<>();
    private Long engineStartedAtMs;
    private boolean notifiedOperatingTempThisSession;
    private Long fuelTrimOutOfRangeSinceMs;
    private Float o2LastSeenVoltage;
    private Long o2LastChangeMs;

    @Override
    public String narrate(List<GaugeData> gauges, VocabularyLevel level) {
        String alert = checkForAlert(gauges, level);
        return alert != null ? alert : allClearMessage(gauges, level);
    }

    @Override
    public String checkForAlert(List<GaugeData> gauges, VocabularyLevel level) {
        boolean basic = level == VocabularyLevel.BASIC;
        long now = System.currentTimeMillis();
        boolean engineRunning = isEngineRunning(gauges);
        trackEngineRunTime(engineRunning, now);

        // 1. Cross-sensor correlations first — a combination can be more urgent
        //    than any single gauge's own reading suggests.
        Verdict correlation = checkCorrelations(gauges, basic);
        if (correlation != null) return correlation.message;

        // 2. Per-gauge static thresholds.
        List<String> critical = new ArrayList<>();
        List<String> warning = new ArrayList<>();
        for (GaugeData g : gauges) {
            if (!g.hasData()) continue;

            if (g.getType() == GaugeData.GaugeType.BATTERY_VOLTAGE) {
                Verdict v = evaluateBattery(g, basic, engineRunning);
                if (v != null) (v.critical ? critical : warning).add(v.message);
                continue;
            }

            String msg = messageFor(g, basic);
            if (msg == null) continue;
            if (g.isCritical()) {
                critical.add(msg);
            } else if (g.isWarning()) {
                warning.add(msg);
            }
        }
        if (!critical.isEmpty()) return critical.get(0);
        if (!warning.isEmpty()) return warning.get(0);

        // 3. Trend-based early warnings — only surface these when nothing above
        //    already fired, since they're advisory rather than an active fault.
        Verdict rateOfChange = checkCoolantRateOfChange(gauges, basic, now);
        if (rateOfChange != null) return rateOfChange.message;

        Verdict slowWarmup = checkSlowWarmup(gauges, basic, engineRunning, now);
        if (slowWarmup != null) return slowWarmup.message;

        Verdict reachedTemp = checkReachedOperatingTemp(gauges, basic, engineRunning);
        if (reachedTemp != null) return reachedTemp.message;

        Verdict fuelTrim = checkPersistentFuelTrim(gauges, basic, engineRunning, now);
        if (fuelTrim != null) return fuelTrim.message;

        Verdict stuckO2 = checkStuckO2Sensor(gauges, basic, engineRunning, now);
        if (stuckO2 != null) return stuckO2.message;

        return null;
    }

    // ─── Engine-running / warm-up tracking ──────────────────────────────────

    private boolean isEngineRunning(List<GaugeData> gauges) {
        GaugeData rpm = find(gauges, GaugeData.GaugeType.RPM);
        return rpm != null && rpm.hasData() && rpm.getCurrentValue() > ENGINE_RUNNING_RPM_THRESHOLD;
    }

    private void trackEngineRunTime(boolean engineRunning, long now) {
        if (engineRunning && engineStartedAtMs == null) {
            engineStartedAtMs = now;
            history.remove(GaugeData.GaugeType.COOLANT_TEMP); // fresh session — don't compare across an off period
            notifiedOperatingTempThisSession = false;
        } else if (!engineRunning) {
            engineStartedAtMs = null;
        }
    }

    // ─── Cross-sensor correlations ───────────────────────────────────────────

    private Verdict checkCorrelations(List<GaugeData> gauges, boolean basic) {
        GaugeData afr = find(gauges, GaugeData.GaugeType.AFR);
        GaugeData boost = find(gauges, GaugeData.GaugeType.BOOST);
        if (afr != null && afr.hasData() && boost != null && boost.hasData()
                && afr.getCurrentValue() >= LEAN_UNDER_BOOST_AFR && boost.getCurrentValue() >= MEANINGFUL_BOOST_PSI) {
            return new Verdict(true, basic
                    ? "Your fuel mix is running lean while you're under boost — that's a bad combo that can hurt your engine fast. Back off now."
                    : "Lean AFR under boost (" + fmt(afr.getCurrentValue()) + ":1 at " + fmt(boost.getCurrentValue()) + " PSI) — real detonation risk, back off now.");
        }

        GaugeData oilPressure = find(gauges, GaugeData.GaugeType.OIL_PRESSURE);
        GaugeData rpm = find(gauges, GaugeData.GaugeType.RPM);
        if (oilPressure != null && oilPressure.hasData() && oilPressure.isWarning()
                && rpm != null && rpm.hasData() && rpm.getCurrentValue() >= HIGH_RPM_FOR_OIL_RISK) {
            return new Verdict(true, basic
                    ? "Your oil pressure's low and you're revving high at the same time — that's a dangerous mix for your engine's bearings. Ease off right now."
                    : "Low oil pressure at " + fmt(rpm.getCurrentValue()) + " RPM — bearing damage risk, ease off now.");
        }

        GaugeData coolant = find(gauges, GaugeData.GaugeType.COOLANT_TEMP);
        GaugeData throttle = find(gauges, GaugeData.GaugeType.THROTTLE_POSITION);
        if (coolant != null && coolant.hasData() && coolant.getCurrentValue() < COLD_ENGINE_THRESHOLD_F
                && throttle != null && throttle.hasData() && throttle.getCurrentValue() >= AGGRESSIVE_THROTTLE_PCT) {
            // A real wear risk right now, regardless of whether the engine will
            // warm up fine given time — distinct from checkSlowWarmup() below,
            // which is about the engine failing to warm up at all.
            return new Verdict(false, basic
                    ? "Hey, your engine's still cold — try to take it easy until it's warmed up."
                    : "Aggressive throttle on a cold engine (coolant " + fmt(coolant.getCurrentValue()) + "°F) — ease in until it's warmed up.");
        }

        return null;
    }

    // ─── Coolant trend heuristics ────────────────────────────────────────────

    private Verdict checkCoolantRateOfChange(List<GaugeData> gauges, boolean basic, long now) {
        GaugeData coolant = find(gauges, GaugeData.GaugeType.COOLANT_TEMP);
        if (coolant == null || !coolant.hasData()) return null;

        HistoryPoint prev = history.get(GaugeData.GaugeType.COOLANT_TEMP);
        history.put(GaugeData.GaugeType.COOLANT_TEMP, new HistoryPoint(coolant.getCurrentValue(), now));
        if (prev == null) return null;

        long elapsedMs = now - prev.timeMs;
        if (elapsedMs < MIN_SAMPLE_INTERVAL_MS) return null; // avoid noisy short-interval spikes

        float degPerMinute = (coolant.getCurrentValue() - prev.value) / (elapsedMs / 60_000f);
        boolean alreadyFlaggedByStaticThreshold = coolant.isWarning() || coolant.isCritical();
        if (!alreadyFlaggedByStaticThreshold && degPerMinute >= RAPID_COOLANT_RISE_PER_MIN_F) {
            return new Verdict(false, basic
                    ? "Your engine temperature's climbing pretty fast — it's not too hot yet, but keep an eye on it."
                    : "Coolant's rising fast, about " + Math.round(degPerMinute) + "°F per minute — still in range, but worth watching.");
        }
        return null;
    }

    private Verdict checkSlowWarmup(List<GaugeData> gauges, boolean basic, boolean engineRunning, long now) {
        if (!engineRunning || engineStartedAtMs == null) return null;
        GaugeData coolant = find(gauges, GaugeData.GaugeType.COOLANT_TEMP);
        if (coolant == null || !coolant.hasData()) return null;

        long runningMs = now - engineStartedAtMs;
        if (runningMs < WARMUP_GRACE_PERIOD_MS) return null; // still within a normal warm-up window
        if (coolant.getCurrentValue() < SLOW_WARMUP_THRESHOLD_F) {
            return new Verdict(false, basic
                    ? "Your engine's been running a while but still isn't warming up like it should — that can mean the thermostat's stuck open."
                    : "Coolant's still under " + (int) SLOW_WARMUP_THRESHOLD_F + "°F well after startup — possible stuck-open thermostat.");
        }
        return null;
    }

    /** Positive counterpart to checkSlowWarmup() — fires once per engine session. */
    private Verdict checkReachedOperatingTemp(List<GaugeData> gauges, boolean basic, boolean engineRunning) {
        if (!engineRunning || notifiedOperatingTempThisSession) return null;
        GaugeData coolant = find(gauges, GaugeData.GaugeType.COOLANT_TEMP);
        if (coolant == null || !coolant.hasData()) return null;

        if (coolant.getCurrentValue() >= OPERATING_TEMP_F) {
            notifiedOperatingTempThisSession = true;
            return new Verdict(false, basic
                    ? "Your engine's all warmed up now."
                    : "Coolant's reached normal operating temperature.");
        }
        return null;
    }

    // ─── Fuel trim (persistent, not momentary) ──────────────────────────────

    private Verdict checkPersistentFuelTrim(List<GaugeData> gauges, boolean basic, boolean engineRunning, long now) {
        GaugeData stft = find(gauges, GaugeData.GaugeType.STFT);
        GaugeData ltft = find(gauges, GaugeData.GaugeType.LTFT);
        if (!engineRunning || stft == null || !stft.hasData() || ltft == null || !ltft.hasData()) {
            fuelTrimOutOfRangeSinceMs = null;
            return null;
        }

        float combined = stft.getCurrentValue() + ltft.getCurrentValue();
        if (Math.abs(combined) < FUEL_TRIM_WARNING_PCT) {
            fuelTrimOutOfRangeSinceMs = null; // back in range resets the debounce, per design principles
            return null;
        }
        if (fuelTrimOutOfRangeSinceMs == null) {
            fuelTrimOutOfRangeSinceMs = now; // just went out of range — could still be a momentary blip
            return null;
        }
        if (now - fuelTrimOutOfRangeSinceMs < FUEL_TRIM_SUSTAIN_MS) return null;

        boolean critical = Math.abs(combined) >= FUEL_TRIM_CRITICAL_PCT;
        // Positive combined trim = ECU is adding fuel (running lean on its own,
        // e.g. a vacuum/unmetered-air leak); negative = removing fuel (running
        // rich, e.g. excess fuel delivery).
        boolean addingFuel = combined > 0;
        return new Verdict(critical, basic
                ? (addingFuel
                        ? "Your engine's computer has been adding extra fuel for a while now — that can point to a small air leak somewhere."
                        : "Your engine's computer has been cutting back fuel for a while now — that can mean it's getting more fuel than it should.")
                : "Fuel trim's been " + (addingFuel ? "+" : "") + Math.round(combined) + "% for a while now — "
                        + (addingFuel ? "possible vacuum/unmetered-air leak." : "possible excess fuel delivery."));
    }

    // ─── Upstream O2 sensor (stuck/lazy detection) ──────────────────────────

    private Verdict checkStuckO2Sensor(List<GaugeData> gauges, boolean basic, boolean engineRunning, long now) {
        GaugeData o2 = find(gauges, GaugeData.GaugeType.O2_SENSOR);
        GaugeData coolant = find(gauges, GaugeData.GaugeType.COOLANT_TEMP);
        boolean warmedUp = coolant != null && coolant.hasData() && coolant.getCurrentValue() >= OPERATING_TEMP_F;

        if (!engineRunning || !warmedUp || o2 == null || !o2.hasData()) {
            o2LastSeenVoltage = null;
            o2LastChangeMs = null;
            return null;
        }

        float v = o2.getCurrentValue();
        if (o2LastSeenVoltage == null || Math.abs(v - o2LastSeenVoltage) >= O2_SWITCH_DELTA_V) {
            o2LastSeenVoltage = v;
            o2LastChangeMs = now;
            return null;
        }
        if (now - o2LastChangeMs < O2_STUCK_DURATION_MS) return null;

        return new Verdict(false, basic
                ? "Your oxygen sensor reading hasn't moved in a while — it might be getting lazy or stuck."
                : "O2 sensor voltage's been flat for a while now — possible lazy/stuck sensor.");
    }

    // ─── Battery (real-world thresholds, not GaugeData's generic ones) ──────

    private Verdict evaluateBattery(GaugeData g, boolean basic, boolean engineRunning) {
        float v = g.getCurrentValue();
        if (engineRunning) {
            if (v < BATTERY_RUNNING_CRITICAL_V) {
                return new Verdict(true, basic
                        ? "Your battery voltage is really low even with the engine running — that's serious, get it checked soon."
                        : "Battery voltage's critically low while running at " + fmt(v) + " volts — likely a charging system fault.");
            }
            if (v < BATTERY_RUNNING_WARNING_V) {
                return new Verdict(false, basic
                        ? "Your battery's showing under thirteen volts while the engine's running — that usually means the part that charges your battery isn't working quite right."
                        : "Voltage's under 13 volts while running (" + fmt(v) + "V) — points at the alternator or charging system, not just the battery.");
            }
            return null;
        } else {
            if (v < BATTERY_RESTING_CRITICAL_V) {
                return new Verdict(true, basic
                        ? "Your battery's voltage is really low — it might not have enough charge to start the car."
                        : "Resting voltage's very low at " + fmt(v) + " volts — may not hold enough charge to start.");
            }
            if (v < BATTERY_RESTING_WARNING_V) {
                return new Verdict(false, basic
                        ? "Your battery's reading under twelve point eight volts at rest — that can be an early sign the battery's getting weak."
                        : "Resting voltage's under 12.8V (" + fmt(v) + "V) — often an early sign of a weakening battery.");
            }
            return null;
        }
    }

    // ─── Everything else: simple static thresholds ──────────────────────────

    private String messageFor(GaugeData g, boolean basic) {
        boolean critical = g.isCritical();
        switch (g.getType()) {
            case COOLANT_TEMP:
                if (critical) {
                    return basic
                            ? "Whoa — your engine's really overheating! Pull over safely as soon as you can."
                            : "Your engine's overheating — you should pull over when it's safe.";
                }
                return basic
                        ? "Your engine is getting a little hot. Let's ease up for a bit."
                        : "Your engine's running a little warm, we should slow down a little.";
            case OIL_TEMP:
                if (critical) {
                    return basic
                            ? "The oil that protects your engine is way too hot — ease off the gas."
                            : "Oil temperature's critically high — ease off the throttle.";
                }
                return basic
                        ? "The oil that protects your engine is heating up a bit."
                        : "Oil temperature's climbing a bit.";
            case OIL_PRESSURE:
                if (critical) {
                    return basic
                            ? "This is important — the oil that keeps your engine's parts moving smoothly is dangerously low. Shut it off as soon as it's safe."
                            : "Oil pressure's dangerously low — shut it down as soon as it's safe to.";
                }
                return basic
                        ? "The oil pressure that keeps your engine's parts moving smoothly is a little low."
                        : "Oil pressure's a little low, keep an eye on it.";
            case FUEL_LEVEL:
            // NOTE: DashboardFragment currently stores the fuel-level percentage under a
            // gauge typed FUEL_PRESSURE (labeled "Fuel Press") rather than FUEL_LEVEL — see
            // its "Fuel level mapped to FUEL_PRESSURE slot for display" comment. Handling
            // both here so fuel narration actually fires against real app data.
            case FUEL_PRESSURE:
                if (critical) {
                    return basic
                            ? "You're almost out of gas!"
                            : "You're almost out of fuel.";
                }
                return basic
                        ? "You're getting low on gas."
                        : "Fuel's getting low.";
            case RPM:
                if (critical) {
                    return basic
                            ? "The engine's spinning as fast as it's allowed to — ease off the gas pedal."
                            : "You're bouncing off the rev limiter — ease up on the throttle.";
                }
                return basic
                        ? "The engine's spinning pretty fast right now."
                        : "You're revving pretty high.";
            case AFR:
                if (critical) {
                    return basic
                            ? "Your engine's fuel mix is way off — that's not great for it, like a recipe missing a key ingredient."
                            : "Your air-fuel mixture's running dangerously lean.";
                }
                return basic
                        ? "Your engine's fuel mix is running a little thin."
                        : "Your air-fuel mixture's running a bit lean.";
            case BOOST:
                if (critical) {
                    return basic
                            ? "Your turbo's pushing really hard right now — that's risky, back off."
                            : "Boost is at a dangerous level — back off now.";
                }
                return basic
                        ? "Your turbo's pushing pretty hard right now."
                        : "Boost is getting up there.";
            default:
                return null; // not narrated in v1 (BATTERY_VOLTAGE is handled separately above)
        }
    }

    private String allClearMessage(List<GaugeData> gauges, VocabularyLevel level) {
        boolean basic = level == VocabularyLevel.BASIC;
        GaugeData rpm = find(gauges, GaugeData.GaugeType.RPM);
        if (rpm != null && rpm.hasData()) {
            return basic
                    ? "Everything's looking good out there."
                    : "Everything's looking good — RPM steady at " + rpm.getFormattedValue() + ".";
        }
        return "Everything's looking good.";
    }

    // ─── Small helpers ───────────────────────────────────────────────────────

    private GaugeData find(List<GaugeData> gauges, GaugeData.GaugeType type) {
        for (GaugeData g : gauges) {
            if (g.getType() == type) return g;
        }
        return null;
    }

    private String fmt(float v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private static class Verdict {
        final boolean critical;
        final String message;
        Verdict(boolean critical, String message) {
            this.critical = critical;
            this.message = message;
        }
    }

    private static class HistoryPoint {
        final float value;
        final long timeMs;
        HistoryPoint(float value, long timeMs) {
            this.value = value;
            this.timeMs = timeMs;
        }
    }
}
