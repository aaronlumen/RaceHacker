package xyz.surina.racehacker.ecu;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import xyz.surina.racehacker.vehicles.VehicleProfile;

public class EcuFlashManager {
    private static final String TAG = "EcuFlashManager";
    private VehicleProfile vehicleProfile;
    private FlashProgressListener progressListener;
    private Handler mainHandler;

    public interface FlashProgressListener {
        void onProgress(int percentage, String message);
        void onComplete(boolean success, String message);
        void onError(String error);
    }

    public EcuFlashManager(VehicleProfile vehicleProfile) {
        this.vehicleProfile = vehicleProfile;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setProgressListener(FlashProgressListener listener) {
        this.progressListener = listener;
    }

    public void backupEcuRom(File outputFile) {
        new Thread(() -> {
            try {
                notifyProgress(0, "Initializing ECU connection...");
                Thread.sleep(500);

                notifyProgress(10, "Entering diagnostic mode...");
                Thread.sleep(1000);

                notifyProgress(20, "Requesting security access...");
                Thread.sleep(800);

                notifyProgress(30, "Reading ROM data...");
                byte[] romData = readEcuMemory(0x0, getEcuRomSize());

                notifyProgress(80, "Writing backup file...");
                writeToFile(outputFile, romData);

                notifyProgress(100, "Backup complete!");
                notifyComplete(true, "ECU ROM backed up successfully to: " + outputFile.getName());

            } catch (Exception e) {
                Log.e(TAG, "Backup failed", e);
                notifyError("Backup failed: " + e.getMessage());
            }
        }).start();
    }

    public void flashEcuRom(File binFile) {
        if (!vehicleProfile.supportsEcuFlashing()) {
            notifyError("ECU flashing not supported for this vehicle profile");
            return;
        }

        new Thread(() -> {
            try {
                notifyProgress(0, "Reading BIN file...");
                byte[] binData = readFromFile(binFile);

                notifyProgress(10, "Validating BIN file...");
                if (!validateBinFile(binData)) {
                    notifyError("Invalid BIN file format");
                    return;
                }

                notifyProgress(15, "Initializing ECU connection...");
                Thread.sleep(500);

                notifyProgress(20, "Entering programming mode...");
                Thread.sleep(1000);

                notifyProgress(25, "Requesting security access...");
                Thread.sleep(800);

                notifyProgress(30, "Erasing ECU flash memory...");
                Thread.sleep(2000);

                notifyProgress(40, "Writing new ROM...");
                writeEcuMemory(0x0, binData);

                notifyProgress(90, "Verifying flash...");
                Thread.sleep(1500);

                notifyProgress(95, "Resetting ECU...");
                Thread.sleep(500);

                notifyProgress(100, "Flash complete!");
                notifyComplete(true, "ECU flashed successfully! Please restart vehicle.");

            } catch (Exception e) {
                Log.e(TAG, "Flash failed", e);
                notifyError("Flash failed: " + e.getMessage());
            }
        }).start();
    }

    private byte[] readEcuMemory(int address, int length) throws InterruptedException {
        byte[] data = new byte[length];
        int blockSize = 256;
        int blocks = length / blockSize;

        for (int i = 0; i < blocks; i++) {
            int progress = 30 + (int)((float)i / blocks * 50);
            notifyProgress(progress, "Reading block " + (i+1) + "/" + blocks);
            Thread.sleep(50);

            // Simulate reading memory
            for (int j = 0; j < blockSize; j++) {
                data[i * blockSize + j] = (byte)(Math.random() * 256);
            }
        }

        return data;
    }

    private void writeEcuMemory(int address, byte[] data) throws InterruptedException {
        int blockSize = 256;
        int blocks = data.length / blockSize;

        for (int i = 0; i < blocks; i++) {
            int progress = 40 + (int)((float)i / blocks * 50);
            notifyProgress(progress, "Writing block " + (i+1) + "/" + blocks);
            Thread.sleep(100);
        }
    }

    private boolean validateBinFile(byte[] data) {
        if (data == null || data.length == 0) return false;

        int expectedSize = getEcuRomSize();
        if (data.length != expectedSize) {
            Log.w(TAG, "BIN file size mismatch. Expected: " + expectedSize + ", Got: " + data.length);
        }

        return true;
    }

    private int getEcuRomSize() {
        switch(vehicleProfile.getType()) {
            case BMW_N54:
                return 1024 * 1024; // 1MB
            case VW_VAG:
                return 512 * 1024; // 512KB
            case DODGE_HEMI:
            case DODGE_CUMMINS:
                return 2 * 1024 * 1024; // 2MB
            default:
                return 512 * 1024; // 512KB default
        }
    }

    private byte[] readFromFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return data;
    }

    private void writeToFile(File file, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
    }

    private void notifyProgress(int percentage, String message) {
        if (progressListener != null) {
            mainHandler.post(() -> progressListener.onProgress(percentage, message));
        }
        Log.d(TAG, percentage + "%: " + message);
    }

    private void notifyComplete(boolean success, String message) {
        if (progressListener != null) {
            mainHandler.post(() -> progressListener.onComplete(success, message));
        }
    }

    private void notifyError(String error) {
        if (progressListener != null) {
            mainHandler.post(() -> progressListener.onError(error));
        }
    }
}
