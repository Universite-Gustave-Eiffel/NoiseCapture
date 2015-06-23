package org.orbisgis.sos;

import org.junit.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static junit.framework.Assert.assertEquals;



/**
 * Created by G. Guillaume on 03/06/2015.
 */
public class AWeightingTest {

    @Test
    public void testGetAWeightingCoefficients() {
        AWeighting aWeighting = new AWeighting();
        double[] numerator = aWeighting.numerator;
        double[] denominator = aWeighting.denominator;
        assertEquals(numerator.length, denominator.length);
    }

    @Test
    public void testAWeighting() throws IOException, UnsupportedAudioFileException {

        final int rate = 44100;

        // Reference A-weighted signal
        Scanner scanRef = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s_A_weighted.txt"));
        List<Double> refData= new ArrayList();
        while (scanRef.hasNext()) {
            refData.add(Double.parseDouble(scanRef.next()));
        }
        scanRef.close();
        double[] aWeightedSigRef = new double[refData.size()];
        for (int i = 0; i < aWeightedSigRef.length; i++) {
            aWeightedSigRef[i] = refData.get(i).doubleValue();
        }

        // A-weigthing of the Audio (.wav) file signal (input is the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
        Scanner scanWav = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
        List<Double> inputSig= new ArrayList();
        while (scanWav.hasNext()) {
            inputSig.add(Double.parseDouble(scanWav.next()));
        }
        scanWav.close();
        double[] inputSigArr = new double[inputSig.size()];
        for (int i = 0; i < inputSigArr.length; i++) {
            inputSigArr[i] = inputSig.get(i);                // java 1.5+ style (outboxing)
        }
        double[] aWeightedSig = AWeighting.aWeightingSignal(inputSigArr);

        for (int id = 0; id < aWeightedSigRef.length; id++) {
            assertEquals(aWeightedSigRef[id], aWeightedSig[id]);
        }
    }
}