package xyz.surina.racehacker.voice;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the user's chosen {@link VocabularyLevel} for Ace across app restarts. Default: BASIC. */
public class VocabularyPrefs {
    private static final String PREFS_NAME = "ace_prefs";
    private static final String KEY_LEVEL = "vocabulary_level";

    private VocabularyPrefs() {}

    public static VocabularyLevel getLevel(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_LEVEL, VocabularyLevel.BASIC.name());
        try {
            return VocabularyLevel.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return VocabularyLevel.BASIC;
        }
    }

    public static void setLevel(Context context, VocabularyLevel level) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LEVEL, level.name())
                .apply();
    }
}
