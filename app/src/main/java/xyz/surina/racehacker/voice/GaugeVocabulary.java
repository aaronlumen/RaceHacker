package xyz.surina.racehacker.voice;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Plain-language names and value readouts for gauges, so Ace can talk about
 * sensor data without requiring the listener to already know what "AFR" or
 * "PSI" means. Used by both {@link RuleBasedNarrationEngine} (warning/critical
 * sentences) and {@link RuleBasedCommandHandler} (reading back a value).
 */
public class GaugeVocabulary {

    private GaugeVocabulary() {}

    /** A simple, jargon-free name for the gauge, e.g. AFR -> "fuel mixture". */
    public static String simpleName(GaugeData.GaugeType type) {
        switch (type) {
            case RPM:               return "engine speed";
            case SPEED:              return "speed";
            case BOOST:              return "turbo boost";
            case AFR:                return "fuel mixture";
            case OIL_TEMP:           return "oil temperature";
            case COOLANT_TEMP:       return "engine temperature";
            case OIL_PRESSURE:       return "oil pressure";
            // NOTE: the dashboard currently stores the fuel-level percentage under a
            // FUEL_PRESSURE-typed gauge — see RuleBasedNarrationEngine's note.
            case FUEL_PRESSURE:      return "fuel level";
            case INTAKE_TEMP:        return "intake air temperature";
            case EXHAUST_TEMP:       return "exhaust temperature";
            case TIMING:             return "ignition timing";
            case THROTTLE_POSITION:  return "gas pedal position";
            case BATTERY_VOLTAGE:    return "battery power";
            case FUEL_LEVEL:         return "fuel level";
            default:                 return "that gauge";
        }
    }

    /** Reads back a gauge's current value, phrased for the given vocabulary level. */
    public static String describe(GaugeData g, VocabularyLevel level) {
        String name = level == VocabularyLevel.ENTHUSIAST ? g.getName() : capitalize(simpleName(g.getType()));
        if (!g.hasData()) {
            return name + " isn't reading right now — check your connection.";
        }
        if (level == VocabularyLevel.ENTHUSIAST) {
            return name + " is " + g.getFormattedValue() + " " + g.getUnit() + ".";
        }
        return "Your " + simpleName(g.getType()) + " is at " + g.getFormattedValue() + ".";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
