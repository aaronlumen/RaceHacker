package xyz.surina.proracingobd.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import xyz.surina.proracingobd.R;
import xyz.surina.proracingobd.auth.AuthManager;

/**
 * WorkshopActivity — CarHackerKit-powered deep diagnostics shell.
 *
 * Sections:
 *   1. Connection status bar (BT / USB / WiFi / Simulator)
 *   2. CAN Bus Monitor — live ISO-TP frame log
 *   3. PID Browser — SAE J1979 Mode 01/09 full table
 *   4. ECU Info — VIN, ECU name, calibration ID
 *   5. DTC Reader/Clearer
 *   6. Security Tester (read-only analysis — no active attacks)
 */
public class WorkshopActivity extends AppCompatActivity {

    private TextView tvConnectionStatus;
    private TextView tvEcuInfo;
    private RecyclerView rvCanLog;
    private RecyclerView rvPidBrowser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Auth guard — Workshop requires sign-in for full feature set
        // (allow through; some features will be gated in-screen)
        setContentView(R.layout.activity_workshop);

        tvConnectionStatus = findViewById(R.id.tv_connection_status);
        tvEcuInfo          = findViewById(R.id.tv_ecu_info);
        rvCanLog           = findViewById(R.id.rv_can_log);
        rvPidBrowser       = findViewById(R.id.rv_pid_browser);

        rvCanLog.setLayoutManager(new LinearLayoutManager(this));
        rvPidBrowser.setLayoutManager(new LinearLayoutManager(this));

        setupConnectionBar();
        setupButtons();
    }

    private void setupConnectionBar() {
        // Will be wired to ObdConnectionService once the Kotlin bridge is in place
        tvConnectionStatus.setText("DISCONNECTED — tap a tile to connect");
    }

    private void setupButtons() {
        // BT tile
        findViewById(R.id.tile_connect_bt).setOnClickListener(v ->
                tvConnectionStatus.setText("Connecting via Bluetooth..."));

        // USB tile
        findViewById(R.id.tile_connect_usb).setOnClickListener(v ->
                tvConnectionStatus.setText("Connecting via USB Serial..."));

        // WiFi tile
        findViewById(R.id.tile_connect_wifi).setOnClickListener(v ->
                tvConnectionStatus.setText("Connecting via WiFi (192.168.0.10:35000)..."));

        // Simulator tile
        findViewById(R.id.tile_connect_sim).setOnClickListener(v -> {
            tvConnectionStatus.setText("SIMULATOR MODE — no hardware needed");
            tvEcuInfo.setText("ECU: SIMULATED  |  VIN: SIM00000000000000  |  Cal: DEMO-1.0");
        });

        // Launch the standalone CarHackerKit app (installed as a separate APK).
        // Try the release package id first, then the debug-suffixed one.
        findViewById(R.id.btn_open_carhackerkit).setOnClickListener(v -> {
            Intent launch = null;
            for (String pkg : new String[]{"com.carhacker.kit", "com.carhacker.kit.debug"}) {
                launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) break;
            }
            if (launch != null) {
                startActivity(launch);
            } else {
                tvConnectionStatus.setText("CarHackerKit is not installed on this device");
            }
        });

        // Back to mode select
        findViewById(R.id.btn_workshop_back).setOnClickListener(v -> finish());
    }
}
