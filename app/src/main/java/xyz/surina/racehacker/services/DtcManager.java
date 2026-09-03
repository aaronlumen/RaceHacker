package xyz.surina.racehacker.services;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DtcManager {
    private static final String TAG = "DtcManager";
    private ObdConnectionService obdService;

    public static class DiagnosticTroubleCode {
        private String code;
        private String description;
        private String status;
        private boolean isPermanent;

        public DiagnosticTroubleCode(String code, String description, String status) {
            this.code = code;
            this.description = description;
            this.status = status;
            this.isPermanent = false;
        }

        public String getCode() { return code; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public boolean isPermanent() { return isPermanent; }
        public void setPermanent(boolean permanent) { isPermanent = permanent; }
    }

    private static final Map<String, String> DTC_DESCRIPTIONS = new HashMap<>();

    static {
        // Fuel and Air Metering
        DTC_DESCRIPTIONS.put("P0171", "System Too Lean (Bank 1)");
        DTC_DESCRIPTIONS.put("P0172", "System Too Rich (Bank 1)");
        DTC_DESCRIPTIONS.put("P0174", "System Too Lean (Bank 2)");
        DTC_DESCRIPTIONS.put("P0175", "System Too Rich (Bank 2)");

        // Ignition System
        DTC_DESCRIPTIONS.put("P0300", "Random/Multiple Cylinder Misfire Detected");
        DTC_DESCRIPTIONS.put("P0301", "Cylinder 1 Misfire Detected");
        DTC_DESCRIPTIONS.put("P0302", "Cylinder 2 Misfire Detected");
        DTC_DESCRIPTIONS.put("P0303", "Cylinder 3 Misfire Detected");
        DTC_DESCRIPTIONS.put("P0304", "Cylinder 4 Misfire Detected");
        DTC_DESCRIPTIONS.put("P0305", "Cylinder 5 Misfire Detected");
        DTC_DESCRIPTIONS.put("P0306", "Cylinder 6 Misfire Detected");

        // Oxygen Sensors
        DTC_DESCRIPTIONS.put("P0130", "O2 Sensor Circuit Malfunction (Bank 1, Sensor 1)");
        DTC_DESCRIPTIONS.put("P0131", "O2 Sensor Circuit Low Voltage (Bank 1, Sensor 1)");
        DTC_DESCRIPTIONS.put("P0132", "O2 Sensor Circuit High Voltage (Bank 1, Sensor 1)");
        DTC_DESCRIPTIONS.put("P0133", "O2 Sensor Circuit Slow Response (Bank 1, Sensor 1)");

        // Boost/Turbo
        DTC_DESCRIPTIONS.put("P0234", "Engine Overboost Condition");
        DTC_DESCRIPTIONS.put("P0235", "Turbocharger Boost Sensor A Circuit");
        DTC_DESCRIPTIONS.put("P0236", "Turbocharger Boost Sensor A Circuit Range/Performance");

        // Transmission
        DTC_DESCRIPTIONS.put("P0700", "Transmission Control System Malfunction");
        DTC_DESCRIPTIONS.put("P0715", "Input/Turbine Speed Sensor Circuit Malfunction");
        DTC_DESCRIPTIONS.put("P0720", "Output Speed Sensor Circuit Malfunction");

        // Emissions
        DTC_DESCRIPTIONS.put("P0420", "Catalyst System Efficiency Below Threshold (Bank 1)");
        DTC_DESCRIPTIONS.put("P0430", "Catalyst System Efficiency Below Threshold (Bank 2)");

        // MAF/MAP
        DTC_DESCRIPTIONS.put("P0100", "Mass Air Flow Circuit Malfunction");
        DTC_DESCRIPTIONS.put("P0101", "Mass Air Flow Circuit Range/Performance");
        DTC_DESCRIPTIONS.put("P0105", "Manifold Absolute Pressure Circuit Malfunction");

        // Common diesel codes
        DTC_DESCRIPTIONS.put("P0087", "Fuel Rail/System Pressure - Too Low");
        DTC_DESCRIPTIONS.put("P0088", "Fuel Rail/System Pressure - Too High");
        DTC_DESCRIPTIONS.put("P0380", "Glow Plug/Heater Circuit A Malfunction");
        DTC_DESCRIPTIONS.put("P0401", "Exhaust Gas Recirculation Flow Insufficient Detected");
    }

    public DtcManager(ObdConnectionService obdService) {
        this.obdService = obdService;
    }

    public List<DiagnosticTroubleCode> readDtcCodes() {
        List<DiagnosticTroubleCode> codes = new ArrayList<>();

        try {
            String response = obdService.sendAndReceive("03");
            codes.addAll(parseDtcResponse(response, "Active"));

            response = obdService.sendAndReceive("07");
            List<DiagnosticTroubleCode> pendingCodes = parseDtcResponse(response, "Pending");
            codes.addAll(pendingCodes);

            response = obdService.sendAndReceive("0A");
            List<DiagnosticTroubleCode> permanentCodes = parseDtcResponse(response, "Permanent");
            for (DiagnosticTroubleCode code : permanentCodes) {
                code.setPermanent(true);
            }
            codes.addAll(permanentCodes);

        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to read DTC codes", e);
        }

        return codes;
    }

    private List<DiagnosticTroubleCode> parseDtcResponse(String response, String status) {
        List<DiagnosticTroubleCode> codes = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            return codes;
        }

        response = response.replaceAll("\\s+", "");
        response = response.replaceAll(">", "");

        if (response.contains("NODATA") || response.length() < 4) {
            return codes;
        }

        for (int i = 0; i < response.length() - 3; i += 4) {
            try {
                String hexCode = response.substring(i, i + 4);
                String dtcCode = hexToDtcCode(hexCode);

                if (dtcCode != null && !dtcCode.equals("P0000")) {
                    String description = DTC_DESCRIPTIONS.getOrDefault(dtcCode,
                            "Unknown diagnostic code");
                    codes.add(new DiagnosticTroubleCode(dtcCode, description, status));
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse DTC segment", e);
            }
        }

        return codes;
    }

    private String hexToDtcCode(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            int firstDigit = (value >> 14) & 0x03;
            int secondDigit = (value >> 12) & 0x03;
            int thirdDigit = (value >> 8) & 0x0F;
            int fourthDigit = (value >> 4) & 0x0F;
            int fifthDigit = value & 0x0F;

            char prefix;
            switch (firstDigit) {
                case 0: prefix = 'P'; break;
                case 1: prefix = 'C'; break;
                case 2: prefix = 'B'; break;
                case 3: prefix = 'U'; break;
                default: return null;
            }

            return String.format("%c%d%X%X%X",
                    prefix, secondDigit, thirdDigit, fourthDigit, fifthDigit);

        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid hex code: " + hex, e);
            return null;
        }
    }

    public boolean clearDtcCodes() {
        try {
            String response = obdService.sendAndReceive("04");
            Log.d(TAG, "Clear DTC response: " + response);
            return !response.contains("ERROR") && !response.contains("?");
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to clear DTC codes", e);
            return false;
        }
    }

    public int getDtcCount() {
        try {
            String response = obdService.sendAndReceive("0101");
            if (response != null && response.length() > 4) {
                String countHex = response.substring(4, 6);
                return Integer.parseInt(countHex, 16) & 0x7F;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get DTC count", e);
        }
        return 0;
    }

    public boolean isMilOn() {
        try {
            String response = obdService.sendAndReceive("0101");
            if (response != null && response.length() > 4) {
                String statusHex = response.substring(4, 6);
                int status = Integer.parseInt(statusHex, 16);
                return (status & 0x80) != 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check MIL status", e);
        }
        return false;
    }
}
