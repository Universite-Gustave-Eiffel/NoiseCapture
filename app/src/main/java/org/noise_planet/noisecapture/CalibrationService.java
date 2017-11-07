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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;

import org.orbisgis.sos.LeqStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.rit.se.wifibuddy.CommunicationManager;
import edu.rit.se.wifibuddy.DnsSdService;
import edu.rit.se.wifibuddy.WifiDirectHandler;

/**
 * This service allow to be the host or the guest to an automated calibration while the android
 * phone GUI is in sleep mode.
 */
public class CalibrationService extends Service implements PropertyChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String EXTRA_HOST = "MODE_HOST";
    public final AtomicBoolean queryRegisterWifiP2PService = new AtomicBoolean(false);

    // properties
    public static final String PROP_CALIBRATION_STATE = "PROP_CALIBRATION_STATE";
    public static final String PROP_PEER_LIST = "PROP_PEER_LIST";
    public static final String PROP_P2P_DEVICE = "PROP_P2P_DEVICE";
    public static final String PROP_PEER_READY = "PROP_PEER_READY"; // Peer ready to start
    public static final String PROP_CALIBRATION_PROGRESSION = "PROP_CALIBRATION_PROGRESSION"; // Calibration/Warmup progression 0-100


    private AudioProcess audioProcess;
    private LeqStats leqStats = new LeqStats();
    private AtomicBoolean recording = new AtomicBoolean(true);
    private AtomicBoolean canceled = new AtomicBoolean(false);

    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pDevice wifiP2pDevice;
    private int defaultWarmupTime;
    private int defaultCalibrationTime;
    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationService.class);
    private static final String NOISECAPTURE_CALIBRATION_SERVICE = "NoiseCapture_Calibration";
    // Registration service timout
    private static final int SERVICE_TIMEOUT = 8000;
    public static final int COUNTDOWN_STEP_MILLISECOND = 125;
    private Handler timeHandler;

    public enum CALIBRATION_STATE {
        WIFI_DISABLED,              // Wifi is not activated (user have to activate it)
        WIFI_BUSY,                  // Wifi is in busy state (errors)
        P2P_UNSUPPORTED,            // Device is not compatible with peer to peer wifi
        LOOKING_FOR_PEERS,          // Host is broadcasting and is awaiting for pairing request
        LOOKING_FOR_HOST,           // Peer is broadcasting and is awaiting for host discovery
        PAIRED_TO_PEER,             // Connected to at least one peer
        PAIRED_TO_HOST,             // Connected to reference device
        AWAITING_START,             // Awaiting host for calibration start
        CANCELED,                   // WARMUP or CALIBRATION canceled, next state is CANCEL_IN_PROGRESS
        WARMUP,                     // Warmup is launched by host
        CALIBRATION,                // Calibration has been started.
        WAITING_FOR_APPLY_OR_RESET,// Calibration is done,guest send stored leqs, waiting for
        // the validation of gain.
        CANCEL_IN_PROGRESS,         // Canceled but waiting for timer to stop. Next state is AWAITING_START
    }

    private static final String messageSeparator = "|";
    private static final String messagePingArgument = "CHECK";

    public static final String SETTINGS_CALIBRATION_WARMUP_TIME = "settings_calibration_warmup_time";
    public static final String SETTINGS_CALIBRATION_TIME = "settings_calibration_time";

    private static final int ID_PING = 1;                   // 1|CHECK
    private static final int ID_PONG = 2;                   // 2|CHECK|DEVICE_ID
    private static final int ID_START_CALIBRATION = 3;      // 3|WARMUP_TIME|CALIBRATION_TIME
    private static final int ID_PEER_CALIBRATION_RESULT = 4;// 4|LEQ|DEVICE_ID
    private static final int ID_HOST_APPLY = 5;             // 5|REFERENCE_LEQ
    private static final int ID_HOST_CANCEL = 6;            // 6

    private CALIBRATION_STATE state = CALIBRATION_STATE.WIFI_DISABLED;

    private ProgressHandler progressHandler = new ProgressHandler(this);

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(isHost) {
            if (SETTINGS_CALIBRATION_TIME.equals(key)) {
                defaultCalibrationTime = MainActivity.getInteger(sharedPreferences,
                        SETTINGS_CALIBRATION_TIME, 10);
            } else if (SETTINGS_CALIBRATION_WARMUP_TIME.equals(key)) {
                defaultWarmupTime = MainActivity.getInteger(sharedPreferences,
                        SETTINGS_CALIBRATION_WARMUP_TIME, 5);
            }
        }
    }

    public void onPeersAvailable(WifiP2pDeviceList peers) {
        listeners.firePropertyChange(PROP_PEER_LIST, null, peers);
    }

    public double getleq() {
        return leqStats.getLeqMean();
    }

    private void initAudioProcess() {
        if(audioProcess != null) {
            audioProcess.getListeners().removePropertyChangeListener(this);
        }
        canceled.set(false);
        recording.set(true);
        audioProcess = new AudioProcess(recording, canceled);
        audioProcess.setDoFastLeq(false);
        audioProcess.setDoOneSecondLeq(true);
        audioProcess.setWeightingA(false);
        audioProcess.setHannWindowOneSecond(true);
        if(isHost) {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(CalibrationService.this);
            audioProcess.setGain((float)Math.pow(10, MainActivity.getDouble(sharedPref,
                    "settings_recording_gain", 0) / 20));
        } else {
            audioProcess.setGain(1);
        }
        audioProcess.getListeners().addPropertyChangeListener(this);

        // Start measurement
        new Thread(audioProcess).start();
    }

    private void stopAudioProcess() {
        canceled.set(true);
        recording.set(false);
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
    private boolean wifiDirectHandlerBound = false;
    private boolean isHost = false;

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
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    public WifiDirectHandler getWifiDirectHandler() {
        return wifiDirectHandler;
    }

    protected void onTimerEnd() {
        if(state == CALIBRATION_STATE.WARMUP) {
            setState(CALIBRATION_STATE.CALIBRATION);
            // Start calibration
            leqStats = new LeqStats();
            progressHandler.start(defaultCalibrationTime * 1000);
        } else if(state == CALIBRATION_STATE.CALIBRATION) {
            if(!isHost) {
                sendMessage(ID_PEER_CALIBRATION_RESULT, leqStats.getLeqMean(), wifiDirectHandler
                        .getThisDevice().deviceAddress);
            }
            setState(CALIBRATION_STATE.WAITING_FOR_APPLY_OR_RESET);
            stopAudioProcess();
        } else {
            // Canceled
            setState(CALIBRATION_STATE.AWAITING_START);
        }
    }

    /**
     * Initialise Wifi P2P service. Listener should be registered before this action
     */
    public void init() {
        LOGGER.info("CalibrationService.init");
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if(!wifi.isWifiEnabled() && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CHANGE_WIFI_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            wifi.setWifiEnabled(true);
        }
        Intent intent = new Intent(this, WifiDirectHandler.class);
        bindService(intent, wifiServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(wifiDirectHandlerBound) {
            unbindService(wifiServiceConnection);
        }
        unregisterReceiver(receiver);
    }

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
            if(isHost) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                defaultCalibrationTime = MainActivity.getInteger(sharedPref, SETTINGS_CALIBRATION_TIME, 10);
                defaultWarmupTime = MainActivity.getInteger(sharedPref, SETTINGS_CALIBRATION_WARMUP_TIME, 5);
            }
        }
        return mBinder;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if(AudioProcess.PROP_DELAYED_STANDART_PROCESSING.equals(event.getPropertyName())){
            // New leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
            if(CALIBRATION_STATE.CALIBRATION.equals(state)) {
                leqStats.addLeq(measure.getGlobaldBaValue());
            }
        }
        listeners.firePropertyChange(event);
    }

    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.addPropertyChangeListener(propertyChangeListener);
    }

    public void applyCalibration() {
        if(CALIBRATION_STATE.WAITING_FOR_APPLY_OR_RESET.equals(state)) {

            setState(CALIBRATION_STATE.AWAITING_START);
        }
    }

    public void cancelCalibration() {
        if(CALIBRATION_STATE.WARMUP.equals(state) || CALIBRATION_STATE.CALIBRATION.equals(state)
                || CALIBRATION_STATE.WAITING_FOR_APPLY_OR_RESET.equals(state)) {
            if(isHost) {
                sendMessage(ID_HOST_CANCEL);
                setState(CALIBRATION_STATE.CANCEL_IN_PROGRESS);
            } else {
                setState(CALIBRATION_STATE.CANCELED);
            }
        }
    }

    public void startCalibration() {
        if(CALIBRATION_STATE.AWAITING_START.equals(state)) {
            if(isHost) {
                sendMessage(ID_START_CALIBRATION, defaultWarmupTime,
                        defaultCalibrationTime);
            }
            setState(CALIBRATION_STATE.WARMUP);
            initAudioProcess();
            runWarmup();
        }
    }

    private static byte[] buildMessage(Object... arguments) {
        StringBuilder sb = new StringBuilder(String.valueOf(arguments[0]));
        for(Object argument : Arrays.copyOfRange(arguments, 1, arguments.length)) {
            sb.append(messageSeparator);
            sb.append(argument);
        }
        LOGGER.debug("Send message " + sb.toString());
        return sb.toString().getBytes();
    }

    private void runWarmup() {
        // Link measurement service with gui
        // Application have right now all permissions
        timeHandler = new Handler(Looper.getMainLooper(), progressHandler);
        progressHandler.start(defaultWarmupTime * 1000);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.removePropertyChangeListener(propertyChangeListener);
    }

    private void handleMessage(String message) {
        CommunicationManager communicationManager = wifiDirectHandler.getCommunicationManager();
        if(communicationManager == null) {
            LOGGER.error("Receive: "+message+", but communication manager is null");
            return;
        }
        if(message == null) {
            LOGGER.error("Receive null message");
            return;
        }
        // Check message

        StringTokenizer stringTokenizer = new StringTokenizer(message, messageSeparator);
        try {
            int id = Integer.valueOf(stringTokenizer.nextToken());
            switch (id) {
                case ID_PING:
                    // Peer receive ping
                    if (messagePingArgument.equals(stringTokenizer.nextToken())) {
                        if(wifiDirectHandler.getThisDevice() == null) {
                            // Too early
                            // TODO handle ping later
                            LOGGER.error("wifiDirectHandler is null");
                        } else {
                            sendMessage(ID_PONG, messagePingArgument,
                                    wifiDirectHandler.getThisDevice().deviceAddress);
                            setState(CALIBRATION_STATE.AWAITING_START);
                        }
                    }
                    break;
                case ID_PONG:
                    // Host receive pong
                    if(messagePingArgument.equals(stringTokenizer.nextToken())) {
                        setState(CALIBRATION_STATE.AWAITING_START);
                        listeners.firePropertyChange(PROP_PEER_READY, null, stringTokenizer.nextToken());
                    }
                    break;
                case ID_START_CALIBRATION:
                    if(!isHost && CALIBRATION_STATE.AWAITING_START.equals(state)) {
                        // Peer receive start order
                        defaultWarmupTime = Integer.valueOf(stringTokenizer.nextToken());
                        defaultCalibrationTime = Integer.valueOf(stringTokenizer.nextToken());
                        startCalibration();
                    }
                    break;
                case ID_HOST_CANCEL:
                    if(!isHost && (CALIBRATION_STATE.WARMUP.equals(state) || CALIBRATION_STATE.CALIBRATION
                            .equals(state))) {
                        cancelCalibration();
                    }
            }
        } catch (NumberFormatException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
    }


    protected void setState(CALIBRATION_STATE state) {
        CALIBRATION_STATE oldState = this.state;
        this.state = state;
        listeners.firePropertyChange(PROP_CALIBRATION_STATE, oldState, state);
        LOGGER.info("CALIBRATION_STATE " + oldState.toString() + "->" + state.toString());
    }


    public CALIBRATION_STATE getState() {
        return state;
    }

    public void addLocalWifiService() {
        if(wifiDirectHandler != null && wifiDirectHandler.getThisDevice() != null) {
            HashMap<String, String> record = new HashMap<>();
            record.put("Name", wifiDirectHandler.getThisDevice().deviceName);
            record.put("Address", wifiDirectHandler.getThisDevice().deviceAddress);
            wifiDirectHandler.addLocalService(NOISECAPTURE_CALIBRATION_SERVICE, record);
        }
    }

    protected void initiateCommunication() {
        sendMessage(ID_PING,messagePingArgument);
    }

    protected void sendMessage(int messageId, Object... args) {
        CommunicationManager communicationManager = wifiDirectHandler.getCommunicationManager();
        if(communicationManager != null) {
            Object[] execArgs = new Object[args.length + 1];
            execArgs[0] = messageId;
            System.arraycopy(args, 0, execArgs, 1, args.length);
            LOGGER.info("SendMessage " + Arrays.toString(execArgs));
            new NetworkTask(communicationManager).execute(execArgs);
        } else {
            LOGGER.error("Communication manager is null");
        }
    }

    private static class CalibrationWifiBroadcastReceiver extends BroadcastReceiver {
        CalibrationService calibrationService;
        private long lastServiceRegistering = 0;

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
                    calibrationService.setWifiP2pDevice(calibrationService.wifiDirectHandler.getThisDevice());
                    if(calibrationService.isHost) {
                        calibrationService.setState(CALIBRATION_STATE.LOOKING_FOR_PEERS);
                    } else {
                        calibrationService.setState(CALIBRATION_STATE.LOOKING_FOR_HOST);
                    }
                    if(!calibrationService.wifiDirectHandler
                            .isDiscovering()) {
                        try {
                            calibrationService.wifiDirectHandler.continuouslyDiscoverServices();
                            LOGGER.info("calibrationService.wifiDirectHandler.continuouslyDiscoverServices()");
                        } catch (NullPointerException ex) {
                            // Ignore
                        }
                    }
                    if(calibrationService.wifiDirectHandler != null &&
                            calibrationService.wifiDirectHandler.getThisDevice() != null &&
                    calibrationService.isHost && (System.currentTimeMillis()  -
                    lastServiceRegistering) > SERVICE_TIMEOUT) {
                        calibrationService.addLocalWifiService();
                        lastServiceRegistering = System.currentTimeMillis();
                        LOGGER.info("calibrationService.addLocalWifiService()");
                    }
                } else if(calibrationService.wifiDirectHandler != null &&
                        calibrationService.wifiDirectHandler.getThisDevice() == null) {
                    LOGGER.info("getThisDevice == null");
                    //calibrationService.wifiDirectHandler.unregisterP2p();
                    //calibrationService.wifiDirectHandler.unregisterP2pReceiver();
                    //calibrationService.wifiDirectHandler.registerP2p();
                    //calibrationService.wifiDirectHandler.registerP2pReceiver();
                }
            } else if(WifiDirectHandler.Action.PEERS_CHANGED.equals(action)) {
                WifiP2pDeviceList peers = intent.getParcelableExtra("peers");
                calibrationService.onPeersAvailable(peers);
                if(calibrationService.isHost && System.currentTimeMillis() - lastServiceRegistering > SERVICE_TIMEOUT) {
                    boolean peerAvailable = false;
                    for(WifiP2pDevice device : peers.getDeviceList()) {
                        if(device.status == WifiP2pDevice.AVAILABLE) {
                            peerAvailable = true;
                            break;
                        }
                    }
                    if(peerAvailable) {
                        // Renew local service if a peer is available but not connected
                        lastServiceRegistering = System.currentTimeMillis();
                        calibrationService.addLocalWifiService();
                        LOGGER.info("calibrationService.addLocalWifiService()");
                    }
                }
            } else if(WifiDirectHandler.Action.DNS_SD_SERVICE_AVAILABLE.equals(action)) {
                String serviceKey = intent.getStringExtra("serviceMapKey");
                Map<String, DnsSdService> serviceMap = calibrationService.wifiDirectHandler.getDnsSdServiceMap();
                if(serviceMap != null) {
                    DnsSdService service = serviceMap.get(serviceKey);
                    if(service != null && NOISECAPTURE_CALIBRATION_SERVICE.equals(service.getInstanceName())) {
                        if (service.getSrcDevice().status == WifiP2pDevice.CONNECTED) {
                            LOGGER.info("Service connected");
                            if(calibrationService.isHost) {
                                calibrationService.setState(CALIBRATION_STATE.PAIRED_TO_PEER);
                            } else {
                                calibrationService.setState(CALIBRATION_STATE.PAIRED_TO_HOST);
                            }
                        } else if (service.getSrcDevice().status == WifiP2pDevice.AVAILABLE) {
                            String sourceDeviceName = service.getSrcDevice().deviceName;
                            if (sourceDeviceName.equals("")) {
                                sourceDeviceName = "other device";
                            }
                            LOGGER.info("Inviting " + sourceDeviceName + " to connect");
                            if(calibrationService.wifiDirectHandler.getThisDevice() == null) {
                                calibrationService.wifiDirectHandler.unregisterP2p();
                                calibrationService.wifiDirectHandler.registerP2p();
                            }
                            calibrationService.wifiDirectHandler.initiateConnectToService(service);
                        } else {
                            LOGGER.info("Service not available: " + service.getSrcDevice().status);
                        }
                    }
                }
            } else if(WifiDirectHandler.Action.SERVICE_CONNECTED.equals(action)) {
                if(calibrationService.isHost) {
                    calibrationService.setState(CALIBRATION_STATE.PAIRED_TO_PEER);
                    // Send PING
                    calibrationService.initiateCommunication();
                } else {
                    calibrationService.setState(CALIBRATION_STATE.PAIRED_TO_HOST);
                }
            } else if(WifiDirectHandler.Action.MESSAGE_RECEIVED.equals(action)) {
                if(intent.hasExtra(WifiDirectHandler.MESSAGE_KEY)) {
                    String message = new String(intent.getByteArrayExtra(WifiDirectHandler.MESSAGE_KEY),
                            Charset.defaultCharset());
                    calibrationService.handleMessage(message);
                }
            }
        }
    }



    /**
     * Manage progress timer
     */
    public static final class ProgressHandler implements Handler.Callback {
        private CalibrationService service;
        private int delay;
        private long beginTime;

        public ProgressHandler(CalibrationService service) {
            this.service = service;
        }

        public void start(int delayMilliseconds) {
            delay = delayMilliseconds;
            beginTime = SystemClock.elapsedRealtime();
            service.timeHandler.sendEmptyMessageDelayed(0, COUNTDOWN_STEP_MILLISECOND);
        }

        @Override
        public boolean handleMessage(Message msg) {
            long currentTime = SystemClock.elapsedRealtime();
            int newProg = (int)((((beginTime + delay) - currentTime) / (float)delay) *
                    100);
            service.listeners.firePropertyChange(PROP_CALIBRATION_PROGRESSION, 0, newProg);
            if(currentTime < beginTime + delay &&
                    (service.state == CALIBRATION_STATE.CALIBRATION || service.state == CALIBRATION_STATE.WARMUP)) {
                service.timeHandler.sendEmptyMessageDelayed(0, COUNTDOWN_STEP_MILLISECOND);
            } else {
                service.onTimerEnd();
            }
            return true;
        }
    }


    private static final class NetworkTask extends AsyncTask {

        CommunicationManager communicationManager;

        public NetworkTask(CommunicationManager communicationManager) {
            this.communicationManager = communicationManager;
        }

        @Override
        protected Object doInBackground(Object[] params) {
            communicationManager.write(buildMessage(params));
            return true;
        }
    }
}
