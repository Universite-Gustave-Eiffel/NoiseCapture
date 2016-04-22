package org.noise_planet.noisecapture;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.FFTSignalProcessing;
import org.orbisgis.sos.LeqStats;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;

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
    private AtomicBoolean recording;
    private AtomicBoolean canceled;
    private final int bufferSize;
    private final int encoding;
    private final int rate;
    private final int audioChannel;
    public enum STATE { WAITING, PROCESSING,WAITING_END_PROCESSING, CLOSED }
    private STATE currentState = STATE.WAITING;
    private PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    public static final String PROP_MOVING_SPECTRUM = "PROP_MS";
    public static final String PROP_DELAYED_STANDART_PROCESSING = "PROP_DSP";
    // 1s level evaluation for upload to server
    private final MovingLeqProcessing fftLeqProcessing;
    private final StandartLeqProcessing standartLeqProcessing;
    private long beginRecordTime;
    private static final int REALTIME_SAMPLE_RATE_LIMITATION = 16000;





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

    public LeqStats getStandartLeqStats() {
        return standartLeqProcessing.leqStats;
    }

    public LeqStats getFastLeqStats() {
        return fftLeqProcessing.leqStats;
    }

    public double getFFTDelay() {
        return MovingLeqProcessing.SECOND_FIRE_MOVING_SPECTRUM;
    }

    @Override
    public void run() {
        try {
            currentState = STATE.PROCESSING;
            AudioRecord audioRecord = createAudioRecord();
            short[] buffer;
            if (recording.get() && audioRecord != null) {
                try {
                    try {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    } catch (IllegalArgumentException | SecurityException ex) {
                        // Ignore
                    }
                    new Thread(fftLeqProcessing).start();
                    new Thread(standartLeqProcessing).start();
                    audioRecord.startRecording();
                    beginRecordTime = System.currentTimeMillis();
                    while (recording.get()) {
                        buffer = new short[bufferSize];
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if(read < buffer.length) {
                            buffer = Arrays.copyOfRange(buffer, 0, read);
                        }
                        fftLeqProcessing.addSample(buffer);
                        standartLeqProcessing.addSample(buffer);
                    }
                    currentState = STATE.WAITING_END_PROCESSING;
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
            currentState = STATE.CLOSED;
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

    public long getBeginRecordTime() {
        return beginRecordTime;
    }

    private static final class MovingLeqProcessing implements Runnable {
        private Queue<short[]> bufferToProcess = new ConcurrentLinkedQueue<short[]>();
        private final AudioProcess audioProcess;
        private AtomicBoolean processing = new AtomicBoolean(false);
        private FFTSignalProcessing signalProcessing;
        private float[] fftResultLvl = new float[0];
        private double leq = 0;
        private LeqStats leqStats = new LeqStats();

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
            fftCenterFreq = FFTSignalProcessing.computeFFTCenterFrequency(REALTIME_SAMPLE_RATE_LIMITATION);
            this.signalProcessing = new FFTSignalProcessing(audioProcess.getRate(),
                    fftCenterFreq, expectedFFTSize);
            thirdOctaveSplLevels = new float[fftCenterFreq.length];
        }

        /**
         * @return In the array fftResultLvl, how many frequency cover one cell.
         */
        public double getFFTFreqArrayStep() {
            return 1 / FFT_TIMELENGTH_FACTOR;
        }

        /**
         * @return Live FFT result up to REALTIME_SAMPLE_RATE_LIMITATION Hz
         */
        public float[] getFineFrequencyLevels() {
            return fftResultLvl;
        }

        public double getFFtSamplingRate() {
            return REALTIME_SAMPLE_RATE_LIMITATION;
        }

        public double[] getFftCenterFreq() {
            return fftCenterFreq;
        }

        /**
         * @return Third octave SPL up to 8Khz (4 Khz if the phone support 8Khz only)
         */
        public float[] getThirdOctaveFrequencySPL() {
            return thirdOctaveSplLevels;
        }

        public double getLeq() {
            return leq;
        }

        public double getLeqMin() {
            return leqStats.getLeqMin();
        }

        public double getLeqMax() {
            return leqStats.getLeqMax();
        }

        public double getLeqMean() {
            return leqStats.getLeqMean();
        }



        public void addSample(short[] sample) {
            bufferToProcess.add(sample);
        }

        public boolean isTaskQueueEmpty() {
            return bufferToProcess.isEmpty() && !processing.get();
        }


        private void processSample(int pushedSamples) {
            if((pushedSamples - lastProcessedSpectrum) / (double)audioProcess.getRate() >
                    SECOND_FIRE_MOVING_SPECTRUM) {
                    lastProcessedSpectrum = pushedSamples;
                    FFTSignalProcessing.ProcessingResult result =
                            signalProcessing.processSample(false, true, true);
                    fftResultLvl = result.getFftResult();
                    thirdOctaveSplLevels = result.getdBaLevels();
                    // Compute leq
                    leq = signalProcessing.computeGlobalLeq();
                    leqStats.addLeq(leq);
                    audioProcess.listeners.firePropertyChange(PROP_MOVING_SPECTRUM,
                            null, fftResultLvl);
            }
        }

        public boolean isProcessing() {
            return processing.get();
        }

        @Override
        public void run() {
            int secondCursor = 0;
            try {
                while (audioProcess.currentState != STATE.WAITING_END_PROCESSING &&
                        !audioProcess.canceled.get()
                        && audioProcess.currentState != STATE.CLOSED) {
                    while (!bufferToProcess.isEmpty() && !audioProcess.canceled.get()) {
                        processing.set(true);
                        short[] buffer = bufferToProcess.poll();
                        signalProcessing.addSample(buffer);
                        secondCursor += buffer.length;
                    }
                    processSample(secondCursor);
                    try {
                        Thread.sleep(1);
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
        private static final double windowDelay = 1.;
        private LeqStats leqStats = new LeqStats();
        private double[] fftCenterFreq;

        public StandartLeqProcessing(AudioProcess audioProcess) {
            this.audioProcess = audioProcess;
            fftCenterFreq = FFTSignalProcessing.computeFFTCenterFrequency(REALTIME_SAMPLE_RATE_LIMITATION);
            this.fftSignalProcessing = new FFTSignalProcessing(audioProcess.getRate(),
                    fftCenterFreq, audioProcess.getRate());
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
            final int processEachSamples = (int)((audioProcess.getRate() * fftSignalProcessing.getSampleDuration()) *

                    windowDelay);
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
                                    fftSignalProcessing.processSample(false, false, false);
                            float[] leqs = result.getdBaLevels();
                            leqStats.addLeq(result.getGlobaldBaValue());
                            // Compute record time
                            long beginRecordTime = audioProcess.beginRecordTime +
                                    (long) (((secondCursor + buffer.length
                                            - remainingSamplesToPostPone) /
                                            (double) audioProcess.getRate()) * 1000);
                            audioProcess.listeners.firePropertyChange(
                                    AudioProcess.PROP_DELAYED_STANDART_PROCESSING, null,
                                    new DelayedStandardAudioMeasure(leqs,  beginRecordTime));
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

    public static final class DelayedStandardAudioMeasure {
        private final float[] leqs;
        private final long beginRecordTime;

        public DelayedStandardAudioMeasure(float[] leqs, long beginRecordTime) {
            this.leqs = leqs;
            this.beginRecordTime = beginRecordTime;
        }

        /**
         * @return Leq value
         */
        public float[] getLeqs() {
            return leqs;
        }

        /**
         * @return Millisecond since epoch of this measure.
         */
        public long getBeginRecordTime() {
            return beginRecordTime;
        }
    }
}

