package xyz.surina.proracingobd.auth;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Syncs the user's vehicle profile (VIN, tuning config) to the Surina race backend.
 *
 * Primary endpoint:  https://race.surina.xyz/api/v1/profile
 * Fallback endpoint: https://race.e164.cloud/api/v1/profile
 *
 * All requests are authenticated with the Firebase ID token as a Bearer token.
 */
public class ProfileSyncService {

    private static final String TAG = "ProfileSyncService";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String PRIMARY_URL  = "https://race.surina.xyz/api/v1/profile";
    private static final String FALLBACK_URL = "https://race.e164.cloud/api/v1/profile";

    private static ProfileSyncService instance;
    private final OkHttpClient http = new OkHttpClient();
    private final Executor bg = Executors.newSingleThreadExecutor();

    public interface SyncCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static ProfileSyncService getInstance() {
        if (instance == null) instance = new ProfileSyncService();
        return instance;
    }

    /**
     * Push the user's VIN + profile JSON to the backend.
     * Automatically fetches a fresh Firebase ID token before posting.
     */
    public void syncProfile(String vin, JSONObject profileJson, SyncCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onFailure("Not signed in");
            return;
        }

        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                callback.onFailure("Could not fetch ID token");
                return;
            }
            String idToken = task.getResult().getToken();
            bg.execute(() -> doPost(idToken, vin, profileJson, PRIMARY_URL, callback));
        });
    }

    private void doPost(String idToken, String vin, JSONObject profileJson,
                        String url, SyncCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("vin", vin);
            body.put("profile", profileJson);
        } catch (JSONException e) {
            callback.onFailure("JSON build error: " + e.getMessage());
            return;
        }

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + idToken)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        http.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (url.equals(PRIMARY_URL)) {
                    Log.w(TAG, "Primary endpoint failed, trying fallback");
                    doPost(idToken, vin, profileJson, FALLBACK_URL, callback);
                } else {
                    callback.onFailure("Both endpoints unreachable: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Profile synced OK to " + url);
                    callback.onSuccess();
                } else {
                    String err = "HTTP " + response.code();
                    if (url.equals(PRIMARY_URL)) {
                        Log.w(TAG, "Primary returned " + err + ", trying fallback");
                        doPost(idToken, vin, profileJson, FALLBACK_URL, callback);
                    } else {
                        callback.onFailure("Sync failed: " + err);
                    }
                }
                response.close();
            }
        });
    }
}
