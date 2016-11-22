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
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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
    private TextView textStatus;
    private ListView peersList;
    private SparseBooleanArray mSelectedItemsIds = new SparseBooleanArray();
    private DeviceListAdapter deviceListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_wifi_host);
        initDrawer();

        textDeviceLevel = (TextView) findViewById(R.id.spl_ref_measured);
        textStatus = (TextView) findViewById(R.id.calibration_state);
        textDeviceName = (TextView) findViewById(R.id.calibration_host_ssid);
        peersList = (ListView) findViewById(R.id.listview_peers);
        deviceListAdapter = new DeviceListAdapter(this);
        peersList.setAdapter(deviceListAdapter);

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
        if(bindService(new Intent(this, CalibrationService.class), mConnection,
                Context.BIND_AUTO_CREATE)) {
            mIsBound = true;
        }
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
            int message;
            switch (newState) {
                case WIFI_DISABLED:
                    message = R.string.calibration_status_p2p_disabled;
                    break;
                case WIFI_BUSY:
                    message = R.string.calibration_status_p2p_busy;
                    break;
                case P2P_UNSUPPORTED:
                    message = R.string.calibration_status_p2p_error;
                    break;
                case LOOKING_FOR_PEERS:
                    message = R.string.calibration_status_waiting_for_peers;
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
        } else if(CalibrationService.PROP_P2P_DEVICE.equals(event.getPropertyName())) {
            WifiP2pDevice p2pDevice = calibrationService.getWifiP2pDevice();
            if(p2pDevice != null) {
                textDeviceName.setText(p2pDevice.deviceName);
            } else {
                textDeviceLevel.setText("");
            }
        } else if(CalibrationService.PROP_PEER_LIST.equals(event.getPropertyName())) {
            deviceListAdapter.onPeersAvailable((WifiP2pDeviceList)event.getNewValue());
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            calibrationService = ((CalibrationService.LocalBinder)service).getService();
            calibrationService.addPropertyChangeListener(CalibrationWifiHost.this);
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
        private List<WifiP2pDevice> peers;
        private CalibrationWifiHost activity;
        private LayoutInflater mInflater;


        public DeviceListAdapter(CalibrationWifiHost activity) {
            this.activity = activity;
            mInflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers) {
            this.peers = new ArrayList<>(peers.getDeviceList());
        }

        public SparseBooleanArray getSelectedIds() {
            return activity.mSelectedItemsIds;
        }

        public void toggleSelection(int position) {
            selectView(position, !activity.mSelectedItemsIds.get(position));
        }

        public void removeSelection() {
            activity.mSelectedItemsIds.clear();
            notifyDataSetChanged();
        }

        public void selectView(int position, boolean value) {
            if (value) {
                activity.mSelectedItemsIds.put(position, true);
            } else {
                activity.mSelectedItemsIds.delete(position);
            }
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
                view = mInflater.inflate(R.layout.wifi_peers_item_layout, parent);
            }

            TextView msgRow = (TextView)view.findViewById(R.id.device_name);
            msgRow.setText(item);
            TextView statusRow = (TextView)view.findViewById(R.id.device_details);
            int stringId;
            switch (getItem(position).status) {
                case WifiP2pDevice.AVAILABLE:
                    stringId = R.string.wifi_peer_available;
                    break;
                case WifiP2pDevice.INVITED:
                    stringId = R.string.wifi_peer_invited;
                    break;
                case WifiP2pDevice.CONNECTED:
                    stringId = R.string.wifi_peer_connected;
                    break;
                case WifiP2pDevice.FAILED:
                    stringId = R.string.wifi_peer_failed;
                    break;
                case WifiP2pDevice.UNAVAILABLE:
                    stringId = R.string.wifi_peer_unavailable;
                    break;
                default:
                    stringId = R.string.wifi_peer_unknown;
            }
            statusRow.setText(activity.getText(stringId));
            return view;
        }

    }
}
