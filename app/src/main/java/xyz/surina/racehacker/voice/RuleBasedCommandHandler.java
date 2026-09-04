package xyz.surina.racehacker.voice;

import java.util.List;
import java.util.Locale;

import xyz.surina.racehacker.models.GaugeData;

/**
 * v1 command handler: keyword matching against a small fixed set of phrases
 * for reading back gauge values. Deliberately honest about what it can't do
 * yet — e.g. vehicle actuation (locks, trunk release) isn't implemented
 * anywhere in the app, so it says so rather than pretending to comply.
 */
public class RuleBasedCommandHandler implements CommandHandler {

    @Override
    public String handle(String spokenText, List<GaugeData> currentGauges, NarrationEngine narrationEngine) {
        String text = spokenText == null ? "" : spokenText.toLowerCase(Locale.US).trim();

        if (text.isEmpty()) {
            return "I didn't catch that.";
        }
        if (containsAny(text, "who are you", "what's your name", "your name")) {
            return "I'm Ace — I keep an eye on your gauges and let you know if something needs attention.";
        }
        if (containsAny(text, "status", "how's it looking", "how am i doing", "how's it going", "how are we doing")) {
            return narrationEngine.narrate(currentGauges);
        }
        if (containsAny(text, "rpm")) {
            return describeGauge(currentGauges, GaugeData.GaugeType.RPM);
        }
        if (containsAny(text, "speed")) {
            return describeGauge(currentGauges, GaugeData.GaugeType.SPEED);
        }
        if (containsAny(text, "coolant", "temperature", "temp")) {
            return describeGauge(currentGauges, GaugeData.GaugeType.COOLANT_TEMP);
        }
        if (containsAny(text, "fuel", "gas")) {
            // See RuleBasedNarrationEngine: fuel level is currently stored under a
            // FUEL_PRESSURE-typed gauge in the dashboard, not FUEL_LEVEL.
            String reply = describeGauge(currentGauges, GaugeData.GaugeType.FUEL_LEVEL);
            return reply.equals("I don't have that gauge.")
                    ? describeGauge(currentGauges, GaugeData.GaugeType.FUEL_PRESSURE)
                    : reply;
        }
        if (containsAny(text, "battery", "voltage")) {
            return describeGauge(currentGauges, GaugeData.GaugeType.BATTERY_VOLTAGE);
        }
        if (containsAny(text, "boost")) {
            return describeGauge(currentGauges, GaugeData.GaugeType.BOOST);
        }

        // Honest fallback rather than silently ignoring or pretending to comply
        // with things the app can't actually do (e.g. "pop the trunk").
        return "I don't know how to do that yet — right now I can tell you how your gauges are looking.";
    }

    private boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }

    private String describeGauge(List<GaugeData> gauges, GaugeData.GaugeType type) {
        for (GaugeData g : gauges) {
            if (g.getType() == type) {
                if (!g.hasData()) {
                    return g.getName() + " isn't reading right now — check your connection.";
                }
                return g.getName() + " is " + g.getFormattedValue() + " " + g.getUnit() + ".";
            }
        }
        return "I don't have that gauge.";
    }
}
