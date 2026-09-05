package xyz.surina.racehacker.network;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.List;

import xyz.surina.racehacker.models.GaugeData;

/**
 * Remote/"in-car" role of the network gauge relay: broadcasts the live gauge
 * snapshot as JSON to every connected client on every update. Deliberately
 * plain JSON over a standard WebSocket handshake, not a custom binary
 * protocol — a mirror device (another phone running this app, see
 * {@link GaugeMirrorClient}) is one consumer, but so is any external tool
 * (a script, VS Code, a browser) that can open a WebSocket, with zero
 * RaceHacker-specific client library needed.
 *
 * Found on the network via {@link NetworkDiscoveryManager}; the port itself
 * carries no auth — this is meant for a trusted local network (home WiFi, a
 * phone's own hotspot in the car), not the open internet.
 */
public class GaugeBroadcastServer extends WebSocketServer {
    private static final String TAG = "GaugeBroadcastServer";
    public static final int DEFAULT_PORT = 8420;

    // Gauges start at Float.NaN ("no data yet") — Gson rejects NaN/Infinity
    // by default (throws rather than silently dropping the field), so this
    // has to be explicitly allowed for broadcasting to work before a real
    // OBD connection has populated every gauge.
    private final Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    public GaugeBroadcastServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Log.d(TAG, "Client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.d(TAG, "Client disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // No inbound commands supported yet — this is a one-way telemetry feed.
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(TAG, "WebSocket server error", ex);
    }

    @Override
    public void onStart() {
        Log.d(TAG, "Broadcast server started on port " + getPort());
    }

    /** Call on every gauge update. No-ops cheaply if nothing is connected yet. */
    public void broadcastGauges(List<GaugeData> gauges) {
        if (getConnections().isEmpty()) return;
        broadcast(gson.toJson(gauges));
    }
}
