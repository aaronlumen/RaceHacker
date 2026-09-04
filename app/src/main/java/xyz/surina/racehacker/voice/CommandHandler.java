package xyz.surina.racehacker.voice;

import java.util.List;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Turns recognized speech text into a spoken reply.
 *
 * v1 ({@link RuleBasedCommandHandler}) only understands a small fixed set of
 * phrases about reading gauges. This interface is the seam where an on-device
 * LLM-backed intent parser will slot in later (see the llama.cpp-on-Android
 * work) — including eventually recognizing commands the app can't act on yet
 * (e.g. vehicle actuation) versus ones it can.
 */
public interface CommandHandler {
    /** @return the spoken reply for the given recognized text. Never null. */
    String handle(String spokenText, List<GaugeData> currentGauges, NarrationEngine narrationEngine);
}
