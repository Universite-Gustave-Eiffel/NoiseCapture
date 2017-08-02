/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

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




//    @Test
//    public void testAWeightingAttenuation() throws IOException{
//
//        /*
//        Reference data (i.e. expected results)
//         */
//
//        int nbExpectedSamples = 44100;
//        int samplingRate = 44100;
//        int binSize = 1024;
//        double[] standardFrequencies = ThirdOctaveBandsFiltering.standardFrequencies;
//        int nbFrequencies = standardFrequencies.length;
//
//        // Double array containing the expected third octave bands attenuation
//        double[] expectedAWeightingAttenuation = new double[]{-56.7, -50.5, -44.7, -39.4, -34.6, -30.2, -26.2, -22.5,
//                                                              -19.1, -16.1, -13.4, -10.9,  -8.6, -6.6, -4.8, -3.2, -1.9,
//                                                              -0.8, 0.0, 0.6, 1.0, 1.2, 1.3, 1.2, 1.0, 0.5, -0.1, -1.1,
//                                                              -2.5, -4.3, -6.6, -9.3};
//
//        /*
//        Actual results
//         */
//        // Loading of the audio signal (i.e. the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
//        Scanner scanAudio = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
//        List<Double> audioSignal = new ArrayList();
//        while (scanAudio.hasNext()) {
//            audioSignal.add(Double.parseDouble(scanAudio.next()));
//        }
//        scanAudio.close();
//        double[] audioSignalArr = new double[nbExpectedSamples];
//        for (int idT = 0; idT < nbExpectedSamples; idT++) {
//            audioSignalArr[idT] = audioSignal.get(idT);
//        }
//
//        // A-weighting of the audio signal
//        double[] actualAWeightedSignal = AWeighting.aWeightingSignal(audioSignalArr);
//
//        // Third octave bands filtering of the A-weighted signal
//        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
//        double[][] actualFilteredAWeightedSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(actualAWeightedSignal);
//
//        // Third octave bands filtering of the input signal (i.e. unweighted)
//        double[][] actualFilteredInputSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(audioSignalArr);
//
//        double[] fftResult = new double[binSize * 2];
//        for (int idF = 0; idF < nbFrequencies; idF++) {
//
//            // TODO FFT of the A-weighted and unweighted signals.
//            int read = 0;
//            double[] spectrums = new double[binSize / 2 + 1];
//            // Buffer of length binSize
//            double[] buffer = Arrays.copyOfRange(actualFilteredInputSignal[idF], read, read + binSize);
//            buffer = AcousticIndicators.hannWindow(buffer);
//            FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
//            Complex resultCmplx[] = fft.transform(buffer, TransformType.FORWARD);
//            double resultsReal[] = new double[binSize];
//
//            for (int i = 0; i < resultCmplx.length; i++) {
//                double real = resultCmplx[i].getReal();
//                double imag = resultCmplx[i].getImaginary();
//                double rms = Math.sqrt(Math.pow(real, 2) + Math.pow(imag, 2));
//                resultsReal[i] = 20 * Math.log10(rms / AcousticIndicators.REF_SOUND_PRESSURE);
//            }
//            read += binSize;
//            }
//    }

}