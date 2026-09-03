package xyz.surina.racehacker.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.activities.MainActivity;
import xyz.surina.racehacker.ecu.EcuFlashManager;

public class EcuFlashFragment extends Fragment {
    private static final int PICK_BIN_FILE = 1;

    private Button backupButton;
    private Button flashButton;
    private Button selectBinButton;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView statusText;
    private TextView selectedFileText;

    private EcuFlashManager flashManager;
    private File selectedBinFile;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ecu_flash, container, false);

        backupButton = view.findViewById(R.id.backup_button);
        flashButton = view.findViewById(R.id.flash_button);
        selectBinButton = view.findViewById(R.id.select_bin_button);
        progressBar = view.findViewById(R.id.progress_bar);
        progressText = view.findViewById(R.id.progress_text);
        statusText = view.findViewById(R.id.status_text);
        selectedFileText = view.findViewById(R.id.selected_file_text);

        setupFlashManager();
        setupButtons();
        updateUI();

        return view;
    }

    private void setupFlashManager() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            flashManager = new EcuFlashManager(mainActivity.getCurrentVehicleProfile());
            flashManager.setProgressListener(new EcuFlashManager.FlashProgressListener() {
                @Override
                public void onProgress(int percentage, String message) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setProgress(percentage);
                            progressText.setText(percentage + "% - " + message);
                        });
                    }
                }

                @Override
                public void onComplete(boolean success, String message) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            statusText.setText(message);
                            progressBar.setProgress(100);
                            enableButtons(true);
                            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            statusText.setText("Error: " + error);
                            enableButtons(true);
                            Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });
        }
    }

    private void setupButtons() {
        backupButton.setOnClickListener(v -> backupEcu());
        flashButton.setOnClickListener(v -> flashEcu());
        selectBinButton.setOnClickListener(v -> selectBinFile());
    }

    private void updateUI() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            boolean supportsFlashing = mainActivity.getCurrentVehicleProfile()
                    .supportsEcuFlashing();

            if (!supportsFlashing) {
                statusText.setText("ECU flashing not supported for this vehicle");
                backupButton.setEnabled(false);
                flashButton.setEnabled(false);
            }
        }
    }

    private void backupEcu() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        if (!mainActivity.getObdService().isConnected()) {
            Toast.makeText(getContext(), "Not connected to OBD adapter",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        enableButtons(false);
        progressBar.setProgress(0);

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        String filename = mainActivity.getCurrentVehicleProfile().getType().toString()
                .toLowerCase() + "_backup_" + timestamp + ".bin";

        File backupDir = new File(getContext().getExternalFilesDir(null), "ECU_Backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        File backupFile = new File(backupDir, filename);
        flashManager.backupEcuRom(backupFile);

        statusText.setText("Backing up ECU ROM...");
    }

    private void flashEcu() {
        if (selectedBinFile == null) {
            Toast.makeText(getContext(), "Please select a BIN file first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        if (!mainActivity.getObdService().isConnected()) {
            Toast.makeText(getContext(), "Not connected to OBD adapter",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        enableButtons(false);
        progressBar.setProgress(0);

        flashManager.flashEcuRom(selectedBinFile);
        statusText.setText("Flashing ECU...");
    }

    private void selectBinFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select BIN file"), PICK_BIN_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_BIN_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                String path = uri.getPath();

                if (path != null && path.endsWith(".bin")) {
                    selectedBinFile = new File(path);
                    selectedFileText.setText("Selected: " + selectedBinFile.getName());
                    flashButton.setEnabled(true);
                } else {
                    Toast.makeText(getContext(), "Please select a .bin file",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void enableButtons(boolean enabled) {
        backupButton.setEnabled(enabled);
        flashButton.setEnabled(enabled && selectedBinFile != null);
        selectBinButton.setEnabled(enabled);
    }
}
