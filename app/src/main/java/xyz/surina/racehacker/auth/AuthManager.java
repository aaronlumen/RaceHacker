package xyz.surina.racehacker.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Manages authentication state across the app.
 * Persists the signed-in user's basic info to SharedPreferences so we can
 * bypass the login screen on subsequent launches without waiting for Firebase.
 */
public class AuthManager {

    private static final String PREFS_NAME  = "surina_auth";
    private static final String KEY_UID     = "uid";
    private static final String KEY_EMAIL   = "email";
    private static final String KEY_NAME    = "display_name";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_LOCAL   = "local_mode";

    private static AuthManager instance;
    private final SharedPreferences prefs;

    private AuthManager(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AuthManager getInstance(Context ctx) {
        if (instance == null) instance = new AuthManager(ctx);
        return instance;
    }

    /**
     * Returns true when the user has a usable session — either a real Firebase
     * sign-in (cached UID + live Firebase user) or a local/guest session.
     */
    public boolean isLoggedIn() {
        if (prefs.getBoolean(KEY_LOCAL, false)) return true;   // local/guest session
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        return fbUser != null && prefs.contains(KEY_UID);
    }

    /** True when running as a local/guest session (no cloud profile sync). */
    public boolean isLocalMode() {
        return prefs.getBoolean(KEY_LOCAL, false);
    }

    /**
     * Establish a persistent local/guest session. Used by "Skip for now" and as
     * the fail-forward when a social sign-in can't complete (e.g. no Firebase
     * project configured). The app is fully usable offline; only cloud profile
     * sync is disabled.
     */
    public void enterLocalMode() {
        prefs.edit()
                .putBoolean(KEY_LOCAL, true)
                .putString(KEY_UID, "local")
                .putString(KEY_NAME, "Local User")
                .putString(KEY_PROVIDER, "local")
                .apply();
    }

    /** Call after a successful Firebase sign-in to cache the user's profile. */
    public void saveAuthState(FirebaseUser user) {
        if (user == null) return;
        prefs.edit()
                .putBoolean(KEY_LOCAL, false)   // a real sign-in supersedes guest mode
                .putString(KEY_UID, user.getUid())
                .putString(KEY_EMAIL, user.getEmail())
                .putString(KEY_NAME, user.getDisplayName())
                .putString(KEY_PROVIDER, user.getProviderId())
                .apply();
    }

    public void signOut(Context ctx) {
        FirebaseAuth.getInstance().signOut();
        prefs.edit().clear().apply();
    }

    public String getUid()         { return prefs.getString(KEY_UID, null); }
    public String getEmail()       { return prefs.getString(KEY_EMAIL, null); }
    public String getDisplayName() { return prefs.getString(KEY_NAME, null); }
    public String getProvider()    { return prefs.getString(KEY_PROVIDER, null); }
}
