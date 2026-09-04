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
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Ace — the app's voice copilot persona. Speaks status/replies via
 * {@link TextToSpeech} and listens for spoken commands via
 * {@link SpeechRecognizer}. Narration and command interpretation are
 * delegated to {@link NarrationEngine} / {@link CommandHandler} so the actual
 * "brain" (rule-based today, an on-device LLM later) can be swapped without
 * touching this class or its callers.
 *
 * Fully on-device: Android's built-in TTS/SpeechRecognizer, no network calls,
 * no new accounts. Caller must hold RECORD_AUDIO before calling
 * {@link #startListening()} — check {@link #hasMicPermission()} first.
 *
 * Lifecycle: call {@link #init()} once the owning Fragment/Activity's view is
 * ready, and {@link #shutdown()} from onDestroyView/onDestroy to release the
 * TTS engine and recognizer.
 */
public class Ace {

    private static final String TAG = "Ace";
    private static final String UTTERANCE_ID = "voice_copilot";

    public interface Listener {
        /** Final recognized text for what was said. */
        void onSpeechRecognized(String text);
        /** A recognition error (no speech, no match, missing permission, ...). */
        void onSpeechError(String message);
        /** TTS started/stopped speaking — handy for e.g. animating the mic button. */
        default void onSpeakingStateChanged(boolean speaking) {}
    }

    private final Context appContext;
    private final NarrationEngine narrationEngine;
    private final CommandHandler commandHandler;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer speechRecognizer;
    private Listener listener;

    public Ace(Context context) {
        this(context, new RuleBasedNarrationEngine(), new RuleBasedCommandHandler());
    }

    public Ace(Context context, NarrationEngine narrationEngine, CommandHandler commandHandler) {
        this.appContext = context.getApplicationContext();
        this.narrationEngine = narrationEngine;
        this.commandHandler = commandHandler;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Initializes the TTS engine. Safe to call more than once. */
    public void init() {
        if (tts != null) return;
        tts = new TextToSpeech(appContext, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(1.0f);
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

    public boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Speaks the given text, interrupting anything currently being spoken. */
    public void speak(String text) {
        if (tts == null || !ttsReady || text == null || text.isEmpty()) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), UTTERANCE_ID);
    }

    /** Speaks a narration of the given live gauge readings. */
    public void speakStatus(List<GaugeData> gauges) {
        speak(narrationEngine.narrate(gauges));
    }

    /**
     * Starts listening for one spoken command. Requires RECORD_AUDIO to
     * already be granted; on missing permission or an unavailable recognizer
     * this reports via {@link Listener#onSpeechError}, it does not throw.
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

    /** Routes recognized speech to the command handler and speaks the reply. */
    public void handleCommand(String spokenText, List<GaugeData> currentGauges) {
        String reply = commandHandler.handle(spokenText, currentGauges, narrationEngine);
        speak(reply);
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

    /** Releases the TTS engine and SpeechRecognizer. Call from onDestroyView/onDestroy. */
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
