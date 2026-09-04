package xyz.surina.racehacker.activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.auth.AuthManager;
import xyz.surina.racehacker.fragments.*;
import xyz.surina.racehacker.models.GaugeData;
import xyz.surina.racehacker.utils.DataLogger;
import xyz.surina.racehacker.vehicles.VehicleProfile;
import xyz.surina.racehacker.services.ObdConnectionService;
import xyz.surina.racehacker.voice.Ace;
import xyz.surina.racehacker.voice.ActionRegistry;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;

    // Indices into liveGauges — must match setupLiveGauges() order
    private static final int IDX_RPM       = 0;
    private static final int IDX_SPEED     = 1;
    private static final int IDX_BOOST     = 2;
    private static final int IDX_AFR       = 3;
    private static final int IDX_OIL_TEMP  = 4;
    private static final int IDX_COOLANT   = 5;
    private static final int IDX_INTAKE    = 6;
    private static final int IDX_OIL_PRESS = 7;
    private static final int IDX_FUEL_PRES = 8;
    private static final int IDX_TIMING    = 9;
    private static final int IDX_THROTTLE  = 10;
    private static final int IDX_BATTERY   = 11;
    private static final int IDX_MAP       = 12;
    private static final int IDX_MAF       = 13;

    private BottomNavigationView bottomNav;
    private VehicleProfile currentVehicleProfile;
    private ObdConnectionService obdService;
    private BluetoothAdapter bluetoothAdapter;

    // Gauge data lives here (not in DashboardFragment) so it keeps updating no
    // matter which tab is showing, and so Ace can narrate/answer questions
    // about it from any screen.
    private final List<GaugeData> liveGauges = new ArrayList<>();
    private Runnable gaugeUpdateListener;

    // Owned here for the same reason as liveGauges — logging shouldn't stop
    // just because the user switched off the Dashboard tab. Was previously
    // unused dead code (no start/stop control existed anywhere in the app).
    private DataLogger dataLogger;
    private Runnable loggingStateListener;

    // Ace — the app's voice copilot, owned once here so it persists across tab
    // switches instead of being re-created (and re-initializing TTS) per fragment.
    private Ace ace;
    private ActionRegistry actionRegistry;
    private FloatingActionButton aceFab;
    private final ActivityResultLauncher<String> micPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    ace.startListening();
                } else {
                    Toast.makeText(this, "Microphone permission is needed to talk to Ace.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Guard: redirect to login if the user signed out from another screen
        // (normal flow: LoginActivity already guards the entrance, but this
        //  catches sign-outs triggered from within the app, e.g. Settings)
        if (!AuthManager.getInstance(this).isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Set default vehicle profile
        currentVehicleProfile = VehicleProfile.createBmwN54Profile();
        obdService = new ObdConnectionService(currentVehicleProfile);
        dataLogger = new DataLogger(this);

        setupLiveGauges();
        setupBluetoothPermissions();
        setupBottomNavigation();
        setupActionRegistry();
        setupAce();

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(new DashboardFragment());
        }
    }

    private void setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnNavigationItemSelectedListener(navListener);
    }

    private BottomNavigationView.OnNavigationItemSelectedListener navListener =
            new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) {
                selectedFragment = new DashboardFragment();
            } else if (itemId == R.id.nav_diagnostics) {
                selectedFragment = new DiagnosticsFragment();
            } else if (itemId == R.id.nav_ecu_flash) {
                selectedFragment = new EcuFlashFragment();
            } else if (itemId == R.id.nav_tuning) {
                selectedFragment = new TuningFragment();
            } else if (itemId == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }
            return false;
        }
    };

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    private void setupBluetoothPermissions() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        }, REQUEST_BLUETOOTH_PERMISSIONS);
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        }, REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    // ─── Live gauge data ─────────────────────────────────────────────────────
    // Owned here (not by DashboardFragment) so it stays live regardless of tab,
    // and so Ace can narrate/answer gauge questions from any screen.

    private void setupLiveGauges() {
        liveGauges.clear();
        liveGauges.add(new GaugeData("RPM",        "RPM", GaugeData.GaugeType.RPM));
        liveGauges.add(new GaugeData("Speed",       "MPH", GaugeData.GaugeType.SPEED));
        liveGauges.add(new GaugeData("Boost",       "PSI", GaugeData.GaugeType.BOOST));
        liveGauges.add(new GaugeData("AFR",         ":1",  GaugeData.GaugeType.AFR));
        liveGauges.add(new GaugeData("Oil Temp",    "°F",  GaugeData.GaugeType.OIL_TEMP));
        liveGauges.add(new GaugeData("Coolant",     "°F",  GaugeData.GaugeType.COOLANT_TEMP));
        liveGauges.add(new GaugeData("Intake Temp", "°F",  GaugeData.GaugeType.INTAKE_TEMP));
        liveGauges.add(new GaugeData("Oil Press",   "PSI", GaugeData.GaugeType.OIL_PRESSURE));
        liveGauges.add(new GaugeData("Fuel Press",  "PSI", GaugeData.GaugeType.FUEL_PRESSURE));
        liveGauges.add(new GaugeData("Timing",      "°",   GaugeData.GaugeType.TIMING));
        liveGauges.add(new GaugeData("Throttle",    "%",   GaugeData.GaugeType.THROTTLE_POSITION));
        liveGauges.add(new GaugeData("Battery",     "V",   GaugeData.GaugeType.BATTERY_VOLTAGE));
        // MAP/MAF — SENSOR_DIAGNOSTICS.md's suggested build order #1: standard
        // Mode 01 PIDs, single values, useful on their own with no correlation
        // logic needed yet.
        liveGauges.add(new GaugeData("Manifold Press", "kPa", GaugeData.GaugeType.MAP));
        liveGauges.add(new GaugeData("Air Flow",        "g/s", GaugeData.GaugeType.MAF));
        for (GaugeData g : liveGauges) g.setCurrentValue(Float.NaN);

        obdService.setDataListener((rpm, speedMph, coolantF, intakeF,
                                     throttle, boostPsi, battery,
                                     timing, fuelLevel, afr,
                                     mapKpa, mafGps) -> {
            liveGauges.get(IDX_RPM).setCurrentValue(rpm);
            liveGauges.get(IDX_SPEED).setCurrentValue(speedMph);
            liveGauges.get(IDX_BOOST).setCurrentValue(boostPsi);
            liveGauges.get(IDX_AFR).setCurrentValue(afr);
            // Oil temp & oil pressure are not standard OBD2 PIDs on all cars;
            // leave them at NaN until a real value arrives.
            liveGauges.get(IDX_COOLANT).setCurrentValue(coolantF);
            liveGauges.get(IDX_INTAKE).setCurrentValue(intakeF);
            liveGauges.get(IDX_TIMING).setCurrentValue(timing);
            liveGauges.get(IDX_THROTTLE).setCurrentValue(throttle);
            liveGauges.get(IDX_BATTERY).setCurrentValue(battery);
            // Fuel level mapped to FUEL_PRESSURE slot for display
            liveGauges.get(IDX_FUEL_PRES).setCurrentValue(fuelLevel);
            liveGauges.get(IDX_MAP).setCurrentValue(mapKpa);
            liveGauges.get(IDX_MAF).setCurrentValue(mafGps);

            if (dataLogger != null && dataLogger.isLogging()) {
                for (GaugeData g : liveGauges) {
                    if (g.hasData()) dataLogger.logData(g);
                }
            }

            if (gaugeUpdateListener != null) gaugeUpdateListener.run();
            // Debounced — only actually speaks up when there's something worth
            // saying, never on every poll tick. See Ace.checkForProactiveAlert().
            if (ace != null) ace.checkForProactiveAlert(liveGauges);
        });
    }

    public List<GaugeData> getLiveGauges() {
        return liveGauges;
    }

    /** Only DashboardFragment (the currently-visible screen, if any) should set this. */
    public void setGaugeUpdateListener(Runnable listener) {
        this.gaugeUpdateListener = listener;
    }

    // ─── Data logging ────────────────────────────────────────────────────────

    public DataLogger getDataLogger() {
        return dataLogger;
    }

    public boolean startDataLogging() {
        boolean started = dataLogger.startLogging();
        if (loggingStateListener != null) loggingStateListener.run();
        return started;
    }

    public void stopDataLogging() {
        dataLogger.stopLogging();
        if (loggingStateListener != null) loggingStateListener.run();
    }

    /** Only the Settings screen (the currently-visible screen, if any) should set this. */
    public void setLoggingStateListener(Runnable listener) {
        this.loggingStateListener = listener;
    }

    // ─── Ace / voice commands ────────────────────────────────────────────────

    private void setupActionRegistry() {
        actionRegistry = new ActionRegistry();
        actionRegistry.registerGlobal("Opening Dashboard.",
                () -> bottomNav.setSelectedItemId(R.id.nav_dashboard),
                "dashboard", "home screen", "gauges");
        actionRegistry.registerGlobal("Opening Diagnostics.",
                () -> bottomNav.setSelectedItemId(R.id.nav_diagnostics),
                "diagnostics", "diagnostic screen", "codes screen");
        actionRegistry.registerGlobal("Opening E C U Flash.",
                () -> bottomNav.setSelectedItemId(R.id.nav_ecu_flash),
                "ecu flash", "flash screen", "flashing screen");
        actionRegistry.registerGlobal("Opening Tuning.",
                () -> bottomNav.setSelectedItemId(R.id.nav_tuning),
                "tuning", "tune screen");
        actionRegistry.registerGlobal("Opening Settings.",
                () -> bottomNav.setSelectedItemId(R.id.nav_settings),
                "settings", "connection screen");
        actionRegistry.registerGlobal("Starting data logging.",
                this::startDataLogging,
                "start logging", "log data", "start data logging", "log to c s v");
        actionRegistry.registerGlobal("Stopping data logging.",
                this::stopDataLogging,
                "stop logging", "stop data logging");
    }

    private void setupAce() {
        ace = new Ace(this, actionRegistry);
        ace.setListener(new Ace.Listener() {
            @Override
            public void onSpeechRecognized(String text) {
                ace.handleCommand(text, liveGauges);
            }

            @Override
            public void onSpeechError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
        ace.init();

        aceFab = findViewById(R.id.ace_fab);
        // Tap: speak a status narration of the current gauges.
        aceFab.setOnClickListener(v -> ace.speakStatus(liveGauges));
        // Long-press: listen for a spoken command — the only way Ace starts listening.
        aceFab.setOnLongClickListener(v -> {
            if (ace.hasMicPermission()) {
                ace.startListening();
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
            return true;
        });
    }

    public Ace getAce() {
        return ace;
    }

    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    public VehicleProfile getCurrentVehicleProfile() {
        return currentVehicleProfile;
    }

    public void setCurrentVehicleProfile(VehicleProfile profile) {
        this.currentVehicleProfile = profile;
        obdService = new ObdConnectionService(profile);
        setupLiveGauges(); // re-attach the data listener to the new service, reset to "no data"
    }

    public ObdConnectionService getObdService() {
        return obdService;
    }

    public void connectToDevice(BluetoothDevice device) {
        obdService.setConnectionListener(new ObdConnectionService.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Connected to OBD adapter",
                            Toast.LENGTH_SHORT).show();
                    // Poll continuously from here, not tied to Dashboard being visible,
                    // so gauge data (and Ace's narration of it) stays live on any tab.
                    obdService.startPidPolling();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Disconnected from OBD adapter",
                            Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Connection error: " + error,
                            Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onDataReceived(String data) {
                // Handle incoming data
            }
        });

        obdService.connect(device);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (obdService != null && obdService.isConnected()) {
            // A forgotten OBD adapter left plugged into the car can slowly
            // drain the battery — worth a nudge on the way out. Uses the
            // application context so the toast still posts even though this
            // activity is finishing (FEATURE_IDEAS.md: "remove-adapter reminder").
            Toast.makeText(getApplicationContext(),
                    "Don't forget to unplug your OBD adapter — leaving it in can drain your battery.",
                    Toast.LENGTH_LONG).show();
            obdService.stopPidPolling();
            obdService.disconnect();
        }
        if (dataLogger != null && dataLogger.isLogging()) {
            dataLogger.stopLogging();
        }
        if (ace != null) {
            ace.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permissions granted",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth permissions required for OBD connection",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
