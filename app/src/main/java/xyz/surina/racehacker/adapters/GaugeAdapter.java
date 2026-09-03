package xyz.surina.racehacker.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import xyz.surina.racehacker.R;
import xyz.surina.racehacker.models.GaugeData;

public class GaugeAdapter extends RecyclerView.Adapter<GaugeAdapter.GaugeViewHolder> {

    private final Context context;
    private final List<GaugeData> gaugeList;

    public GaugeAdapter(Context context, List<GaugeData> gaugeList) {
        this.context = context;
        this.gaugeList = gaugeList;
    }

    @NonNull
    @Override
    public GaugeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_gauge, parent, false);
        return new GaugeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GaugeViewHolder holder, int position) {
        GaugeData gauge = gaugeList.get(position);

        holder.gaugeName.setText(gauge.getName());
        holder.gaugeValue.setText(gauge.getFormattedValue());
        holder.gaugeUnit.setText(gauge.hasData() ? gauge.getUnit() : "");

        if (!gauge.hasData()) {
            // Not connected — muted
            holder.gaugeValue.setTextColor(Color.parseColor("#444444"));
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        } else if (gauge.isCritical()) {
            holder.gaugeValue.setTextColor(Color.parseColor("#FF2222"));
            holder.itemView.setBackgroundColor(Color.parseColor("#2A0000"));
        } else if (gauge.isWarning()) {
            holder.gaugeValue.setTextColor(Color.parseColor("#FF8C00"));
            holder.itemView.setBackgroundColor(Color.parseColor("#221500"));
        } else {
            holder.gaugeValue.setTextColor(Color.parseColor("#00E676"));
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public int getItemCount() { return gaugeList.size(); }

    public static class GaugeViewHolder extends RecyclerView.ViewHolder {
        TextView gaugeName;
        TextView gaugeValue;
        TextView gaugeUnit;

        public GaugeViewHolder(@NonNull View itemView) {
            super(itemView);
            gaugeName  = itemView.findViewById(R.id.gauge_name);
            gaugeValue = itemView.findViewById(R.id.gauge_value);
            gaugeUnit  = itemView.findViewById(R.id.gauge_unit);
        }
    }
}
