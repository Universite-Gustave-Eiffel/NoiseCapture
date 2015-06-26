package org.orbisgis.sos;

import org.junit.Assert;
import org.junit.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


/**
 * Created by G. Guillaume on 03/06/2015.
 * Unit tests concerning the A-weighting of audio data
 */
public class AWeightingTest {

    /**
     * Unit test on getting A-weighting coefficients
     */
    @Test
    public void testGetAWeightingCoefficients() {
        AWeighting aWeighting = new AWeighting();
        double[] numerator = aWeighting.numerator;
        double[] denominator = aWeighting.denominator;
        Assert.assertEquals(numerator.length, denominator.length);
    }

    /**
     * Unit test on A-weighting a 1-second pink noise
     * @throws IOException
     * @throws UnsupportedAudioFileException
     */
    @Test
    public void testAWeighting() throws IOException, UnsupportedAudioFileException {

        /*
        Reference data (i.e. expected results)
         */

        // Reference A-weighted signal (i.e. expected results)
        Scanner scanExpectedData = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s_A_weighted.txt"));
        List<Double> expectedData = new ArrayList();
        int nbExpectedSamples = 0;
        while (scanExpectedData.hasNext()) {
            expectedData.add(Double.parseDouble(scanExpectedData.next()));
            nbExpectedSamples++;
        }
        scanExpectedData.close();
        double[] expectedAWeightedSignal = new double[nbExpectedSamples];
        for (int idT = 0; idT < nbExpectedSamples; idT++) {
            expectedAWeightedSignal[idT] = expectedData.get(idT).doubleValue();
        }

        /*
        Actual results
         */

        // Loading of the audio signal (i.e. the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
        Scanner scanAudio = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
        List<Double> audioSignal = new ArrayList();
        int nbActualSamples = 0;
        while (scanAudio.hasNext()) {
            audioSignal.add(Double.parseDouble(scanAudio.next()));
            nbActualSamples++;
        }
        scanAudio.close();
        double[] audioSignalArr = new double[nbActualSamples];
        for (int idT = 0; idT < nbActualSamples; idT++) {
            audioSignalArr[idT] = audioSignal.get(idT);
        }

        // A-weighting of the audio signal
        double[] actualAWeightedSignal = AWeighting.aWeightingSignal(audioSignalArr);

        /*
        Comparisons of expected and actual results
         */

        Assert.assertEquals(expectedAWeightedSignal.length, actualAWeightedSignal.length);
        Assert.assertArrayEquals(expectedAWeightedSignal, actualAWeightedSignal, 0);

    }
}