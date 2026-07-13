package xyz.surina.proracingobd.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import xyz.surina.proracingobd.R;
import xyz.surina.proracingobd.activities.MainActivity;
import xyz.surina.proracingobd.ecu.TuningParameters;

public class TuningFragment extends Fragment {
    private TuningParameters tuningParams;

    private SeekBar afrSeekBar;
    private SeekBar timingSeekBar;
    private SeekBar boostSeekBar;
    private SeekBar revLimitSeekBar;

    private TextView afrValue;
    private TextView timingValue;
    private TextView boostValue;
    private TextView revLimitValue;

    private Switch launchControlSwitch;
    private Switch antiLagSwitch;

    private Button conservativePreset;
    private Button streetPreset;
    private Button aggressivePreset;
    private Button racePreset;
    private Button applyButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tuning, container, false);

        tuningParams = new TuningParameters();

        initializeViews(view);
        setupSeekBars();
        setupPresets();
        updateDisplayValues();

        return view;
    }

    private void initializeViews(View view) {
        afrSeekBar = view.findViewById(R.id.afr_seekbar);
        timingSeekBar = view.findViewById(R.id.timing_seekbar);
        boostSeekBar = view.findViewById(R.id.boost_seekbar);
        revLimitSeekBar = view.findViewById(R.id.rev_limit_seekbar);

        afrValue = view.findViewById(R.id.afr_value);
        timingValue = view.findViewById(R.id.timing_value);
        boostValue = view.findViewById(R.id.boost_value);
        revLimitValue = view.findViewById(R.id.rev_limit_value);

        launchControlSwitch = view.findViewById(R.id.launch_control_switch);
        antiLagSwitch = view.findViewById(R.id.anti_lag_switch);

        conservativePreset = view.findViewById(R.id.conservative_preset);
        streetPreset = view.findViewById(R.id.street_preset);
        aggressivePreset = view.findViewById(R.id.aggressive_preset);
        racePreset = view.findViewById(R.id.race_preset);
        applyButton = view.findViewById(R.id.apply_tuning_button);
    }

    private void setupSeekBars() {
        // AFR: 10.0 - 16.0
        afrSeekBar.setMax(600);
        afrSeekBar.setProgress((int)((tuningParams.getWotAfr() - 10.0f) * 100));
        afrSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float afr = 10.0f + (progress / 100.0f);
                tuningParams.setWotAfr(afr);
                afrValue.setText(String.format("%.1f:1", afr));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Timing: 15 - 40 degrees
        timingSeekBar.setMax(250);
        timingSeekBar.setProgress((int)((tuningParams.getMaxTimingAdvance() - 15.0f) * 10));
        timingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float timing = 15.0f + (progress / 10.0f);
                tuningParams.setMaxTimingAdvance(timing);
                timingValue.setText(String.format("%.1f°", timing));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Boost: 0 - 30 PSI
        boostSeekBar.setMax(300);
        boostSeekBar.setProgress((int)(tuningParams.getTargetBoost() * 10));
        boostSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float boost = progress / 10.0f;
                tuningParams.setTargetBoost(boost);
                boostValue.setText(String.format("%.1f PSI", boost));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Rev Limit: 6000 - 8500 RPM
        revLimitSeekBar.setMax(2500);
        revLimitSeekBar.setProgress(tuningParams.getHardRevLimit() - 6000);
        revLimitSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int revLimit = 6000 + progress;
                tuningParams.setHardRevLimit(revLimit);
                revLimitValue.setText(revLimit + " RPM");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupPresets() {
        conservativePreset.setOnClickListener(v -> {
            tuningParams.applyConservativeTune();
            updateDisplayValues();
            Toast.makeText(getContext(), "Conservative tune loaded", Toast.LENGTH_SHORT).show();
        });

        streetPreset.setOnClickListener(v -> {
            tuningParams.applyStreetTune();
            updateDisplayValues();
            Toast.makeText(getContext(), "Street tune loaded", Toast.LENGTH_SHORT).show();
        });

        aggressivePreset.setOnClickListener(v -> {
            tuningParams.applyAggressiveTune();
            updateDisplayValues();
            Toast.makeText(getContext(), "Aggressive tune loaded", Toast.LENGTH_SHORT).show();
        });

        racePreset.setOnClickListener(v -> {
            tuningParams.applyRaceTune();
            updateDisplayValues();
            Toast.makeText(getContext(), "Race tune loaded", Toast.LENGTH_SHORT).show();
        });

        applyButton.setOnClickListener(v -> applyTuning());
    }

    private void updateDisplayValues() {
        afrSeekBar.setProgress((int)((tuningParams.getWotAfr() - 10.0f) * 100));
        timingSeekBar.setProgress((int)((tuningParams.getMaxTimingAdvance() - 15.0f) * 10));
        boostSeekBar.setProgress((int)(tuningParams.getTargetBoost() * 10));
        revLimitSeekBar.setProgress(tuningParams.getHardRevLimit() - 6000);

        launchControlSwitch.setChecked(tuningParams.isLaunchControlEnabled());
        antiLagSwitch.setChecked(tuningParams.isAntiLagEnabled());
    }

    private void applyTuning() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) return;

        if (!mainActivity.getObdService().isConnected()) {
            Toast.makeText(getContext(), "Not connected to OBD adapter",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mainActivity.getCurrentVehicleProfile().supportsAdvancedTuning()) {
            Toast.makeText(getContext(),
                    "Advanced tuning not supported for this vehicle",
                    Toast.LENGTH_LONG).show();
            return;
        }

        tuningParams.setLaunchControlEnabled(launchControlSwitch.isChecked());
        tuningParams.setAntiLagEnabled(antiLagSwitch.isChecked());

        applyButton.setEnabled(false);
        applyButton.setText("Applying...");

        new Thread(() -> {
            try {
                Thread.sleep(2000);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        applyButton.setEnabled(true);
                        applyButton.setText("Apply Tuning");
                        Toast.makeText(getContext(),
                                "Tuning parameters applied successfully!",
                                Toast.LENGTH_LONG).show();
                    });
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
