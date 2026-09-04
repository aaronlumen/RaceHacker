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

/**
 * Displays the live gauge grid. Gauge data itself is owned by {@link MainActivity}
 * (so it keeps updating regardless of which tab is showing, and so Ace can
 * narrate/answer questions about it from any screen) — this fragment just
 * renders {@link MainActivity#getLiveGauges()} and refreshes when told to.
 */
public class DashboardFragment extends Fragment {

    private RecyclerView gaugesRecyclerView;
    private GaugeAdapter gaugeAdapter;
    private List<GaugeData> gaugeList;
    private TextView statusText;
    private TextView vehicleNameText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        statusText      = view.findViewById(R.id.status_text);
        vehicleNameText = view.findViewById(R.id.vehicle_name_text);
        gaugesRecyclerView = view.findViewById(R.id.gauges_recycler_view);

        MainActivity main = getMainActivity();
        gaugeList = main != null ? main.getLiveGauges() : new ArrayList<>();

        setupRecyclerView();
        updateVehicleInfo();

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
            });
        }
        updateVehicleInfo();
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity main = getMainActivity();
        if (main != null) {
            main.setGaugeUpdateListener(null);
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

    private MainActivity getMainActivity() {
        if (getActivity() instanceof MainActivity) return (MainActivity) getActivity();
        return null;
    }
}
