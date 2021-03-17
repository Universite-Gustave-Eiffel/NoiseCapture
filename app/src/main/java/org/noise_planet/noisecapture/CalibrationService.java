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
import android.content.Context;
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
import androidx.annotation.Nullable;

import org.noise_planet.jwarble.Configuration;
import org.noise_planet.jwarble.MessageCallback;
import org.noise_planet.jwarble.OpenWarble;
import org.orbisgis.sos.LeqStats;
import org.orbisgis.sos.SOSSignalProcessing;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This service allow to be the host or the guest to an automated calibration while the android
 * phone GUI is in sleep mode.
 */
public class CalibrationService extends Service implements PropertyChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener, MessageCallback {
    public static final String EXTRA_HOST = "MODE_HOST";


    private final int[] MODEM_FREQUENCIES;

    private static final int FREQ_START = 10;

    private static final double PINK_POWER = Short.MAX_VALUE;
    private static final double MESSAGE_RMS = Short.MAX_VALUE * 3.0/4.0;

    // properties
    public static final String PROP_CALIBRATION_STATE = "PROP_CALIBRATION_STATE";
    public static final String PROP_CALIBRATION_PROGRESSION = "PROP_CALIBRATION_PROGRESSION"; // Calibration/Warmup progression 0-100
    public static final String PROP_CALIBRATION_REF_LEVEL = "PROP_CALIBRATION_REF_LEVEL"; //
    public static final String PROP_CALIBRATION_SEND_MESSAGE = "PROP_CALIBRATION_SEND_MESSAGE";
    public static final String PROP_CALIBRATION_NEW_MESSAGE = "PROP_CALIBRATION_NEW_MESSAGE";
    public static final String PROP_CALIBRATION_RECEIVE_PITCH = "PROP_CALIBRATION_RECEIVE_PITCH";
    public static final String PROP_CALIBRATION_RECEIVE_ERROR = "PROP_CALIBRATION_RECEIVE_ERROR";

    // received ref level


    private AudioProcess audioProcess;
    private LeqStats leqStats = new LeqStats();

    private AtomicBoolean recording = new AtomicBoolean(true);
    private AtomicBoolean recordingModem = new AtomicBoolean(true);
    private AtomicBoolean canceled = new AtomicBoolean(false);

    private int defaultWarmupTime;
    private int defaultCalibrationTime;
    private boolean emitNoise;
    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationService.class);

    public static final int COUNTDOWN_STEP_MILLISECOND = 125;

    private Handler timeHandler;
    private AudioTrack audioTrack;
    private OpenWarble acousticModem;

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
    static final int PAYLOAD_SIZE = (Short.SIZE + Float.SIZE * 2) / Byte.SIZE;

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
        if(audioProcess == null) {
            canceled.set(false);
            recording.set(true);
            AcousticModemListener acousticModemListener = new AcousticModemListener(this, canceled, recordingModem);
            audioProcess = new AudioProcess(recording, canceled, acousticModemListener);
            audioProcess.setDoFastLeq(false);
            audioProcess.setDoOneSecondLeq(false);
            audioProcess.setWeightingA(true);
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
            Configuration configuration = new Configuration(PAYLOAD_SIZE, audioProcess.getRate(),
                    Configuration.DEFAULT_AUDIBLE_FIRST_FREQUENCY, 0,
                    Configuration.MULT_SEMITONE, 0.120, 0,
                    Configuration.DEFAULT_TRIGGER_SNR, Configuration.DEFAULT_DOOR_PEAK_RATIO,
                    true);

            acousticModem = new OpenWarble(configuration);

            acousticModem.setCallback(this);

            acousticModemListener.setAcousticModem(acousticModem);

            new Thread(acousticModemListener).start();
            // Start measurement
            new Thread(audioProcess).start();
        }
    }

    private void stopAudioProcess() {
        canceled.set(true);
        recording.set(false);
    }

    @Override
    public void onNewMessage(byte[] bytes, long l) {
        onNewMessage(bytes);
    }

    @Override
    public void onPitch(long l) {
        listeners.firePropertyChange(PROP_CALIBRATION_RECEIVE_PITCH, null, l / acousticModem.getConfiguration().sampleRate);
    }

    @Override
    public void onError(long l) {
        listeners.firePropertyChange(PROP_CALIBRATION_RECEIVE_ERROR, null, l / acousticModem.getConfiguration().sampleRate);
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

    public CalibrationService() {
        MODEM_FREQUENCIES = new int[8];
        int modemId = 0;
        for(int idFreq = FREQ_START; idFreq < FREQ_START + 8; idFreq++) {
            MODEM_FREQUENCIES[modemId++] = (int)ThirdOctaveBandsFiltering
                    .STANDARD_FREQUENCIES_REDUCED[idFreq];
        }
    }

    @Override
    public void onCreate() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    private int getAudioOutput() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String value = sharedPref.getString("settings_calibration_audio_output", "STREAM_MUSIC");

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

    /**
     * @param emitNoise If true, will emit pink noise on speaker while calibrating
     */
    public void setEmitNoise(boolean emitNoise) {
        this.emitNoise = emitNoise;
    }

    private void playMessage(byte[] data) {
        double[] signalDouble = acousticModem.generateSignal(1.0, data);
        double maxValue = Double.MIN_VALUE;
        for(int i = 0; i < signalDouble.length; i++) {
            maxValue = Math.max(signalDouble[i], maxValue);
        }
        short[] signal = new short[signalDouble.length];
        for(int i = 0; i < signalDouble.length; i++) {
            signal[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (signalDouble[i] / maxValue) * MESSAGE_RMS));
        }
        playAudio(signal, 1);
    }

    private void playAudio(short[] signal, int loop) {
        if(audioTrack != null) {
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
        }
        audioTrack = new AudioTrack(getAudioOutput(), audioProcess.getRate(), AudioFormat
                .CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, signal.length * (Short
                .SIZE / Byte.SIZE), AudioTrack.MODE_STATIC);
        if(loop > 1) {
            audioTrack.setLoopPoints(0, audioTrack.write(signal, 0, signal.length), loop);
        } else {
            audioTrack.write(signal, 0, signal.length);
        }
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(audioManager != null) {
            try {
                audioManager.setStreamVolume(getAudioOutput(),audioManager.getStreamMaxVolume(getAudioOutput()) / 2 , 0);
            } catch (SecurityException ex) {
                // ignore
            }
        }
        audioTrack.play();
    }

    private void playPinkNoise(double length, double power) {
        if(audioTrack != null) {
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
        }
        audioTrack = new AudioTrack(getAudioOutput(), audioProcess.getRate(), AudioFormat
                .CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, audioProcess.getRate() * (Short
                .SIZE / Byte.SIZE), AudioTrack.MODE_STREAM);
        audioTrack.play();


        new Thread(new PinkNoiseFeed(this, audioTrack, power, length)).start();
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void sendMessage(short id, float f1, float f2) {
        float[] data = {f1, f2};
        ByteBuffer byteBuffer = ByteBuffer.allocate((Short.SIZE + Float.SIZE * data.length) /
                Byte.SIZE);
        byteBuffer.putShort(id);
        for (float aData : data) {
            byteBuffer.putFloat(aData);
        }
        byte[] messageBytes = byteBuffer.array();
        listeners.firePropertyChange(CalibrationService
                .PROP_CALIBRATION_SEND_MESSAGE, null, bytesToHex(messageBytes));
        LOGGER.info("Send message: " + id + " - " + Arrays.toString(data) + " " + Arrays
                .toString(messageBytes));
        playMessage(messageBytes);
    }

    private void onNewMessage(byte... data) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        short id = byteBuffer.getShort();
        LOGGER.info("New message id:" + id);

        listeners.firePropertyChange(CalibrationService
                .PROP_CALIBRATION_NEW_MESSAGE, null, Short.toString(id) + " ("+bytesToHex(data)
                +")");
        if(id == MESSAGEID_START_CALIBRATION) {
            if(!isHost) {
                defaultWarmupTime = Math.max(1, (int)byteBuffer.getFloat());
                defaultCalibrationTime =  Math.max(1, (int)byteBuffer.getFloat());
            } else {
                audioTrack.stop();
            }
            setState(CALIBRATION_STATE.WARMUP);
            recordingModem.set(false);
            runWarmup();
        } else if(id == MESSAGEID_APPLY_REFERENCE_GAIN && state == CALIBRATION_STATE.AWAITING_FOR_APPLY_OR_RESTART) {
            if(!isHost) {
                float referenceLeq = byteBuffer.getFloat();
                double gain = Math.round((referenceLeq - leqStats.getLeqMean()) * 100.) / 100.;
                listeners.firePropertyChange(PROP_CALIBRATION_REF_LEVEL, leqStats.getLeqMean(),
                        referenceLeq);
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("settings_recording_gain", String.valueOf(gain));
                editor.putString("settings_calibration_method", String.valueOf(Storage.Record.CALIBRATION_METHODS.CalibratedSmartPhone.ordinal()));
                editor.apply();
                setState(CALIBRATION_STATE.AWAITING_START);
            }
        }
    }

    protected void onTimerEnd() {
        if (state == CALIBRATION_STATE.DELAY_BEFORE_SEND_SIGNAL) {
            // Ready to send start signal to other devices
            sendMessage(MESSAGEID_START_CALIBRATION,  defaultWarmupTime, defaultCalibrationTime);
        }else if(state == CALIBRATION_STATE.WARMUP) {
            setState(CALIBRATION_STATE.CALIBRATION);
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
                recordingModem.set(true);
            }
        } else if (state == CALIBRATION_STATE.HOST_COOLDOWN){
            sendMessage(MESSAGEID_APPLY_REFERENCE_GAIN, (float)getleq(), 0);
            setState(CALIBRATION_STATE.AWAITING_START);
            recordingModem.set(true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(audioTrack != null) {
            audioTrack.stop();
        }
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
        if(AudioProcess.PROP_SLOW_LEQ.equals(event.getPropertyName())){
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

    public void cancelCalibration() {
        setState(CALIBRATION_STATE.AWAITING_START);
        recordingModem.set(true);
    }

    public void startCalibration() {
        if(CALIBRATION_STATE.AWAITING_START.equals(state) || CALIBRATION_STATE
                .DELAY_BEFORE_SEND_SIGNAL.equals(state)) {
            if(isHost) {
                setState(CALIBRATION_STATE.DELAY_BEFORE_SEND_SIGNAL);
                if(audioTrack != null) {
                    audioTrack.stop();
                }
            }
            initAudioProcess();
            runWarmup();
        }
    }

    private void runWarmup() {
        // Link measurement service with gui
        // Application have right now all permissions
        if(state.equals(CALIBRATION_STATE.WARMUP)) {
            audioProcess.setDoOneSecondLeq(true);
            if(isHost && emitNoise) {
                playPinkNoise(defaultCalibrationTime + defaultWarmupTime, PINK_POWER);
            }
        } else {
            audioProcess.setDoOneSecondLeq(false);
        }
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

    private static class AcousticModemListener implements AudioProcess.ProcessingThread {
        private final CalibrationService calibrationService;
        private final AtomicBoolean canceled;
        private final AtomicBoolean recording;
        private OpenWarble openWarble;
        private Queue<short[]> bufferToProcess = new ConcurrentLinkedQueue<short[]>();

        public AcousticModemListener(CalibrationService calibrationService, AtomicBoolean
                canceled, AtomicBoolean recording) {
            this.calibrationService = calibrationService;
            this.canceled = canceled;
            this.recording = recording;
        }

        public void setAcousticModem(OpenWarble openWarble) {
            this.openWarble = openWarble;
        }

        @Override
        public void addSample(short[] sample) {
            if(recording.get()) {
                bufferToProcess.add(sample);
            }
        }

        @Override
        public void run() {
            while (!canceled.get() && openWarble != null) {
                while(!bufferToProcess.isEmpty()) {
                    short[] buffer = bufferToProcess.poll();
                    if(buffer != null) {
                        boolean doProcessBuffer = true;
                        while(doProcessBuffer) {
                            doProcessBuffer = false;
                            double[] samples = new double[Math.min(buffer.length, openWarble.getMaxPushSamplesLength())];
                            for (int i = 0; i < samples.length; i++) {
                                samples[i] = buffer[i] / (double)Short.MAX_VALUE;
                            }
                            openWarble.pushSamples(samples);
                            if (buffer.length > samples.length) {
                                buffer = Arrays.copyOfRange(buffer, samples.length, buffer.length);
                                doProcessBuffer = true;
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    private static class PinkNoiseFeed implements Runnable {
        private CalibrationService calibrationService;
        private AudioTrack audioTrack;
        private final int sampleBufferLength;
        private final short[] signal;
        private final int bufferSize;

        public PinkNoiseFeed(CalibrationService calibrationService, AudioTrack audioTrack,
                              double powerRMS, double maxLength) {
            this.calibrationService = calibrationService;
            this.audioTrack = audioTrack;
            sampleBufferLength = (int)(audioTrack.getSampleRate() * maxLength);
            signal = SOSSignalProcessing.makePinkNoise(sampleBufferLength, (short)powerRMS, 0);
            bufferSize = (int)(audioTrack.getSampleRate() * 0.1);
        }


        @Override
        public void run() {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            } catch (IllegalArgumentException | SecurityException ex) {
                // Ignore
            }
            int cursor = 0;
            while((CALIBRATION_STATE.WARMUP.equals(calibrationService.getState()) ||
                    CALIBRATION_STATE.CALIBRATION.equals(calibrationService.getState()) ||
                    CALIBRATION_STATE.HOST_COOLDOWN.equals(calibrationService.getState())) &&
                            !calibrationService.canceled.get()) {
                int size = Math.min(signal.length - cursor, bufferSize);
                try {
                    audioTrack.write(Arrays.copyOfRange(signal, cursor, cursor + size), 0, size);
                } catch (IllegalStateException ex) {
                    return;
                }
                cursor += size;
                if(cursor >= signal.length) {
                    cursor = 0;
                }
            }
            try {
                audioTrack.pause();
            } catch (IllegalStateException ex) {
                // AudioTrack has been unloaded
            }
        }
    }
}
