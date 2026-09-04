package xyz.surina.racehacker.voice;

import java.util.ArrayList;
import java.util.List;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Threshold-based narration: turns each gauge's warning/critical state
 * (computed by {@link GaugeData#isWarning()} / {@link GaugeData#isCritical()})
 * into a short, human sentence. No AI involved — this is the "always works,
 * zero dependencies" narrator the app ships with today, while on-device LLM
 * narration is still being integrated (see the llama.cpp-on-Android work).
 *
 * Two vocabulary levels (see {@link VocabularyLevel}): BASIC is plain,
 * jargon-free language aimed at someone with no car background; ENTHUSIAST
 * uses real terms and numbers for people who already know their car.
 *
 * Only gauges that are meaningfully safety-relevant when out of range are
 * narrated (coolant/oil temp, oil pressure, battery, fuel, RPM, AFR, boost).
 * Gauges like throttle position or timing are intentionally left out — being
 * "critical" per their default thresholds (e.g. throttle at 100%) doesn't
 * mean something is wrong.
 */
public class RuleBasedNarrationEngine implements NarrationEngine {

    @Override
    public String narrate(List<GaugeData> gauges, VocabularyLevel level) {
        List<String> critical = new ArrayList<>();
        List<String> warning = new ArrayList<>();

        for (GaugeData g : gauges) {
            if (!g.hasData()) continue;
            String msg = messageFor(g, level);
            if (msg == null) continue;
            if (g.isCritical()) {
                critical.add(msg);
            } else if (g.isWarning()) {
                warning.add(msg);
            }
        }

        if (!critical.isEmpty()) return critical.get(0);
        if (!warning.isEmpty()) return warning.get(0);
        return allClearMessage(gauges, level);
    }

    private String messageFor(GaugeData g, VocabularyLevel level) {
        boolean critical = g.isCritical();
        boolean basic = level == VocabularyLevel.BASIC;
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
            case BATTERY_VOLTAGE:
                if (critical) {
                    return basic
                            ? "Your car's battery power is really low — something may be wrong with your charging system."
                            : "Battery voltage's very low — check your charging system.";
                }
                return basic
                        ? "Your car's battery power is a bit low."
                        : "Battery voltage's a bit low.";
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
                return null; // not narrated in v1
        }
    }

    private String allClearMessage(List<GaugeData> gauges, VocabularyLevel level) {
        boolean basic = level == VocabularyLevel.BASIC;
        for (GaugeData g : gauges) {
            if (g.getType() == GaugeData.GaugeType.RPM && g.hasData()) {
                return basic
                        ? "Everything's looking good out there."
                        : "Everything's looking good — RPM steady at " + g.getFormattedValue() + ".";
            }
        }
        return "Everything's looking good.";
    }
}
