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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Random;

/**
 * This service allow to be the host or the guest to an automated calibration while the android
 * phone GUI is in sleep mode.
 */
public class CalibrationService extends Service implements PropertyChangeListener {
    public static final String EXTRA_HOST = "MODE_HOST";

    public enum CALIBRATION_STATE {
        WIFI_DISABLED,              // Wifi is not activated (user have to activate it)
        WIFI_BUSY,                  // Wifi is in busy state (errors)
        P2P_UNSUPPORTED,            // Device is not compatible with peer to peer wifi
        LOOKING_FOR_PEERS,          // Host is broadcasting and is awaiting for calibration start
        LOOKING_FOR_HOST,           // Wifi is enabled and is currently fetching hosts
        AWAITING_HOST_SELECTION,    // One or more host has been found and the system must choose one
        AWAITING_START,             // Awaiting host for calibration start
        WARMUP,                     // Warmup is launched by host, Leq are sent to host
        CALIBRATION,                // Calibration has been started leq is stored
        WAITING_FOR_APPLY_OR_CANCEL // Calibration is done,guest send stored leqs, waiting for the validation of gain
    }

    private CALIBRATION_STATE state = CALIBRATION_STATE.WIFI_DISABLED;
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

    // Other resources
    private boolean mIsBound = false;
    private boolean isHost = false;

    private MeasurementService measurementService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            measurementService = ((MeasurementService.LocalBinder)service).getService();

            measurementService.addPropertyChangeListener(CalibrationService.this);
            if(!measurementService.isRecording()) {
                measurementService.startRecording();
            }
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
        }
    };

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
    }

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        if(!bindService(new Intent(this, MeasurementService.class), mConnection,
                Context.BIND_AUTO_CREATE)) {
            Toast.makeText(CalibrationService.this, R.string.measurement_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        } else {
            mIsBound = true;
        }
    }

    void doUnbindService() {
        if (mIsBound) {
            measurementService.removePropertyChangeListener(this);
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }



    private String generateRandomSSID(double maxSize){
        Random random = new Random();
        final String[] consonants = {"B", "BL", "C", "D", "F", "G", "GR", "H", "J", "K", "L", "M", "N", "P", "R", "S", "T", "V", "W", "X", "Y", "Z"};
        final String[] vowels = {"A", "E", "I", "O", "OO", "U"};

        StringBuilder randomString = new StringBuilder("CALIBRATION_");
        boolean consonant_toggle = true;

        while(randomString.length() < maxSize){
            final String newChars = consonant_toggle ? consonants[random.nextInt(consonants.length)] :
                    vowels[random.nextInt(vowels.length)];
            if(randomString.length() >= maxSize) {
                break;
            } else if(randomString.length() + newChars.length() <= maxSize) {
                randomString.append(newChars);
                consonant_toggle = !consonant_toggle;
            }
        }
        return randomString.toString();
    }

    public CALIBRATION_STATE getState() {
        return state;
    }
}
