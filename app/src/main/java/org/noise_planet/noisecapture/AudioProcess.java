package org.noise_planet.noisecapture;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jtransforms.fft.FloatFFT_1D;
import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.CoreSignalProcessing;
import org.orbisgis.sos.LeqStats;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;
import org.orbisgis.sos.ThirdOctaveFrequencies;

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
    private double calibrationPressureReference = Math.pow(10, 135.5 / 20);
    private long beginRecordTime;




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


    /**
     * @return Minimal dB(A) value computed from FFT
     */
    public double getRealtimeLeqMin() {
        return fftLeqProcessing.getLeqMin();
    }

    /**
     * @return Maximal dB(A) value computed from FFT
     */
    public double getRealtimeLeqMax() {
        return fftLeqProcessing.getLeqMax();
    }

    /**
     * @return Average dB(A) value computed from FFT
     */
    public double getRealtimeLeqMean() {
        return fftLeqProcessing.getLeqMean();
    }

    /**
     * @return Computed mean leq.
     */
    public double getLeqMean() {
        return standartLeqProcessing.leqStats.getLeqMean();
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
        private CoreSignalProcessing coreSignalProcessing;
        private float[] fftResultLvl = new float[0];
        private double leq = 0;
        private LeqStats leqStats = new LeqStats();

        // 0.066 mean 15 fps max
        public final static double FFT_TIMELENGTH_FACTOR = Math.min(1, AcousticIndicators.TIMEPERIOD_FAST);
        public final static double SECOND_FIRE_MOVING_SPECTRUM = FFT_TIMELENGTH_FACTOR;
        // Target sampling is REALTIME_SAMPLE_RATE_LIMITATION, then sub-sampling the signal if it is greater than needed (taking Nyquist factor)
        private final double fftSamplingrateFactor;
        // Output only frequency response on this sample rate on the real time result (center + upper band)
        private static final double REALTIME_SAMPLE_RATE_LIMITATION = 9000;
        private final int expectedFFTSize;
        private final double[] fftCenterFreq;
        private int lastProcessedSpectrum = 0;
        private FloatFFT_1D floatFFT_1D;
        private float[] thirdOctaveSplLevels;
        private final double normHanning;

        public MovingLeqProcessing(AudioProcess audioProcess) {
            this.audioProcess = audioProcess;
            this.coreSignalProcessing = new CoreSignalProcessing(audioProcess.getRate(), ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
            fftSamplingrateFactor = audioProcess.getRate() / (REALTIME_SAMPLE_RATE_LIMITATION * 2);
            expectedFFTSize = (int)(audioProcess.getRate() * FFT_TIMELENGTH_FACTOR);
            this.floatFFT_1D = new FloatFFT_1D(expectedFFTSize);
            fftCenterFreq = computeFFTCenterFrequency();
            thirdOctaveSplLevels = new float[fftCenterFreq.length];
            // Computation normalisation factor of hanning window
            float[] normSignal = new float[expectedFFTSize];
            Arrays.fill(normSignal, 1.F);
            AcousticIndicators.hanningWindow(normSignal);
            double sumNorm = 0;
            for(float normVal : normSignal) {
                sumNorm += normVal * normVal;
            }
            normHanning = sumNorm;
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
            if(fftSamplingrateFactor >= 1) {
                return REALTIME_SAMPLE_RATE_LIMITATION;
            } else {
                return audioProcess.getRate() * fftSamplingrateFactor;
            }
        }

        private double[] computeFFTCenterFrequency() {
            double limitFreq = getFFtSamplingRate();
            double[] allCenterFreq = ThirdOctaveBandsFiltering.getStandardFrequencies(ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
            double[] retCenterFreq = new double[allCenterFreq.length];
            int retSize = 0;
            for(double centerFreq : allCenterFreq) {
                if(limitFreq >= centerFreq) {
                    retCenterFreq[retSize++] = centerFreq;
                }else {
                    break;
                }
            }
            return Arrays.copyOfRange(retCenterFreq, 0, retSize);
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
                    double[] signalDouble = coreSignalProcessing.getSampleBuffer();
                    // Convert into float precision - Speedup processing
                    float[] signal = new float[expectedFFTSize];
                    int offset = (int)(signalDouble.length * (1 - FFT_TIMELENGTH_FACTOR));
                    for(int i=0; i < signal.length; i++) {
                        signal[i] = (float)signalDouble[offset + i];
                    }
                    // Apply Hanning window
                    AcousticIndicators.hanningWindow(signal);
                    floatFFT_1D.realForward(signal);
                    // Keep only 1Hz to REALTIME_SAMPLE_RATE_LIMITATION from the FFT Result (if sampleRate is > 11025 Hz)
                    final double freqByCell = 1 / FFT_TIMELENGTH_FACTOR;
                    float[] fftResult =  new float[(int)(Math.min(signal.length / 2, REALTIME_SAMPLE_RATE_LIMITATION / freqByCell))];
                    //a[offa+2*k] = Re[k], 0<=k<n/2
                    for(int k = 0; k < fftResult.length; k++) {
                        final float re = signal[k * 2];
                        final float im = signal[k * 2 + 1];
                        final double rms = Math.sqrt(re * re + im * im) / fftResult.length;
                        fftResult[k] = (float)(rms * rms / normHanning);
                    }
                    lastProcessedSpectrum = pushedSamples;
                    fftResultLvl = fftResult;
                    // Compute A weighted third octave bands
                    final double fd = Math.pow(2, 1. / 6.);
                    final double[] fCenters = getFftCenterFreq();
                    float[] splLevels = new float[fCenters.length];
                    int thirdOctaveId = 0;
                    double rmsSumFreqs = 0;
                    for(double fCenter : fCenters) {
                        // Compute lower and upper value of third-octave
                        final double fLower = fCenter / fd;
                        final double fUpper = fCenter * fd;
                        int cellLower = (int)(Math.ceil(fLower / freqByCell));
                        int cellUpper = Math.min(fftResultLvl.length - 1, (int) (Math.floor(fUpper / freqByCell)));
                        double sumVal = 0;
                        for(int idCell = cellLower; idCell <= cellUpper; idCell++) {
                            sumVal += fftResultLvl[idCell];
                        }
                        sumVal = (float)(10 * Math.log10(sumVal));
                        // Apply A weighting
                        int freqIndex = Arrays.binarySearch(
                                ThirdOctaveFrequencies.STANDARD_FREQUENCIES, fCenter);
                        sumVal = (float)(sumVal + ThirdOctaveFrequencies.A_WEIGHTING[freqIndex]);
                        rmsSumFreqs += Math.pow(10., sumVal / 10.);
                        splLevels[thirdOctaveId] = (float) sumVal;
                        thirdOctaveId++;
                    }
                    thirdOctaveSplLevels = splLevels;
                    // Compute leq
                    leq = leqStats.addRms(rmsSumFreqs);
                    leqStats.addLeq(leq);
                    audioProcess.listeners.firePropertyChange(PROP_MOVING_SPECTRUM,
                            null, fftResult);
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
                        double[] samples = new double[buffer.length];
                        for (int i = 0; i < buffer.length; ++i) {
                            if (buffer[i] > 0) {
                                samples[i] = Math.min(1.0D, (double) buffer[i] / 32767.0D);
                            } else {
                                samples[i] = Math.max(-1.0D, (double) buffer[i] / 32768.0D);
                            }
                            samples[i] *= audioProcess.calibrationPressureReference;
                        }
                        coreSignalProcessing.addSample(samples);
                        secondCursor += samples.length;
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
        private CoreSignalProcessing coreSignalProcessing;
        private static final double windowFactor = 0.66;
        private LeqStats leqStats = new LeqStats();

        public StandartLeqProcessing(AudioProcess audioProcess) {
            this.audioProcess = audioProcess;
            this.coreSignalProcessing = new CoreSignalProcessing(audioProcess.getRate(),
                    ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
        }


        public double[] getComputedFrequencies() {
            return coreSignalProcessing.getStandardFrequencies();
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
            final int processEachSamples = (int)((audioProcess.getRate() * coreSignalProcessing.getSampleDuration()) * windowFactor);
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
                        double[] samples = new double[buffer.length];
                        for (int i = 0; i < buffer.length; ++i) {
                            if (buffer[i] > 0) {
                                samples[i] = Math.min(1.0D, (double) buffer[i] / 32767.0D);
                            } else {
                                samples[i] = Math.max(-1.0D, (double) buffer[i] / 32768.0D);
                            }
                            samples[i] *= audioProcess.calibrationPressureReference;
                        }
                        // Cancel Hanning window weighting by overlapping signal by 66%
                        if(secondCursor + samples.length - lastProcessedSamples >= processEachSamples) {
                            // Check if some samples are to be processed in the next batch
                            int remainingSamplesToPostPone = (int)(secondCursor + samples.length -
                                    lastProcessedSamples - processEachSamples);
                            if (remainingSamplesToPostPone > 0) {
                                coreSignalProcessing.addSample(Arrays.copyOfRange(samples, 0, samples.length - remainingSamplesToPostPone));
                            } else {
                                coreSignalProcessing.addSample(samples);
                            }
                            // Do processing
                            double[] leqs = coreSignalProcessing.processSample(1.);
                            double rmsSum = 0;
                            for(double leq : leqs) {
                                rmsSum += Math.pow(10, leq / 10);
                            }
                            leqStats.addRms(rmsSum);
                            // Compute record time
                            long beginRecordTime = audioProcess.beginRecordTime +
                                    (long) (((secondCursor + samples.length
                                            - remainingSamplesToPostPone) /
                                            (double) audioProcess.getRate()) * 1000);
                            audioProcess.listeners.firePropertyChange(
                                    AudioProcess.PROP_DELAYED_STANDART_PROCESSING, null,
                                    new DelayedStandardAudioMeasure(leqs,  beginRecordTime));
                            lastProcessedSamples = secondCursor;
                            // Add not processed samples for the next batch
                            if(remainingSamplesToPostPone > 0) {
                                coreSignalProcessing.addSample(Arrays.copyOfRange(samples,
                                        samples.length - remainingSamplesToPostPone, samples.length));
                            }
                        } else {
                            coreSignalProcessing.addSample(samples);
                        }
                        secondCursor += samples.length;
                    }
                    try {
                        Thread.sleep(1);
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
        private final double[] leqs;
        private final long beginRecordTime;

        public DelayedStandardAudioMeasure(double[] leqs, long beginRecordTime) {
            this.leqs = leqs;
            this.beginRecordTime = beginRecordTime;
        }

        /**
         * @return Leq value
         */
        public double[] getLeqs() {
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

