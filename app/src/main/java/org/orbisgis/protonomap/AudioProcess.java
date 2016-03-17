package org.orbisgis.protonomap;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.beans.PropertyChangeSupport;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jtransforms.fft.FloatFFT_1D;
import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.CoreSignalProcessing;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;
import org.orbisgis.sos.ThirdOctaveFrequencies;

/**
 * Processing thread of packets of Audio signal
 */
public class AudioProcess implements Runnable {
    private AtomicBoolean recording;
    private final int bufferSize;
    private final int encoding;
    private final int rate;
    private final int audioChannel;
    public enum STATE { WAITING, PROCESSING,WAITING_END_PROCESSING, CLOSED }
    private STATE currentState = STATE.WAITING;
    private PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    public static final String PROP_MOVING_LEQ = "PROP_MOVING_LEQ";
    private double movingLeq;
    // Moving Level evaluation for rendering to user
    private double[] movingLvl = new double[ThirdOctaveFrequencies.STANDARD_FREQUENCIES.length];
    // 1s level evaluation for upload to server
    private List<double[]> stdLvl = new ArrayList<>();
    private final MovingLeqProcessing movingLeqProcessing;




    /**
     * Constructor
     * @param recording Recording state
     */
    public AudioProcess(AtomicBoolean recording) {
        this.recording = recording;
        final int[] mSampleRates = new int[] { 44100 };
        final int[] encodings = new int[] { AudioFormat.ENCODING_PCM_16BIT , AudioFormat.ENCODING_PCM_8BIT };
        final short[] audioChannels = new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO };
        for (int tryRate : mSampleRates) {
            for (int tryEncoding : encodings) {
                for(short tryAudioChannel : audioChannels) {
                    int tryBufferSize = AudioRecord.getMinBufferSize(tryRate,
                            tryAudioChannel, tryEncoding);
                    if (tryBufferSize != AudioRecord.ERROR_BAD_VALUE) {
                        bufferSize = tryBufferSize;
                        encoding = tryEncoding;
                        audioChannel = tryAudioChannel;
                        rate = tryRate;
                        this.movingLeqProcessing = new MovingLeqProcessing(this);
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

    private AudioRecord createAudioRecord() {
        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
            return new AudioRecord(MediaRecorder.AudioSource.MIC,
                    rate, audioChannel,
                    encoding, bufferSize);
        } else {
            return null;
        }
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
                    new Thread(movingLeqProcessing).start();
                    audioRecord.startRecording();
                    while (recording.get()) {
                        buffer = new short[bufferSize];
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if(read < buffer.length) {
                            short[] filledBuffer = new short[read];
                            System.arraycopy(buffer, 0, filledBuffer, 0, read);
                            buffer = filledBuffer;
                        }
                        movingLeqProcessing.addSample(buffer);
                    }
                    currentState = STATE.WAITING_END_PROCESSING;
                    while (movingLeqProcessing.isProcessing()) {
                        Thread.sleep(10);
                    }
                } catch (Exception ex) {
                    Log.e("tag_record", "Error while recording", ex);
                } finally {
                    audioRecord.stop();
                    audioRecord.release();
                }
            }
        } finally {
            currentState = STATE.CLOSED;
        }
    }

    /**
     * @return Listener manager
     */
    public PropertyChangeSupport getListeners() {
        return listeners;
    }

    /**
     * Filter the last 1s record.
     * @return Level in dB(A) of the last 1s for each frequency band.
     */
    double[] getMovingLvl() {
        return movingLeqProcessing.getLastLvls();
    }

    double getLeq() {
        return movingLeqProcessing.getLeq();
    }

    long getProcessingTime() {
        return movingLeqProcessing.processingTime;
    }

    public int getRate() {
        return rate;
    }

    private static class MovingLeqProcessing implements Runnable {
        private List<short[]> bufferToProcess = new CopyOnWriteArrayList<short[]>();
        private final AudioProcess audioProcess;
        private AtomicBoolean processing = new AtomicBoolean(false);
        private CoreSignalProcessing coreSignalProcessing;
        private double[] lastLvls = new double[ThirdOctaveBandsFiltering.getStandardFrequencies(ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED).length];
        private double leq = 0;
        private final static double SECOND_FIRE_MOVING_LEQ = 0.;
        private final static double SECOND_FIRE_MOVING_SPECTRUM = 0.;
        private int lastProcessedMovingLeq = 0;
        private int lastProcessedSpectrum = 0;
        public long processingTime = 0;
        private FloatFFT_1D floatFFT_1D;

        public MovingLeqProcessing(AudioProcess audioProcess) {
            this.audioProcess = audioProcess;
            this.coreSignalProcessing = new CoreSignalProcessing(audioProcess.getRate(), ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
            this.floatFFT_1D = new FloatFFT_1D(audioProcess.getRate());
        }

        public double[] getLastLvls() {
            return lastLvls;
        }

        public double getLeq() {
            return leq;
        }

        public void addSample(short[] sample) {
            bufferToProcess.add(sample);
        }

        public boolean isTaskQueueEmpty() {
            return bufferToProcess.isEmpty() && !processing.get();
        }


        private void processSample(int pushedSamples) {
            if((pushedSamples - lastProcessedMovingLeq) / (double)audioProcess.getRate() >
                    SECOND_FIRE_MOVING_LEQ) {
                leq = AcousticIndicators.getLeq(coreSignalProcessing.getSampleBuffer());
                audioProcess.listeners.firePropertyChange(PROP_MOVING_LEQ, lastProcessedMovingLeq,
                        lastProcessedMovingLeq + pushedSamples);
                lastProcessedMovingLeq = pushedSamples;
            }

            if((pushedSamples - lastProcessedSpectrum) / (double)audioProcess.getRate() >
                    SECOND_FIRE_MOVING_SPECTRUM) {
                    long beginProcess = System.currentTimeMillis();
                    double[] signalDouble = coreSignalProcessing.getSampleBuffer();
                    float[] signal = new float[signalDouble.length];
                    for(int i=0; i<signalDouble.length;i++) {
                        signal[i] = (float)signalDouble[i];
                    }
                    floatFFT_1D.realForward(signal);
                    processingTime = System.currentTimeMillis() - beginProcess;
                    lastProcessedSpectrum = pushedSamples;
                    audioProcess.listeners.firePropertyChange(PROP_MOVING_LEQ,
                            lastProcessedSpectrum, lastProcessedSpectrum + pushedSamples);
            }
            /*
            if((pushedSamples - lastProcessedSpectrum) / (double)audioProcess.getRate() >
                    SECOND_FIRE_MOVING_SPECTRUM) {
                try {
                    long beginProcess = System.currentTimeMillis();
                    List<double[]> allLeq = coreSignalProcessing.processSample(0.5);
                    processingTime = System.currentTimeMillis() - beginProcess;
                    if(!allLeq.isEmpty()) {
                        lastLvls = allLeq.get(allLeq.size() - 1);
                        audioProcess.listeners.firePropertyChange(PROP_MOVING_LEQ,
                                lastProcessedSpectrum, lastProcessedSpectrum + pushedSamples);
                        lastProcessedSpectrum = pushedSamples;
                    }
                } catch (FileNotFoundException ex) {
                    // Ignore, do not process sample
                }
            }
            */
        }

        public boolean isProcessing() {
            return processing.get();
        }

        @Override
        public void run() {
            final int rate = audioProcess.getRate();
            int secondCursor = 0;
            try {
                while (audioProcess.currentState != STATE.WAITING_END_PROCESSING
                        && audioProcess.currentState != STATE.CLOSED) {
                    while (!bufferToProcess.isEmpty()) {
                        processing.set(true);
                        short[] buffer = bufferToProcess.remove(0);
                        double[] samples = new double[buffer.length];
                        for (int i = 0; i < buffer.length; ++i) {
                            if (buffer[i] > 0) {
                                samples[i] = Math.min(1.0D, (double) buffer[i] / 32767.0D);
                            } else {
                                samples[i] = Math.max(-1.0D, (double) buffer[i] / 32768.0D);
                            }
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

    private static class StandartLeqProcessing implements Runnable {
        private List<byte[]> bufferToProcess = new CopyOnWriteArrayList<byte[]>();
        private final AudioProcess audioProcess;
        private AtomicBoolean processing = new AtomicBoolean(false);
        private CoreSignalProcessing coreSignalProcessing;
        private List<double[]> lvls = new ArrayList<>();

        public StandartLeqProcessing(AudioProcess audioProcess) {
            this.audioProcess = audioProcess;
            this.coreSignalProcessing = new CoreSignalProcessing(audioProcess.getRate(), ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
        }

        public void addSample(byte[] sample) {
            bufferToProcess.add(sample);
        }

        public boolean isTaskQueueEmpty() {
            return bufferToProcess.isEmpty() && !processing.get();
        }

        private void processSecondSample(double[] secondSample) {
            try {
                coreSignalProcessing.addSample(secondSample);
                lvls.addAll(coreSignalProcessing.processSample(1.));
            } catch (FileNotFoundException ex) {
                // Ignore, do not process sample
            }
        }

        @Override
        public void run() {
            final int rate = audioProcess.getRate();
            double[] secondSample = new double[rate];
            int secondCursor = 0;
            int totalRead = 0;
            while (audioProcess.currentState != STATE.WAITING_END_PROCESSING) {
                while(!bufferToProcess.isEmpty()) {
                    try {
                        processing.set(true);
                        byte[] buffer = bufferToProcess.remove(0);
                        double[] samples = CoreSignalProcessing.convertBytesToDouble(buffer, buffer.length);
                        int lengthRead = Math.min(samples.length, rate - secondCursor);
                        // Copy sample fragment into second array
                        System.arraycopy(samples, 0, secondSample, secondCursor, lengthRead);
                        secondCursor+=lengthRead;
                        if(lengthRead < samples.length) {
                            processSecondSample(secondSample);
                            secondCursor = 0;
                            // Copy remaining sample fragment into new second array
                            int newLengthRead = samples.length - lengthRead;
                            System.arraycopy(samples, lengthRead, secondSample, secondCursor, newLengthRead);
                            secondCursor+=newLengthRead;
                        }
                        if(secondCursor == rate) {
                            processSecondSample(secondSample);
                            secondCursor = 0;
                        }
                    } finally {
                        processing.set(!bufferToProcess.isEmpty());
                    }
                }
                processing.set(false);
            }
        }
    }
}

