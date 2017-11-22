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
import org.orbisgis.sos.FFTSignalProcessing;
import org.orbisgis.sos.LeqStats;
import org.orbisgis.sos.SOSSignalProcessing;
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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This service allow to be the host or the guest to an automated calibration while the android
 * phone GUI is in sleep mode.
 */
public class CalibrationService extends Service implements PropertyChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String EXTRA_HOST = "MODE_HOST";


    private final int[] MODEM_FREQUENCIES;

    private static final int FREQ_START = 10;

    private static final double PINK_POWER = Short.MAX_VALUE;
    private static final short MESSAGE_RMS = 450;

    // properties
    public static final String PROP_CALIBRATION_STATE = "PROP_CALIBRATION_STATE";
    public static final String PROP_CALIBRATION_PROGRESSION = "PROP_CALIBRATION_PROGRESSION"; // Calibration/Warmup progression 0-100
    public static final String PROP_CALIBRATION_REF_LEVEL = "PROP_CALIBRATION_REF_LEVEL"; //
    // received ref level


    private AudioProcess audioProcess;
    private LeqStats leqStats = new LeqStats();

    private AtomicBoolean recording = new AtomicBoolean(true);
    private AtomicBoolean recordingModem = new AtomicBoolean(true);
    private AtomicBoolean canceled = new AtomicBoolean(false);

    private static final double WORD_TIME_LENGTH = 0.200;

    private int defaultWarmupTime;
    private int defaultCalibrationTime;
    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationService.class);

    public static final int COUNTDOWN_STEP_MILLISECOND = 125;
    // Empty audio before playing signal
    public static final int EMPTY_AUDIO_LENGTH = 2000;
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
        if(audioProcess == null) {
            canceled.set(false);
            recording.set(true);
            AcousticModemListener acousticModemListener = new AcousticModemListener(this, canceled, recordingModem);
            audioProcess = new AudioProcess(recording, canceled, acousticModemListener);
            audioProcess.setDoFastLeq(false);
            audioProcess.setDoOneSecondLeq(false);
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

            acousticModemListener.setFftSignalProcessing(new FFTSignalProcessing(audioProcess.getRate
                    (), ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, (int)(AcousticIndicators.TIMEPERIOD_FAST *
                    audioProcess.getRate
                            ())));

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
        final int paddingLength = (int)((EMPTY_AUDIO_LENGTH / 1000.) * audioProcess.getRate());
        short[] signal = new short[paddingLength + acousticModem.getSignalLength(data, 0, data
                .length)];
        acousticModem.wordsToSignal(data, 0,
                data.length, signal, paddingLength, MESSAGE_RMS);
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
    private void sendMessage(short id, float... data) {
        ByteBuffer byteBuffer = ByteBuffer.allocate((Short.SIZE + Float.SIZE * data.length) /
                Byte.SIZE);
        byteBuffer.putShort(id);
        for(int i=0; i<data.length; i++) {
            byteBuffer.putFloat(data[i]);
        }
        try {
            byte[] messageBytes = acousticModem.encode(byteBuffer.array());
            LOGGER.info("Send message: " + id + " - " + Arrays.toString(data) + " " + Arrays
                    .toString(messageBytes));
            playMessage(messageBytes);
        } catch (IOException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
    }

    private void onNewMessage(byte... data) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        short id = byteBuffer.getShort();
        LOGGER.info("New message id:" + id);
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
            sendMessage(MESSAGEID_APPLY_REFERENCE_GAIN, (float)getleq());
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
            if(isHost) {
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
        private AcousticModem acousticModem;
        private FFTSignalProcessing fftSignalProcessing = null;
        private Queue<short[]> bufferToProcess = new ConcurrentLinkedQueue<short[]>();
        private long processedSamples = 0;
        private ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private long lastReceivedWordTime = 0;
        private static final int wordDeprecationTimeFactor = 10;
        private int wordDeprecationTime;

        public AcousticModemListener(CalibrationService calibrationService, AtomicBoolean
                canceled, AtomicBoolean recording) {
            this.calibrationService = calibrationService;
            this.canceled = canceled;
            this.recording = recording;
        }

        public void setFftSignalProcessing(FFTSignalProcessing fftSignalProcessing) {
            this.fftSignalProcessing = fftSignalProcessing;
        }

        public void setAcousticModem(AcousticModem acousticModem) {
            this.acousticModem = acousticModem;
            wordDeprecationTime = (int)(acousticModem.getSettings().wordTimeLength *
                    wordDeprecationTimeFactor * 1000);
        }

        @Override
        public void addSample(short[] sample) {
            if(recording.get()) {
                bufferToProcess.add(sample);
            }
        }

        @Override
        public void run() {
            while (!canceled.get() && fftSignalProcessing != null) {
                while(!bufferToProcess.isEmpty()) {
                    short[] buffer = bufferToProcess.poll();
                    if(buffer.length <= fftSignalProcessing.getWindowSize() - processedSamples) {
                        // Good buffer size, use it
                        fftSignalProcessing.addSample(buffer);
                        processedSamples+=buffer.length;
                    } else {
                        // Buffer is too large for the window
                        // Split the buffer in multiple parts
                        int cursor = 0;
                        while(cursor < buffer.length) {
                            int sampleLen = (int)Math.min(buffer.length - cursor, fftSignalProcessing
                                    .getWindowSize() - processedSamples);
                            short[] samples = Arrays.copyOfRange(buffer, cursor, cursor + sampleLen);
                            cursor += samples.length;
                            fftSignalProcessing.addSample(samples);
                            processedSamples+=sampleLen;
                        }
                    }
                    if(processedSamples >= fftSignalProcessing
                            .getWindowSize()) {
                        FFTSignalProcessing.ProcessingResult measure = fftSignalProcessing
                                .processSample
                                (true, false, false);
                        Byte word = acousticModem.spectrumToWord(acousticModem
                                .filterSpectrum(Arrays.copyOfRange(measure.getdBaLevels(), FREQ_START, FREQ_START + 8)));
                        if(word != null) {
                            long curTime = System.currentTimeMillis();
                            if( curTime - lastReceivedWordTime > wordDeprecationTime ) {
                                LOGGER.info("Clear audio modem cache " + wordDeprecationTime + " " +
                                        "ms");
                                bytes.reset();
                            }
                            LOGGER.info("Receive audio byte :" + word);
                            bytes.write(word);
                            lastReceivedWordTime = curTime;
                            byte[] message = bytes.toByteArray();
                            while(message.length >= AcousticModem.CRC_SIZE + (Short.SIZE / Byte
                                    .SIZE + Float.SIZE / Byte.SIZE)) {
                                if(acousticModem.isMessageCheck(message)) {
                                    calibrationService.onNewMessage(bytes.toByteArray());
                                    bytes.reset();
                                    break;
                                } else {
                                    // Skip old messages that may be not well understood
                                    message = Arrays.copyOfRange(message, 1, message.length);
                                }
                            }
                        }
                        processedSamples = 0;
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
                    CALIBRATION_STATE.CALIBRATION.equals(calibrationService.getState())) &&
                            !calibrationService.canceled.get()) {
                int size = Math.min(signal.length - cursor, bufferSize);
                audioTrack.write(Arrays.copyOfRange(signal, cursor, cursor+size), 0, size);
                cursor += size;
                if(cursor >= signal.length) {
                    cursor = 0;
                }
            }
            audioTrack.pause();
        }
    }
}
