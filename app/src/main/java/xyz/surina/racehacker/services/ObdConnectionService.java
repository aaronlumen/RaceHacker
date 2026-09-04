package xyz.surina.racehacker.services;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import xyz.surina.racehacker.vehicles.VehicleProfile;

public class ObdConnectionService {
    private static final String TAG = "ObdConnectionService";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private ConnectionListener connectionListener;
    private ObdDataListener dataListener;
    private VehicleProfile vehicleProfile;
    private boolean isConnected = false;
    private boolean isPidPolling = false;
    private Handler mainHandler;

    // Stashed by queryBoostPsi() so the raw MAP reading it already fetches
    // (PID 010B) can be exposed as its own gauge without a second query.
    private float lastMapKpa;

    // ─── Listener interfaces ────────────────────────────────────────────────

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onDataReceived(String data);
    }

    public interface ObdDataListener {
        void onSensorData(float rpm, float speedMph, float coolantTempF,
                          float intakeTempF, float throttlePct,
                          float boostPsi, float batteryV,
                          float timingDeg, float fuelLevelPct, float afr,
                          float mapKpa, float mafGps);
    }

    // ─── Constructor ────────────────────────────────────────────────────────

    public ObdConnectionService(VehicleProfile vehicleProfile) {
        this.vehicleProfile = vehicleProfile;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void setDataListener(ObdDataListener listener) {
        this.dataListener = listener;
    }

    // ─── Connect / Disconnect ────────────────────────────────────────────────

    public void connect(BluetoothDevice device) {
        new Thread(() -> {
            try {
                cancelDiscoveryIfActive();

                socket = connectSocket(device);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                initializeObd();
                isConnected = true;
                notifyConnected();

                if (dataListener != null) {
                    startPidPolling();
                } else {
                    startReading();
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Connection failed", e);
                notifyError("Connection failed: " + e.getMessage());
                disconnect();
            }
        }).start();
    }

    /**
     * An in-progress Bluetooth discovery (device scan) drastically slows down,
     * and can outright break, RFCOMM socket setup — a well-documented Android
     * gotcha. Never harmful to call even if nothing is scanning. Swallows any
     * SecurityException (missing BLUETOOTH_SCAN on Android 12+) rather than
     * letting it crash the connect attempt — worst case we just skip this and
     * proceed straight to connecting.
     */
    private void cancelDiscoveryIfActive() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Could not check/cancel discovery (missing permission?)", e);
        }
    }

    /**
     * Standard SPP-UUID RFCOMM connect first — works when the adapter properly
     * advertises SDP records. Many ELM327 clones don't, and Android's SDP-based
     * connect then fails or times out even though a raw RFCOMM channel 1
     * connection would work fine. This is exactly why mature OBD apps (e.g.
     * Torque) fall back to a raw channel via reflection when the standard path
     * fails, rather than giving up — so this does too.
     */
    private BluetoothSocket connectSocket(BluetoothDevice device) throws IOException {
        try {
            BluetoothSocket s = device.createRfcommSocketToServiceRecord(SPP_UUID);
            s.connect();
            return s;
        } catch (IOException e) {
            Log.w(TAG, "Standard SPP connect failed, falling back to raw RFCOMM channel 1", e);
            BluetoothSocket fallback = createRfcommSocketChannel1(device);
            fallback.connect();
            return fallback;
        }
    }

    /**
     * BluetoothDevice doesn't publicly expose "connect to a specific RFCOMM
     * channel" (only "connect via SDP lookup of a UUID") — channel 1 is
     * reached via the hidden createRfcommSocket(int) method instead, the same
     * long-standing workaround other Android OBD/SPP clients use.
     */
    private BluetoothSocket createRfcommSocketChannel1(BluetoothDevice device) throws IOException {
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            return (BluetoothSocket) m.invoke(device, 1);
        } catch (Exception reflectionError) {
            throw new IOException("Raw RFCOMM channel 1 connect unavailable: " + reflectionError.getMessage());
        }
    }

    private void initializeObd() throws IOException, InterruptedException {
        sendCommand("ATZ");   Thread.sleep(1000);
        sendCommand("ATE0");  Thread.sleep(100);
        sendCommand("ATL0");  Thread.sleep(100);
        sendCommand("ATS0");  Thread.sleep(100);
        sendCommand("ATH1");  Thread.sleep(100);

        String protocol = getProtocolCommand();
        if (protocol != null) {
            sendCommand(protocol);
            Thread.sleep(200);
        }
        sendCommand("0100");
        Thread.sleep(200);
        Log.d(TAG, "OBD initialized for: " + vehicleProfile.getName());
    }

    private String getProtocolCommand() {
        switch (vehicleProfile.getProtocol()) {
            case ISO_9141_2:       return "ATSP3";
            case ISO_14230_4_KWP:  return "ATSP4";
            case ISO_15765_4_CAN:  return "ATSP6";
            case SAE_J1939:        return "ATSP9";
            case SAE_J1850_PWM:    return "ATSP1";
            case SAE_J1850_VPW:    return "ATSP2";
            default:               return "ATSP0";
        }
    }

    public void disconnect() {
        isConnected = false;
        isPidPolling = false;
        closeStream(inputStream);  inputStream = null;
        closeStream(outputStream); outputStream = null;
        closeSocket();
        notifyDisconnected();
    }

    private void closeStream(java.io.Closeable c) {
        if (c != null) try { c.close(); } catch (IOException e) {
            Log.e(TAG, "Error closing stream", e);
        }
    }

    private void closeSocket() {
        if (socket != null) try { socket.close(); socket = null; } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
    }

    public boolean isConnected() { return isConnected; }

    // ─── Raw send / receive ──────────────────────────────────────────────────

    public void sendCommand(String command) throws IOException {
        if (outputStream == null) throw new IOException("Not connected");
        outputStream.write((command + "\r").getBytes());
        outputStream.flush();
        Log.d(TAG, "Sent: " + command);
    }

    public String readResponse() throws IOException, InterruptedException {
        if (inputStream == null) throw new IOException("No input stream");
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[1024];
        long timeout = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < timeout) {
            if (inputStream.available() > 0) {
                int n = inputStream.read(buf);
                String chunk = new String(buf, 0, n);
                sb.append(chunk);
                if (chunk.contains(">")) break;
            }
            Thread.sleep(50);
        }
        String result = sb.toString().trim();
        Log.d(TAG, "Recv: " + result);
        return result;
    }

    public String sendAndReceive(String command) throws IOException, InterruptedException {
        sendCommand(command);
        Thread.sleep(100);
        return readResponse();
    }

    // ─── Passive read loop (used when no dataListener is set) ───────────────

    private void startReading() {
        new Thread(() -> {
            while (isConnected && !isPidPolling) {
                try {
                    if (inputStream != null && inputStream.available() > 0) {
                        String data = readResponse();
                        notifyDataReceived(data);
                    }
                    Thread.sleep(100);
                } catch (IOException | InterruptedException e) {
                    if (isConnected) { Log.e(TAG, "Read error", e); disconnect(); }
                    break;
                }
            }
        }).start();
    }

    // ─── Live PID polling (replaces simulation) ──────────────────────────────

    public void startPidPolling() {
        if (isPidPolling) return;
        isPidPolling = true;
        new Thread(() -> {
            while (isConnected && isPidPolling) {
                try {
                    float rpm        = queryRpm();
                    float speedMph   = querySpeedMph();
                    float coolantF   = queryCoolantTempF();
                    float intakeF    = queryIntakeTempF();
                    float throttle   = queryThrottlePct();
                    float boostPsi   = queryBoostPsi();
                    float battery    = queryBattery();
                    float timing     = queryTiming();
                    float fuelLevel  = queryFuelLevel();
                    float afr        = queryAfr();
                    float mapKpa     = lastMapKpa; // set by queryBoostPsi() above
                    float mafGps     = queryMaf();

                    if (dataListener != null) {
                        mainHandler.post(() -> dataListener.onSensorData(
                                rpm, speedMph, coolantF, intakeF,
                                throttle, boostPsi, battery, timing,
                                fuelLevel, afr, mapKpa, mafGps));
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "PID poll error", e);
                }
            }
        }).start();
    }

    public void stopPidPolling() { isPidPolling = false; }

    /**
     * Read VIN via OBD Mode 09 PID 02 (0902).
     * ELM327 returns ASCII bytes after the "49 02 xx" header bytes.
     * Returns empty string on failure.
     */
    public String readVin() {
        try {
            String response = sendAndReceive("0902");
            return parseVin(response);
        } catch (Exception e) {
            Log.e(TAG, "VIN read error", e);
            return "";
        }
    }

    private String parseVin(String response) {
        // Collapse whitespace, uppercase
        String clean = response.toUpperCase()
                .replaceAll("[^0-9A-F\\s]", " ")
                .replaceAll("\\s+", " ").trim();

        // Collect all hex tokens after any "49 02" header
        String[] parts = clean.split(" ");
        StringBuilder vinHex = new StringBuilder();
        boolean collecting = false;
        int skipCount = 0; // skip the frame-number byte after "49 02"

        for (int i = 0; i < parts.length; i++) {
            if (!collecting) {
                if ("49".equals(parts[i]) && i + 1 < parts.length && "02".equals(parts[i + 1])) {
                    collecting = true;
                    skipCount = 1; // next byte is frame number — skip it
                    i++; // skip "02"
                }
            } else {
                if (skipCount > 0) { skipCount--; continue; }
                if (parts[i].length() == 2) {
                    int b = Integer.parseInt(parts[i], 16);
                    if (b >= 0x20 && b <= 0x7E) vinHex.append((char) b);
                }
            }
        }

        String vin = vinHex.toString().trim();
        Log.d(TAG, "Parsed VIN: " + vin);
        return (vin.length() >= 5) ? vin : ""; // sanity check
    }

    // ─── OBD PID query helpers ───────────────────────────────────────────────

    /** RPM: PID 010C  → (A*256+B)/4 */
    private float queryRpm() {
        try {
            String r = sendAndReceive("010C");
            return parseByteAB(r, "0C", (a, b) -> (a * 256f + b) / 4f);
        } catch (Exception e) { return 0; }
    }

    /** Speed: PID 010D → A km/h → MPH */
    private float querySpeedMph() {
        try {
            String r = sendAndReceive("010D");
            float kmh = parseByte1(r, "0D");
            return kmh * 0.621371f;
        } catch (Exception e) { return 0; }
    }

    /** Coolant temp: PID 0105 → A-40 °C → °F */
    private float queryCoolantTempF() {
        try {
            String r = sendAndReceive("0105");
            float c = parseByte1(r, "05") - 40f;
            return c * 9f / 5f + 32f;
        } catch (Exception e) { return 0; }
    }

    /** Intake air temp: PID 010F → A-40 °C → °F */
    private float queryIntakeTempF() {
        try {
            String r = sendAndReceive("010F");
            float c = parseByte1(r, "0F") - 40f;
            return c * 9f / 5f + 32f;
        } catch (Exception e) { return 0; }
    }

    /** Throttle: PID 0111 → A*100/255 % */
    private float queryThrottlePct() {
        try {
            String r = sendAndReceive("0111");
            return parseByte1(r, "11") * 100f / 255f;
        } catch (Exception e) { return 0; }
    }

    /**
     * Boost: (MAP kPa - Baro kPa) * 0.145038
     * MAP PID 010B, Baro PID 0133
     */
    private float queryBoostPsi() {
        try {
            float map  = parseByte1(sendAndReceive("010B"), "0B");
            lastMapKpa = map;
            float baro = parseByte1(sendAndReceive("0133"), "33");
            float boostKpa = map - baro;
            return boostKpa * 0.145038f;
        } catch (Exception e) { return 0; }
    }

    /** MAF — mass air flow: PID 0110 → (A*256+B)/100 g/s */
    private float queryMaf() {
        try {
            String r = sendAndReceive("0110");
            return parseByteAB(r, "10", (a, b) -> (a * 256f + b) / 100f);
        } catch (Exception e) { return 0; }
    }

    /** Battery: ELM327 "ATRV" → "14.2V" */
    private float queryBattery() {
        try {
            String r = sendAndReceive("ATRV");
            r = r.replaceAll("[^0-9.]", "").trim();
            if (!r.isEmpty()) return Float.parseFloat(r);
        } catch (Exception e) { /* fall through */ }
        return 0;
    }

    /** Timing advance: PID 010E → A/2 - 64 degrees */
    private float queryTiming() {
        try {
            String r = sendAndReceive("010E");
            return parseByte1(r, "0E") / 2f - 64f;
        } catch (Exception e) { return 0; }
    }

    /** Fuel level: PID 012F → A*100/255 % */
    private float queryFuelLevel() {
        try {
            String r = sendAndReceive("012F");
            return parseByte1(r, "2F") * 100f / 255f;
        } catch (Exception e) { return 0; }
    }

    /**
     * AFR estimate via short-term fuel trim (PID 0106) and stoich 14.7:1.
     * STFT% = (A/128 - 1) * 100  → lambda ≈ 1 + STFT/100 → AFR = lambda * 14.7
     */
    private float queryAfr() {
        try {
            float stft = (parseByte1(sendAndReceive("0106"), "06") / 128f - 1f) * 100f;
            float ltft = (parseByte1(sendAndReceive("0107"), "07") / 128f - 1f) * 100f;
            float lambda = 1f + (stft + ltft) / 100f;
            return lambda * 14.7f;
        } catch (Exception e) { return 14.7f; }
    }

    // ─── Response parsers ────────────────────────────────────────────────────

    /**
     * Parse a single data byte from an ELM327 response.
     * Looks for the pattern "41 XX YY" and returns YY as float.
     */
    private float parseByte1(String response, String pid) {
        String clean = response.toUpperCase()
                .replaceAll("[^0-9A-F\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        String[] parts = clean.split(" ");
        String PID = pid.toUpperCase();
        for (int i = 0; i < parts.length - 2; i++) {
            if ("41".equals(parts[i]) && PID.equals(parts[i + 1])) {
                return (float) Integer.parseInt(parts[i + 2], 16);
            }
        }
        return 0;
    }

    /** Parse two data bytes A,B from an ELM327 response. */
    private float parseByteAB(String response, String pid, ByteABParser parser) {
        String clean = response.toUpperCase()
                .replaceAll("[^0-9A-F\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        String[] parts = clean.split(" ");
        String PID = pid.toUpperCase();
        for (int i = 0; i < parts.length - 3; i++) {
            if ("41".equals(parts[i]) && PID.equals(parts[i + 1])) {
                int a = Integer.parseInt(parts[i + 2], 16);
                int b = Integer.parseInt(parts[i + 3], 16);
                return parser.parse(a, b);
            }
        }
        return 0;
    }

    @FunctionalInterface
    interface ByteABParser { float parse(int a, int b); }

    // ─── Notification helpers ────────────────────────────────────────────────

    private void notifyConnected() {
        if (connectionListener != null)
            mainHandler.post(() -> connectionListener.onConnected());
    }

    private void notifyDisconnected() {
        if (connectionListener != null)
            mainHandler.post(() -> connectionListener.onDisconnected());
    }

    private void notifyError(String error) {
        if (connectionListener != null)
            mainHandler.post(() -> connectionListener.onError(error));
    }

    private void notifyDataReceived(String data) {
        if (connectionListener != null)
            mainHandler.post(() -> connectionListener.onDataReceived(data));
    }
}
