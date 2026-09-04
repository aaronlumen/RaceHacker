package xyz.surina.racehacker.voice;

import java.util.List;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Produces spoken-language summaries from live gauge readings.
 *
 * v1 ({@link RuleBasedNarrationEngine}) is threshold-driven and has zero
 * dependencies — it always works, with or without a model. This interface is
 * the seam where an on-device LLM (see the llama.cpp-on-Android work) can
 * later be swapped in as a richer narrator without touching {@link Ace}
 * or any UI code.
 */
public interface NarrationEngine {
    /**
     * @param gauges live gauge readings to narrate
     * @param level  how technical the narration should be — see {@link VocabularyLevel}
     * @return a summary. Always returns something — including an "all clear"
     *         message when nothing's wrong. Use this for on-demand status
     *         requests (e.g. tapping the FAB, asking "how's it looking").
     */
    String narrate(List<GaugeData> gauges, VocabularyLevel level);

    /**
     * Like {@link #narrate}, but returns null when nothing is worth mentioning
     * (instead of an "all clear" message). Use this for proactive/unprompted
     * narration — e.g. checked on every live data update — so Ace only speaks
     * up when there's actually something to say, not on every tick.
     */
    String checkForAlert(List<GaugeData> gauges, VocabularyLevel level);
}
