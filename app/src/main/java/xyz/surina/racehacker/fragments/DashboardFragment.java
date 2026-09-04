package xyz.surina.racehacker.fragments;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.activities.MainActivity;
import xyz.surina.racehacker.adapters.GaugeAdapter;
import xyz.surina.racehacker.models.GaugeData;

/**
 * Displays the live gauge grid. Gauge data itself is owned by {@link MainActivity}
 * (so it keeps updating regardless of which tab is showing, and so Ace can
 * narrate/answer questions about it from any screen) — this fragment just
 * renders {@link MainActivity#getLiveGauges()} and refreshes when told to.
 */
public class DashboardFragment extends Fragment implements SensorEventListener {

    private RecyclerView gaugesRecyclerView;
    private GaugeAdapter gaugeAdapter;
    private List<GaugeData> gaugeList;
    private TextView statusText;
    private TextView vehicleNameText;
    private View shiftLight1;
    private View shiftLight2;
    private View shiftLight3;
    private View shiftLight4;

    // G-force — phone accelerometer only, entirely independent of the OBD
    // connection/gauge data above.
    private TextView gForceText;
    private SensorManager sensorManager;
    private Sensor accelSensor;
    private long lastGForceUpdateMs;
    private static final long G_FORCE_UPDATE_INTERVAL_MS = 150;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        statusText      = view.findViewById(R.id.status_text);
        vehicleNameText = view.findViewById(R.id.vehicle_name_text);
        gaugesRecyclerView = view.findViewById(R.id.gauges_recycler_view);
        shiftLight1 = view.findViewById(R.id.shift_light_1);
        shiftLight2 = view.findViewById(R.id.shift_light_2);
        shiftLight3 = view.findViewById(R.id.shift_light_3);
        shiftLight4 = view.findViewById(R.id.shift_light_4);
        gForceText = view.findViewById(R.id.g_force_text);

        MainActivity main = getMainActivity();
        gaugeList = main != null ? main.getLiveGauges() : new ArrayList<>();

        setupRecyclerView();
        setupAccelerometer();
        updateVehicleInfo();
        updateShiftLights();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity main = getMainActivity();
        if (main != null) {
            // Refresh the grid whenever MainActivity's shared gauge data updates.
            main.setGaugeUpdateListener(() -> {
                if (gaugeAdapter != null) gaugeAdapter.notifyDataSetChanged();
                updateVehicleInfo();
                updateShiftLights();
            });
        }
        if (sensorManager != null && accelSensor != null) {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI);
        }
        updateVehicleInfo();
        updateShiftLights();
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity main = getMainActivity();
        if (main != null) {
            main.setGaugeUpdateListener(null);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void setupRecyclerView() {
        gaugeAdapter = new GaugeAdapter(getContext(), gaugeList);
        gaugesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        gaugesRecyclerView.setAdapter(gaugeAdapter);
    }

    private void updateVehicleInfo() {
        MainActivity main = getMainActivity();
        if (main == null) return;

        vehicleNameText.setText(main.getCurrentVehicleProfile().getName());

        if (main.getObdService().isConnected()) {
            statusText.setText("● LIVE");
            statusText.setTextColor(getResources().getColor(R.color.status_normal));
        } else {
            statusText.setText("○ NO SIGNAL");
            statusText.setTextColor(getResources().getColor(R.color.status_critical));
            if (gaugeAdapter != null) gaugeAdapter.notifyDataSetChanged();
        }
    }

    // ─── Shift lights ────────────────────────────────────────────────────────
    // Green -> yellow -> orange -> red as RPM climbs toward the rev limiter.
    // Reuses GaugeData's own RPM warning/critical thresholds (the same ones
    // Ace's narration and the gauge card coloring already use) rather than a
    // separate hardcoded shift point.

    private void updateShiftLights() {
        if (shiftLight1 == null || gaugeList == null) return;

        GaugeData rpmGauge = null;
        for (GaugeData g : gaugeList) {
            if (g.getType() == GaugeData.GaugeType.RPM) {
                rpmGauge = g;
                break;
            }
        }

        if (rpmGauge == null || !rpmGauge.hasData() || rpmGauge.getCriticalThreshold() <= 0) {
            setLight(shiftLight1, R.color.gauge_nodata);
            setLight(shiftLight2, R.color.gauge_nodata);
            setLight(shiftLight3, R.color.gauge_nodata);
            setLight(shiftLight4, R.color.gauge_nodata);
            return;
        }

        float pct = rpmGauge.getCurrentValue() / rpmGauge.getCriticalThreshold();
        setLight(shiftLight1, pct >= 0.50f ? R.color.racing_green  : R.color.gauge_nodata);
        setLight(shiftLight2, pct >= 0.70f ? R.color.racing_yellow : R.color.gauge_nodata);
        setLight(shiftLight3, pct >= 0.85f ? R.color.racing_orange : R.color.gauge_nodata);
        setLight(shiftLight4, pct >= 1.00f ? R.color.racing_red_bright : R.color.gauge_nodata);
    }

    private void setLight(View light, int colorRes) {
        if (light == null || light.getBackground() == null || getContext() == null) return;
        light.getBackground().setTint(ContextCompat.getColor(getContext(), colorRes));
    }

    private MainActivity getMainActivity() {
        if (getActivity() instanceof MainActivity) return (MainActivity) getActivity();
        return null;
    }

    // ─── G-force ─────────────────────────────────────────────────────────────
    // Phone accelerometer only — works with no OBD adapter or vehicle at all.

    private void setupAccelerometer() {
        if (getContext() == null) return;
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            // TYPE_LINEAR_ACCELERATION is gravity-compensated — no need to
            // subtract Earth's 1g ourselves like with the raw accelerometer.
            accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }
        if (accelSensor == null && gForceText != null) {
            gForceText.setText("G-Force: sensor unavailable");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (gForceText == null) return;
        long now = System.currentTimeMillis();
        if (now - lastGForceUpdateMs < G_FORCE_UPDATE_INTERVAL_MS) return; // throttle — sensor fires fast
        lastGForceUpdateMs = now;

        float gX = event.values[0] / SensorManager.STANDARD_GRAVITY;
        float gY = event.values[1] / SensorManager.STANDARD_GRAVITY;
        float magnitude = (float) Math.sqrt(gX * gX + gY * gY);
        gForceText.setText(String.format(Locale.US, "G-Force: %.2f g", magnitude));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used.
    }
}
