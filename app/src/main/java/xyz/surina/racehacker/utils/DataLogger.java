package xyz.surina.racehacker.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import xyz.surina.racehacker.models.GaugeData;

public class DataLogger {
    private static final String TAG = "DataLogger";
    private static final String LOG_DIR = "ProRacingOBD";
    private static final String LOG_SUBDIR = "DataLogs";

    private Context context;
    private FileWriter fileWriter;
    private File currentLogFile;
    private boolean isLogging = false;
    private long startTime;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timestampFormat;

    private List<LogEntry> logBuffer;
    private static final int BUFFER_SIZE = 100;

    public static class LogEntry {
        long timestamp;
        String parameterName;
        float value;
        String unit;

        public LogEntry(long timestamp, String parameterName, float value, String unit) {
            this.timestamp = timestamp;
            this.parameterName = parameterName;
            this.value = value;
            this.unit = unit;
        }
    }

    public DataLogger(Context context) {
        this.context = context;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        this.timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        this.logBuffer = new ArrayList<>();
    }

    public boolean startLogging() {
        if (isLogging) {
            Log.w(TAG, "Already logging");
            return false;
        }

        try {
            File logDir = getLogDirectory();
            if (logDir == null) {
                Log.e(TAG, "Could not create log directory");
                return false;
            }

            String filename = "log_" + dateFormat.format(new Date()) + ".csv";
            currentLogFile = new File(logDir, filename);

            fileWriter = new FileWriter(currentLogFile, true);
            writeHeader();

            startTime = System.currentTimeMillis();
            isLogging = true;

            Log.d(TAG, "Started logging to: " + currentLogFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to start logging", e);
            return false;
        }
    }

    private void writeHeader() throws IOException {
        fileWriter.write("Timestamp,Elapsed Time (s),Parameter,Value,Unit\n");
        fileWriter.flush();
    }

    public void logData(GaugeData gaugeData) {
        if (!isLogging) return;

        long timestamp = System.currentTimeMillis();
        logBuffer.add(new LogEntry(timestamp, gaugeData.getName(),
                                   gaugeData.getCurrentValue(), gaugeData.getUnit()));

        if (logBuffer.size() >= BUFFER_SIZE) {
            flushBuffer();
        }
    }

    public void logData(String parameterName, float value, String unit) {
        if (!isLogging) return;

        long timestamp = System.currentTimeMillis();
        logBuffer.add(new LogEntry(timestamp, parameterName, value, unit));

        if (logBuffer.size() >= BUFFER_SIZE) {
            flushBuffer();
        }
    }

    private void flushBuffer() {
        if (fileWriter == null || logBuffer.isEmpty()) return;

        try {
            for (LogEntry entry : logBuffer) {
                double elapsedSeconds = (entry.timestamp - startTime) / 1000.0;
                String timestampStr = timestampFormat.format(new Date(entry.timestamp));

                fileWriter.write(String.format(Locale.US, "%s,%.3f,%s,%.2f,%s\n",
                        timestampStr, elapsedSeconds, entry.parameterName,
                        entry.value, entry.unit));
            }
            fileWriter.flush();
            logBuffer.clear();

        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }

    public void stopLogging() {
        if (!isLogging) return;

        flushBuffer();

        try {
            if (fileWriter != null) {
                fileWriter.close();
                fileWriter = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing log file", e);
        }

        isLogging = false;
        Log.d(TAG, "Stopped logging. File saved: " + currentLogFile.getAbsolutePath());
    }

    public boolean isLogging() {
        return isLogging;
    }

    public File getCurrentLogFile() {
        return currentLogFile;
    }

    public List<File> getLogFiles() {
        List<File> logFiles = new ArrayList<>();
        File logDir = getLogDirectory();

        if (logDir != null && logDir.exists()) {
            File[] files = logDir.listFiles((dir, name) -> name.endsWith(".csv"));
            if (files != null) {
                for (File file : files) {
                    logFiles.add(file);
                }
            }
        }

        return logFiles;
    }

    private File getLogDirectory() {
        File baseDir;

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            baseDir = context.getExternalFilesDir(null);
        } else {
            baseDir = context.getFilesDir();
        }

        if (baseDir == null) {
            return null;
        }

        File logDir = new File(baseDir, LOG_DIR + File.separator + LOG_SUBDIR);

        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                Log.e(TAG, "Failed to create log directory");
                return null;
            }
        }

        return logDir;
    }

    public boolean exportToCSV(File destination) {
        if (currentLogFile == null || !currentLogFile.exists()) {
            return false;
        }

        try {
            java.nio.file.Files.copy(
                currentLogFile.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to export log file", e);
            return false;
        }
    }

    public long getLogDuration() {
        if (!isLogging) return 0;
        return System.currentTimeMillis() - startTime;
    }
}
