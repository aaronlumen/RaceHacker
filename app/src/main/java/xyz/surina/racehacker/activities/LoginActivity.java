package xyz.surina.racehacker.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Arrays;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.auth.AuthManager;
import xyz.surina.racehacker.voice.Ace;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 100;

    private static final String LAUNCH_PREFS = "race_hacking_launch_prefs";
    private static final String KEY_LAUNCH_COUNT = "launch_count";
    private static final String DEDICATION = " This one's dedicated to David and Andrew.";

    // The very first launch ever gets the full, original intro (the complete
    // feature rundown + donation pitch). Every launch after that rotates
    // through the shorter WELCOME_GREETINGS below instead.
    private static final String FIRST_LAUNCH_GREETING =
            "Welcome to Race Hacking — your all-in-one toolkit for taking on the competition. "
            + "Clearing engine codes, O-B-D-2 diagnostics, E.C.U. programming, stage one through "
            + "stage four tuning — we've got you covered. And hey, the other guys charge an arm "
            + "and a leg for this. If we're saving you some money, toss a few bucks our way — "
            + "it keeps this thing running faster and longer than they ever will.";

    // Spoken on every launch after the first, before any sign-in/skip navigation
    // (so it plays on the common auto-skip path too). Rotates through 5 variants
    // by launch count so it isn't the same line every time.
    private static final String[] WELCOME_GREETINGS = {
            // 1. S3 — Race Mode
            "Welcome to S3 — Surina 3. This isn't just another OBD scanner. S3 puts advanced "
                    + "E.C.U. diagnostics, live telemetry, tuning analysis, sensor intelligence, and "
                    + "vehicle learning tools in your hands. From street cars to race builds, "
                    + "diesel torque monsters to high-performance motorcycles — S3 helps you "
                    + "understand what your machine is actually doing. Connect. Analyze. Learn. "
                    + "Tune. S3. Know your machine.",
            // 2. S3 — The Machine Learns
            "S3 online. Your vehicle has thousands of signals running through its electronic "
                    + "systems. S3 turns those signals into knowledge. Monitor sensors in real "
                    + "time. Map E.C.U. behavior. Compare commanded versus actual values. Discover "
                    + "patterns. Learn how your engine, transmission, fuel, boost, ignition, and "
                    + "emissions systems work together. Whether you're building a daily driver, "
                    + "a diesel powerhouse, or a track weapon — S3 gives you the tools to learn "
                    + "what your vehicle is telling you. Surina 3. Data in. Performance out.",
            // 3. S3 — Full Send
            "Ignition on. E.C.U. connected. Welcome to S3. Live data. Advanced diagnostics. "
                    + "Performance telemetry. E.C.U. analysis. Sensor correlation. Vehicle learning. "
                    + "From Harley baggers to superbikes. From turbo imports to German "
                    + "performance machines. From Cummins and Power Stroke diesels to full race "
                    + "builds. S3 doesn't just show you numbers. It teaches you what the numbers "
                    + "mean. Push your knowledge. Understand your machine. S3 — built for people "
                    + "who want to know what's under the hood.",
            // 4. S3 — Engineering Mode
            "Welcome to S3 Engineering Mode. Every RPM tells a story. Every pressure, "
                    + "temperature, timing value, fuel correction, throttle movement, and sensor "
                    + "response adds another piece to the puzzle. S3 gives you an advanced "
                    + "environment for exploring that data — with live monitoring, diagnostics, "
                    + "performance analysis, E.C.U. learning, and cross-system correlation. Start "
                    + "with a sensor. Follow the data. Understand the system. Then build smarter. "
                    + "S3 — Surina 3. Where vehicle data becomes knowledge.",
            // 5. S3 — Cinematic / Premium
            "This is S3. Not just a scanner. Not just a code reader. A complete vehicle "
                    + "intelligence platform. Real-time E.C.U. data. Advanced diagnostics. "
                    + "Performance monitoring. Sensor analytics. Tuning education. System "
                    + "correlation. And tools designed to help you understand virtually every "
                    + "signal your vehicle makes available. Cars. Trucks. Diesels. Motorcycles. "
                    + "Performance machines. Your vehicle is already talking. S3 gives you the "
                    + "language. Surina 3 — connect to the machine.",
    };

    // Not present in any of the 5 scripts above, so it's appended separately —
    // still fires every 5th launch, on top of whichever script plays that time.
    private static final String DONATION_PITCH =
            "And hey — the other guys charge an arm and a leg for this. If we're saving you "
            + "some money, toss a few bucks our way — it keeps this thing running faster and "
            + "longer than they ever will.";

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager facebookCallbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playWelcomeGreeting();

        // Already signed in — skip straight to the app
        firebaseAuth = FirebaseAuth.getInstance();
        if (AuthManager.getInstance(this).isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        setupGoogleSignIn();
        setupFacebookSignIn();
        setupSocialMosaicTiles();
        setupSkipButton();
    }

    // ── Ace's welcome greeting ────────────────────────────────────────────────

    /**
     * Speaks a one-time welcome when the app opens. Purely one-way — Ace never
     * listens for a reply here; the user has to explicitly ask to talk to Ace
     * later (long-press the mic button) if they want to.
     */
    // 20% faster than normal — only for the launch greeting, not Ace's regular
    // in-app speech (which stays at the default rate).
    private static final float GREETING_SPEECH_RATE = 1.2f;

    private void playWelcomeGreeting() {
        String greeting = nextWelcomeGreeting();
        Ace greeter = new Ace(this, null);
        greeter.setListener(new Ace.Listener() {
            @Override public void onSpeechRecognized(String text) {}
            @Override public void onSpeechError(String message) {}
            @Override public void onReady() {
                greeter.setSpeechRate(GREETING_SPEECH_RATE);
                greeter.speak(greeting);
            }
        });
        greeter.init();
    }

    /**
     * First launch ever gets FIRST_LAUNCH_GREETING; every launch after that
     * rotates through WELCOME_GREETINGS. Persisted across app restarts.
     */
    private String nextWelcomeGreeting() {
        SharedPreferences prefs = getSharedPreferences(LAUNCH_PREFS, MODE_PRIVATE);
        int count = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1;
        prefs.edit().putInt(KEY_LAUNCH_COUNT, count).apply();

        String greeting = (count == 1)
                ? FIRST_LAUNCH_GREETING
                : WELCOME_GREETINGS[(count - 2) % WELCOME_GREETINGS.length];

        if (count % 5 == 0) {
            greeting += " " + DONATION_PITCH;
        }
        return greeting + DEDICATION;
    }

    // ── Google ──────────────────────────────────────────────────────────────

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        Button googleButton = findViewById(R.id.btn_google_signin);
        googleButton.setOnClickListener(v ->
                startActivityForResult(googleSignInClient.getSignInIntent(), RC_GOOGLE_SIGN_IN));
    }

    // ── Facebook ────────────────────────────────────────────────────────────

    private void setupFacebookSignIn() {
        facebookCallbackManager = CallbackManager.Factory.create();

        findViewById(R.id.tile_facebook).setOnClickListener(v ->
                LoginManager.getInstance().logInWithReadPermissions(
                        this, Arrays.asList("email", "public_profile")));

        LoginManager.getInstance().registerCallback(facebookCallbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult result) {
                        AuthCredential cred = FacebookAuthProvider.getCredential(
                                result.getAccessToken().getToken());
                        firebaseAuth.signInWithCredential(cred)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        onAuthSuccess(firebaseAuth.getCurrentUser());
                                    } else {
                                        continueLocally("Couldn't reach Facebook — continuing offline");
                                    }
                                });
                    }
                    @Override public void onCancel() {}
                    @Override public void onError(FacebookException e) {
                        continueLocally("Facebook sign-in unavailable — continuing offline");
                    }
                });
    }

    // ── Other social tiles (coming soon) ────────────────────────────────────

    private void setupSocialMosaicTiles() {
        int[]    ids    = { R.id.tile_twitter, R.id.tile_snapchat,
                            R.id.tile_instagram, R.id.tile_amazon, R.id.tile_autozone };
        String[] labels = { "X / Twitter", "Snapchat", "Instagram", "Amazon", "AutoZone" };

        for (int i = 0; i < ids.length; i++) {
            final String label = labels[i];
            findViewById(ids[i]).setOnClickListener(v ->
                    Toast.makeText(this, label + " login — coming soon",
                            Toast.LENGTH_SHORT).show());
        }
    }

    // ── Skip ────────────────────────────────────────────────────────────────

    private void setupSkipButton() {
        TextView skip = findViewById(R.id.btn_skip);
        skip.setOnClickListener(v -> {
            // Continue without sign-in; profile syncing will be disabled
            AuthManager.getInstance(this).enterLocalMode();
            goToMain();
        });
    }

    // ── Activity result handling ─────────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Facebook needs the first shot
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            // The user explicitly backed out of the Google chooser — leave them on
            // the login screen so they can pick another option.
            if (resultCode == RESULT_CANCELED) {
                return;
            }
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                AuthCredential cred = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                firebaseAuth.signInWithCredential(cred)
                        .addOnCompleteListener(this, t -> {
                            if (t.isSuccessful()) {
                                onAuthSuccess(firebaseAuth.getCurrentUser());
                            } else {
                                // Firebase rejected the credential (e.g. no backing
                                // project) — fail forward into a local session.
                                continueLocally("Couldn't reach Google — continuing offline");
                            }
                        });
            } catch (ApiException e) {
                // GoogleSignInStatusCodes.SIGN_IN_CANCELLED == 12501
                if (e.getStatusCode() == 12501) {
                    return;
                }
                // Any real failure (misconfigured client, developer error, network)
                // shouldn't dead-end the user — drop straight into local mode.
                continueLocally("Google sign-in unavailable — continuing offline");
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void onAuthSuccess(FirebaseUser user) {
        AuthManager.getInstance(this).saveAuthState(user);
        goToMain();
    }

    /**
     * Fail forward: when a social sign-in can't complete, continue into the app as
     * a local/guest session instead of dead-ending on the login screen.
     */
    private void continueLocally(String reason) {
        if (reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
        }
        AuthManager.getInstance(this).enterLocalMode();
        goToMain();
    }

    private void goToMain() {
        // Land on mode selection after login
        startActivity(new Intent(this, ModeSelectActivity.class));
        finish();
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
