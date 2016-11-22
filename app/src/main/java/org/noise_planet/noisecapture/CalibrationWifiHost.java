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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class CalibrationWifiHost extends MainActivity implements PropertyChangeListener {

    private boolean mIsBound = false;
    private CalibrationService calibrationService;
    private TextView textDeviceLevel;
    private TextView textDeviceName;
    private ListView peersList;
    private ImageView connectionStatusImage;
    private TextView textStatus;
    private DeviceListAdapter deviceListAdapter;
    private static final int BLINK_DELAY = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_wifi_host);
        initDrawer();

        textDeviceLevel = (TextView) findViewById(R.id.spl_ref_measured);
        textDeviceName = (TextView) findViewById(R.id.calibration_host_ssid);
        peersList = (ListView) findViewById(R.id.listview_peers);
        deviceListAdapter = new DeviceListAdapter(this);
        peersList.setAdapter(deviceListAdapter);
        connectionStatusImage = (ImageView) findViewById(R.id.imageView_value_wifi_state);
        textStatus = (TextView) findViewById(R.id.calibration_state);

        if(checkAndAskWifiP2PPermissions()) {
            doBindService();
        }
    }


    void doUnbindService() {
        if (mIsBound) {
            calibrationService.removePropertyChangeListener(this);
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Intent intent = new Intent(this, CalibrationService.class);
        intent.putExtra(CalibrationService.EXTRA_HOST, true);
        if(bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            mIsBound = true;
        }
    }

    public static void applyStateChange(CalibrationService.CALIBRATION_STATE newState, ImageView connectionStatusImage, TextView textStatus) {
        int message;
        AlphaAnimation alphaAnimation;
        switch (newState) {
            case WIFI_DISABLED:
                message = R.string.calibration_status_p2p_disabled;
                connectionStatusImage.setImageResource(R.drawable.no_wifi);
                connectionStatusImage.clearAnimation();
                break;
            case WIFI_BUSY:
                message = R.string.calibration_status_p2p_busy;
                connectionStatusImage.setImageResource(R.drawable.no_wifi);
                connectionStatusImage.clearAnimation();
                break;
            case P2P_UNSUPPORTED:
                message = R.string.calibration_status_p2p_error;
                connectionStatusImage.setImageResource(R.drawable.no_wifi);
                connectionStatusImage.clearAnimation();
                break;
            case LOOKING_FOR_HOST:
                message = R.string.calibration_status_waiting_for_host;
                connectionStatusImage.setImageResource(R.drawable.wifi_lookup);
                alphaAnimation = new AlphaAnimation(1,0);
                alphaAnimation.setDuration(BLINK_DELAY);
                alphaAnimation.setInterpolator(new LinearInterpolator());
                alphaAnimation.setRepeatCount(Animation.INFINITE);
                alphaAnimation.setRepeatMode(Animation.REVERSE);
                connectionStatusImage.startAnimation(alphaAnimation);
                break;
            case LOOKING_FOR_PEERS:
                message = R.string.calibration_status_waiting_for_peers;
                connectionStatusImage.setImageResource(R.drawable.wifi_lookup);
                alphaAnimation = new AlphaAnimation(1,0);
                alphaAnimation.setDuration(BLINK_DELAY);
                alphaAnimation.setInterpolator(new LinearInterpolator());
                alphaAnimation.setRepeatCount(Animation.INFINITE);
                alphaAnimation.setRepeatMode(Animation.REVERSE);
                connectionStatusImage.startAnimation(alphaAnimation);
                break;
            case PAIRED_TO_HOST:
                message = R.string.calibration_status_paired_to_host;
                connectionStatusImage.setImageResource(R.drawable.wifi_active);
                connectionStatusImage.clearAnimation();
                break;
            case PAIRED_TO_PEER:
                message = R.string.calibration_status_paired_to_peer;
                connectionStatusImage.setImageResource(R.drawable.wifi_active);
                connectionStatusImage.clearAnimation();
                break;
            case AWAITING_START:
                message = R.string.calibration_status_waiting_for_user_start;
                break;
            case WARMUP:
                message = R.string.calibration_status_waiting_for_start_timer;
                break;
            case CALIBRATION:
                message = R.string.calibration_status_on;
                break;
            case WAITING_FOR_APPLY_OR_CANCEL:
                message = R.string.calibration_status_end;
                break;
            default:
                message = R.string.calibration_status_p2p_error;
        }
        textStatus.setText(message);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if(AudioProcess.PROP_DELAYED_STANDART_PROCESSING.equals(event.getPropertyName())){
            // New leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
            final double leq = measure.getSignalLeq();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textDeviceLevel.setText(
                            String.format(Locale.getDefault(), "%.1f", leq));
                }
            });
        } else if(CalibrationService.PROP_CALIBRATION_STATE.equals(event.getPropertyName())) {
            // Calibration service state change, inform user
            CalibrationService.CALIBRATION_STATE newState =
                    (CalibrationService.CALIBRATION_STATE)event.getNewValue();
            applyStateChange(newState, connectionStatusImage, textStatus);
        } else if(CalibrationService.PROP_P2P_DEVICE.equals(event.getPropertyName())) {
            WifiP2pDevice p2pDevice = calibrationService.getWifiP2pDevice();
            if(p2pDevice != null) {
                textDeviceName.setText(p2pDevice.deviceName);
            } else {
                textDeviceName.setText("");
            }
        } else if(CalibrationService.PROP_PEER_LIST.equals(event.getPropertyName())) {
            deviceListAdapter.onPeersAvailable((WifiP2pDeviceList)event.getNewValue());
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            calibrationService = ((CalibrationService.LocalBinder)service).getService();
            applyStateChange(calibrationService.getState(), connectionStatusImage, textStatus);
            calibrationService.addPropertyChangeListener(CalibrationWifiHost.this);
            calibrationService.init();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            calibrationService.removePropertyChangeListener(CalibrationWifiHost.this);
            calibrationService = null;
        }
    };


    @Override
    protected void onRestart() {
        super.onRestart();
        doBindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Disconnect listener from measurement
        doUnbindService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(calibrationService != null) {
            // Disconnect listener from measurement
            doUnbindService();
        }
    }


    public static class DeviceListAdapter extends BaseAdapter implements WifiP2pManager.PeerListListener {
        private List<WifiP2pDevice> peers = new ArrayList<>();
        private CalibrationWifiHost activity;
        private LayoutInflater mInflater;


        public DeviceListAdapter(CalibrationWifiHost activity) {
            this.activity = activity;
            mInflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers) {
            this.peers = new ArrayList<>(peers.getDeviceList());
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return peers.size();
        }

        @Override
        public WifiP2pDevice getItem(int position) {
            return peers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;  // old view to re-use if possible. Useful for Heterogeneous list with diff item view type.
            String item = getItem(position).deviceName;

            if( view == null ){
                view = mInflater.inflate(R.layout.wifi_peers_item_layout, null);
            }

            TextView msgRow = (TextView)view.findViewById(R.id.device_name);
            msgRow.setText(item);
            TextView statusRow = (TextView)view.findViewById(R.id.device_details);
            ImageView statusIcon = (ImageView) view.findViewById(R.id.icon);
            int stringId;
            AlphaAnimation animation;
            switch (getItem(position).status) {
                case WifiP2pDevice.AVAILABLE:
                    // yellow
                    stringId = R.string.wifi_peer_available;
                    statusIcon.setImageResource(R.drawable.wifi_lookup);
                    statusIcon.clearAnimation();
                    break;
                case WifiP2pDevice.INVITED:
                    // yellow blink
                    stringId = R.string.wifi_peer_invited;
                    statusIcon.setImageResource(R.drawable.wifi_lookup);
                    animation = new AlphaAnimation(1, 0);
                    animation.setInterpolator(new LinearInterpolator());
                    animation.setRepeatCount(Animation.INFINITE);
                    animation.setRepeatMode(Animation.REVERSE);
                    animation.setDuration(BLINK_DELAY);
                    statusIcon.startAnimation(animation);
                    break;
                case WifiP2pDevice.CONNECTED:
                    // green
                    statusIcon.setImageResource(R.drawable.wifi_active);
                    animation = new AlphaAnimation(1, 0);
                    animation.setInterpolator(new LinearInterpolator());
                    animation.setRepeatCount(Animation.INFINITE);
                    animation.setRepeatMode(Animation.REVERSE);
                    animation.setDuration(BLINK_DELAY);
                    statusIcon.startAnimation(animation);
                    stringId = R.string.wifi_peer_paired;
                    break;
                case WifiP2pDevice.FAILED:
                    // red
                    statusIcon.setImageResource(R.drawable.no_wifi);
                    statusIcon.clearAnimation();
                    stringId = R.string.wifi_peer_failed;
                    break;
                case WifiP2pDevice.UNAVAILABLE:
                    // red
                    statusIcon.setImageResource(R.drawable.no_wifi);
                    statusIcon.clearAnimation();
                    stringId = R.string.wifi_peer_unavailable;
                    break;
                default:
                    // red
                    statusIcon.setImageResource(R.drawable.no_wifi);
                    statusIcon.clearAnimation();
                    stringId = R.string.wifi_peer_unknown;
            }
            statusRow.setText(activity.getText(stringId));
            return view;
        }

    }
}
