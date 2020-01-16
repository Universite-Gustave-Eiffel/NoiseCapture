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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.FFTSignalProcessing;
import org.orbisgis.sos.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processing thread of packets of Audio signal
 */
public class AudioProcess implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioProcess.class);
    private AtomicBoolean recording;
    private AtomicBoolean canceled;
    private boolean doFastLeq = true;
    private boolean doOneSecondLeq = true;
    private final int bufferSize;
    private final int encoding;
    private final int rate;
    private final int audioChannel;
    public enum STATE { WAITING, PROCESSING,WAITING_END_PROCESSING, CLOSED }
    private STATE currentState = STATE.WAITING;
    private PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    public static final String PROP_FAST_LEQ = "PROP_MS";
    public static final String PROP_SLOW_LEQ = "PROP_DSP";
    public static final String PROP_STATE_CHANGED = "PROP_STATE_CHANGED";
    // 1s level evaluation for upload to server
    private final LeqProcessingThread fastLeqProcessing;
    private final LeqProcessingThread slowLeqProcessing;
    private final ProcessingThread customLeqProcessing;


    public static final int REALTIME_SAMPLE_RATE_LIMITATION = 16000;
    public static final double[] realTimeCenterFrequency = FFTSignalProcessing.computeFFTCenterFrequency(REALTIME_SAMPLE_RATE_LIMITATION);
    private float gain = 1;
    private boolean hasGain = false;
    private boolean hannWindowFast = false;
    private boolean hannWindowOneSecond = true;




    public AudioProcess(AtomicBoolean recording, AtomicBoolean canceled) {
        this(recording, canceled, null);
    }

    /**
     * Constructor
     * @param recording Recording state
     * @param canceled Canceled state
     * @param customLeqProcessing Custom receiver of sound signals
     */
    public AudioProcess(AtomicBoolean recording, AtomicBoolean canceled, ProcessingThread
            customLeqProcessing) {
        this.recording = recording;
        this.canceled = canceled;
        this.customLeqProcessing = customLeqProcessing;
        final int[] mSampleRates = new int[] {44100}; // AWeigting coefficient are based on 44100
        // Hz sampling rate, so we do not support other samplings (22050, 16000, 11025,8000)
        final int[] encodings = new int[] { AudioFormat.ENCODING_PCM_16BIT , AudioFormat.ENCODING_PCM_8BIT };
        final short[] audioChannels = new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO };
        for (int tryRate : mSampleRates) {
            for (int tryEncoding : encodings) {
                for(short tryAudioChannel : audioChannels) {
                    int tryBufferSize = AudioRecord.getMinBufferSize(tryRate,
                            tryAudioChannel, tryEncoding);
                    if (tryBufferSize != AudioRecord.ERROR_BAD_VALUE) {
                        // Take a higher buffer size in order to get a smooth recording under load
                        // avoiding Buffer overflow error on AudioRecord side.
                        bufferSize = Math.max(tryBufferSize,
                                (int)(AcousticIndicators.TIMEPERIOD_FAST * tryRate));
                        encoding = tryEncoding;
                        audioChannel = tryAudioChannel;
                        rate = tryRate;
                        this.fastLeqProcessing = new LeqProcessingThread(this,
                                AcousticIndicators.TIMEPERIOD_FAST, true,
                                hannWindowFast ? FFTSignalProcessing.WINDOW_TYPE.TUKEY :
                                        FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, PROP_FAST_LEQ, true);
                        this.slowLeqProcessing = new LeqProcessingThread(this,
                                AcousticIndicators.TIMEPERIOD_SLOW, true,
                                hannWindowOneSecond ? FFTSignalProcessing.WINDOW_TYPE.TUKEY :
                                        FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR,
                                PROP_SLOW_LEQ, false);
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("This device is not compatible");
    }
    public STATE getCurrentState() {
        return currentState;
    }

    public void setHannWindowFast(boolean hannWindowFast) {
        this.hannWindowFast = hannWindowFast;
        fastLeqProcessing.setWindowType(hannWindowFast ? FFTSignalProcessing.WINDOW_TYPE.TUKEY :
                FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR);
    }

    public boolean isHannWindowFast() {
        return hannWindowFast;
    }

    public boolean isHannWindowOneSecond() {
        return hannWindowOneSecond;
    }

    public void setHannWindowOneSecond(boolean hannWindowOneSecond) {
        this.hannWindowOneSecond = hannWindowOneSecond;
        fastLeqProcessing.setWindowType(hannWindowOneSecond ? FFTSignalProcessing.WINDOW_TYPE.TUKEY :
                FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR);
    }

    public void setDoFastLeq(boolean doFastLeq) {
        this.doFastLeq = doFastLeq;
        if(!doFastLeq) {
            fastLeqProcessing.bufferToProcess.clear();
        }
    }

    public void setDoOneSecondLeq(boolean doOneSecondLeq) {
        this.doOneSecondLeq = doOneSecondLeq;
    }

    public void setWeightingA(boolean weightingA) {
        fastLeqProcessing.setAweighting(weightingA);
        slowLeqProcessing.setAweighting(weightingA);
    }

    /**
     * Multiply the signal by the provided factor
     * @param gain Factor on signal, 1 for no gain
     */
    public void setGain(float gain) {
        this.gain = gain;
        this.hasGain = Float.compare(1, gain) != 0;
    }

    private void setCurrentState(STATE state) {
        STATE oldState = currentState;
        currentState = state;
        listeners.firePropertyChange(PROP_STATE_CHANGED, oldState, currentState );
        LOGGER.info("AudioRecord : "+oldState+" -> "+state.toString());
    }
    /**
     * @return Frequency feed in {@link AudioProcess#PROP_FAST_LEQ} {@link PropertyChangeEvent#getNewValue()}
    */
    public double[] getRealtimeCenterFrequency() {
        return realTimeCenterFrequency;
    }

    /**
     * @return Frequency feed in {@link AudioProcess#PROP_SLOW_LEQ} {@link PropertyChangeEvent#getNewValue()}
     */
    public double[] getDelayedCenterFrequency() {
        return realTimeCenterFrequency;
    }

    public int getRemainingNotProcessSamples() {
        return slowLeqProcessing.bufferToProcess.size() + fastLeqProcessing.bufferToProcess.size();
    }

    /**
     * @return The current delay between the audio input and the processed output
     */
    public long getFastNotProcessedMilliseconds() {
        return (fastLeqProcessing.getPushedSamples() - fastLeqProcessing.getProcessedSamples()) / (rate / 1000);
    }

    /**
     * @return Currently pushed samples
     */
    public long getSlowProcessedSamples() {
        if(slowLeqProcessing != null) {
            return slowLeqProcessing.getPushedSamples();
        } else {
            return 0;
        }
    }


    /**
     * @return Third octave SPL up to 8Khz (4 Khz if the phone support 8Khz only)
     */
    public float[] getThirdOctaveFrequencySPL() {
        return fastLeqProcessing.getThirdOctaveFrequencySPL();
    }

    private AudioRecord createAudioRecord() {
        // Source:
        //  section 5.3 of the Android 4.0 Compatibility Definition
        // https://source.android.com/compatibility/4.0/android-4.0-cdd.pdf
        // Using VOICE_RECOGNITION
        // Noise reduction processing, if present, is disabled.
        // Except for 5.0+ where android.media.audiofx.NoiseSuppressor could be use to cancel such processing
        // Automatic gain control, if present, is disabled.
        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
            return new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    rate, audioChannel,
                    encoding, bufferSize);
        } else {
            return null;
        }
    }

    public double getFFTDelay() {
        return fastLeqProcessing.getWindow().getWindowTime();
    }

    @Override
    public void run() {
        try {
            setCurrentState(STATE.PROCESSING);
            AudioRecord audioRecord = createAudioRecord();
            short[] buffer;
            if (recording.get() && audioRecord != null) {
                try {
                    try {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    } catch (IllegalArgumentException | SecurityException ex) {
                        // Ignore
                    }
                    new Thread(fastLeqProcessing).start();
                    new Thread(slowLeqProcessing).start();
                    audioRecord.startRecording();

                    while (recording.get()) {
                        buffer = new short[bufferSize];
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if(read < buffer.length) {
                            buffer = Arrays.copyOfRange(buffer, 0, read);
                        }
                        if (hasGain) {
                            // In place multiply
                            for (int i = 0; i < buffer.length; i++) {
                                buffer[i] = (short) (Math.max(Math.min(buffer[i] * gain, Short.MAX_VALUE), Short.MIN_VALUE));
                            }
                        }
                        if(doFastLeq) {
                            fastLeqProcessing.addSample(buffer);
                        }
                        if(doOneSecondLeq) {
                            slowLeqProcessing.addSample(buffer);
                        }
                        if(customLeqProcessing != null) {
                            customLeqProcessing.addSample(buffer);
                        }
                    }
                    setCurrentState(STATE.WAITING_END_PROCESSING);
                    while (fastLeqProcessing.isProcessing() || slowLeqProcessing.isProcessing()) {
                        Thread.sleep(10);
                    }
                } catch (Exception ex) {
                    Log.e("tag_record", "Error while recording", ex);
                } finally {
                    if(audioRecord.getState() != AudioRecord.STATE_UNINITIALIZED) {
                        audioRecord.stop();
                        audioRecord.release();
                    }
                }
            }
        } finally {
            setCurrentState(STATE.CLOSED);
        }
    }

    /**
     * @return In the array fftResultLvl, how many frequency cover one cell.
     */
    public double getFFTFreqArrayStep() {
        return fastLeqProcessing.getFFTFreqArrayStep();
    }
    /**
     * @return Listener manager
     */
    public PropertyChangeSupport getListeners() {
        return listeners;
    }

    /**
     * @return Fast refreshed 1s leq
     */
    double getLeq(boolean movingLeq) {
        if(doOneSecondLeq && movingLeq) {
            return slowLeqProcessing.computeLeq();
        } else if(doFastLeq && !movingLeq){
            return fastLeqProcessing.getLeq();
        } else {
            return 0;
        }
    }

    public int getRate() {
        return rate;
    }

    public interface  ProcessingThread extends Runnable {
        /**
         * Add Signed Short sound samples
         * @param sample
         */
        void addSample(short[] sample);
    }

    public static final class LeqProcessingThread implements ProcessingThread {
        private Queue<short[]> bufferToProcess = new ConcurrentLinkedQueue<short[]>();
        private final AudioProcess audioProcess;
        private AtomicBoolean processing = new AtomicBoolean(false);
        private Window window;
        private double leq = 0;
        private String propertyName;
        private double timePeriod;
        private boolean Aweighting;
        private long pushedSamples = 0;
        private long processedSamples = 0;
        private int lastPushIndex = 0;

        // Output only frequency response on this sample rate on the real time result (center + upper band)
        private float[] thirdOctaveSplLevels;

        public LeqProcessingThread(AudioProcess audioProcess, double timePeriod, boolean Aweighting,
                                   FFTSignalProcessing.WINDOW_TYPE window_type, String propertyName, boolean outputSpectrogram) {
            this.audioProcess = audioProcess;
            this.propertyName = propertyName;
            this.timePeriod = timePeriod;
            this.Aweighting = Aweighting;
            this.window = new Window(window_type,
                    audioProcess.getRate(), audioProcess.getRealtimeCenterFrequency(), timePeriod,
                    Aweighting, FFTSignalProcessing.DB_FS_REFERENCE, outputSpectrogram);
            thirdOctaveSplLevels = new float[audioProcess.getRealtimeCenterFrequency().length];
        }

        public void setWindowType(FFTSignalProcessing.WINDOW_TYPE windowType) {
            if(windowType != window.getWindowType()) {
                this.window = new Window(windowType,
                        audioProcess.getRate(), audioProcess.getRealtimeCenterFrequency(), timePeriod,
                        window.isAWeighting(), FFTSignalProcessing.DB_FS_REFERENCE, window.isOutputThinFrequency());
                lastPushIndex = 0;
            }
        }

        /**
         * @return Samples processed by FFT
         */
        public long getProcessedSamples() {
            return processedSamples;
        }

        public void setAweighting(boolean Aweighting) {
            if(Aweighting != this.Aweighting) {
                this.Aweighting = Aweighting;
                window.setaWeighting(Aweighting);
            }
        }

        public Window getWindow() {
            return window;
        }

        /**
         * @return In the array fftResultLvl, how many frequency cover one cell.
         */
        public double getFFTFreqArrayStep() {
            return 1 / timePeriod;
        }

        public double getLeq() {
            return leq;
        }


        /**
         * @return Compute leq from the last pushed 1s
         */
        public double computeLeq() {
            return window.computeWindowLeq();
        }

        /**
         * @return Third octave SPL up to 8Khz (4 Khz if the phone support 8Khz only)
         */
        public float[] getThirdOctaveFrequencySPL() {
            return thirdOctaveSplLevels;
        }

        public void addSample(short[] sample) {
            bufferToProcess.add(sample);
            pushedSamples+=sample.length;
        }

        public long getPushedSamples() {
            return pushedSamples;
        }

        private void processWindow() {
            lastPushIndex = window.getWindowIndex();
            FFTSignalProcessing.ProcessingResult  result = window.getLastWindowMean();
            window.cleanWindows();
            thirdOctaveSplLevels = result.getdBaLevels();
            // Compute leq
            leq = result.getGlobaldBaValue();
            // Compute record time
            // Take current time minus the computed delay of the measurement
            long beginRecordTime = System.currentTimeMillis() -
                    (long) (((pushedSamples - result.getId())  /
                            (double) audioProcess.getRate()) * 1000);
            audioProcess.listeners.firePropertyChange(propertyName,
                    null,
                    new AudioMeasureResult(result,  beginRecordTime));
        }

        private void processSample(short[] buffer) {
            window.pushSample(buffer);
            if (window.getWindowIndex() != lastPushIndex) {
                processWindow();
            }
            processedSamples += buffer.length;
        }

        public boolean isProcessing() {
            return processing.get();
        }

        @Override
        public void run() {
            try {
                while (audioProcess.currentState != STATE.WAITING_END_PROCESSING &&
                        !audioProcess.canceled.get()
                        && audioProcess.currentState != STATE.CLOSED) {
                    while (!bufferToProcess.isEmpty() && !audioProcess.canceled.get()) {
                        processing.set(true);
                        short[] buffer = bufferToProcess.poll();
                        if(buffer != null) {
                            if (buffer.length <= window.getMaximalBufferSize()) {
                                // Good buffer size, use it
                                processSample(buffer);
                            } else {
                                // Buffer is too large for the window
                                // Split the buffer in multiple parts
                                int cursor = 0;
                                while (cursor < buffer.length) {
                                    int sampleLen = Math.min(window.getMaximalBufferSize(), buffer.length - cursor);
                                    short[] samples = Arrays.copyOfRange(buffer, cursor, cursor + sampleLen);
                                    cursor += samples.length;
                                    processSample(samples);
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
                // Gather incomplete window
                if(!window.isCacheEmpty()) {
                    processWindow();
                }
            } finally {
                processing.set(false);
            }
        }
    }

    public static final class AudioMeasureResult {
        private final FFTSignalProcessing.ProcessingResult result;
        private final long beginRecordTime;

        public AudioMeasureResult(FFTSignalProcessing.ProcessingResult result, long beginRecordTime) {
            this.result = result;
            this.beginRecordTime = beginRecordTime;
        }

        public FFTSignalProcessing.ProcessingResult getResult() {
            return result;
        }

        /**
         * @return Leq value
         */
        public float[] getLeqs() {
            return result.getdBaLevels();
        }

        public float getGlobaldBaValue() {
            return result.getGlobaldBaValue();
        }

        /**
         * @return Millisecond since epoch of this measure.
         */
        public long getBeginRecordTime() {
            return beginRecordTime;
        }
    }
}

