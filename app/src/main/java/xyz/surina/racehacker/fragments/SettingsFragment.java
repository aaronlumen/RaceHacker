package xyz.surina.racehacker.fragments;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.activities.MainActivity;
import xyz.surina.racehacker.adapters.BluetoothDeviceAdapter;
import xyz.surina.racehacker.models.GaugeData;
import xyz.surina.racehacker.models.GaugeThresholdPrefs;
import xyz.surina.racehacker.network.NetworkDiscoveryManager;
import xyz.surina.racehacker.vehicles.VehicleProfile;
import xyz.surina.racehacker.voice.ActionRegistry;
import xyz.surina.racehacker.voice.VocabularyLevel;

public class SettingsFragment extends Fragment {
    private Spinner vehicleTypeSpinner;
    private Spinner cableTypeSpinner;
    private RecyclerView bluetoothDevicesList;
    private Button scanDevicesButton;
    private Button connectButton;
    private Button autoDetectButton;
    private TextView connectionStatusText;
    private EditText deviceSearchField;
    private Switch aceVocabularySwitch;
    private Switch aceMuteSwitch;
    private android.widget.SeekBar acePitchSeekBar;
    private Button aceTestSpeechButton;
    private Switch dataLoggingSwitch;
    private TextView dataLoggingStatusText;
    private Spinner thresholdGaugeSpinner;
    private EditText thresholdWarningInput;
    private EditText thresholdCriticalInput;
    private TextView thresholdStatusText;
    private Button thresholdSaveButton;
    private Button thresholdResetButton;
    private Switch networkBroadcastSwitch;
    private Button findNearbyDevicesButton;
    private TextView networkStatusText;

    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private List<String> allDeviceNames = new ArrayList<>();
    private BluetoothDeviceAdapter devicesAdapter;
    private BluetoothDevice selectedDevice;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        vehicleTypeSpinner = view.findViewById(R.id.vehicle_type_spinner);
        cableTypeSpinner = view.findViewById(R.id.cable_type_spinner);
        bluetoothDevicesList = view.findViewById(R.id.bluetooth_devices_list);
        scanDevicesButton = view.findViewById(R.id.scan_devices_button);
        connectButton = view.findViewById(R.id.connect_button);
        connectionStatusText = view.findViewById(R.id.connection_status_text);
        deviceSearchField = view.findViewById(R.id.device_search_field);
        aceVocabularySwitch = view.findViewById(R.id.ace_vocabulary_switch);
        aceMuteSwitch = view.findViewById(R.id.ace_mute_switch);
        acePitchSeekBar = view.findViewById(R.id.ace_pitch_seekbar);
        aceTestSpeechButton = view.findViewById(R.id.ace_test_speech_button);
        dataLoggingSwitch = view.findViewById(R.id.data_logging_switch);
        dataLoggingStatusText = view.findViewById(R.id.data_logging_status_text);
        thresholdGaugeSpinner = view.findViewById(R.id.threshold_gauge_spinner);
        thresholdWarningInput = view.findViewById(R.id.threshold_warning_input);
        thresholdCriticalInput = view.findViewById(R.id.threshold_critical_input);
        thresholdStatusText = view.findViewById(R.id.threshold_status_text);
        thresholdSaveButton = view.findViewById(R.id.threshold_save_button);
        thresholdResetButton = view.findViewById(R.id.threshold_reset_button);
        networkBroadcastSwitch = view.findViewById(R.id.network_broadcast_switch);
        findNearbyDevicesButton = view.findViewById(R.id.find_nearby_devices_button);
        networkStatusText = view.findViewById(R.id.network_status_text);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        setupVehicleTypeSpinner();
        setupCableTypeSpinner();
        setupBluetoothDevicesList();
        setupDeviceSearch();
        setupButtons();
        setupAceVocabularySwitch();
        setupAceSpeechControls();
        setupDataLoggingSwitch();
        setupThresholdEditor();
        setupNetworkRelay();
        updateConnectionStatus();

        return view;
    }

    // ─── Network gauge relay ──────────────────────────────────────────────────
    // One device broadcasts its live gauge data over the local WiFi network,
    // another mirrors it — see xyz.surina.racehacker.network and
    // MainActivity's NetworkMode. This screen owns the picker UI;
    // MainActivity owns the actual server/client lifecycle.

    private void setupNetworkRelay() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null) return;

        networkBroadcastSwitch.setChecked(main.getNetworkMode() == MainActivity.NetworkMode.BROADCASTING);
        networkBroadcastSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                main.startBroadcasting();
                Toast.makeText(getContext(), "Broadcasting gauge data on the network", Toast.LENGTH_SHORT).show();
            } else {
                main.stopBroadcasting();
            }
            updateNetworkStatus();
        });

        findNearbyDevicesButton.setOnClickListener(v -> findNearbyDevices());
        updateNetworkStatus();
    }

    private void findNearbyDevices() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || getContext() == null) return;

        List<NsdServiceInfo> found = new ArrayList<>();
        Toast.makeText(getContext(), "Scanning for nearby devices...", Toast.LENGTH_SHORT).show();

        main.findNearbyDevices(devices -> {
            found.clear();
            found.addAll(devices);
        });

        // A live-updating picker needs a persistent discovery session; a fixed
        // scan window keeps this simple and matches how Bluetooth scanning
        // already works elsewhere on this screen (one tap, one result set).
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            main.stopFindingNearbyDevices();
            if (getContext() == null) return;

            if (found.isEmpty()) {
                Toast.makeText(getContext(), "No RaceHacker devices found on this network", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] names = new String[found.size()];
            for (int i = 0; i < found.size(); i++) names[i] = found.get(i).getServiceName();

            new AlertDialog.Builder(getContext())
                    .setTitle("Nearby Devices")
                    .setItems(names, (dialog, which) -> {
                        main.connectToMirror(found.get(which));
                        updateNetworkStatus();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }, 4000);
    }

    private void updateNetworkStatus() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || networkStatusText == null) return;

        switch (main.getNetworkMode()) {
            case BROADCASTING:
                networkStatusText.setText("Broadcasting this device's gauge data");
                break;
            case MIRRORING:
                networkStatusText.setText("Mirroring gauge data from another device");
                break;
            default:
                networkStatusText.setText("Not broadcasting, not mirroring");
        }
    }

    // ─── Alarm thresholds ────────────────────────────────────────────────────
    // FEATURE_IDEAS.md: "User-configurable alarm thresholds — GaugeData.
    // setDefaultRanges() is hardcoded; Torque exposes warning/critical
    // thresholds as user-editable." One editor for whichever gauge is picked
    // from the spinner, rather than a row per gauge — the live gauge list
    // already has 17 entries and most people will only ever want to fix one
    // or two that don't fit their car.

    private void setupThresholdEditor() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || thresholdGaugeSpinner == null) return;
        List<GaugeData> gauges = main.getLiveGauges();

        List<String> names = new ArrayList<>();
        for (GaugeData g : gauges) names.add(g.getName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        thresholdGaugeSpinner.setAdapter(adapter);

        thresholdGaugeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < gauges.size()) populateThresholdFields(gauges.get(position));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        if (!gauges.isEmpty()) populateThresholdFields(gauges.get(0));

        thresholdSaveButton.setOnClickListener(v -> {
            int pos = thresholdGaugeSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= gauges.size() || getContext() == null) return;
            GaugeData gauge = gauges.get(pos);
            try {
                float warning = Float.parseFloat(thresholdWarningInput.getText().toString());
                float critical = Float.parseFloat(thresholdCriticalInput.getText().toString());
                gauge.setWarningThreshold(warning);
                gauge.setCriticalThreshold(critical);
                GaugeThresholdPrefs.setOverride(getContext(), gauge.getType(), warning, critical);
                populateThresholdFields(gauge);
                Toast.makeText(getContext(), "Saved threshold for " + gauge.getName(), Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Enter valid numbers for both thresholds", Toast.LENGTH_SHORT).show();
            }
        });

        thresholdResetButton.setOnClickListener(v -> {
            int pos = thresholdGaugeSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= gauges.size() || getContext() == null) return;
            GaugeData gauge = gauges.get(pos);
            GaugeThresholdPrefs.clearOverride(getContext(), gauge.getType());
            // GaugeData has no "reset to default" of its own — build a scratch
            // instance of the same type just to read its built-in numbers back.
            GaugeData defaults = new GaugeData(gauge.getName(), gauge.getUnit(), gauge.getType());
            gauge.setWarningThreshold(defaults.getWarningThreshold());
            gauge.setCriticalThreshold(defaults.getCriticalThreshold());
            populateThresholdFields(gauge);
            Toast.makeText(getContext(), "Reset " + gauge.getName() + " to default", Toast.LENGTH_SHORT).show();
        });
    }

    private void populateThresholdFields(GaugeData gauge) {
        if (thresholdWarningInput == null || getContext() == null) return;
        thresholdWarningInput.setText(formatThreshold(gauge.getWarningThreshold()));
        thresholdCriticalInput.setText(formatThreshold(gauge.getCriticalThreshold()));
        boolean custom = GaugeThresholdPrefs.hasOverride(getContext(), gauge.getType());
        thresholdStatusText.setText(custom ? "Custom override active" : "Using built-in default");
    }

    /** Avoids "6500.0" for whole-number thresholds while keeping decimals for e.g. AFR's 11.5. */
    private String formatThreshold(float v) {
        return (v == Math.round(v)) ? String.valueOf(Math.round(v)) : String.valueOf(v);
    }

    private void setupAceSpeechControls() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || main.getAce() == null) return;

        aceMuteSwitch.setChecked(main.getAce().isMuted());
        aceMuteSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                main.getAce().setMuted(isChecked));

        // SeekBar 0-150 maps to pitch 0.5-2.0 (progress 50 = pitch 1.0, normal).
        float currentPitch = main.getAce().getPitch();
        acePitchSeekBar.setProgress(Math.round((currentPitch - 0.5f) * 100));
        acePitchSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float pitch = 0.5f + (progress / 100f);
                    main.getAce().setPitch(pitch);
                }
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                main.getAce().speakForTest("This is what Ace sounds like.");
            }
        });

        aceTestSpeechButton.setOnClickListener(v ->
                main.getAce().speakForTest("This is what Ace sounds like."));
    }

    private void setupDataLoggingSwitch() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || main.getDataLogger() == null) return;

        dataLoggingSwitch.setChecked(main.getDataLogger().isLogging());
        updateDataLoggingStatus();
        dataLoggingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                main.startDataLogging();
            } else {
                main.stopDataLogging();
            }
            updateDataLoggingStatus();
        });
    }

    private void updateDataLoggingStatus() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || main.getDataLogger() == null || dataLoggingStatusText == null) return;

        if (main.getDataLogger().isLogging() && main.getDataLogger().getCurrentLogFile() != null) {
            dataLoggingStatusText.setText("Logging to " + main.getDataLogger().getCurrentLogFile().getName());
        } else {
            dataLoggingStatusText.setText("Not logging");
        }
    }

    private void setupAceVocabularySwitch() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null || main.getAce() == null) return;

        aceVocabularySwitch.setChecked(main.getAce().getVocabularyLevel() == VocabularyLevel.ENTHUSIAST);
        aceVocabularySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                main.getAce().setVocabularyLevel(isChecked ? VocabularyLevel.ENTHUSIAST : VocabularyLevel.BASIC));
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity main = (MainActivity) getActivity();
        if (main != null) {
            main.getActionRegistry().setScreenActions(Arrays.asList(
                    ActionRegistry.action("Scanning for devices.", this::scanForDevices,
                            "scan for devices", "scan", "find devices"),
                    ActionRegistry.action("Reading your VIN.", this::autoDetectVehicle,
                            "auto detect vehicle", "auto-detect vehicle", "detect vin", "find my vehicle")
            ));
            main.getActionRegistry().setScreenPrefixActions(Arrays.asList(
                    ActionRegistry.prefixAction(this::connectByVoice, "connect to ", "connect ")
            ));
            // Keep the switch/status text in sync if logging is started/stopped by
            // voice (global command, works from any screen) while Settings is up.
            main.setLoggingStateListener(() -> {
                if (dataLoggingSwitch != null && main.getDataLogger() != null) {
                    dataLoggingSwitch.setChecked(main.getDataLogger().isLogging());
                }
                updateDataLoggingStatus();
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity main = (MainActivity) getActivity();
        if (main != null) {
            main.getActionRegistry().clearScreenActions();
            main.setLoggingStateListener(null);
        }
    }

    private void setupVehicleTypeSpinner() {
        List<String> vehicleTypes = new ArrayList<>();
        for (VehicleProfile.VehicleType type : VehicleProfile.VehicleType.values()) {
            vehicleTypes.add(type.getDisplayName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, vehicleTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleTypeSpinner.setAdapter(adapter);

        vehicleTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateVehicleProfile();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupCableTypeSpinner() {
        List<String> cableTypes = new ArrayList<>();
        for (VehicleProfile.CableType type : VehicleProfile.CableType.values()) {
            cableTypes.add(type.getDisplayName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, cableTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cableTypeSpinner.setAdapter(adapter);
    }

    private void setupBluetoothDevicesList() {
        devicesAdapter = new BluetoothDeviceAdapter(getContext(), device -> {
            selectedDevice = device;
            devicesAdapter.setSelectedDevice(device);
            connectButton.setEnabled(true);
            Toast.makeText(getContext(), "Selected: " + device.getName(), Toast.LENGTH_SHORT).show();
        });
        bluetoothDevicesList.setLayoutManager(new LinearLayoutManager(getContext()));
        bluetoothDevicesList.setAdapter(devicesAdapter);
        bluetoothDevicesList.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));
        // The outer screen is a ScrollView — let it handle all scrolling through the
        // (now unbounded-height) device list rather than this RecyclerView trying to
        // scroll internally too.
        bluetoothDevicesList.setNestedScrollingEnabled(false);
    }

    private void setupDeviceSearch() {
        deviceSearchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDevices(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterDevices(String query) {
        List<BluetoothDevice> filtered = new ArrayList<>();
        String lower = query.toLowerCase().trim();
        for (int i = 0; i < deviceList.size(); i++) {
            String name = allDeviceNames.get(i).toLowerCase();
            if (lower.isEmpty() || name.contains(lower)) {
                filtered.add(deviceList.get(i));
            }
        }
        devicesAdapter.setDevices(filtered);
    }

    private void setupButtons() {
        scanDevicesButton.setOnClickListener(v -> scanForDevices());
        connectButton.setOnClickListener(v -> connectToDevice());
        connectButton.setEnabled(false);

        autoDetectButton = getView() == null ? null :
                getView().findViewById(R.id.auto_detect_button);
        if (autoDetectButton != null) {
            autoDetectButton.setOnClickListener(v -> autoDetectVehicle());
        }
    }

    private void autoDetectVehicle() {
        MainActivity main = (MainActivity) getActivity();
        if (main == null) return;

        if (!main.getObdService().isConnected()) {
            Toast.makeText(getContext(),
                    "Connect to OBD adapter first, then tap Auto-Detect",
                    Toast.LENGTH_LONG).show();
            return;
        }

        autoDetectButton.setEnabled(false);
        autoDetectButton.setText("Reading VIN...");

        new Thread(() -> {
            String vin = main.getObdService().readVin();
            VehicleProfile detected = vin.isEmpty()
                    ? VehicleProfile.createGenericProfile()
                    : VehicleProfile.detectFromVin(vin);

            // Try plugin server if still generic
            if (!vin.isEmpty() &&
                    detected.getType() == VehicleProfile.VehicleType.GENERIC_OBD2) {
                detected = VehicleProfile.fetchPluginProfile(vin);
            }

            final VehicleProfile finalProfile = detected;
            final String finalVin = vin;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    main.setCurrentVehicleProfile(finalProfile);
                    autoDetectButton.setEnabled(true);
                    autoDetectButton.setText("AUTO-DETECT VEHICLE (VIN)");
                    String msg = finalVin.isEmpty()
                            ? "Could not read VIN — select manually"
                            : "Detected: " + finalProfile.getName() + "\nVIN: " + finalVin;
                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void scanForDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(getContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            return;
        }

        deviceList.clear();
        allDeviceNames.clear();
        selectedDevice = null;
        devicesAdapter.setSelectedDevice(null);
        connectButton.setEnabled(false);

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device);
                String displayName = (device.getName() != null ? device.getName() : "Unknown")
                        + "\n" + device.getAddress();
                allDeviceNames.add(displayName);
            }
            // Apply any existing filter query
            filterDevices(deviceSearchField.getText().toString());
            Toast.makeText(getContext(), "Found " + deviceList.size() + " paired devices",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "No paired Bluetooth devices found",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice() {
        if (selectedDevice == null) {
            Toast.makeText(getContext(), "Please select a device first",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        performConnect(selectedDevice);
    }

    /** Shared by the Connect button and connectByVoice() so both go through the same flow. */
    private void performConnect(BluetoothDevice device) {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;
        mainActivity.connectToDevice(device);
        connectButton.setEnabled(false);
        connectButton.setText("Connecting...");

        new android.os.Handler().postDelayed(() -> {
            if (connectButton != null) {
                connectButton.setEnabled(true);
                connectButton.setText("Connect");
                updateConnectionStatus();
            }
        }, 3000);
    }

    /**
     * Voice-driven device connect ("connect to BT12", "connect to OBDII") — matches
     * the spoken remainder against the currently scanned device list's names/
     * addresses (substring, case-insensitive) and connects on a match.
     *
     * If more than one paired device shares the same/similar name (real case:
     * two adapters both named "OBDII" with different addresses), this
     * deliberately does NOT guess and connect to the first one — asks the
     * user to pick from the on-screen list instead, since dictating enough of
     * a MAC address by voice to disambiguate isn't realistic.
     *
     * @return what Ace should speak, or null to defer (empty query — e.g. just
     *         "connect" with nothing after — isn't this feature's to handle).
     */
    @SuppressWarnings("MissingPermission") // BLUETOOTH_CONNECT already requested by MainActivity
    private String connectByVoice(String query) {
        if (query.isEmpty()) return null;
        if (deviceList.isEmpty()) {
            return "I don't have any devices to connect to yet — say \"scan for devices\" first.";
        }

        List<BluetoothDevice> matches = new ArrayList<>();
        for (BluetoothDevice device : deviceList) {
            String name = device.getName();
            String haystack = ((name != null ? name : "") + " " + device.getAddress()).toLowerCase(Locale.US);
            if (haystack.contains(query)) matches.add(device);
        }

        if (matches.isEmpty()) {
            return "I couldn't find a paired device matching \"" + query + "\".";
        }
        if (matches.size() > 1) {
            return matches.size() + " paired devices match \"" + query + "\" — "
                    + "tap the one you want from the list on screen instead of by voice.";
        }

        BluetoothDevice device = matches.get(0);
        selectedDevice = device;
        devicesAdapter.setSelectedDevice(device);
        performConnect(device);
        String name = device.getName();
        return "Connecting to " + (name != null ? name : device.getAddress()) + ".";
    }

    private void updateVehicleProfile() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        VehicleProfile.VehicleType selectedType =
                VehicleProfile.VehicleType.values()[vehicleTypeSpinner.getSelectedItemPosition()];
        VehicleProfile.CableType selectedCable =
                VehicleProfile.CableType.values()[cableTypeSpinner.getSelectedItemPosition()];

        VehicleProfile newProfile;
        switch (selectedType) {
            // GM
            case GMC_ACADIA:         newProfile = VehicleProfile.createGmcAcadiaProfile();        break;
            case GMC_SIERRA:         newProfile = VehicleProfile.createGmcSierraProfile();        break;
            case CHEVY_SILVERADO:    newProfile = VehicleProfile.createChevySilveradoProfile();   break;
            case CHEVY_DURAMAX:      newProfile = VehicleProfile.createChevyDuramaxProfile();     break;
            case CHEVY_CAMARO:       newProfile = VehicleProfile.createChevyCamaroProfile();      break;
            // Ford
            case FORD_F150:          newProfile = VehicleProfile.createFordF150Profile();         break;
            case FORD_POWERSTROKE:   newProfile = VehicleProfile.createFordPowerstrokeProfile();  break;
            case FORD_MUSTANG:       newProfile = VehicleProfile.createFordMustangProfile();      break;
            case FORD_RAPTOR:        newProfile = VehicleProfile.createFordRaptorProfile();       break;
            // Dodge / Jeep
            case DODGE_HEMI:         newProfile = VehicleProfile.createDodgeHemiProfile();        break;
            case DODGE_RAM_1500:     newProfile = VehicleProfile.createDodgeRam1500Profile();     break;
            case DODGE_CUMMINS:      newProfile = VehicleProfile.createDodgeCumminsProfile();     break;
            case DODGE_CHALLENGER:   newProfile = VehicleProfile.createDodgeChallengerProfile();  break;
            case DODGE_CHARGER:      newProfile = VehicleProfile.createDodgeChargerProfile();     break;
            case JEEP_CHEROKEE:      newProfile = VehicleProfile.createJeepCherokeeProfile();     break;
            case JEEP_GRAND_CHEROKEE: newProfile = VehicleProfile.createJeepGrandCherokeeProfile(); break;
            case JEEP_WRANGLER:      newProfile = VehicleProfile.createJeepWranglerProfile();     break;
            // Toyota / Lexus
            case TOYOTA_TACOMA:      newProfile = VehicleProfile.createToyotaTacomaProfile();     break;
            case TOYOTA_TUNDRA:      newProfile = VehicleProfile.createToyotaTundraProfile();     break;
            case TOYOTA_SUPRA:       newProfile = VehicleProfile.createToyotaSupraProfile();      break;
            case LEXUS_IS:           newProfile = VehicleProfile.createLexusIsProfile();          break;
            case LEXUS_RC_F:         newProfile = VehicleProfile.createLexusRcFProfile();         break;
            // Nissan / Infiniti
            case NISSAN_GTR:         newProfile = VehicleProfile.createNissanGtrProfile();        break;
            case NISSAN_370Z:        newProfile = VehicleProfile.createNissan370zProfile();       break;
            case NISSAN_TITAN:       newProfile = VehicleProfile.createNissanTitanProfile();      break;
            case INFINITI_Q50:       newProfile = VehicleProfile.createInfinitiQ50Profile();      break;
            case INFINITI_Q60:       newProfile = VehicleProfile.createInfinitiQ60Profile();      break;
            // Honda / Acura
            case HONDA_CIVIC_SI:     newProfile = VehicleProfile.createHondaCivicSiProfile();     break;
            case HONDA_ACCORD:       newProfile = VehicleProfile.createHondaAccordProfile();      break;
            case ACURA_TLX:          newProfile = VehicleProfile.createAcuraTlxProfile();         break;
            case ACURA_NSX:          newProfile = VehicleProfile.createAcuraNsxProfile();         break;
            case ACURA_INTEGRA:      newProfile = VehicleProfile.createAcuraIntegraProfile();     break;
            // Mitsubishi
            case MITSUBISHI_EVO:     newProfile = VehicleProfile.createMitsubishiEvoProfile();    break;
            case MITSUBISHI_ECLIPSE: newProfile = VehicleProfile.createMitsubishiEclipseProfile(); break;
            // BMW
            case BMW_N54:            newProfile = VehicleProfile.createBmwN54Profile();           break;
            case BMW_N55:            newProfile = VehicleProfile.createBmwN55Profile();           break;
            case BMW_S58:            newProfile = VehicleProfile.createBmwS58Profile();           break;
            // VW
            case VW_VAG:             newProfile = VehicleProfile.createVwVagProfile();            break;
            // Subaru
            case SUBARU_WRX:         newProfile = VehicleProfile.createSubaruWrxProfile();        break;
            // Legacy / misc
            case MILWAUKEE_117:      newProfile = VehicleProfile.createMilwaukee117Profile();     break;
            case BULLYDOG_GENERIC:   newProfile = VehicleProfile.createBullyDogProfile();         break;
            default:                 newProfile = VehicleProfile.createGenericProfile();           break;
        }

        newProfile.setCableType(selectedCable);
        mainActivity.setCurrentVehicleProfile(newProfile);
        Toast.makeText(getContext(), "Profile: " + newProfile.getName(), Toast.LENGTH_SHORT).show();
    }

    private void updateConnectionStatus() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null && mainActivity.getObdService().isConnected()) {
            connectionStatusText.setText("Status: CONNECTED");
            connectionStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_light));
        } else {
            connectionStatusText.setText("Status: DISCONNECTED");
            connectionStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        }
    }
}
