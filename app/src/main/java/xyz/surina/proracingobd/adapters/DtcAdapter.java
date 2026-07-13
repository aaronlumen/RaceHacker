package xyz.surina.proracingobd.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import xyz.surina.proracingobd.R;
import xyz.surina.proracingobd.services.DtcManager;

public class DtcAdapter extends RecyclerView.Adapter<DtcAdapter.DtcViewHolder> {
    private Context context;
    private List<DtcManager.DiagnosticTroubleCode> dtcList;

    public DtcAdapter(Context context) {
        this.context = context;
        this.dtcList = new ArrayList<>();
    }

    public void setCodes(List<DtcManager.DiagnosticTroubleCode> codes) {
        this.dtcList = codes;
        notifyDataSetChanged();
    }

    public void clearCodes() {
        this.dtcList.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DtcViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_dtc, parent, false);
        return new DtcViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DtcViewHolder holder, int position) {
        DtcManager.DiagnosticTroubleCode dtc = dtcList.get(position);

        holder.dtcCode.setText(dtc.getCode());
        holder.dtcDescription.setText(dtc.getDescription());
        holder.dtcStatus.setText(dtc.getStatus());

        // Color code by status
        switch (dtc.getStatus()) {
            case "Active":
                holder.dtcStatus.setTextColor(Color.parseColor("#FF0000")); // Red
                holder.itemView.setBackgroundColor(Color.parseColor("#331111"));
                break;
            case "Pending":
                holder.dtcStatus.setTextColor(Color.parseColor("#FFA500")); // Orange
                holder.itemView.setBackgroundColor(Color.parseColor("#332211"));
                break;
            case "Permanent":
                holder.dtcStatus.setTextColor(Color.parseColor("#FF00FF")); // Magenta
                holder.itemView.setBackgroundColor(Color.parseColor("#331133"));
                break;
            default:
                holder.dtcStatus.setTextColor(Color.parseColor("#FFFFFF"));
                holder.itemView.setBackgroundColor(Color.parseColor("#111111"));
        }

        if (dtc.isPermanent()) {
            holder.dtcCode.setTextColor(Color.parseColor("#FF00FF"));
        }
    }

    @Override
    public int getItemCount() {
        return dtcList.size();
    }

    public static class DtcViewHolder extends RecyclerView.ViewHolder {
        TextView dtcCode;
        TextView dtcDescription;
        TextView dtcStatus;

        public DtcViewHolder(@NonNull View itemView) {
            super(itemView);
            dtcCode = itemView.findViewById(R.id.dtc_code);
            dtcDescription = itemView.findViewById(R.id.dtc_description);
            dtcStatus = itemView.findViewById(R.id.dtc_status);
        }
    }
}
