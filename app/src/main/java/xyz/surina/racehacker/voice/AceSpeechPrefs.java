package xyz.surina.racehacker.voice;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists Ace's mute state and TTS pitch across app restarts. Shares the
 * same SharedPreferences file as {@link VocabularyPrefs} — one small "ace_prefs"
 * store rather than a file per setting.
 */
public class AceSpeechPrefs {
    private static final String PREFS_NAME = "ace_prefs";
    private static final String KEY_MUTED = "muted";
    private static final String KEY_PITCH = "pitch";
    private static final float DEFAULT_PITCH = 1.0f;

    private AceSpeechPrefs() {}

    public static boolean isMuted(Context context) {
        return prefs(context).getBoolean(KEY_MUTED, false);
    }

    public static void setMuted(Context context, boolean muted) {
        prefs(context).edit().putBoolean(KEY_MUTED, muted).apply();
    }

    public static float getPitch(Context context) {
        return prefs(context).getFloat(KEY_PITCH, DEFAULT_PITCH);
    }

    public static void setPitch(Context context, float pitch) {
        prefs(context).edit().putFloat(KEY_PITCH, pitch).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
