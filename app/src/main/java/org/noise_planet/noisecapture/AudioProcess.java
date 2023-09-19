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

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MicrophoneInfo;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.ConfigurationSpectrumChannel;
import org.orbisgis.sos.FFTSignalProcessing;
import org.orbisgis.sos.SpectrumChannel;
import org.orbisgis.sos.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processing thread of packets of Audio signal
 */
public class AudioProcess implements Runnable {
    // If there is a delay of this time in processing use a lightweight version of noise analysis
    public static final int DELAY_PROCESSING_SWITCH_TO_FFT = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioProcess.class);
    private AtomicBoolean recording;
    private AtomicBoolean canceled;
    private boolean doFastLeq = true;
    private boolean doOneSecondLeq = true;
    private final int bufferSize; // buffer size in bytes
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
    private ProcessingThread slowLeqProcessing;
    private final ProcessingThread customLeqProcessing;
    private AtomicBoolean filterBankCancel = new AtomicBoolean(false);


    public static final int REALTIME_SAMPLE_RATE_LIMITATION = 16000;
    public static final double[] realTimeCenterFrequency = FFTSignalProcessing.computeFFTCenterFrequency(REALTIME_SAMPLE_RATE_LIMITATION);
    private boolean hannWindowFast = false;
    private boolean hannWindowOneSecond = true;
    private AudioRecord audioRecord;
    private MicrophoneInfo microphoneInfo;

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
        final int[] mSampleRates = new int[] {44100, 48000};
        // Hz sampling rate, so we do not support other samplings (22050, 16000, 11025,8000)
        final int[] encodings;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            encodings = new int[] { AudioFormat.ENCODING_PCM_FLOAT ,AudioFormat.ENCODING_PCM_16BIT ,
                    AudioFormat.ENCODING_PCM_8BIT };
        } else {
            encodings = new int[] { AudioFormat.ENCODING_PCM_16BIT , AudioFormat.ENCODING_PCM_8BIT };
        }
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
                                (int) (AcousticIndicators.TIMEPERIOD_FAST * tryRate *
                                        (tryEncoding == AudioFormat.ENCODING_PCM_16BIT ? 2 : 4) *
                                        tryAudioChannel == AudioFormat.CHANNEL_IN_MONO ? 1 : 2));
                        encoding = tryEncoding;
                        audioChannel = tryAudioChannel;
                        rate = tryRate;
                        this.fastLeqProcessing = new LeqProcessingThread(this,
                                AcousticIndicators.TIMEPERIOD_FAST, false,
                                hannWindowFast ? FFTSignalProcessing.WINDOW_TYPE.TUKEY :
                                        FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR, PROP_FAST_LEQ, true);
                        loadFilterSlowAnalyzer();
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

    private void loadFFTSlowAnalyzer() {
        this.slowLeqProcessing = new LeqProcessingThread(this,
        AcousticIndicators.TIMEPERIOD_SLOW, false,
        hannWindowOneSecond ? FFTSignalProcessing.WINDOW_TYPE.TUKEY :
                FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR,
        PROP_SLOW_LEQ, false);
    }

    private void loadFilterSlowAnalyzer() {
        this.slowLeqProcessing = new FilterBankProcessingThread(this,
                PROP_SLOW_LEQ, 1.0, filterBankCancel);
        String configuration = "config_44100_third_octave.json";
        if(rate == 48000) {
            configuration = "config_48000_third_octave.json";
        }
        try (InputStream s = SpectrumChannel.class.getResourceAsStream(configuration)) {
            ((FilterBankProcessingThread)this.slowLeqProcessing).loadConfiguration(s);
        } catch (IOException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
    }

    public void setHannWindowFast(boolean hannWindowFast) {
        this.hannWindowFast = hannWindowFast;
        fastLeqProcessing.setWindowType(hannWindowFast ? FFTSignalProcessing.WINDOW_TYPE.TUKEY :
                FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR);
    }

    public void refreshMicrophoneInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                List<MicrophoneInfo> microphoneInfoList =
                        audioRecord.getActiveMicrophones();
                if(!microphoneInfoList.isEmpty()) {
                    microphoneInfo = microphoneInfoList.get(0);
                }
            } catch (IOException ex) {
                LOGGER.warn("Can't read microphone information", ex);
                // Ignore
            }
        }
    }

    public MicrophoneInfo getMicrophoneInfo() {
        return microphoneInfo;
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
    public void setGain(double gain) {
        if(BuildConfig.DEBUG) {
            System.out.println("Set gain " + gain);
        }
        if(doOneSecondLeq) {
            slowLeqProcessing.setGain(gain);
        }
        if(doFastLeq) {
            fastLeqProcessing.setGain(gain);
        }
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

    public double getRemainingNotProcessTime() {
        return slowLeqProcessing.getProcessingDelayTime() + fastLeqProcessing.getProcessingDelayTime();
    }

    /**
     * @return The current delay between the audio input and the processed output
     */
    public long getFastNotProcessedMilliseconds() {
        return (fastLeqProcessing.getPushedSamples() - fastLeqProcessing.getProcessedSamples()) / (rate / 1000);
    }

    /**
     * @return Third octave SPL up to 8Khz (4 Khz if the phone support 8Khz only)
     */
    public double[] getThirdOctaveFrequencySPL() {
        return fastLeqProcessing.getThirdOctaveFrequencySPL();
    }

    @SuppressLint("MissingPermission")
    private AudioRecord createAudioRecord() {
        // Source:
        //  section 5.3 of the Android 4.0 Compatibility Definition
        // https://source.android.com/compatibility/4.0/android-4.0-cdd.pdf
        // Using VOICE_RECOGNITION
        // Noise reduction processing, if present, is disabled.
        // Except for 5.0+ where android.media.audiofx.NoiseSuppressor could be used to cancel such processing
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
            audioRecord = createAudioRecord();
            refreshMicrophoneInfo();
            float[] buffer = new float[bufferSize/4];
            // Compute the correction to set to PCM values in order to obtain the same value as 16 bits scale (without rescale)
            if (recording.get() && audioRecord != null) {
                try {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                    } catch (IllegalArgumentException | SecurityException ex) {
                        // Ignore
                    }
                    new Thread(fastLeqProcessing).start();
                    new Thread(slowLeqProcessing).start();
                    audioRecord.startRecording();

                    while (recording.get()) {
                        if(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_8BIT) {
                            short[] shortBuffer = new short[bufferSize / 2];
                            int read = audioRecord.read(shortBuffer, 0, shortBuffer.length);
                            if (read < shortBuffer.length) {
                                shortBuffer = Arrays.copyOfRange(shortBuffer, 0, read);
                            }
                            buffer = SOSSignalProcessing.convertShortToFloat(shortBuffer);
                        } else if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                              int read = audioRecord.read(buffer, 0, buffer.length,
                                    AudioRecord.READ_BLOCKING);
                              if (read < buffer.length) {
                                  buffer = Arrays.copyOfRange(buffer, 0, read);
                              }
                        }
                        if(doFastLeq) {
                            fastLeqProcessing.addSample(buffer);
                        }
                        if(doOneSecondLeq) {
                            slowLeqProcessing.addSample(buffer);
                            // if FilterBank is not able to catch up to realtime switch to fft processing (old low end phones)
                            if(slowLeqProcessing instanceof FilterBankProcessingThread
                            && slowLeqProcessing.getProcessingDelayTime() > DELAY_PROCESSING_SWITCH_TO_FFT) {
                                filterBankCancel.set(true); // stop this processing
                                LOGGER.error("Too much delay switch to lightweight processing");
                                loadFFTSlowAnalyzer();
                            }
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
     * @return Fast refreshed lAeq
     */
    double getLAeq() {
        if(doFastLeq) {
            return fastLeqProcessing.getLeq();
        } else if(doOneSecondLeq){
            return slowLeqProcessing.getLeq();
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
        void addSample(float[] sample);
        void setAweighting(boolean Aweighting);
        void setGain(double gain);
        double getProcessingDelayTime();
        boolean isProcessing();
        double getLeq();
    }

    public static final class FilterBankProcessingThread implements ProcessingThread {
        private Queue<float[]> bufferToProcess = new ConcurrentLinkedQueue<float[]>();
        private final AudioProcess audioProcess;
        private AtomicBoolean processing = new AtomicBoolean(false);
        private String propertyName;
        private long pushedSamples = 0;
        private long processedSamples = 0;
        private int lastPushIndex = 0;
        private SpectrumChannel spectrumChannel;
        private final double windowTime;
        private ConfigurationSpectrumChannel configuration;
        private boolean aWeighting = true;
        private double leq = 0;
        private static final double NATIVE_GAIN = 90-20*Math.log10(FFTSignalProcessing.RMS_REFERENCE_90DB / Short.MAX_VALUE);
        private double dbGain = NATIVE_GAIN;
        double processingDelayTime;
        final AtomicBoolean canceled;

        public FilterBankProcessingThread(AudioProcess audioProcess, String propertyName, double windowTime, AtomicBoolean canceled) {
            this.audioProcess = audioProcess;
            this.propertyName = propertyName;
            this.spectrumChannel = new SpectrumChannel();
            this.windowTime = windowTime;
            this.canceled = canceled;
        }

        public void loadConfiguration(InputStream filterConfigurationFile) throws IOException {
            ObjectMapper objectMapper = new ObjectMapper();
            configuration = objectMapper.readValue(filterConfigurationFile, ConfigurationSpectrumChannel.class);
            this.spectrumChannel.loadConfiguration(configuration, true);
        }

        public double getProcessingDelayTime() {
            return processingDelayTime;
        }

        public void setGain(double gain) {
            this.dbGain = NATIVE_GAIN + 20 * Math.log10(gain);
        }

        public void addSample(float[] samples) {
            bufferToProcess.add(samples);
            pushedSamples += samples.length;
        }

        public long getPushedSamples() {
            return pushedSamples;
        }

        public boolean isProcessing() {
            return processing.get();
        }


        public void setAweighting(boolean aWeighting) {
            this.aWeighting=aWeighting;
        }


        public double getLeq() {
            return leq;
        }

        @Override
        public void run() {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            } catch (IllegalArgumentException | SecurityException ex) {
                // Ignore
            }
            float[] windowBuffer = new float[(int)(configuration.getConfiguration().getSampleRate()*windowTime)];
            int windowBufferCursor = 0;
            try {
                while (audioProcess.currentState != STATE.WAITING_END_PROCESSING &&
                        !audioProcess.canceled.get() && !canceled.get()
                        && audioProcess.currentState != STATE.CLOSED) {
                    while (!bufferToProcess.isEmpty() && !audioProcess.canceled.get() && !canceled.get()) {
                        processing.set(true);
                        float[] buffer = bufferToProcess.poll();
                        if(buffer != null) {
                            int polledBufferCursor = 0;
                            while(polledBufferCursor < buffer.length) {
                                int copyLength = Math.min(buffer.length-polledBufferCursor,
                                        windowBuffer.length - windowBufferCursor);
                                System.arraycopy(buffer, polledBufferCursor, windowBuffer, windowBufferCursor, copyLength);
                                polledBufferCursor += copyLength;
                                windowBufferCursor += copyLength;
                                processedSamples += copyLength;
                                if(windowBuffer.length == windowBufferCursor) {
                                    // complete audio samples to analyze
                                    long startAnalyze = System.currentTimeMillis();
                                    if(aWeighting) {
                                        leq = spectrumChannel.processSamplesWeightA(windowBuffer) + dbGain;
                                    } else {
                                        leq = AcousticIndicators.getLeq(windowBuffer,
                                                1/Math.pow(10, dbGain / 20));
                                    }
                                    LOGGER.debug(String.format(Locale.ROOT,
                                            "For gain %.2f -> Leq %.2f%n", dbGain, leq));
                                    double[] spectrum = spectrumChannel.processSamples(windowBuffer);
                                    long analysis_time = System.currentTimeMillis() - startAnalyze;
                                    for (int i = 0; i < spectrum.length; i++) {
                                        spectrum[i] += dbGain;
                                    }
                                    double sumSamples = 0;
                                    for (float[] toProcess : bufferToProcess) {
                                        sumSamples += toProcess.length;
                                    }
                                    processingDelayTime = sumSamples /
                                            configuration.getConfiguration().getSampleRate();
                                    System.out.println(String.format(Locale.ROOT,
                                            "Analysis done in %d milliseconds queue is %.2f ms",
                                            analysis_time, processingDelayTime
                                    ));
                                    long beginRecordTime = System.currentTimeMillis()
                                            - (int)(windowTime * 1000)
                                            - (int)(processingDelayTime * 1000);
                                    FFTSignalProcessing.ProcessingResult processingResult =
                                            new FFTSignalProcessing.ProcessingResult(
                                                    processedSamples, new double[0], spectrum, leq);
                                    audioProcess.listeners.firePropertyChange(propertyName,
                                            null, new AudioMeasureResult(processingResult,
                                                    beginRecordTime));
                                    windowBufferCursor = 0;
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
            } finally {
                processing.set(false);
            }
        }
    }

    public static final class LeqProcessingThread implements ProcessingThread {
        private Queue<float[]> bufferToProcess = new ConcurrentLinkedQueue<float[]>();
        private final AudioProcess audioProcess;
        private AtomicBoolean processing = new AtomicBoolean(false);
        private Window window;
        private double leq = 0;
        private String propertyName;
        private double timePeriod;
        private long pushedSamples = 0;
        private long processedSamples = 0;
        private int lastPushIndex = 0;
        private double gain = 1;

        // Output only frequency response on this sample rate on the real time result (center + upper band)
        private double[] thirdOctaveSplLevels;

        public LeqProcessingThread(AudioProcess audioProcess, double timePeriod, boolean Aweighting,
                                   FFTSignalProcessing.WINDOW_TYPE window_type, String propertyName,
                                   boolean outputSpectrogram) {
            this.audioProcess = audioProcess;
            this.propertyName = propertyName;
            this.timePeriod = timePeriod;
            this.window = new Window(window_type,
                    audioProcess.getRate(), audioProcess.getRealtimeCenterFrequency(), timePeriod,
                    Aweighting, FFTSignalProcessing.DB_FS_REFERENCE, outputSpectrogram);
            this.window.setaWeighting(Aweighting);
            thirdOctaveSplLevels = new double[audioProcess.getRealtimeCenterFrequency().length];
        }

        public void setGain(double gain) {
            this.gain = gain;
            window.setDbFsReference(FFTSignalProcessing.DB_FS_REFERENCE+20*Math.log10(gain));
        }

        public void setWindowType(FFTSignalProcessing.WINDOW_TYPE windowType) {
            if (windowType != window.getWindowType()) {
                this.window = new Window(windowType, audioProcess.getRate(),
                        audioProcess.getRealtimeCenterFrequency(), timePeriod,
                        window.isAWeighting(), FFTSignalProcessing.DB_FS_REFERENCE,
                        window.isOutputThinFrequency());
                setGain(gain);
                lastPushIndex = 0;
            }
        }

        /**
         * @return Samples processed by FFT
         */
        public long getProcessedSamples() {
            return processedSamples;
        }

        @Override
        public double getProcessingDelayTime() {
            int totalSamples=0;
            for (float[] toProcess : bufferToProcess) {
                totalSamples += toProcess.length;
            }
            return totalSamples / (double)audioProcess.getRate();
        }

        public void setAweighting(boolean Aweighting) {
            window.setaWeighting(Aweighting);
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
         * @return Third octave SPL up to 8Khz (4 Khz if the phone support 8Khz only)
         */
        public double[] getThirdOctaveFrequencySPL() {
            return thirdOctaveSplLevels;
        }

        public void addSample(float[] samples) {
            bufferToProcess.add(samples);
            pushedSamples += samples.length;
        }

        public long getPushedSamples() {
            return pushedSamples;
        }

        private void processWindow() {
            lastPushIndex = window.getWindowIndex();
            FFTSignalProcessing.ProcessingResult  result = window.getLastWindowMean();
            window.cleanWindows();
            thirdOctaveSplLevels = result.getSpl();
            // Compute leq
            leq = result.getWindowLeq();
            // Compute record time
            // Take current time minus the computed delay of the measurement
            long beginRecordTime = System.currentTimeMillis() -
                    (long) (((pushedSamples - result.getId())  /
                            (double) audioProcess.getRate()) * 1000);
            audioProcess.listeners.firePropertyChange(propertyName,
                    null,
                    new AudioMeasureResult(result,  beginRecordTime));
        }

        private void processSample(float[] buffer) {
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
                        float[] buffer = bufferToProcess.poll();
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
                                    float[] samples = Arrays.copyOfRange(buffer, cursor, cursor + sampleLen);
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
        public double[] getLeqs() {
            return result.getSpl();
        }

        public double getGlobaldBaValue() {
            return result.getWindowLeq();
        }

        /**
         * @return Millisecond since epoch of this measure.
         */
        public long getBeginRecordTime() {
            return beginRecordTime;
        }
    }
}

