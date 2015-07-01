package org.orbisgis.protonomap;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processing thread of packets of Audio signal
 */
public class AudioProcess implements Runnable {
    private AtomicBoolean recording;
    private final int bufferSize;
    private final int encoding;
    private final int rate;
    private final int audioChannel;
    private AudioRecord audioRecord;

    /**
     * Constructor
     * @param recording Recording state
     */
    public AudioProcess(AtomicBoolean recording) {
        this.recording = recording;
        final int[] mSampleRates = new int[] { 8000, 11025, 22050, 44100 };
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
                        return;
                    }
                }
            }
        }
        bufferSize = AudioRecord.ERROR_BAD_VALUE;
        encoding = -1;
        audioChannel = -1;
        rate = -1;
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
        audioRecord = createAudioRecord();
        byte[] buffer = new byte[bufferSize];
        if(recording.get()) {
            try {

                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                } catch (IllegalArgumentException | SecurityException ex) {
                    // Ignore
                }
                audioRecord.startRecording();
                while (recording.get()) {
                    audioRecord.read(buffer, 0, buffer.length);
                    //TODO add to stacks of process threads
                }
            } catch (Exception ex) {
                Log.e("tag_record", "Error while recording", ex);
            } finally {
                audioRecord.stop();
                audioRecord.release();
            }
        }
    }
}

