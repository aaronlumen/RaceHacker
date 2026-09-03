package xyz.surina.racehacker.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.activities.MainActivity;
import xyz.surina.racehacker.adapters.DtcAdapter;
import xyz.surina.racehacker.services.DtcManager;

public class DiagnosticsFragment extends Fragment {
    private RecyclerView dtcRecyclerView;
    private DtcAdapter dtcAdapter;
    private Button readCodesButton;
    private Button clearCodesButton;
    private TextView dtcCountText;
    private TextView milStatusText;
    private DtcManager dtcManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_diagnostics, container, false);

        dtcRecyclerView = view.findViewById(R.id.dtc_recycler_view);
        readCodesButton = view.findViewById(R.id.read_codes_button);
        clearCodesButton = view.findViewById(R.id.clear_codes_button);
        dtcCountText = view.findViewById(R.id.dtc_count_text);
        milStatusText = view.findViewById(R.id.mil_status_text);

        setupDtcManager();
        setupRecyclerView();
        setupButtons();

        return view;
    }

    private void setupDtcManager() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            dtcManager = new DtcManager(mainActivity.getObdService());
        }
    }

    private void setupRecyclerView() {
        dtcRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        dtcAdapter = new DtcAdapter(getContext());
        dtcRecyclerView.setAdapter(dtcAdapter);
    }

    private void setupButtons() {
        readCodesButton.setOnClickListener(v -> readDtcCodes());
        clearCodesButton.setOnClickListener(v -> clearDtcCodes());
    }

    private void readDtcCodes() {
        if (!isObdConnected()) {
            Toast.makeText(getContext(), "Not connected to OBD adapter",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        readCodesButton.setEnabled(false);
        readCodesButton.setText("Reading...");

        new Thread(() -> {
            List<DtcManager.DiagnosticTroubleCode> codes = dtcManager.readDtcCodes();
            int dtcCount = dtcManager.getDtcCount();
            boolean milOn = dtcManager.isMilOn();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    dtcAdapter.setCodes(codes);
                    dtcCountText.setText("Codes Found: " + codes.size());

                    if (milOn) {
                        milStatusText.setText("MIL: ON");
                        milStatusText.setTextColor(getResources().getColor(
                                android.R.color.holo_orange_light));
                    } else {
                        milStatusText.setText("MIL: OFF");
                        milStatusText.setTextColor(getResources().getColor(
                                android.R.color.holo_green_light));
                    }

                    readCodesButton.setEnabled(true);
                    readCodesButton.setText("Read Codes");

                    if (codes.isEmpty()) {
                        Toast.makeText(getContext(), "No trouble codes found",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Found " + codes.size() + " codes",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void clearDtcCodes() {
        if (!isObdConnected()) {
            Toast.makeText(getContext(), "Not connected to OBD adapter",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        clearCodesButton.setEnabled(false);
        clearCodesButton.setText("Clearing...");

        new Thread(() -> {
            boolean success = dtcManager.clearDtcCodes();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    clearCodesButton.setEnabled(true);
                    clearCodesButton.setText("Clear Codes");

                    if (success) {
                        dtcAdapter.clearCodes();
                        dtcCountText.setText("Codes Found: 0");
                        milStatusText.setText("MIL: OFF");
                        milStatusText.setTextColor(getResources().getColor(
                                android.R.color.holo_green_light));
                        Toast.makeText(getContext(), "Codes cleared successfully",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Failed to clear codes",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private boolean isObdConnected() {
        MainActivity mainActivity = (MainActivity) getActivity();
        return mainActivity != null && mainActivity.getObdService().isConnected();
    }
}
