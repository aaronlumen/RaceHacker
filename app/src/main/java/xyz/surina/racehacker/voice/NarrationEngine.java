package xyz.surina.racehacker.voice;

import java.util.List;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Produces a spoken-language status summary from live gauge readings.
 *
 * v1 ({@link RuleBasedNarrationEngine}) is threshold-driven and has zero
 * dependencies — it always works, with or without a model. This interface is
 * the seam where an on-device LLM (see the llama.cpp-on-Android work) can
 * later be swapped in as a richer narrator without touching {@link Ace}
 * or any UI code.
 */
public interface NarrationEngine {
    /** @return a short, spoken-language summary of the given gauges. Never null. */
    String narrate(List<GaugeData> gauges);
}
