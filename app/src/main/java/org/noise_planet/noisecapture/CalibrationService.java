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
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.widget.Toast;

import org.noise_planet.acousticmodem.AcousticModem;
import org.noise_planet.acousticmodem.Settings;
import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.LeqStats;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This service allow to be the host or the guest to an automated calibration while the android
 * phone GUI is in sleep mode.
 */
public class CalibrationService extends Service implements PropertyChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String EXTRA_HOST = "MODE_HOST";


    private static final int[] MODEM_FREQUENCIES = new int[]{
            (int) ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[15],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[16],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[17],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[18],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[19],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[20],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[21],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[22]};

    private static final int FREQ_START = Arrays.binarySearch(ThirdOctaveBandsFiltering
            .STANDARD_FREQUENCIES_REDUCED, MODEM_FREQUENCIES[0]);

    // properties
    public static final String PROP_CALIBRATION_STATE = "PROP_CALIBRATION_STATE";
    public static final String PROP_CALIBRATION_PROGRESSION = "PROP_CALIBRATION_PROGRESSION"; // Calibration/Warmup progression 0-100
    public static final String PROP_CALIBRATION_REF_LEVEL = "PROP_CALIBRATION_REF_LEVEL"; //
    // received ref level

    private ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    private AudioProcess audioProcess;
    private LeqStats leqStats = new LeqStats();

    private AtomicBoolean recording = new AtomicBoolean(true);
    private AtomicBoolean canceled = new AtomicBoolean(false);

    private static final double WORD_TIME_LENGTH = AcousticIndicators.TIMEPERIOD_FAST * 2;

    private int defaultWarmupTime;
    private int defaultCalibrationTime;
    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationService.class);

    public static final int COUNTDOWN_STEP_MILLISECOND = 125;
    private Handler timeHandler;
    private AudioTrack audioTrack;
    private AcousticModem acousticModem;

    public enum CALIBRATION_STATE {
        AWAITING_START,                // Awaiting signal for start of warmup
        DELAY_BEFORE_SEND_SIGNAL,      // Delay after the user click on launch calibration
        WARMUP,                        // Warmup is launched by host
        CALIBRATION,                   // Calibration has been started.
        HOST_COOLDOWN,                 // Wait time after end of calibration and send of level
        AWAITING_FOR_APPLY_OR_RESTART, // Calibration is done waiting for reference device level
    }

    public static final short MESSAGEID_START_CALIBRATION = 1;
    public static final short MESSAGEID_APPLY_REFERENCE_GAIN = MESSAGEID_START_CALIBRATION + 1;

    public static final String SETTINGS_CALIBRATION_WARMUP_TIME = "settings_calibration_warmup_time";
    public static final String SETTINGS_CALIBRATION_TIME = "settings_calibration_time";

    private CALIBRATION_STATE state = CALIBRATION_STATE.AWAITING_START;

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
        audioProcess.setDoFastLeq(true);
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

        // Init audio modem

        acousticModem = new AcousticModem(new Settings(audioProcess.getRate(), WORD_TIME_LENGTH,
                Settings.wordsFrom8frequencies(MODEM_FREQUENCIES)));

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
    private boolean isHost = false;

    @Override
    public void onCreate() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    private double dbToRms(double db) {
        return (Math.pow(10, db / 20.)/(Math.pow(10, 90./20.))) * 2500;
    }

    private int getAudioOutput() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String value = sharedPref.getString("settings_calibration_audio_output", "STREAM_RING");

        if("STREAM_VOICE_CALL".equals(value)) {
            return AudioManager.STREAM_VOICE_CALL;
        } else if("STREAM_SYSTEM".equals(value)) {
            return AudioManager.STREAM_SYSTEM;
        } else if("STREAM_RING".equals(value)) {
            return AudioManager.STREAM_RING;
        } else if("STREAM_MUSIC".equals(value)) {
            return AudioManager.STREAM_MUSIC;
        } else if("STREAM_ALARM".equals(value)) {
            return AudioManager.STREAM_ALARM;
        } else if("STREAM_NOTIFICATION".equals(value)) {
            return AudioManager.STREAM_NOTIFICATION;
        } else if("STREAM_DTMF".equals(value)) {
            return AudioManager.STREAM_DTMF;
        } else {
            return AudioManager.STREAM_RING;
        }
    }

    private void playMessage(byte[] data) {
        double rms = dbToRms(99);
        short[] signal = new short[acousticModem.getSignalLength(data, 0, data.length)];
        acousticModem.wordsToSignal(data, 0,
                data.length, signal, 0, (short)rms);
        if (audioTrack == null) {
            audioTrack = new AudioTrack(getAudioOutput(), 44100, AudioFormat
                    .CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, signal.length * (Short
                    .SIZE / Byte.SIZE), AudioTrack.MODE_STATIC);
        } else {
            try {
                audioTrack.pause();
                audioTrack.flush();
            } catch (IllegalStateException ex) {
                // Ignore
            }
        }
        audioTrack.write(signal, 0, signal.length);
        //audioTrack.setLoopPoints(0, audioTrack.write(data, 0, data.length), -1);
        audioTrack.play();
    }

    private void sendMessage(short id, float... data) {
        ByteBuffer byteBuffer = ByteBuffer.allocate((Short.SIZE + Float.SIZE * data.length) /
                Byte.SIZE);
        byteBuffer.putShort(id);
        for(int i=0; i<data.length; i++) {
            byteBuffer.putFloat(data[i]);
        }
        try {
            playMessage(acousticModem.encode(byteBuffer.array()));
        } catch (IOException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
    }

    private void onNewMessage(byte... data) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        int id = byteBuffer.getInt();
        if(id == MESSAGEID_START_CALIBRATION) {
            if(!isHost) {
                defaultWarmupTime = Math.max(1, (int)byteBuffer.getFloat());
                defaultCalibrationTime =  Math.max(1, (int)byteBuffer.getFloat());
            }
            setState(CALIBRATION_STATE.WARMUP);
            runWarmup();
        } else if(id == MESSAGEID_APPLY_REFERENCE_GAIN) {
            if(!isHost) {
                float referenceLeq = byteBuffer.getFloat();
                double gain = Math.round((referenceLeq - leqStats.getLeqMean()) * 100.) / 100.;
                listeners.firePropertyChange(PROP_CALIBRATION_REF_LEVEL, leqStats.getLeqMean(),
                        referenceLeq);
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("settings_recording_gain", String.valueOf(gain));
                editor.apply();
                Toast.makeText(getApplicationContext(),
                        getString(R.string.calibrate_done, gain), Toast.LENGTH_LONG).show();
                setState(CALIBRATION_STATE.AWAITING_FOR_APPLY_OR_RESTART);
            }
        }
    }

    protected void onTimerEnd() {
        if (state == CALIBRATION_STATE.DELAY_BEFORE_SEND_SIGNAL) {
            // Ready to send start signal to other devices
            sendMessage(MESSAGEID_START_CALIBRATION,  defaultWarmupTime, defaultCalibrationTime);
        }else if(state == CALIBRATION_STATE.WARMUP) {
            setState(CALIBRATION_STATE.CALIBRATION);
            audioProcess.setDoFastLeq(false);
            // Start calibration
            leqStats = new LeqStats();
            progressHandler.start(defaultCalibrationTime * 1000);
        } else if(state == CALIBRATION_STATE.CALIBRATION) {
            if(isHost) {
                setState(CALIBRATION_STATE.HOST_COOLDOWN);
                progressHandler.start(defaultWarmupTime * 1000);
            } else {
                // Guest waiting for host reference level or relaunch of measurement
                setState(CALIBRATION_STATE.AWAITING_FOR_APPLY_OR_RESTART);
                audioProcess.setDoFastLeq(true);
            }
        } else if (state == CALIBRATION_STATE.HOST_COOLDOWN){
            // TODO send reference noise level
            sendMessage(MESSAGEID_APPLY_REFERENCE_GAIN, (float)getleq());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAudioProcess();
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
        } else if(AudioProcess.PROP_MOVING_SPECTRUM.equals(event.getPropertyName())) {
            // new 150 ms leq
            AudioProcess.AudioMeasureResult measure =
                    (AudioProcess.AudioMeasureResult) event.getNewValue();
            Byte word = acousticModem.spectrumToWord(acousticModem.filterSpectrum(Arrays.copyOfRange
                    (measure.getLeqs(), FREQ_START, FREQ_START + 8)));
            if(word != null) {
                bytes.write(word);
                byte[] message = bytes.toByteArray();
                while(message.length > AcousticModem.CRC_SIZE) {
                    if(acousticModem.isMessageCheck(message)) {
                        bytes.reset();
                        onNewMessage(bytes.toByteArray());
                        break;
                    } else {
                        // Skip old messages that may be not well understood
                        message = Arrays.copyOfRange(message, 1, message.length);
                    }
                }
            }
        }
        listeners.firePropertyChange(event);
    }

    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.addPropertyChangeListener(propertyChangeListener);
    }

    public void startCalibration() {
        if(CALIBRATION_STATE.AWAITING_START.equals(state)) {
            if(isHost) {
                setState(CALIBRATION_STATE.DELAY_BEFORE_SEND_SIGNAL);
            }
            initAudioProcess();
            runWarmup();
        }
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


    protected void setState(CALIBRATION_STATE state) {
        CALIBRATION_STATE oldState = this.state;
        this.state = state;
        listeners.firePropertyChange(PROP_CALIBRATION_STATE, oldState, state);
        LOGGER.info("CALIBRATION_STATE " + oldState.toString() + "->" + state.toString());
    }


    public CALIBRATION_STATE getState() {
        return state;
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
                    (service.state == CALIBRATION_STATE.CALIBRATION || service.state ==
                            CALIBRATION_STATE.WARMUP || service.state == CALIBRATION_STATE
                            .DELAY_BEFORE_SEND_SIGNAL || service.state == CALIBRATION_STATE.HOST_COOLDOWN)) {
                service.timeHandler.sendEmptyMessageDelayed(0, COUNTDOWN_STEP_MILLISECOND);
            } else {
                service.onTimerEnd();
            }
            return true;
        }
    }
}
