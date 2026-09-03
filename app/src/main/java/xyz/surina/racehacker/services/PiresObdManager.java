package xyz.surina.racehacker.services;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.ThrottlePositionCommand;
import com.github.pires.obd.commands.fuel.FuelLevelCommand;
import com.github.pires.obd.commands.control.TroubleCodesCommand;
import com.github.pires.obd.commands.control.PendingTroubleCodesCommand;
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.commands.pressure.IntakeManifoldPressureCommand;
import com.github.pires.obd.commands.pressure.BarometricPressureCommand;
import com.github.pires.obd.enums.AvailableCommandNames;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced OBD Manager using Pires OBD-Java API
 * Provides access to 80+ standard OBD2 PIDs
 */
public class PiresObdManager {
    private static final String TAG = "PiresObdManager";

    private BluetoothSocket socket;
    private Map<String, ObdCommand> commandCache;
    private boolean isInitialized = false;

    public PiresObdManager() {
        commandCache = new HashMap<>();
        initializeCommands();
    }

    private void initializeCommands() {
        // Engine commands
        commandCache.put("RPM", new RPMCommand());
        commandCache.put("SPEED", new SpeedCommand());
        commandCache.put("THROTTLE", new ThrottlePositionCommand());

        // Temperature commands
        commandCache.put("COOLANT_TEMP", new EngineCoolantTemperatureCommand());
        commandCache.put("AIR_TEMP", new AmbientAirTemperatureCommand());

        // Pressure commands
        commandCache.put("INTAKE_PRESSURE", new IntakeManifoldPressureCommand());
        commandCache.put("BAROMETRIC_PRESSURE", new BarometricPressureCommand());

        // Fuel commands
        commandCache.put("FUEL_LEVEL", new FuelLevelCommand());

        // Diagnostic commands
        commandCache.put("TROUBLE_CODES", new TroubleCodesCommand());
        commandCache.put("PENDING_CODES", new PendingTroubleCodesCommand());
    }

    public void initialize(BluetoothSocket socket) throws IOException, InterruptedException {
        this.socket = socket;

        // Initialize OBD connection with standard setup
        new com.github.pires.obd.commands.protocol.EchoOffCommand().run(
            socket.getInputStream(), socket.getOutputStream());

        new com.github.pires.obd.commands.protocol.LineFeedOffCommand().run(
            socket.getInputStream(), socket.getOutputStream());

        new com.github.pires.obd.commands.protocol.TimeoutCommand(125).run(
            socket.getInputStream(), socket.getOutputStream());

        new com.github.pires.obd.commands.protocol.SelectProtocolCommand(
            com.github.pires.obd.enums.ObdProtocols.AUTO).run(
            socket.getInputStream(), socket.getOutputStream());

        isInitialized = true;
        Log.d(TAG, "Pires OBD Manager initialized");
    }

    /**
     * Execute a command and return the result
     */
    public String executeCommand(String commandName) throws IOException, InterruptedException {
        if (!isInitialized) {
            throw new IllegalStateException("OBD Manager not initialized");
        }

        ObdCommand command = commandCache.get(commandName);
        if (command == null) {
            throw new IllegalArgumentException("Unknown command: " + commandName);
        }

        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getFormattedResult();
    }

    /**
     * Get RPM value
     */
    public int getRPM() throws IOException, InterruptedException {
        RPMCommand command = new RPMCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getRPM();
    }

    /**
     * Get vehicle speed in km/h
     */
    public int getSpeed() throws IOException, InterruptedException {
        SpeedCommand command = new SpeedCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getMetricSpeed();
    }

    /**
     * Get throttle position (0-100%)
     */
    public float getThrottlePosition() throws IOException, InterruptedException {
        ThrottlePositionCommand command = new ThrottlePositionCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getPercentage();
    }

    /**
     * Get coolant temperature in Celsius
     */
    public float getCoolantTemperature() throws IOException, InterruptedException {
        EngineCoolantTemperatureCommand command = new EngineCoolantTemperatureCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getTemperature();
    }

    /**
     * Get intake manifold pressure (can be used for boost calculation)
     */
    public int getIntakePressure() throws IOException, InterruptedException {
        IntakeManifoldPressureCommand command = new IntakeManifoldPressureCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getMetricUnit();
    }

    /**
     * Get barometric pressure
     */
    public int getBarometricPressure() throws IOException, InterruptedException {
        BarometricPressureCommand command = new BarometricPressureCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getMetricUnit();
    }

    /**
     * Calculate boost pressure (Intake - Barometric)
     */
    public float getBoostPressure() throws IOException, InterruptedException {
        int intake = getIntakePressure();
        int baro = getBarometricPressure();

        // Convert to PSI and calculate boost
        float intakePsi = intake * 0.145038f;
        float baroPsi = baro * 0.145038f;

        return intakePsi - baroPsi;
    }

    /**
     * Get fuel level percentage
     */
    public float getFuelLevel() throws IOException, InterruptedException {
        FuelLevelCommand command = new FuelLevelCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getFuelLevel();
    }

    /**
     * Get trouble codes
     */
    public String getTroubleCodes() throws IOException, InterruptedException {
        TroubleCodesCommand command = new TroubleCodesCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getFormattedResult();
    }

    /**
     * Get pending trouble codes
     */
    public String getPendingTroubleCodes() throws IOException, InterruptedException {
        PendingTroubleCodesCommand command = new PendingTroubleCodesCommand();
        command.run(socket.getInputStream(), socket.getOutputStream());
        return command.getFormattedResult();
    }

    /**
     * Execute a raw OBD command
     */
    public String executeRawCommand(String command) throws IOException, InterruptedException {
        if (!isInitialized) {
            throw new IllegalStateException("OBD Manager not initialized");
        }

        com.github.pires.obd.commands.protocol.ObdRawCommand rawCommand =
            new com.github.pires.obd.commands.protocol.ObdRawCommand(command);

        rawCommand.run(socket.getInputStream(), socket.getOutputStream());
        return rawCommand.getResult();
    }

    /**
     * Check if manager is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Get available command names
     */
    public String[] getAvailableCommands() {
        return commandCache.keySet().toArray(new String[0]);
    }
}
