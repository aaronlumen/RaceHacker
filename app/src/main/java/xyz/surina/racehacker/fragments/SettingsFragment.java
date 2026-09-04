package xyz.surina.racehacker.fragments;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
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
import java.util.Set;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.activities.MainActivity;
import xyz.surina.racehacker.adapters.BluetoothDeviceAdapter;
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

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        setupVehicleTypeSpinner();
        setupCableTypeSpinner();
        setupBluetoothDevicesList();
        setupDeviceSearch();
        setupButtons();
        setupAceVocabularySwitch();
        updateConnectionStatus();

        return view;
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
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity main = (MainActivity) getActivity();
        if (main != null) {
            main.getActionRegistry().clearScreenActions();
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
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            mainActivity.connectToDevice(selectedDevice);
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
