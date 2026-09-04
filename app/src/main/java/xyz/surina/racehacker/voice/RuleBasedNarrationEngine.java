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
 * Only gauges that are meaningfully safety-relevant when out of range are
 * narrated (coolant/oil temp, oil pressure, battery, fuel, RPM, AFR, boost).
 * Gauges like throttle position or timing are intentionally left out — being
 * "critical" per their default thresholds (e.g. throttle at 100%) doesn't
 * mean something is wrong.
 */
public class RuleBasedNarrationEngine implements NarrationEngine {

    @Override
    public String narrate(List<GaugeData> gauges) {
        List<String> critical = new ArrayList<>();
        List<String> warning = new ArrayList<>();

        for (GaugeData g : gauges) {
            if (!g.hasData()) continue;
            String msg = messageFor(g);
            if (msg == null) continue;
            if (g.isCritical()) {
                critical.add(msg);
            } else if (g.isWarning()) {
                warning.add(msg);
            }
        }

        if (!critical.isEmpty()) return critical.get(0);
        if (!warning.isEmpty()) return warning.get(0);
        return allClearMessage(gauges);
    }

    private String messageFor(GaugeData g) {
        boolean critical = g.isCritical();
        switch (g.getType()) {
            case COOLANT_TEMP:
                return critical
                        ? "Your engine's overheating — you should pull over when it's safe."
                        : "Your engine's running a little warm, we should slow down a little.";
            case OIL_TEMP:
                return critical
                        ? "Oil temperature's critically high — ease off the throttle."
                        : "Oil temperature's climbing a bit.";
            case OIL_PRESSURE:
                return critical
                        ? "Oil pressure's dangerously low — shut it down as soon as it's safe to."
                        : "Oil pressure's a little low, keep an eye on it.";
            case BATTERY_VOLTAGE:
                return critical
                        ? "Battery voltage's very low — check your charging system."
                        : "Battery voltage's a bit low.";
            case FUEL_LEVEL:
            // NOTE: DashboardFragment currently stores the fuel-level percentage under a
            // gauge typed FUEL_PRESSURE (labeled "Fuel Press") rather than FUEL_LEVEL — see
            // its "Fuel level mapped to FUEL_PRESSURE slot for display" comment. Handling
            // both here so fuel narration actually fires against real app data.
            case FUEL_PRESSURE:
                return critical
                        ? "You're almost out of fuel."
                        : "Fuel's getting low.";
            case RPM:
                return critical
                        ? "You're bouncing off the rev limiter — ease up on the throttle."
                        : "You're revving pretty high.";
            case AFR:
                return critical
                        ? "Your air-fuel mixture's running dangerously lean."
                        : "Your air-fuel mixture's running a bit lean.";
            case BOOST:
                return critical
                        ? "Boost is at a dangerous level — back off now."
                        : "Boost is getting up there.";
            default:
                return null; // not narrated in v1
        }
    }

    private String allClearMessage(List<GaugeData> gauges) {
        for (GaugeData g : gauges) {
            if (g.getType() == GaugeData.GaugeType.RPM && g.hasData()) {
                return "Everything's looking good — RPM steady at " + g.getFormattedValue() + ".";
            }
        }
        return "Everything's looking good.";
    }
}
