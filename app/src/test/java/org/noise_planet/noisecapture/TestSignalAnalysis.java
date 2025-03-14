package org.noise_planet.noisecapture;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.assertEquals;

import android.media.AudioFormat;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public class TestSignalAnalysis {

    @Test
    public void testAudioProcess() throws Exception {
        AtomicBoolean recording = new AtomicBoolean(true);
        AtomicBoolean processing = new AtomicBoolean(true);
        AudioProcess audioProcess = new AudioProcess(recording, processing);
        assertEquals(44100, audioProcess.getRate());
        assertEquals(AudioFormat.ENCODING_PCM_FLOAT, audioProcess.getEncoding());
    }
}
