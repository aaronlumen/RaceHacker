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

    private final Map<GaugeData.GaugeType, HistoryPoint> history = new HashMap<>();
    private Long engineStartedAtMs;

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
