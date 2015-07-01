package org.orbisgis.sos;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

/**
 * Created by G. Guillaume on 26/06/15.
 *  * Unit tests concerning audio signal processing
 */
public class CoreSignalProcessingTest extends TestCase {

//    @Test
//    public void testGetSamplingRate() throws Exception {
//
//    }
//
//    @Test
//    public void testAddSample() throws Exception {
//
//    }

//    @Test
//    public void testFilterSignal() throws Exception {
//
//    }

    @Test
    public void testProcessAudioOneSecond() throws Exception {

        /*
        Reference data (i.e. expected results)
         */

        // A_weighted third octave equivalent sound pressure level for a 1-second duration pink noise
        double[] leqARef = {-48.277045867404325, -42.197332866243016, -39.81981182661584, -34.123114313620789, -24.799668829041678, -20.61843065726044, -16.690255852823462, -9.8533858675098589, -9.4558491377955072, -3.377719771580558, 1.040458902433707, 4.6841927443986116, 8.585031581505838, 9.8291684889675093, 12.926879878016472, 15.969617163105097, 17.413779797610282, 19.663668814588902, 22.480276685249059, 23.705464196232903, 24.698471070436298, 25.946891069975354, 27.561750697954462, 28.238782186778565, 29.102300557890786, 29.513809466969313, 29.745584167472231, 29.062644619491262, 27.921771613125177, 25.053554237069591, 19.003738644603811, 4.8107157610588063};

        /*
        Actual results
         */

        // Stream of the audio signal (i.e. pinknoise_1s.wav)
        InputStream inputStream = CoreSignalProcessingTest.class.getResourceAsStream("pinknoise_1s.raw");

        final int rate = 44100;
        CoreSignalProcessing signalProcessing = new CoreSignalProcessing(rate);

        List<double[]> leqs = signalProcessing.processAudio(16, rate, inputStream, AcousticIndicators.TIMEPERIOD_SLOW);
        inputStream.close();

        /*
        Comparisons of expected and actual results
         */

        assertEquals(32, leqs.get(0).length);
        Assert.assertArrayEquals(leqARef, leqs.get(0), 1e-3);
    }
}