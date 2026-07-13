package xyz.surina.proracingobd.activities;

import android.content.Intent;
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

import xyz.surina.proracingobd.R;
import xyz.surina.proracingobd.auth.AuthManager;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 100;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager facebookCallbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                                        showError("Facebook sign-in failed");
                                    }
                                });
                    }
                    @Override public void onCancel() {}
                    @Override public void onError(FacebookException e) {
                        showError("Facebook: " + e.getMessage());
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
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                AuthCredential cred = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                firebaseAuth.signInWithCredential(cred)
                        .addOnCompleteListener(this, t -> {
                            if (t.isSuccessful()) {
                                onAuthSuccess(firebaseAuth.getCurrentUser());
                            } else {
                                showError("Google sign-in failed");
                            }
                        });
            } catch (ApiException e) {
                showError("Sign-in error: " + e.getStatusCode());
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void onAuthSuccess(FirebaseUser user) {
        AuthManager.getInstance(this).saveAuthState(user);
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
