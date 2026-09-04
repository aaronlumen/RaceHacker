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

    /**
     * For commands that need a parameter pulled out of speech (e.g. "connect
     * to BT12" -> deviceQuery="bt12"), unlike the fixed exact-phrase
     * {@link Entry}. Returns what Ace should speak, or null to defer to
     * normal handling (e.g. the phrase matched the prefix but no device by
     * that name was found — fall through rather than go silent).
     */
    public interface PrefixAction {
        String run(String remainder);
    }

    public static class PrefixEntry {
        final String[] prefixes;
        final PrefixAction action;

        PrefixEntry(PrefixAction action, String... prefixes) {
            this.action = action;
            this.prefixes = prefixes;
        }
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
    private final List<PrefixEntry> screenPrefixActions = new ArrayList<>();

    public void registerGlobal(String label, VoiceAction action, String... phrases) {
        global.add(new Entry(label, action, false, phrases));
    }

    /** Replaces the current screen's voice actions (call from onResume). */
    public void setScreenActions(List<Entry> actions) {
        screen.clear();
        if (actions != null) screen.addAll(actions);
    }

    /** Replaces the current screen's prefix-parameterized actions (call from onResume). */
    public void setScreenPrefixActions(List<PrefixEntry> actions) {
        screenPrefixActions.clear();
        if (actions != null) screenPrefixActions.addAll(actions);
    }

    /** Call from onPause so a backgrounded screen's actions/prefix actions can't fire. */
    public void clearScreenActions() {
        screen.clear();
        screenPrefixActions.clear();
    }

    public static Entry action(String label, VoiceAction action, String... phrases) {
        return new Entry(label, action, false, phrases);
    }

    /** For consequential actions (ECU flash, applying tuning) — requires a spoken confirmation. */
    public static Entry confirmedAction(String label, VoiceAction action, String... phrases) {
        return new Entry(label, action, true, phrases);
    }

    public static PrefixEntry prefixAction(PrefixAction action, String... prefixes) {
        return new PrefixEntry(action, prefixes);
    }

    /**
     * Tries the current screen's prefix actions against the given text —
     * e.g. "connect to bt12" matching prefix "connect to " calls the action
     * with remainder "bt12". @return the spoken reply if a prefix matched and
     * the action produced one, else null (no prefix matched, or the action
     * deferred).
     */
    public String tryPrefixAction(String spokenTextLower) {
        for (PrefixEntry pe : screenPrefixActions) {
            for (String prefix : pe.prefixes) {
                if (spokenTextLower.startsWith(prefix)) {
                    String remainder = spokenTextLower.substring(prefix.length()).trim();
                    String reply = pe.action.run(remainder);
                    if (reply != null) return reply;
                }
            }
        }
        return null;
    }

    /** Screen-specific actions take priority over global navigation for the same phrase. */
    public Entry find(String spokenTextLower) {
        Entry match = findIn(screen, spokenTextLower);
        if (match != null) return match;
        return findIn(global, spokenTextLower);
    }

    /**
     * The first registered phrase for each global (navigation) action — e.g.
     * "dashboard", "diagnostics" — for a "what can I say" listing. Reflects
     * whatever's actually registered, so it can't drift out of sync with the
     * real commands.
     */
    public List<String> globalPhraseSamples() {
        return firstPhrases(global);
    }

    /** Same as {@link #globalPhraseSamples()}, for the current screen's own actions. */
    public List<String> screenPhraseSamples() {
        return firstPhrases(screen);
    }

    private List<String> firstPhrases(List<Entry> entries) {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.phrases.length > 0) out.add(e.phrases[0]);
        }
        return out;
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
