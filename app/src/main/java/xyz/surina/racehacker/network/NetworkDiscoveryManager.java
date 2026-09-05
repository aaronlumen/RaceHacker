package xyz.surina.racehacker.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wraps Android's built-in NsdManager (mDNS/DNS-SD) so RaceHacker devices on
 * the same WiFi network can find each other with zero manual IP entry and no
 * extra dependency. One device registers as a broadcaster (in the car,
 * connected to the OBD adapter); another device on the same network
 * discovers it and connects as a mirror/display — see
 * {@link GaugeBroadcastServer} and {@link GaugeMirrorClient}.
 */
public class NetworkDiscoveryManager {
    private static final String TAG = "NetworkDiscovery";
    public static final String SERVICE_TYPE = "_racehacker._tcp.";

    public interface DiscoveryListener {
        void onDevicesChanged(List<NsdServiceInfo> devices);
    }

    private final NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private final List<NsdServiceInfo> discoveredDevices = new CopyOnWriteArrayList<>();
    private DiscoveryListener callback;

    public NetworkDiscoveryManager(Context context) {
        nsdManager = (NsdManager) context.getApplicationContext().getSystemService(Context.NSD_SERVICE);
    }

    /** Advertises this device as a gauge broadcaster on the local network. */
    public void registerBroadcaster(String deviceName, int port) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(deviceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                Log.d(TAG, "Registered as: " + info.getServiceName());
            }
            @Override
            public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.e(TAG, "Registration failed: " + errorCode);
            }
            @Override
            public void onServiceUnregistered(NsdServiceInfo info) {}
            @Override
            public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {}
        };
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    public void unregisterBroadcaster() {
        if (registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                Log.w(TAG, "unregisterService: not currently registered", e);
            }
            registrationListener = null;
        }
    }

    /** Browses for other RaceHacker devices broadcasting on the network. */
    public void startDiscovery(DiscoveryListener callback) {
        this.callback = callback;
        discoveredDevices.clear();

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                if (!SERVICE_TYPE.equals(service.getServiceType())) return;
                // A "found" service only carries name+type — resolve() is
                // needed to get the actual host/port to connect to.
                nsdManager.resolveService(service, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                        Log.w(TAG, "Resolve failed for " + info.getServiceName() + ": " + errorCode);
                    }
                    @Override
                    public void onServiceResolved(NsdServiceInfo info) {
                        // mDNS re-announces periodically, so onServiceFound()
                        // firing more than once for the same advertised
                        // service during a single scan is normal, expected
                        // behavior, not an error — replace any existing entry
                        // for this service name rather than appending a
                        // duplicate (confirmed real: the picker showed the
                        // same device twice before this fix).
                        for (NsdServiceInfo existing : discoveredDevices) {
                            if (existing.getServiceName().equals(info.getServiceName())) {
                                discoveredDevices.remove(existing);
                                break;
                            }
                        }
                        discoveredDevices.add(info);
                        notifyChanged();
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                for (NsdServiceInfo d : discoveredDevices) {
                    if (d.getServiceName().equals(service.getServiceName())) {
                        discoveredDevices.remove(d);
                        break;
                    }
                }
                notifyChanged();
            }

            @Override
            public void onDiscoveryStopped(String regType) {}

            @Override
            public void onStartDiscoveryFailed(String regType, int errorCode) {
                Log.e(TAG, "Start discovery failed: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String regType, int errorCode) {}
        };
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    public void stopDiscovery() {
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                Log.w(TAG, "stopServiceDiscovery: not currently discovering", e);
            }
            discoveryListener = null;
        }
    }

    private void notifyChanged() {
        if (callback != null) callback.onDevicesChanged(new ArrayList<>(discoveredDevices));
    }
}
