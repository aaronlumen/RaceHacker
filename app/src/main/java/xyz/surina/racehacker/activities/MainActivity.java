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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.auth.AuthManager;
import xyz.surina.racehacker.fragments.*;
import xyz.surina.racehacker.vehicles.VehicleProfile;
import xyz.surina.racehacker.services.ObdConnectionService;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;

    private BottomNavigationView bottomNav;
    private VehicleProfile currentVehicleProfile;
    private ObdConnectionService obdService;
    private BluetoothAdapter bluetoothAdapter;

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

        setupBluetoothPermissions();
        setupBottomNavigation();

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

    public VehicleProfile getCurrentVehicleProfile() {
        return currentVehicleProfile;
    }

    public void setCurrentVehicleProfile(VehicleProfile profile) {
        this.currentVehicleProfile = profile;
        obdService = new ObdConnectionService(profile);
    }

    public ObdConnectionService getObdService() {
        return obdService;
    }

    public void connectToDevice(BluetoothDevice device) {
        obdService.setConnectionListener(new ObdConnectionService.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "Connected to OBD adapter",
                            Toast.LENGTH_SHORT).show()
                );
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
            obdService.disconnect();
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
