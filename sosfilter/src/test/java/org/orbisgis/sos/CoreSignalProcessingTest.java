package org.orbisgis.sos;

import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by G. Guillaume on 26/06/15.
 *  * Unit tests concerning audio signal processing
 */
public class CoreSignalProcessingTest {

    // Reference sound pressure level [Pa]
    public static final double REF_SOUND_PRESSURE = Math.sqrt(0.00002);

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
    public void testLeqStats() {
        LeqStats leqStats = new LeqStats();
        leqStats.addLeq(45.15);
        leqStats.addLeq(45.21);
        leqStats.addLeq(45.35);
        leqStats.addLeq(46.33);
        leqStats.addLeq(46.87);
        leqStats.addLeq(45.86);
        leqStats.addLeq(48.15);
        leqStats.addLeq(49.5);
        leqStats.addLeq(50.5);
        leqStats.addLeq(50.52);
        leqStats.addLeq(50.62);
        leqStats.addLeq(49.89);
        leqStats.addLeq(49.88);
        leqStats.addLeq(48.45);
        leqStats.addLeq(65.15);
        leqStats.addLeq(65.14);
        leqStats.addLeq(66.12);
        leqStats.addLeq(66.2);
        leqStats.addLeq(67.21);
        leqStats.addLeq(66.25);

        LeqStats.LeqOccurences leqOccurences = leqStats.computeLeqOccurences();
        assertEquals(50, leqOccurences.getLa10(), 0.01);
    }

    @Test
    public void testProcessAudioOneSecond() throws Exception {

        /*
        Reference data (i.e. expected results)
         */

        // A_weighted third octave equivalent sound pressure level for a 1-second duration pink noise
        double[] leqARef = {-9.4558491377955072, -3.377719771580558, 1.040458902433707,
                4.6841927443986116, 8.585031581505838, 9.8291684889675093, 12.926879878016472,
                15.969617163105097, 17.413779797610282, 19.663668814588902, 22.480276685249059,
                23.705464196232903, 24.698471070436298, 25.946891069975354, 27.561750697954462,
                28.238782186778565, 29.102300557890786, 29.513809466969313, 29.745584167472231,
                29.062644619491262, 27.921771613125177, 25.053554237069591, 19.003738644603811,
                4.8107157610588063};

        /*
        Actual results
         */

        // Stream of the audio signal (i.e. pinknoise_1s.wav)

        final int rate = 44100;
        ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands = ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED;
        SOSSignalProcessing signalProcessing = new SOSSignalProcessing(rate, frequencyBands);
        //long deb = System.currentTimeMillis();
        //for(int i = 0; i < 100; i++) {
            InputStream inputStream = CoreSignalProcessingTest.class.getResourceAsStream("pinknoise_1s.raw");
            List<double[]> leqs = signalProcessing.processAudio(16, rate, inputStream, AcousticIndicators.TIMEPERIOD_SLOW, REF_SOUND_PRESSURE, ByteOrder.BIG_ENDIAN);
            inputStream.close();
        //}
        //System.out.println("Comp done in "+(System.currentTimeMillis() - deb)+" ms");
        /*
        Comparisons of expected and actual results
         */
        double[] leqf = leqs.get(0);
        assertEquals(24, leqf.length);
        Assert.assertArrayEquals(leqARef, leqf, 0.01);
    }
}