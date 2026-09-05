package xyz.surina.racehacker.network;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Local/"display" role of the network gauge relay: connects to a broadcaster
 * device found via {@link NetworkDiscoveryManager} and relays incoming gauge
 * snapshots to a listener. MainActivity feeds these into the same
 * liveGauges list a direct OBD connection would populate, so the rest of
 * the UI/narration pipeline doesn't need to know or care whether the data
 * came from a real adapter or a mirrored one.
 */
public class GaugeMirrorClient extends WebSocketClient {
    private static final String TAG = "GaugeMirrorClient";
    private final Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    private final Type listType = new TypeToken<List<GaugeData>>() {}.getType();
    private final MirrorListener listener;

    public interface MirrorListener {
        void onGaugesReceived(List<GaugeData> gauges);
        void onMirrorConnected();
        void onMirrorDisconnected();
    }

    public GaugeMirrorClient(URI serverUri, MirrorListener listener) {
        super(serverUri);
        this.listener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        Log.d(TAG, "Connected to broadcaster at " + getURI());
        if (listener != null) listener.onMirrorConnected();
    }

    @Override
    public void onMessage(String message) {
        try {
            List<GaugeData> gauges = gson.fromJson(message, listType);
            if (listener != null) listener.onGaugesReceived(gauges);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse gauge JSON from broadcaster", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "Disconnected from broadcaster: " + reason);
        if (listener != null) listener.onMirrorDisconnected();
    }

    @Override
    public void onError(Exception ex) {
        Log.e(TAG, "WebSocket client error", ex);
    }
}
