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
import android.os.Process;
import android.util.Log;

import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.FFTSignalProcessing;
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
    public static final String PROP_MOVING_SPECTRUM = "PROP_MS";
    public static final String PROP_DELAYED_STANDART_PROCESSING = "PROP_DSP";
    public static final String PROP_STATE_CHANGED = "PROP_STATE_CHANGED";
    // 1s level evaluation for upload to server
    private final MovingLeqProcessing fftLeqProcessing;
    private final StandartLeqProcessing standartLeqProcessing;
    private long beginRecordTime;
    public static final int REALTIME_SAMPLE_RATE_LIMITATION = 16000;
    private float gain = 1;
    private boolean hasGain = false;
    private boolean weightingA = true;
    private boolean hannWindowFast = false;
    private boolean hannWindowOneSecond = true;





    /**
     * Constructor
     * @param recording Recording state
     */
    public AudioProcess(AtomicBoolean recording, AtomicBoolean canceled) {
        this.recording = recording;
        this.canceled = canceled;
        final int[] mSampleRates = new int[] {44100, 22050, 16000, 11025,8000};
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
                        this.fftLeqProcessing = new MovingLeqProcessing(this);
                        this.standartLeqProcessing = new StandartLeqProcessing(this);
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
        fftLeqProcessing.initSampleBuffer();
    }

    public void setHannWindowOneSecond(boolean hannWindowOneSecond) {
        this.hannWindowOneSecond = hannWindowOneSecond;
        standartLeqProcessing.recomputeSample();
    }

    public void setDoFastLeq(boolean doFastLeq) {
        this.doFastLeq = doFastLeq;
    }

    public void setDoOneSecondLeq(boolean doOneSecondLeq) {
        this.doOneSecondLeq = doOneSecondLeq;
    }

    public void setWeightingA(boolean weightingA) {
        this.weightingA = weightingA;
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
     * @return Frequency feed in {@link AudioProcess#PROP_MOVING_SPECTRUM} {@link PropertyChangeEvent#getNewValue()}
    */
    public double[] getRealtimeCenterFrequency() {
        return fftLeqProcessing.getFftCenterFreq();
    }

    /**
     * @return Frequency feed in {@link AudioProcess#PROP_DELAYED_STANDART_PROCESSING} {@link PropertyChangeEvent#getNewValue()}
     */
    public double[] getDelayedCenterFrequency() {
        return standartLeqProcessing.getComputedFrequencies();
    }

    public int getRemainingNotProcessSamples() {
        return standartLeqProcessing.bufferToProcess.size();
    }

    /**
     * @return Third octave SPL up to 8Khz (4 Khz if the phone support 8Khz only)
     */
    public float[] getThirdOctaveFrequencySPL() {
        return fftLeqProcessing.getThirdOctaveFrequencySPL();
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
        return MovingLeqProcessing.SECOND_FIRE_MOVING_SPECTRUM;
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
                    if(doFastLeq) {
                        new Thread(fftLeqProcessing).start();
                    }
                    if(doOneSecondLeq) {
                        new Thread(standartLeqProcessing).start();
                    }
                    audioRecord.startRecording();
                    beginRecordTime = System.currentTimeMillis();
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
                        fftLeqProcessing.addSample(buffer);
                        standartLeqProcessing.addSample(buffer);
                    }
                    setCurrentState(STATE.WAITING_END_PROCESSING);
                    while (fftLeqProcessing.isProcessing() || standartLeqProcessing.isProcessing()) {
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
        return fftLeqProcessing.getFFTFreqArrayStep();
    }
    /**
     * @return Listener manager
     */
    public PropertyChangeSupport getListeners() {
        return listeners;
    }

    double getLeq() {
        return fftLeqProcessing.getLeq();
    }

    public int getRate() {
        return rate;
    }

    private static final class MovingLeqProcessing implements Runnable {
        private Queue<short[]> bufferToProcess = new ConcurrentLinkedQueue<short[]>();
        private final AudioProcess audioProcess;
        private short[] sampleBuffer;
        private AtomicBoolean processing = new AtomicBoolean(false);
        private FFTSignalProcessing signalProcessing;
        private double leq = 0;

        // 0.066 mean 15 fps max
        public final static double FFT_TIMELENGTH_FACTOR = Math.min(1, AcousticIndicators.TIMEPERIOD_FAST);
        public final static double SECOND_FIRE_MOVING_SPECTRUM = FFT_TIMELENGTH_FACTOR;
        // Output only frequency response on this sample rate on the real time result (center + upper band)
        private final int expectedFFTSize;
        private final double[] fftCenterFreq;
        private int lastProcessedSpectrum = 0;
        private float[] thirdOctaveSplLevels;
        public MovingLeqProcessing(AudioProcess audioProcess) {
            this.audioProcess = audioProcess;
            expectedFFTSize = (int)(audioProcess.getRate() * FFT_TIMELENGTH_FACTOR);
            initSampleBuffer();
            fftCenterFreq = FFTSignalProcessing.computeFFTCenterFrequency(REALTIME_SAMPLE_RATE_LIMITATION);
            this.signalProcessing = new FFTSignalProcessing(audioProcess.getRate(),
                    fftCenterFreq, expectedFFTSize);
            thirdOctaveSplLevels = new float[fftCenterFreq.length];
        }

        private void initSampleBuffer() {
            this.sampleBuffer = new short[audioProcess.hannWindowFast ?
                    (int)(Math.ceil(audioProcess.getRate() * (5. / 3.) * FFT_TIMELENGTH_FACTOR ))
                    : expectedFFTSize];
        }

        /**
         * @return In the array fftResultLvl, how many frequency cover one cell.
         */
        public double getFFTFreqArrayStep() {
            return 1 / FFT_TIMELENGTH_FACTOR;
        }

        public double[] getFftCenterFreq() {
            return fftCenterFreq;
        }

        public double getLeq() {
            return leq;
        }

        /**
         * @return Third octave SPL up to 8Khz (4 Khz if the phone support 8Khz only)
         */
        public float[] getThirdOctaveFrequencySPL() {
            return thirdOctaveSplLevels;
        }

        public void addSample(short[] sample) {
            bufferToProcess.add(sample);
        }

        private void processSample(int pushedSamples) {
            if((pushedSamples - lastProcessedSpectrum) / (double)audioProcess.getRate() >
                    (audioProcess.hannWindowFast ? SECOND_FIRE_MOVING_SPECTRUM / 3.
                            : SECOND_FIRE_MOVING_SPECTRUM)) {
                lastProcessedSpectrum = pushedSamples;
                FFTSignalProcessing.ProcessingResult result;
                if(audioProcess.hannWindowFast) {
                    // Window, energetic sum of overlapping
                    int step = (int)(audioProcess.getRate() * (SECOND_FIRE_MOVING_SPECTRUM / 3.));
                    int start = Math.max(0, sampleBuffer.length - step * 5);
                    FFTSignalProcessing.ProcessingResult[] results =
                            new FFTSignalProcessing.ProcessingResult[3];
                    signalProcessing.addSample(Arrays.copyOfRange(sampleBuffer, start,
                            Math.min(sampleBuffer.length, start + (3 * step))));
                    results[0] = signalProcessing.processSample(audioProcess.hannWindowFast,
                                    audioProcess.weightingA, true);
                    start +=  3 * step;
                    signalProcessing.addSample(Arrays.copyOfRange(sampleBuffer, start,
                            Math.min(sampleBuffer.length, start + step)));
                    results[1] = signalProcessing.processSample(audioProcess.hannWindowFast,
                            audioProcess.weightingA, true);
                    start += step;
                    signalProcessing.addSample(Arrays.copyOfRange(sampleBuffer, start,
                            Math.min(sampleBuffer.length, start + step)));
                    results[2] = signalProcessing.processSample(audioProcess.hannWindowFast,
                            audioProcess.weightingA, true);
                    result = new FFTSignalProcessing.ProcessingResult(results);
                } else {
                    // No window
                    signalProcessing.addSample(sampleBuffer);
                    result = signalProcessing.processSample(audioProcess.hannWindowFast,
                                    audioProcess.weightingA, true);
                }
                thirdOctaveSplLevels = result.getdBaLevels();
                // Compute leq
                leq = result.getGlobaldBaValue();
                // Compute record time
                long beginRecordTime = audioProcess.beginRecordTime +
                        (long) ((pushedSamples  /
                                (double) audioProcess.getRate()) * 1000);
                audioProcess.listeners.firePropertyChange(PROP_MOVING_SPECTRUM,
                        null,
                        new AudioMeasureResult(result,  beginRecordTime, 0));
            }
        }

        public boolean isProcessing() {
            return processing.get();
        }

        @Override
        public void run() {
            int secondCursor = 0;
            try {
                try {
                    android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                } catch (IllegalArgumentException | SecurityException ex) {
                    // Ignore
                }
                while (audioProcess.currentState != STATE.WAITING_END_PROCESSING &&
                        !audioProcess.canceled.get()
                        && audioProcess.currentState != STATE.CLOSED) {
                    while (!bufferToProcess.isEmpty() && !audioProcess.canceled.get()) {
                        processing.set(true);
                        short[] buffer = bufferToProcess.poll();
                        if(buffer.length < sampleBuffer.length) {
                            // Move previous samples backward
                            System.arraycopy(sampleBuffer, buffer.length, sampleBuffer, 0, sampleBuffer.length - buffer.length);
                            System.arraycopy(buffer, 0, sampleBuffer, sampleBuffer.length - buffer.length, buffer.length);
                        } else {
                            // Take last samples
                            System.arraycopy(buffer, Math.max(0, buffer.length - sampleBuffer.length), sampleBuffer, 0,
                                    sampleBuffer.length);
                        }
                        secondCursor += buffer.length;
                    }
                    processSample(secondCursor);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            } finally {
                processing.set(false);
            }
        }
    }

    /**
     * Delayed Second-order recursive linear filtering that will be kept for future storage and optionally uploaded.
     */
    private static final class StandartLeqProcessing implements Runnable {
        private Queue<short[]> bufferToProcess = new ConcurrentLinkedQueue<short[]>();
        private boolean processing = false;
        private final AudioProcess audioProcess;
        private FFTSignalProcessing fftSignalProcessing;
        private double windowDelay = 1.;
        private double[] fftCenterFreq;
        private int processEachSamples = 0;

        public StandartLeqProcessing(AudioProcess audioProcess) {
            this.audioProcess = audioProcess;
            fftCenterFreq = FFTSignalProcessing.computeFFTCenterFrequency(REALTIME_SAMPLE_RATE_LIMITATION);
            this.fftSignalProcessing = new FFTSignalProcessing(audioProcess.getRate(),
                    fftCenterFreq, audioProcess.getRate());
        }

        public void recomputeSample() {
            //TODO change window delay to 1/3 if hann window then sum the last 3 processing result
            // each second.
            processEachSamples = (int)((audioProcess.getRate() * fftSignalProcessing.getSampleDuration()) *
                    windowDelay);
        }

        public double[] getComputedFrequencies() {
            return fftSignalProcessing.getStandardFrequencies();
        }

        public boolean isProcessing() {
            return processing;
        }

        public void addSample(short[] sample) {
            bufferToProcess.add(sample);
        }

        @Override
        public void run() {
            long secondCursor = 0;
            recomputeSample();
            long lastProcessedSamples = 0;
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            } catch (IllegalArgumentException | SecurityException ex) {
                // Ignore
            }
            try {
                while (audioProcess.currentState != STATE.WAITING_END_PROCESSING &&
                        !audioProcess.canceled.get()
                        && audioProcess.currentState != STATE.CLOSED) {
                    while (!bufferToProcess.isEmpty() && !audioProcess.canceled.get()) {
                        processing = true;
                        short[] buffer = bufferToProcess.poll();
                        // Cancel Hanning window weighting by overlapping signal by 50%
                        if(secondCursor + buffer.length - lastProcessedSamples >= processEachSamples) {
                            // Check if some samples are to be processed in the next batch
                            int remainingSamplesToPostPone = (int)(secondCursor + buffer.length -
                                    lastProcessedSamples - processEachSamples);
                            if (remainingSamplesToPostPone > 0) {
                                fftSignalProcessing.addSample(Arrays.copyOfRange(buffer, 0,
                                        buffer.length - remainingSamplesToPostPone));
                            } else {
                                fftSignalProcessing.addSample(buffer);
                            }
                            // Do processing
                            FFTSignalProcessing.ProcessingResult result =
                                    fftSignalProcessing.processSample(audioProcess.hannWindowOneSecond,
                                            audioProcess.weightingA, false);
                            // Compute record time
                            long beginRecordTime = audioProcess.beginRecordTime +
                                    (long) (((secondCursor + buffer.length
                                            - remainingSamplesToPostPone) /
                                            (double) audioProcess.getRate()) * 1000);
                            audioProcess.listeners.firePropertyChange(
                                    AudioProcess.PROP_DELAYED_STANDART_PROCESSING, null,
                                    new AudioMeasureResult(result,  beginRecordTime,
                                            fftSignalProcessing.computeGlobalLeq()));
                            lastProcessedSamples = secondCursor;
                            // Add not processed samples for the next batch
                            if(remainingSamplesToPostPone > 0) {
                                fftSignalProcessing.addSample(Arrays.copyOfRange(buffer,
                                        buffer.length - remainingSamplesToPostPone, buffer.length));
                            }
                        } else {
                            fftSignalProcessing.addSample(buffer);
                        }
                        secondCursor += buffer.length;
                    }
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            } finally {
                processing = false;
            }
        }
    }

    public static final class AudioMeasureResult {
        private final FFTSignalProcessing.ProcessingResult result;
        private final long beginRecordTime;
        private double signalLeq;

        public AudioMeasureResult(FFTSignalProcessing.ProcessingResult result, long beginRecordTime, double signalLeq) {
            this.result = result;
            this.beginRecordTime = beginRecordTime;
            this.signalLeq = signalLeq;
        }

        public FFTSignalProcessing.ProcessingResult getResult() {
            return result;
        }

        /**
         * @return Leq computed using signal; not recomposed third-octave bands
         */
        public double getSignalLeq() {
            return signalLeq;
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

