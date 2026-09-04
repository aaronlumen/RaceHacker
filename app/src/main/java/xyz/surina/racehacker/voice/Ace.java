package xyz.surina.racehacker.voice;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Ace — the app's voice copilot persona. Speaks status/replies via
 * {@link TextToSpeech} and listens for spoken commands via
 * {@link SpeechRecognizer}. Narration and command interpretation are
 * delegated to {@link NarrationEngine} / {@link CommandHandler} so the actual
 * "brain" (rule-based today, an on-device LLM later) can be swapped without
 * touching this class or its callers.
 *
 * Ace never starts listening on its own — {@link #init()} only sets up
 * speech output. The user has to explicitly ask to talk (e.g. long-press the
 * mic button, which calls {@link #startListening()}).
 *
 * Button/navigation commands are resolved first against an {@link ActionRegistry}
 * (so Ace can do anything a button on screen can do); anything else falls
 * through to {@link CommandHandler}.
 *
 * Fully on-device: Android's built-in TTS/SpeechRecognizer, no network calls,
 * no new accounts. Caller must hold RECORD_AUDIO before calling
 * {@link #startListening()} — check {@link #hasMicPermission()} first.
 *
 * Lifecycle: call {@link #init()} once the owning Activity's view is ready,
 * and {@link #shutdown()} from onDestroy to release the TTS engine and
 * recognizer. Ace is meant to be owned once by MainActivity (not
 * re-created per fragment) so it persists across tab switches.
 */
public class Ace {

    private static final String TAG = "Ace";
    private static final String UTTERANCE_ID = "ace";

    public interface Listener {
        /** Final recognized text for what was said. */
        void onSpeechRecognized(String text);
        /** A recognition error (no speech, no match, missing permission, ...). */
        void onSpeechError(String message);
        /** TTS started/stopped speaking — handy for e.g. animating the mic button. */
        default void onSpeakingStateChanged(boolean speaking) {}
        /** TTS engine finished initializing and {@link #speak} can now be used. */
        default void onReady() {}
    }

    private final Context appContext;
    private final NarrationEngine narrationEngine;
    private final CommandHandler commandHandler;
    private final ActionRegistry actionRegistry;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer speechRecognizer;
    private Listener listener;
    private VocabularyLevel vocabularyLevel;
    private ActionRegistry.Entry pendingConfirmation;

    // Proactive alerts (e.g. "your engine's running a little warm") — see
    // checkForProactiveAlert(). Debounced so a fluctuating sensor (RPM updates
    // ~every 500ms) can't turn into Ace repeating itself every tick.
    private boolean proactiveAlertsEnabled = true;
    private String lastAlertMessage;
    private long lastAlertTimeMs;
    private static final long ALERT_MIN_REPEAT_INTERVAL_MS = 20_000;

    public Ace(Context context, ActionRegistry actionRegistry) {
        this(context, new RuleBasedNarrationEngine(), new RuleBasedCommandHandler(), actionRegistry);
    }

    private boolean muted;

    public Ace(Context context, NarrationEngine narrationEngine, CommandHandler commandHandler, ActionRegistry actionRegistry) {
        this.appContext = context.getApplicationContext();
        this.narrationEngine = narrationEngine;
        this.commandHandler = commandHandler;
        this.actionRegistry = actionRegistry;
        this.vocabularyLevel = VocabularyPrefs.getLevel(appContext);
        this.muted = AceSpeechPrefs.isMuted(appContext);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public VocabularyLevel getVocabularyLevel() {
        return vocabularyLevel;
    }

    public void setVocabularyLevel(VocabularyLevel level) {
        this.vocabularyLevel = level;
        VocabularyPrefs.setLevel(appContext, level);
    }

    public boolean isMuted() {
        return muted;
    }

    /** When muted, speak() is a no-op (but speakForTest() still works, to preview while deciding). */
    public void setMuted(boolean muted) {
        this.muted = muted;
        AceSpeechPrefs.setMuted(appContext, muted);
    }

    public float getPitch() {
        return AceSpeechPrefs.getPitch(appContext);
    }

    public void setPitch(float pitch) {
        AceSpeechPrefs.setPitch(appContext, pitch);
        if (tts != null) tts.setPitch(pitch);
    }

    /** Initializes the TTS engine. Safe to call more than once. Does not touch the microphone. */
    public void init() {
        if (tts != null) return;
        tts = new TextToSpeech(appContext, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(1.0f);
                tts.setPitch(AceSpeechPrefs.getPitch(appContext));
                selectBestOnDeviceVoice();
                if (listener != null) listener.onReady();
            } else {
                Log.w(TAG, "TextToSpeech init failed, status=" + status);
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                if (listener != null) listener.onSpeakingStateChanged(true);
            }
            @Override public void onDone(String utteranceId) {
                if (listener != null) listener.onSpeakingStateChanged(false);
            }
            @Override public void onError(String utteranceId) {
                if (listener != null) listener.onSpeakingStateChanged(false);
            }
        });
    }

    /**
     * Picks the highest-quality available voice for en-US that doesn't
     * require a network connection, so speech sounds less robotic than the
     * TTS engine's default without breaking the "fully on-device, no network
     * calls" guarantee. Falls back silently to the default voice if
     * enumeration fails or nothing better is found (e.g. older devices with
     * only one installed voice).
     */
    private void selectBestOnDeviceVoice() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice best = null;
            for (Voice v : voices) {
                if (v.getLocale() == null || !"eng".equals(v.getLocale().getISO3Language())) continue;
                if (v.isNetworkConnectionRequired()) continue;
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                if (best == null || v.getQuality() > best.getQuality()) {
                    best = v;
                }
            }
            if (best != null) {
                tts.setVoice(best);
                Log.i(TAG, "Selected voice: " + best.getName() + " (quality=" + best.getQuality() + ")");
            }
        } catch (Exception e) {
            Log.w(TAG, "Voice selection failed, using engine default", e);
        }
    }

    /**
     * Overrides the TTS speech rate (1.0 = normal). Meant for callers with a
     * specific need (e.g. LoginActivity speeding up just the launch greeting)
     * — the shared Ace instance used for regular in-app speech should stay at
     * the default rate set in {@link #init()}.
     */
    public void setSpeechRate(float rate) {
        if (tts != null) tts.setSpeechRate(rate);
    }

    public boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Speaks the given text, interrupting anything currently being spoken. No-op while muted. */
    public void speak(String text) {
        if (muted) return;
        speakForTest(text);
    }

    /**
     * Bypasses mute — for a "hear a sample" preview button, so you can check
     * how Ace sounds even while otherwise muted.
     */
    public void speakForTest(String text) {
        if (tts == null || !ttsReady || text == null || text.isEmpty()) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), UTTERANCE_ID);
    }

    /** Speaks a narration of the given live gauge readings, at the current vocabulary level. */
    public void speakStatus(List<GaugeData> gauges) {
        speak(narrationEngine.narrate(gauges, vocabularyLevel));
    }

    public void setProactiveAlertsEnabled(boolean enabled) {
        this.proactiveAlertsEnabled = enabled;
    }

    public boolean isProactiveAlertsEnabled() {
        return proactiveAlertsEnabled;
    }

    /**
     * Call on every live gauge update (not just on demand). Speaks up on its
     * own only when there's something worth mentioning — never on every tick:
     * a message identical to the last one spoken is suppressed until
     * {@link #ALERT_MIN_REPEAT_INTERVAL_MS} has passed, while a genuinely new
     * or worsened condition (a different message) always speaks immediately.
     * Silent whenever nothing is wrong.
     */
    public void checkForProactiveAlert(List<GaugeData> gauges) {
        if (!proactiveAlertsEnabled) return;

        String alert = narrationEngine.checkForAlert(gauges, vocabularyLevel);
        if (alert == null) {
            lastAlertMessage = null; // condition cleared — allow re-announcing if it comes back
            return;
        }

        long now = System.currentTimeMillis();
        boolean sameAsLast = alert.equals(lastAlertMessage);
        boolean cooldownElapsed = (now - lastAlertTimeMs) >= ALERT_MIN_REPEAT_INTERVAL_MS;
        if (sameAsLast && !cooldownElapsed) return;

        speak(alert);
        lastAlertMessage = alert;
        lastAlertTimeMs = now;
    }

    /**
     * Starts listening for one spoken command. Only ever called explicitly by
     * the user (e.g. long-pressing the mic button) — Ace never listens on its
     * own. Requires RECORD_AUDIO to already be granted; on missing permission
     * or an unavailable recognizer this reports via
     * {@link Listener#onSpeechError}, it does not throw.
     */
    public void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            if (listener != null) listener.onSpeechError("Speech recognition isn't available on this device.");
            return;
        }
        if (!hasMicPermission()) {
            if (listener != null) listener.onSpeechError("Microphone permission is required.");
            return;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
        }
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onResults(Bundle results) {
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = (matches != null && !matches.isEmpty()) ? matches.get(0) : "";
                if (listener != null) listener.onSpeechRecognized(text);
            }
            @Override public void onError(int error) {
                if (listener != null) listener.onSpeechError(describeError(error));
            }
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        speechRecognizer.startListening(intent);
    }

    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    /**
     * Routes recognized speech: first to a pending "are you sure?" confirmation
     * if one is outstanding, then to the {@link ActionRegistry} (anything a
     * button on screen can do — navigation, scans, flashing, tuning presets,
     * ...), then to {@link CommandHandler} for everything else. Always speaks
     * a reply.
     */
    public void handleCommand(String spokenText, List<GaugeData> currentGauges) {
        String textLower = spokenText == null ? "" : spokenText.toLowerCase(Locale.US).trim();
        Log.i(TAG, "Recognized: \"" + spokenText + "\"");

        if (pendingConfirmation != null) {
            ActionRegistry.Entry toRun = pendingConfirmation;
            pendingConfirmation = null;
            if (isAffirmative(textLower)) {
                Log.i(TAG, "-> confirmed pending action: " + toRun.label);
                speak(toRun.label);
                toRun.action.run();
            } else {
                Log.i(TAG, "-> pending action cancelled");
                speak("Okay, cancelled.");
            }
            return;
        }

        if (isCapabilityQuestion(textLower)) {
            Log.i(TAG, "-> matched capability question");
            speak(describeCapabilities());
            return;
        }

        if (actionRegistry != null) {
            String prefixReply = actionRegistry.tryPrefixAction(textLower);
            if (prefixReply != null) {
                Log.i(TAG, "-> matched prefix action: " + prefixReply);
                speak(prefixReply);
                return;
            }

            ActionRegistry.Entry matched = actionRegistry.find(textLower);
            if (matched != null) {
                Log.i(TAG, "-> matched ActionRegistry entry: " + matched.label);
                if (matched.confirm) {
                    pendingConfirmation = matched;
                    speak("Are you sure? Say yes to confirm.");
                } else {
                    speak(matched.label);
                    matched.action.run();
                }
                return;
            }
        }

        Log.i(TAG, "-> no match, falling through to CommandHandler");
        String reply = commandHandler.handle(spokenText, currentGauges, narrationEngine, vocabularyLevel);
        speak(reply);
    }

    private boolean isCapabilityQuestion(String textLower) {
        // "commands" / "help" are the primary keywords for this; "what ..." only
        // triggers it for the specific phrasings below, not just any question
        // starting with "what" — a "what" question Ace has a real answer for
        // (e.g. "what's my RPM") should reach that answer, not this listing.
        return textLower.contains("commands") || textLower.contains("help")
                || textLower.contains("what screens") || textLower.contains("what can you do")
                || textLower.contains("what can i say") || textLower.contains("what can i ask")
                // Unspecific nav requests ("open a screen for me") — instead of a flat
                // "I don't know how to do that", point at the actual screen names.
                || textLower.contains("open a screen") || textLower.contains("switch screen")
                || textLower.contains("change screen") || textLower.contains("which screen");
    }

    /**
     * Lists navigable screens and, if any, the current screen's own commands —
     * built directly from {@link ActionRegistry}'s registered phrases, so it
     * can't drift out of sync with what's actually available.
     */
    private String describeCapabilities() {
        if (actionRegistry == null) {
            return "I can tell you how your gauges are looking.";
        }
        StringBuilder sb = new StringBuilder("You can say things like: ");
        sb.append(String.join(", ", actionRegistry.globalPhraseSamples()));
        sb.append(" to switch screens");

        List<String> screenPhrases = actionRegistry.screenPhraseSamples();
        if (!screenPhrases.isEmpty()) {
            sb.append(". On this screen, you can also say: ");
            sb.append(String.join(", ", screenPhrases));
        }
        sb.append(". Or ask how your gauges are looking.");
        return sb.toString();
    }

    private boolean isAffirmative(String textLower) {
        return textLower.contains("yes") || textLower.contains("yeah") || textLower.contains("confirm")
                || textLower.contains("do it") || textLower.contains("go ahead");
    }

    private String describeError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "I didn't catch that.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "I didn't hear anything.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network error during speech recognition.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Microphone permission is required.";
            default:
                return "Speech recognition error.";
        }
    }

    /** Releases the TTS engine and SpeechRecognizer. Call from onDestroy. */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }
}
