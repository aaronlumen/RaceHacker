package xyz.surina.racehacker.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import xyz.surina.racehacker.vehicles.VehicleProfile;

/**
 * Enhanced OBD Service combining custom code with Pires OBD-Java API
 * - Uses Pires API for standard OBD2 commands (80+ PIDs)
 * - Uses custom code for ECU flashing and advanced tuning
 */
public class EnhancedObdService {
    private static final String TAG = "EnhancedObdService";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket socket;
    private VehicleProfile vehicleProfile;
    private PiresObdManager piresManager;
    private ObdConnectionService customObdService;
    private DtcManager dtcManager;

    private ConnectionListener connectionListener;
    private Handler mainHandler;
    private boolean isConnected = false;

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onDataReceived(String data);
    }

    public EnhancedObdService(VehicleProfile vehicleProfile) {
        this.vehicleProfile = vehicleProfile;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.piresManager = new PiresObdManager();
        this.customObdService = new ObdConnectionService(vehicleProfile);
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;

        // Also set listener on custom service
        customObdService.setConnectionListener(new ObdConnectionService.ConnectionListener() {
            @Override
            public void onConnected() {
                if (connectionListener != null) {
                    connectionListener.onConnected();
                }
            }

            @Override
            public void onDisconnected() {
                if (connectionListener != null) {
                    connectionListener.onDisconnected();
                }
            }

            @Override
            public void onError(String error) {
                if (connectionListener != null) {
                    connectionListener.onError(error);
                }
            }

            @Override
            public void onDataReceived(String data) {
                if (connectionListener != null) {
                    connectionListener.onDataReceived(data);
                }
            }
        });
    }

    /**
     * Connect to OBD2 adapter
     */
    public void connect(BluetoothDevice device) {
        new Thread(() -> {
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();

                // Initialize Pires OBD Manager
                piresManager.initialize(socket);

                // Initialize DTC Manager with custom service
                customObdService.connect(device);
                dtcManager = new DtcManager(customObdService);

                isConnected = true;
                notifyConnected();

                Log.d(TAG, "Enhanced OBD Service connected with Pires API");

            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Connection failed", e);
                notifyError("Connection failed: " + e.getMessage());
                disconnect();
            }
        }).start();
    }

    /**
     * Disconnect from OBD2 adapter
     */
    public void disconnect() {
        isConnected = false;

        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }

        if (customObdService != null) {
            customObdService.disconnect();
        }

        notifyDisconnected();
    }

    // ============ Pires API Methods (Standard OBD2) ============

    /**
     * Get RPM using Pires API
     */
    public int getRPM() {
        try {
            return piresManager.getRPM();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error getting RPM", e);
            return 0;
        }
    }

    /**
     * Get speed using Pires API
     */
    public int getSpeed() {
        try {
            return piresManager.getSpeed();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error getting speed", e);
            return 0;
        }
    }

    /**
     * Get throttle position using Pires API
     */
    public float getThrottlePosition() {
        try {
            return piresManager.getThrottlePosition();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error getting throttle position", e);
            return 0;
        }
    }

    /**
     * Get coolant temperature using Pires API
     */
    public float getCoolantTemperature() {
        try {
            return piresManager.getCoolantTemperature();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error getting coolant temp", e);
            return 0;
        }
    }

    /**
     * Get boost pressure using Pires API (calculated from MAP and Baro)
     */
    public float getBoostPressure() {
        try {
            return piresManager.getBoostPressure();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error getting boost pressure", e);
            return 0;
        }
    }

    /**
     * Get fuel level using Pires API
     */
    public float getFuelLevel() {
        try {
            return piresManager.getFuelLevel();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error getting fuel level", e);
            return 0;
        }
    }

    // ============ Custom Methods (ECU Flashing & Advanced) ============

    /**
     * Get DTC Manager for diagnostics (uses custom service)
     */
    public DtcManager getDtcManager() {
        return dtcManager;
    }

    /**
     * Send raw command (uses custom service for ECU flashing)
     */
    public String sendRawCommand(String command) {
        try {
            return customObdService.sendAndReceive(command);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error sending raw command", e);
            return "";
        }
    }

    /**
     * Execute Pires command by name
     */
    public String executePiresCommand(String commandName) {
        try {
            return piresManager.executeCommand(commandName);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error executing Pires command", e);
            return "";
        }
    }

    // ============ Status Methods ============

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isPiresInitialized() {
        return piresManager.isInitialized();
    }

    public PiresObdManager getPiresManager() {
        return piresManager;
    }

    public ObdConnectionService getCustomObdService() {
        return customObdService;
    }

    // ============ Notification Methods ============

    private void notifyConnected() {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onConnected());
        }
    }

    private void notifyDisconnected() {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onDisconnected());
        }
    }

    private void notifyError(String error) {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onError(error));
        }
    }
}
