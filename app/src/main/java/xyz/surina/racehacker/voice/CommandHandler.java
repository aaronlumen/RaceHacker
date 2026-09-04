package xyz.surina.racehacker.voice;

import java.util.List;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Turns recognized speech text into a spoken reply, for anything that isn't a
 * registered button/navigation action (see {@link ActionRegistry}, checked by
 * {@link Ace} before this is ever consulted).
 *
 * v1 ({@link RuleBasedCommandHandler}) only understands a small fixed set of
 * phrases about reading gauges. This interface is the seam where an on-device
 * LLM-backed intent parser will slot in later (see the llama.cpp-on-Android
 * work).
 */
public interface CommandHandler {
    /**
     * @param spokenText     recognized speech text
     * @param currentGauges  live gauge readings
     * @param narrationEngine used to answer "how's it looking" style questions
     * @param level          how technical the reply should be — see {@link VocabularyLevel}
     * @return the spoken reply. Never null.
     */
    String handle(String spokenText, List<GaugeData> currentGauges, NarrationEngine narrationEngine, VocabularyLevel level);
}
