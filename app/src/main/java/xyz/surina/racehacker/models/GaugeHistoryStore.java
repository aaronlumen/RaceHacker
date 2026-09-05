package xyz.surina.racehacker.models;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rolling in-memory history of gauge readings, for the History screen's chart
 * (FEATURE_IDEAS.md: "Historical graphing — MPAndroidChart is already a
 * dependency ... but nothing plots sensor history over time yet, just
 * current value"). In-memory only, cleared on app restart — this is for a
 * quick "what did this look like the last few minutes" chart, not a
 * replacement for {@link xyz.surina.racehacker.utils.DataLogger}'s CSV
 * logging, which is the durable record.
 *
 * Capped by both a time window and a per-gauge sample count so a long
 * connected session doesn't grow this unbounded.
 */
public class GaugeHistoryStore {
    private static final long WINDOW_MS = 5 * 60 * 1000; // last 5 minutes
    private static final int MAX_SAMPLES_PER_GAUGE = 300; // generous cap even at a fast poll rate

    public static class Sample {
        public final long timeMs;
        public final float value;
        public Sample(long timeMs, float value) {
            this.timeMs = timeMs;
            this.value = value;
        }
    }

    private final Map<GaugeData.GaugeType, Deque<Sample>> history = new EnumMap<>(GaugeData.GaugeType.class);

    /** No-op for a NaN value ("no data") — nothing worth plotting. */
    public void record(GaugeData.GaugeType type, float value, long now) {
        if (Float.isNaN(value)) return;
        Deque<Sample> samples = history.computeIfAbsent(type, t -> new ArrayDeque<>());
        samples.addLast(new Sample(now, value));
        while (samples.size() > MAX_SAMPLES_PER_GAUGE) samples.removeFirst();
        while (!samples.isEmpty() && now - samples.peekFirst().timeMs > WINDOW_MS) samples.removeFirst();
    }

    /** Returns a snapshot list, oldest first. Empty (not null) if nothing recorded yet. */
    public List<Sample> get(GaugeData.GaugeType type) {
        Deque<Sample> samples = history.get(type);
        return samples == null ? Collections.emptyList() : new ArrayList<>(samples);
    }
}
