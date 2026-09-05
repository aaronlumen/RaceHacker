package xyz.surina.racehacker.models;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists user overrides of {@link GaugeData}'s built-in warning/critical
 * thresholds. FEATURE_IDEAS.md: "User-configurable alarm thresholds —
 * GaugeData.setDefaultRanges() is hardcoded; Torque exposes warning/critical
 * thresholds as user-editable." Also flagged in SENSOR_DIAGNOSTICS.md as
 * pairing with the vehicle-aware-thresholds gap — this doesn't solve that
 * (overrides are global, not per-VehicleProfile), but it does let someone
 * fix an individual threshold that's wrong for their specific car.
 *
 * One shared preferences file, two float entries per {@link GaugeData.GaugeType}
 * that's actually been overridden — a gauge with no stored override just keeps
 * using whatever {@link GaugeData#setDefaultRanges} already set.
 */
public class GaugeThresholdPrefs {
    private static final String PREFS_NAME = "gauge_threshold_prefs";

    private GaugeThresholdPrefs() {}

    public static boolean hasOverride(Context context, GaugeData.GaugeType type) {
        return prefs(context).contains(warningKey(type));
    }

    /** Applies any stored override for this gauge's type onto it. No-op if none is stored. */
    public static void applyOverride(Context context, GaugeData gauge) {
        SharedPreferences p = prefs(context);
        String wKey = warningKey(gauge.getType());
        String cKey = criticalKey(gauge.getType());
        if (p.contains(wKey)) gauge.setWarningThreshold(p.getFloat(wKey, gauge.getWarningThreshold()));
        if (p.contains(cKey)) gauge.setCriticalThreshold(p.getFloat(cKey, gauge.getCriticalThreshold()));
    }

    public static void setOverride(Context context, GaugeData.GaugeType type, float warning, float critical) {
        prefs(context).edit()
                .putFloat(warningKey(type), warning)
                .putFloat(criticalKey(type), critical)
                .apply();
    }

    public static void clearOverride(Context context, GaugeData.GaugeType type) {
        prefs(context).edit()
                .remove(warningKey(type))
                .remove(criticalKey(type))
                .apply();
    }

    private static String warningKey(GaugeData.GaugeType type) { return type.name() + "_warning"; }
    private static String criticalKey(GaugeData.GaugeType type) { return type.name() + "_critical"; }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
