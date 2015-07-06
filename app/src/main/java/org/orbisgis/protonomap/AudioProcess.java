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
    private final CoreSignalProcessing movingLeqProcessing;
    private final static int MILLISECOND_FIRE_MOVING_LEQ = 100;
    private long lastTimeFiredMovingLeq = 0;




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
                        this.movingLeqProcessing = new CoreSignalProcessing(rate,
                                ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("This device is not compatible");
    }

    private void init() {
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
            byte[] buffer = new byte[bufferSize];
            if (recording.get() && audioRecord != null) {
                try {
                    try {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                    } catch (IllegalArgumentException | SecurityException ex) {
                        // Ignore
                    }
                    lastTimeFiredMovingLeq = System.currentTimeMillis();
                    audioRecord.startRecording();
                    while (recording.get()) {
                        audioRecord.read(buffer, 0, buffer.length);
                        movingLeqProcessing.addSample(CoreSignalProcessing.convertBytesToDouble(buffer, buffer.length));
                        long now = System.currentTimeMillis();
                        if(lastTimeFiredMovingLeq + MILLISECOND_FIRE_MOVING_LEQ < now) {
                            listeners.firePropertyChange(PROP_MOVING_LEQ, lastTimeFiredMovingLeq, now);
                            lastTimeFiredMovingLeq = now;
                        }
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
        try {
            synchronized (movingLeqProcessing) {
                List<double[]> timeLevels = movingLeqProcessing.processSample(MILLISECOND_FIRE_MOVING_LEQ / 1000.);
                if(!timeLevels.isEmpty()) {
                    return timeLevels.get(timeLevels.size() - 1);
                } else {
                    return null;
                }
            }
        } catch (FileNotFoundException ex) {
            return null;
        }
    }

    public int getRate() {
        return rate;
    }

    private static class MovingLeqProcessing implements Runnable {
        private List<byte[]> bufferToProcess = new CopyOnWriteArrayList<byte[]>();
        private final AudioProcess audioProcess;
        private AtomicBoolean processing = new AtomicBoolean(false);
        private CoreSignalProcessing coreSignalProcessing;
        private List<double[]> lvls = new ArrayList<>();

        public MovingLeqProcessing(AudioProcess audioProcess) {
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

