package xyz.surina.racehacker.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import xyz.surina.racehacker.R;

/**
 * Backs the (filtered) list of paired Bluetooth devices in Settings.
 *
 * A RecyclerView rather than the ListView this used to be — a ListView
 * nested inside the screen's outer ScrollView only ever showed its first
 * few rows and couldn't scroll to reveal the rest, since the ScrollView
 * intercepts vertical touch events before the ListView sees them. This
 * adapter is meant to be used with nested scrolling disabled
 * (see SettingsFragment) so the outer ScrollView handles scrolling through
 * the whole device list smoothly instead.
 */
public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    private final Context context;
    private final OnDeviceClickListener clickListener;
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothDevice selectedDevice;

    public BluetoothDeviceAdapter(Context context, OnDeviceClickListener clickListener) {
        this.context = context;
        this.clickListener = clickListener;
    }

    public void setDevices(List<BluetoothDevice> newDevices) {
        devices.clear();
        if (newDevices != null) devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    public void setSelectedDevice(BluetoothDevice device) {
        this.selectedDevice = device;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bluetooth_device, parent, false);
        return new ViewHolder(view);
    }

    @SuppressWarnings("MissingPermission") // BLUETOOTH_CONNECT already requested by MainActivity before scanning
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        String name = device.getName();
        holder.nameText.setText(name != null ? name : "Unknown device");
        holder.addressText.setText(device.getAddress());
        holder.itemView.setBackgroundColor(context.getResources().getColor(
                device.equals(selectedDevice) ? R.color.gauge_card_bg : R.color.card_background));
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onDeviceClick(device);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView nameText;
        final TextView addressText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.device_name_text);
            addressText = itemView.findViewById(R.id.device_address_text);
        }
    }
}
