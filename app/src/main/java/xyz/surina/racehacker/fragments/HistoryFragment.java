package xyz.surina.racehacker.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.activities.MainActivity;
import xyz.surina.racehacker.models.GaugeData;
import xyz.surina.racehacker.models.GaugeHistoryStore;

/**
 * Plots a chosen gauge's recent history ({@link GaugeHistoryStore}'s rolling
 * 5-minute in-memory buffer) as a line chart. FEATURE_IDEAS.md: "Historical
 * graphing — MPAndroidChart is already a dependency ... but nothing plots
 * sensor history over time yet, just current value."
 */
public class HistoryFragment extends Fragment {

    private Spinner gaugeSpinner;
    private LineChart chart;
    private TextView emptyText;
    private List<GaugeData> gauges;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        gaugeSpinner = view.findViewById(R.id.history_gauge_spinner);
        chart = view.findViewById(R.id.history_chart);
        emptyText = view.findViewById(R.id.history_empty_text);

        chart.getDescription().setEnabled(false);
        chart.setNoDataText("No data yet.");
        chart.setTouchEnabled(true);
        chart.setPinchZoom(true);

        MainActivity main = getMainActivity();
        gauges = main != null ? main.getLiveGauges() : new ArrayList<>();

        List<String> names = new ArrayList<>();
        for (GaugeData g : gauges) names.add(g.getName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gaugeSpinner.setAdapter(adapter);
        gaugeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshChart();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        MainActivity main = getMainActivity();
        if (main != null) {
            // Only the currently-visible screen should own this listener —
            // same single-owner convention DashboardFragment follows, safe
            // here since bottom-nav fragment swaps mean only one is ever
            // resumed at a time.
            main.setGaugeUpdateListener(this::refreshChart);
        }
        refreshChart();
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity main = getMainActivity();
        if (main != null) main.setGaugeUpdateListener(null);
    }

    private void refreshChart() {
        MainActivity main = getMainActivity();
        if (main == null || chart == null || gauges.isEmpty()) return;

        int pos = gaugeSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= gauges.size()) pos = 0;
        GaugeData gauge = gauges.get(pos);

        List<GaugeHistoryStore.Sample> samples = main.getGaugeHistory().get(gauge.getType());
        if (samples.isEmpty()) {
            chart.clear();
            chart.setVisibility(View.GONE);
            if (emptyText != null) emptyText.setVisibility(View.VISIBLE);
            return;
        }
        chart.setVisibility(View.VISIBLE);
        if (emptyText != null) emptyText.setVisibility(View.GONE);

        List<Entry> entries = new ArrayList<>();
        long firstMs = samples.get(0).timeMs;
        for (GaugeHistoryStore.Sample s : samples) {
            // X axis: seconds since the first sample currently in the window,
            // not raw epoch millis — keeps the chart's numbers small/readable.
            entries.add(new Entry((s.timeMs - firstMs) / 1000f, s.value));
        }

        LineDataSet dataSet = new LineDataSet(entries, gauge.getName() + " (" + gauge.getUnit() + ")");
        dataSet.setColor(Color.RED);
        dataSet.setCircleColor(Color.RED);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setLineWidth(2f);

        chart.setData(new LineData(dataSet));
        chart.invalidate();
    }

    private MainActivity getMainActivity() {
        if (getActivity() instanceof MainActivity) return (MainActivity) getActivity();
        return null;
    }
}
