/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

package org.noise_planet.noisecapture;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import edu.rit.se.wifibuddy.DnsSdService;
import edu.rit.se.wifibuddy.WifiDirectHandler;

/**
 * This service allow to be the host or the guest to an automated calibration while the android
 * phone GUI is in sleep mode.
 */
public class CalibrationService extends Service implements PropertyChangeListener {
    public static final String EXTRA_HOST = "MODE_HOST";
    public static final String PROP_CALIBRATION_STATE = "PROP_CALIBRATION_STATE";
    public static final String PROP_PEER_LIST = "PROP_PEER_LIST";
    public static final String PROP_P2P_DEVICE = "PROP_P2P_DEVICE";
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pDevice wifiP2pDevice;
    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationService.class);
    private static final String NOISECAPTURE_CALIBRATION_SERVICE = "NoiseCapture_Calibration";


    public enum CALIBRATION_STATE {
        WIFI_DISABLED,              // Wifi is not activated (user have to activate it)
        WIFI_BUSY,                  // Wifi is in busy state (errors)
        P2P_UNSUPPORTED,            // Device is not compatible with peer to peer wifi
        LOOKING_FOR_PEERS,          // Host is broadcasting and is awaiting for pairing request
        LOOKING_FOR_HOST,           // Peer is broadcasting and is awaiting for host discovery
        PAIRED_TO_PEER,             // Connected to at least one peer
        PAIRED_TO_HOST,             // Connected to reference device
        AWAITING_HOST_SELECTION,    // One or more host has been found and the system must choose one
        AWAITING_START,             // Awaiting host for calibration start
        WARMUP,                     // Warmup is launched by host, Leq are sent to host
        CALIBRATION,                // Calibration has been started leq is stored
        WAITING_FOR_APPLY_OR_CANCEL // Calibration is done,guest send stored leqs, waiting for the validation of gain
    }

    private CALIBRATION_STATE state = CALIBRATION_STATE.WIFI_DISABLED;


    public void onPeersAvailable(WifiP2pDeviceList peers) {
        listeners.firePropertyChange(PROP_PEER_LIST, null, peers);
        // Connect automatically to the first peer that contains prefix
        /*
        if(!isHost) {
            for (WifiP2pDevice wifiP2pDevice : peers.getDeviceList()) {
                if (wifiP2pDevice.deviceName.contains(SSID_PREFIX)
                        && wifiP2pDevice.status == WifiP2pDevice.AVAILABLE) {
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = wifiP2pDevice.deviceAddress;
                    config.wps.setup = WpsInfo.PBC;
                    config.groupOwnerIntent = 0;
                    wifiP2pManager.connect(mChannel, config, null);
                    break;
                }
            }
        }
        */
    }

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        CalibrationService getService() {
            return CalibrationService.this;
        }
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new CalibrationService.LocalBinder();
    private PropertyChangeSupport listeners = new PropertyChangeSupport(this);

    // Other resources
    private boolean measurementIsBound = false;
    private boolean wifiDirectHandlerBound = false;
    private boolean isHost = false;

    private MeasurementService measurementService;
    private WifiDirectHandler wifiDirectHandler;
    private BroadcastReceiver receiver = null;


    @Override
    public void onCreate() {
        intentFilter.addAction(WifiDirectHandler.Action.SERVICE_CONNECTED);
        intentFilter.addAction(WifiDirectHandler.Action.MESSAGE_RECEIVED);
        intentFilter.addAction(WifiDirectHandler.Action.DEVICE_CHANGED);
        intentFilter.addAction(WifiDirectHandler.Action.WIFI_STATE_CHANGED);
        intentFilter.addAction(WifiDirectHandler.Action.DNS_SD_SERVICE_AVAILABLE);
        intentFilter.addAction(WifiDirectHandler.Action.DNS_SD_TXT_RECORD_AVAILABLE);
        intentFilter.addAction(WifiDirectHandler.Action.PEERS_CHANGED);
        receiver = new CalibrationWifiBroadcastReceiver(this);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
        registerReceiver(receiver, intentFilter);
    }

    /**
     * Initialise Wifi P2P service. Listener should be registered before this action
     */
    public void init() {
        LOGGER.info("CalibrationService.init");
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if(!wifi.isWifiEnabled()) {
            wifi.setWifiEnabled(true);
        }
        Intent intent = new Intent(this, WifiDirectHandler.class);
        bindService(intent, wifiServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /*
        // Remove all associations initiated by this p2p service
        if(wifiP2pManager != null && mChannel!=null) {
            try {
                wifiP2pManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        if (group != null && wifiP2pManager != null && mChannel != null) {
                            wifiP2pManager.removeGroup(mChannel, null);
                        }
                    }
                });
            } catch (Exception ex) {
                //ignore
            }
        }
        */
        unregisterReceiver(receiver);
        doUnbindService();
    }

    /*
    private void renameDevice() {
        // Change device name
        try {
            Class[] paramTypes = new Class[3];
            paramTypes[0] = WifiP2pManager.Channel.class;
            paramTypes[1] = String.class;
            paramTypes[2] = WifiP2pManager.ActionListener.class;
            Method setDeviceName = wifiDirectHandler.get.getClass().getMethod(
                    "setDeviceName", paramTypes);
            setDeviceName.setAccessible(true);

            Object arglist[] = new Object[3];
            arglist[0] = mChannel;
            arglist[1] = generateRandomSSID(SSID_LENGTH);
            arglist[2] = new WifiP2pManager.ActionListener() {

                @Override
                public void onSuccess() {
                    LOGGER.info("Successfully rename wifi device");
                }

                @Override
                public void onFailure(int reason) {
                    // Cannot specify SSID, ignore it, as it is only a convenient feature
                    LOGGER.info("Fail to rename wifi device");
                }
            };
            setDeviceName.invoke(wifiP2pManager, arglist);
        } catch (Exception ex) {
            // Cannot specify SSID, ignore it, as it is only a convenient feature
            LOGGER.error("Fail to rename wifi device", ex);
        }
    }
    */

    private ServiceConnection wifiServiceConnection = new ServiceConnection() {

        /**
         * Called when a connection to the Service has been established, with the IBinder of the
         * communication channel to the Service.
         * @param name The component name of the service that has been connected
         * @param service The IBinder of the Service's communication channel
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WifiDirectHandler.WifiTesterBinder binder = (WifiDirectHandler.WifiTesterBinder) service;
            LOGGER.info("wifiDirectHandler bound ");
            wifiDirectHandler = binder.getService();
            CalibrationService.this.wifiDirectHandlerBound = true;
        }

        /**
         * Called when a connection to the Service has been lost.  This typically
         * happens when the process hosting the service has crashed or been killed.
         * This does not remove the ServiceConnection itself -- this
         * binding to the service will remain active, and you will receive a call
         * to onServiceConnected when the Service is next running.
         */
        @Override
        public void onServiceDisconnected(ComponentName name) {
            LOGGER.info("wifiDirectHandler unbound ");
            CalibrationService.this.wifiDirectHandlerBound = false;
        }
    };

    private ServiceConnection measureConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            measurementService = ((MeasurementService.LocalBinder)service).getService();
            CalibrationService.this.measurementIsBound = true;

            measurementService.addPropertyChangeListener(CalibrationService.this);
            if(!measurementService.isRecording()) {
                measurementService.startRecording();
            }
            measurementService.setdBGain(0);
            measurementService.getAudioProcess().setDoFastLeq(false);
            measurementService.getAudioProcess().setDoOneSecondLeq(true);
            measurementService.getAudioProcess().setWeightingA(false);
            measurementService.getAudioProcess().setHannWindowOneSecond(false);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            measurementService.removePropertyChangeListener(CalibrationService.this);
            measurementService = null;
            CalibrationService.this.measurementIsBound = false;
        }
    };

    public void setWifiP2pDevice(WifiP2pDevice wifiP2pDevice) {
        this.wifiP2pDevice = wifiP2pDevice;
        listeners.firePropertyChange(PROP_P2P_DEVICE, null, wifiP2pDevice);
    }

    public WifiP2pDevice getWifiP2pDevice() {
        return wifiP2pDevice;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if(intent.hasExtra(EXTRA_HOST) ) {
            isHost = intent.getIntExtra(EXTRA_HOST, 0) != 0;
        }
        return mBinder;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if(AudioProcess.PROP_DELAYED_STANDART_PROCESSING.equals(event.getPropertyName())){
            // New leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
        }
        listeners.firePropertyChange(event);
    }

    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.addPropertyChangeListener(propertyChangeListener);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.removePropertyChangeListener(propertyChangeListener);
    }

    void doBindMeasureService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        if(!bindService(new Intent(this, MeasurementService.class), measureConnection,
                Context.BIND_AUTO_CREATE)) {
            Toast.makeText(CalibrationService.this, R.string.measurement_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        } else {
            measurementIsBound = true;
        }
    }

    protected void setState(CALIBRATION_STATE state) {
        CALIBRATION_STATE oldState = this.state;
        this.state = state;
        listeners.firePropertyChange(PROP_CALIBRATION_STATE, oldState, state);
        LOGGER.info("CALIBRATION_STATE " + oldState.toString() + "->" + state.toString());
    }

    void doUnbindService() {
        if (measurementIsBound) {
            measurementService.removePropertyChangeListener(this);
            // Detach our existing connection.
            unbindService(measureConnection);
        }
        if(wifiDirectHandlerBound) {
            unbindService(wifiServiceConnection);
        }
    }
    public CALIBRATION_STATE getState() {
        return state;
    }

    private void addLocalWifiService() {
        if(wifiDirectHandler != null) {
            HashMap<String, String> record = new HashMap<>();
            record.put("Name", wifiDirectHandler.getThisDevice().deviceName);
            record.put("Address", wifiDirectHandler.getThisDevice().deviceAddress);
            wifiDirectHandler.addLocalService(NOISECAPTURE_CALIBRATION_SERVICE, record);
        }
    }

    private static class CalibrationWifiBroadcastReceiver extends BroadcastReceiver {
        CalibrationService calibrationService;

        public CalibrationWifiBroadcastReceiver(CalibrationService calibrationService) {
            this.calibrationService = calibrationService;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LOGGER.info(action.toUpperCase() + " " + intent.toString());
            if (WifiDirectHandler.Action.DEVICE_CHANGED.equals(action)) {
                if(calibrationService.wifiDirectHandler != null &&
                        calibrationService.wifiDirectHandler.getThisDevice() != null &&
                        calibrationService.wifiDirectHandler.getThisDevice().status == WifiP2pDevice.AVAILABLE) {
                    if(calibrationService.isHost) {
                        calibrationService.setState(CALIBRATION_STATE.LOOKING_FOR_PEERS);
                    } else {
                        calibrationService.setState(CALIBRATION_STATE.LOOKING_FOR_HOST);
                    }
                    if(calibrationService.isHost) {
                        calibrationService.addLocalWifiService();
                        LOGGER.info("calibrationService.addLocalWifiService()");
                    }
                    if(!calibrationService.wifiDirectHandler.isDiscovering()) {
                        calibrationService.wifiDirectHandler.continuouslyDiscoverServices();
                        LOGGER.info("calibrationService.wifiDirectHandler.continuouslyDiscoverServices()");
                    }
                }
            } else if(WifiDirectHandler.Action.PEERS_CHANGED.equals(action)) {
                WifiP2pDeviceList peers = intent.getParcelableExtra("peers");
                calibrationService.onPeersAvailable(peers);
            } else if(WifiDirectHandler.Action.DNS_SD_SERVICE_AVAILABLE.equals(action)) {
                String serviceKey = intent.getStringExtra("serviceMapKey");
                Map<String, DnsSdService> serviceMap = calibrationService.wifiDirectHandler.getDnsSdServiceMap();
                if(serviceMap != null) {
                    DnsSdService service = serviceMap.get(serviceKey);
                    if(service != null && NOISECAPTURE_CALIBRATION_SERVICE.equals(service.getInstanceName())) {
                        if (service.getSrcDevice().status == WifiP2pDevice.CONNECTED) {
                            LOGGER.info("Service connected");
                            calibrationService.setState(CALIBRATION_STATE.PAIRED_TO_HOST);
                        } else if (service.getSrcDevice().status == WifiP2pDevice.AVAILABLE) {
                            String sourceDeviceName = service.getSrcDevice().deviceName;
                            if (sourceDeviceName.equals("")) {
                                sourceDeviceName = "other device";
                            }
                            LOGGER.info("Inviting " + sourceDeviceName + " to connect");
                            calibrationService.wifiDirectHandler.initiateConnectToService(service);
                        } else {
                            LOGGER.info("Service not available");
                        }
                    }
                }
            } else if(WifiDirectHandler.Action.SERVICE_CONNECTED.equals(action)) {
                if(calibrationService.isHost) {
                    calibrationService.setState(CALIBRATION_STATE.PAIRED_TO_PEER);
                } else {
                    calibrationService.setState(CALIBRATION_STATE.PAIRED_TO_HOST);
                }
            }
        }
    }
}
