package xyz.surina.racehacker.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lets any screen register the voice phrases for buttons/actions it exposes,
 * so Ace can trigger anything a physical button can — navigation, scans,
 * reading/clearing codes, tuning presets, ECU backup/flash, etc.
 *
 * "Global" entries (app navigation between tabs) are registered once by
 * MainActivity and are always available. "Screen" entries belong to whichever
 * fragment is currently visible — each fragment registers its own via
 * {@link #setScreenActions(List)} in onResume and clears them in onPause, so
 * Ace never runs an action against a screen that isn't showing.
 */
public class ActionRegistry {

    public interface VoiceAction {
        void run();
    }

    public static class Entry {
        /** Spoken back right before running the action, e.g. "Opening Diagnostics." */
        public final String label;
        public final VoiceAction action;
        public final String[] phrases;
        /** If true, Ace asks "Are you sure?" and waits for a spoken yes before running this. */
        public final boolean confirm;

        Entry(String label, VoiceAction action, boolean confirm, String... phrases) {
            this.label = label;
            this.action = action;
            this.confirm = confirm;
            this.phrases = phrases;
        }
    }

    private final List<Entry> global = new ArrayList<>();
    private final List<Entry> screen = new ArrayList<>();

    public void registerGlobal(String label, VoiceAction action, String... phrases) {
        global.add(new Entry(label, action, false, phrases));
    }

    /** Replaces the current screen's voice actions (call from onResume). */
    public void setScreenActions(List<Entry> actions) {
        screen.clear();
        if (actions != null) screen.addAll(actions);
    }

    /** Call from onPause so a backgrounded screen's actions can't fire. */
    public void clearScreenActions() {
        screen.clear();
    }

    public static Entry action(String label, VoiceAction action, String... phrases) {
        return new Entry(label, action, false, phrases);
    }

    /** For consequential actions (ECU flash, applying tuning) — requires a spoken confirmation. */
    public static Entry confirmedAction(String label, VoiceAction action, String... phrases) {
        return new Entry(label, action, true, phrases);
    }

    /** Screen-specific actions take priority over global navigation for the same phrase. */
    public Entry find(String spokenTextLower) {
        Entry match = findIn(screen, spokenTextLower);
        if (match != null) return match;
        return findIn(global, spokenTextLower);
    }

    private Entry findIn(List<Entry> entries, String textLower) {
        for (Entry e : entries) {
            for (String phrase : e.phrases) {
                if (textLower.contains(phrase.toLowerCase(Locale.US))) {
                    return e;
                }
            }
        }
        return null;
    }
}
