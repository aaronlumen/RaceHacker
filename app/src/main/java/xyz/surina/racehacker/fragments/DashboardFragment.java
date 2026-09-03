package xyz.surina.racehacker.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.activities.MainActivity;
import xyz.surina.racehacker.adapters.GaugeAdapter;
import xyz.surina.racehacker.models.GaugeData;
import xyz.surina.racehacker.services.ObdConnectionService;

public class DashboardFragment extends Fragment {

    private RecyclerView gaugesRecyclerView;
    private GaugeAdapter gaugeAdapter;
    private List<GaugeData> gaugeList;
    private TextView statusText;
    private TextView vehicleNameText;

    // Indices into gaugeList — must match setupGauges() order
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        statusText      = view.findViewById(R.id.status_text);
        vehicleNameText = view.findViewById(R.id.vehicle_name_text);
        gaugesRecyclerView = view.findViewById(R.id.gauges_recycler_view);

        setupGauges();
        setupRecyclerView();
        updateVehicleInfo();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        attachObdListener();
        updateVehicleInfo();
    }

    @Override
    public void onPause() {
        super.onPause();
        detachObdListener();
    }

    // ─── Gauge setup ─────────────────────────────────────────────────────────

    private void setupGauges() {
        gaugeList = new ArrayList<>();
        gaugeList.add(new GaugeData("RPM",        "RPM", GaugeData.GaugeType.RPM));
        gaugeList.add(new GaugeData("Speed",       "MPH", GaugeData.GaugeType.SPEED));
        gaugeList.add(new GaugeData("Boost",       "PSI", GaugeData.GaugeType.BOOST));
        gaugeList.add(new GaugeData("AFR",         ":1",  GaugeData.GaugeType.AFR));
        gaugeList.add(new GaugeData("Oil Temp",    "°F",  GaugeData.GaugeType.OIL_TEMP));
        gaugeList.add(new GaugeData("Coolant",     "°F",  GaugeData.GaugeType.COOLANT_TEMP));
        gaugeList.add(new GaugeData("Intake Temp", "°F",  GaugeData.GaugeType.INTAKE_TEMP));
        gaugeList.add(new GaugeData("Oil Press",   "PSI", GaugeData.GaugeType.OIL_PRESSURE));
        gaugeList.add(new GaugeData("Fuel Press",  "PSI", GaugeData.GaugeType.FUEL_PRESSURE));
        gaugeList.add(new GaugeData("Timing",      "°",   GaugeData.GaugeType.TIMING));
        gaugeList.add(new GaugeData("Throttle",    "%",   GaugeData.GaugeType.THROTTLE_POSITION));
        gaugeList.add(new GaugeData("Battery",     "V",   GaugeData.GaugeType.BATTERY_VOLTAGE));

        // Start all gauges in "no data" state
        setAllGaugesNoData();
    }

    private void setupRecyclerView() {
        gaugeAdapter = new GaugeAdapter(getContext(), gaugeList);
        gaugesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        gaugesRecyclerView.setAdapter(gaugeAdapter);
    }

    // ─── OBD data wiring ─────────────────────────────────────────────────────

    private void attachObdListener() {
        MainActivity main = getMainActivity();
        if (main == null) return;

        ObdConnectionService service = main.getObdService();

        // Register live data callback
        service.setDataListener((rpm, speedMph, coolantF, intakeF,
                                  throttle, boostPsi, battery,
                                  timing, fuelLevel, afr) -> {
            if (getActivity() == null) return;

            gaugeList.get(IDX_RPM).setCurrentValue(rpm);
            gaugeList.get(IDX_SPEED).setCurrentValue(speedMph);
            gaugeList.get(IDX_BOOST).setCurrentValue(boostPsi);
            gaugeList.get(IDX_AFR).setCurrentValue(afr);
            // Oil temp & oil pressure are not standard OBD2 PIDs on all cars;
            // leave them at NaN until a real value arrives.
            gaugeList.get(IDX_COOLANT).setCurrentValue(coolantF);
            gaugeList.get(IDX_INTAKE).setCurrentValue(intakeF);
            gaugeList.get(IDX_TIMING).setCurrentValue(timing);
            gaugeList.get(IDX_THROTTLE).setCurrentValue(throttle);
            gaugeList.get(IDX_BATTERY).setCurrentValue(battery);
            // Fuel level mapped to FUEL_PRESSURE slot for display
            gaugeList.get(IDX_FUEL_PRES).setCurrentValue(fuelLevel);

            gaugeAdapter.notifyDataSetChanged();
            updateVehicleInfo();
        });

        // If already connected start polling immediately
        if (service.isConnected()) {
            service.startPidPolling();
        }
    }

    private void detachObdListener() {
        MainActivity main = getMainActivity();
        if (main == null) return;
        ObdConnectionService service = main.getObdService();
        service.stopPidPolling();
        service.setDataListener(null);
    }

    // ─── UI helpers ──────────────────────────────────────────────────────────

    private void setAllGaugesNoData() {
        for (GaugeData g : gaugeList) {
            g.setCurrentValue(Float.NaN);
        }
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
            setAllGaugesNoData();
            if (gaugeAdapter != null) gaugeAdapter.notifyDataSetChanged();
        }
    }

    private MainActivity getMainActivity() {
        if (getActivity() instanceof MainActivity) return (MainActivity) getActivity();
        return null;
    }
}
