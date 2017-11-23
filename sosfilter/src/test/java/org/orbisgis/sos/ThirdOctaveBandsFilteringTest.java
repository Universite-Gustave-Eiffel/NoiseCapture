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

import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;


/**
 * Created by G. Guillaume on 02/06/2015.
 * Unit tests concerning both the A-weighting and third octave bands filtering (mainly) of audio data
 */
public class ThirdOctaveBandsFilteringTest {
    private final static Logger LOGGER = LoggerFactory.getLogger(ThirdOctaveBandsFiltering.class);
    private static final double REF_SOUND_PRESSURE = 2e-5;

    /**
     * Get the index of a double value in a double array
     * @param arr double array
     * @param val double value
     * @return integer index of the element in the array (return -1 if the element does not exist)
     */
    private int getIndexOfElementInArray(double[] arr, double val) {
        int index = -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == val) {
                index = i;
            }
        }
        return index;
    }

    private File[] getFilesListStartingWith(File filesPath, final String fileNameStartsWith) {
        File[] foundFiles = filesPath.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(fileNameStartsWith);
            }
        });
        return foundFiles;
    }

    /**
     * Unit test on the reading of the csv file containing the coefficients of the third octave bands filters
     */
    @Test
    public void testReadCsv() {
        int samplingRate = 44100;
        ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands = ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED;
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, frequencyBands);
        List<ThirdOctaveBandsFiltering.FiltersParameters> filtersCoefficients = thirdOctaveBandsFiltering.getFilterParameters();
        assertEquals(24, filtersCoefficients.size());
    }

    @Test
    public void benchmarkFiltering() throws IOException {


        /*
        Reference data (i.e. expected results)
         */

        int samplingRate = 44100;
        ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands = ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED;
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, frequencyBands);
        double[] standardFrequencies = ThirdOctaveBandsFiltering.getStandardFrequencies(frequencyBands);
        int nbFrequencies = standardFrequencies.length;

        /*
        Actual results
         */

        // Loading of the audio signal (i.e. the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
        Scanner scanAudio = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
        List<Double> inputSig= new ArrayList<Double>();
        while (scanAudio.hasNext()) {
            inputSig.add(Double.parseDouble(scanAudio.next()));
        }
        scanAudio.close();
        double[] audioSignalArr = new double[inputSig.size()];
        for (int idT = 0; idT < audioSignalArr.length; idT++) {
            audioSignalArr[idT] = inputSig.get(idT);
        }
        // Warmup
        for(int i = 0; i < 5; i++) {
            thirdOctaveBandsFiltering.thirdOctaveFiltering(audioSignalArr);
        }

        long beginFiltering = System.currentTimeMillis();

        // Third octave bands filtering of the audio signal
//        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, sampleLength);
        double[][] actualFilteredSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(audioSignalArr);

        LOGGER.info("Filtering done in {} ms", (System.currentTimeMillis() - beginFiltering));

        // Equivalent sound pressure levels of the third octave bands filtered signals
        // Warmup
        for(int i = 0; i < 5; i++) {
            AcousticIndicators.getLeq(actualFilteredSignal[0], REF_SOUND_PRESSURE);
        }

        long beginLeq = System.currentTimeMillis();
        double[] actualLeq = new double[nbFrequencies];
        for (int idf = 0; idf < nbFrequencies; idf++) {
            actualLeq[idf] = AcousticIndicators.getLeq(actualFilteredSignal[idf], REF_SOUND_PRESSURE);
        }


        LOGGER.info("Leq done in {} ms", (System.currentTimeMillis() - beginLeq));
    }

    /**
     * Unit test on third octave bands filtering a 1-second pink noise: comparison of the filtered time signals with
     * expected ones
     * @throws IOException
     */
    public void testThirdOctaveBandsFiltering() throws IOException{

        /*
        Reference data (i.e. expected results)
         */

        int samplingRate = 44100;
        double sampleLength = 1.;
        ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands = ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED;
        int nbExpectedSamples = (int)(samplingRate * sampleLength);
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, frequencyBands);
        double[] standardFrequencies = ThirdOctaveBandsFiltering.getStandardFrequencies(frequencyBands);
        int nbFrequencies = standardFrequencies.length;

        // Reference third octave bands filtered signals (i.e. expected results)
        File filesPath = new File("src/test/resources/org/orbisgis/sos/");
        String fileNameRoot = "pinknoise_1s_3rd_oct_";
        File[] foundFiles = getFilesListStartingWith(filesPath, fileNameRoot);

        // Double array containing the expected filtered signals
        double[][] expectedFilteredSignal = new double[nbFrequencies][nbExpectedSamples];
        for (File file : foundFiles) {
            String fileName = file.getName();
            // Standard nominal center frequency of the third octave band
            double ctrFreq = Double.parseDouble(fileName.substring(fileNameRoot.length(), fileName.indexOf("Hz")));
            int idCtrFreq = getIndexOfElementInArray(standardFrequencies, ctrFreq);
            Scanner scanExpectedData = new Scanner(file);
            List<Double> refData= new ArrayList<Double>();
            while (scanExpectedData.hasNext()) {
                refData.add(Double.parseDouble(scanExpectedData.next()));
            }
            scanExpectedData.close();
            for (int idT = 0; idT < nbExpectedSamples; idT++) {
                expectedFilteredSignal[idCtrFreq][idT] = refData.get(idT);
            }
        }

        // Double array containing the expected equivalent sound pressure levels of the audio signal
        double[] expectedLeq = new double[]{-7.0973669866213065, -6.2965899956866345, -5.3078017689128814,
                                            -6.8795990415408594, -5.3277667783912595, -5.6278761344874884,
                                            -5.6970352863031, -6.2160336916659347, -5.7015316919145809,
                                            -5.7866266910773518, -6.0190061348242629, -6.1732453627428914,
                                            -5.9426324145047253, -6.2428616092913529, -5.8656603608320772,
                                            -5.4352349729066596, -5.6635779294813551, -5.6954504341153678,
                                            -5.1865800342603752, -5.0790653253417961, -4.6935101856572512,
                                            -4.765503741093208, -4.7147868664115666, -4.9323563222821489};

        /*
        Actual results
         */

        // Loading of the audio signal (i.e. the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
        Scanner scanAudio = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
        List<Double> inputSig= new ArrayList<Double>();
        while (scanAudio.hasNext()) {
            inputSig.add(Double.parseDouble(scanAudio.next()));
        }
        scanAudio.close();
        double[] audioSignalArr = new double[inputSig.size()];
        for (int idT = 0; idT < audioSignalArr.length; idT++) {
            audioSignalArr[idT] = inputSig.get(idT);
        }

        long beginFiltering = System.currentTimeMillis();

        // Third octave bands filtering of the audio signal
//        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, sampleLength);
        double[][] actualFilteredSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(audioSignalArr);

        long beginLeq = System.currentTimeMillis();
        LOGGER.info("Filtering done in {} ms", (beginLeq - beginFiltering));

        // Equivalent sound pressure levels of the third octave bands filtered signals
        double[] actualLeq = new double[nbFrequencies];
        for (int idf = 0; idf < nbFrequencies; idf++) {
            actualLeq[idf] = AcousticIndicators.getLeq(actualFilteredSignal[idf], REF_SOUND_PRESSURE);
        }


        LOGGER.info("Leq done in {} ms", (System.currentTimeMillis() - beginLeq));

        /*
        Comparisons of expected and actual results
         */

        // Comparison of expected and actual results
        for (int idf = 0; idf < nbFrequencies; idf++) {
            Assert.assertArrayEquals(expectedFilteredSignal[idf], actualFilteredSignal[idf], 1E-12);
        }

        // Comparison of expected and actual equivalent sound pressure levels
        Assert.assertArrayEquals(expectedLeq, actualLeq, 1E-3);
    }

    /**
     * Unit test on third octave bands filtering a 1-second pink noise: comparison of the equivalent sound pressure
     * levels per third octave bands with expected ones
     * @throws IOException
     */
    @Test
    public void testAWeightingAnThirdOctaveBandsFiltering() throws IOException{
        Logger logger = LoggerFactory.getLogger(ThirdOctaveBandsFilteringTest.class);
        /*
        Reference data (i.e. expected results)
         */

        int samplingRate = 44100;
        ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands = ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED;
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, frequencyBands);
        double[] standardFrequencies = ThirdOctaveBandsFiltering.getStandardFrequencies(frequencyBands);
        int nbFrequencies = standardFrequencies.length;
        int nbExpectedSamples = (int)(ThirdOctaveBandsFiltering.getSampleBufferDuration(frequencyBands) * samplingRate);

        // Expected third octave bands filtered signals
        File filesPath = new File("src/test/resources/org/orbisgis/sos/");
        final String fileNameRoot = "pinknoise_1s_A_weighted_3rd_oct_";
        File[] foundFiles = getFilesListStartingWith(filesPath, fileNameRoot);

        // Double array containing the expected filtered signals
        double[][] expectedFilteredSignal = new double[nbFrequencies][nbExpectedSamples];
        for (File file : foundFiles) {
            String fileName = file.getName();
            // Standard nominal center frequency of the third octave band
            double ctrFreq = Double.parseDouble(fileName.substring(fileNameRoot.length(), fileName.indexOf("Hz")));
            int idCtrFreq = getIndexOfElementInArray(standardFrequencies, ctrFreq);
            Scanner scanRef = new Scanner(file);
            List<Double> refData= new ArrayList();
            while (scanRef.hasNext()) {
                refData.add(Double.parseDouble(scanRef.next()));
            }
            scanRef.close();
            for (int idT = 0; idT < nbExpectedSamples; idT++) {
                expectedFilteredSignal[idCtrFreq][idT] = refData.get(idT).doubleValue();
            }
        }

        // Double array containing the expected equivalent sound pressure levels of the A-weighted signal
        double[] expectedLAeq = new double[]{-26.161183471504501, -22.306693915046981, -18.633466776366664,
                                             -17.72022287948035, -13.89977584781793, -12.301951032576017,
                                             -10.559414688636281, -9.5034089175215524, -7.6675678600608501,
                                             -6.6277333815003736, -6.0127170847103351, -5.5759839976091481,
                                             -4.9576870805312439, -5.0412837223810172, -4.6020305469466161,
                                             -4.2570338838010571, -4.7537949562959882, -5.2801486059526459,
                                             -5.6127883969334462, -6.8972448281621892, -8.7803132921304758,
                                             -12.628481580985344, -19.615200681068686, -34.091179733661974};

        /*
        Actual results
         */

        // Loading of the audio signal (i.e. the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
        Scanner scanAudio = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
        List<Double> audioSignal = new ArrayList();
        while (scanAudio.hasNext()) {
            audioSignal.add(Double.parseDouble(scanAudio.next()));
        }
        scanAudio.close();
        double[] audioSignalArr = new double[nbExpectedSamples];
        for (int idT = 0; idT < nbExpectedSamples; idT++) {
            audioSignalArr[idT] = audioSignal.get(idT);
        }

        // A-weighting of the audio signal
        double[] actualAWeightedSignal = AWeighting.aWeightingSignal(audioSignalArr);

        // Third octave bands filtering of the A-weighted signal
        double[][] actualFilteredAWeightedSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(actualAWeightedSignal);

        // Third octave bands filtering of the input signal (i.e. unweighted)
        long deb = System.currentTimeMillis();
        logger.info("Compute Filtering signal in "+(System.currentTimeMillis() - deb)+" ms");

        // Equivalent sound pressure levels of the third octave bands filtered A-weighted signals
        double[] actualLAeq = new double[nbFrequencies];
        for (int idf = 0; idf < nbFrequencies; idf++) {
            actualLAeq[idf] = AcousticIndicators.getLeq(actualFilteredAWeightedSignal[idf], Math.sqrt(2e-5));
        }

        //Comparisons of expected and actual results

        // Comparison of expected and actual filtered signals
        for (int idf = 0; idf < nbFrequencies; idf++) {
            Assert.assertArrayEquals(expectedFilteredSignal[idf], actualFilteredAWeightedSignal[idf], 1E-13);
        }

        // Comparison of expected and actual equivalent sound pressure levels
        Assert.assertArrayEquals(expectedLAeq, actualLAeq, 1E-3);
    }


    @Test
    public void testPinkNoise() {
        short[] pinkNoise = SOSSignalProcessing.makePinkNoise(441000, (short)2500, 0);
        FFTSignalProcessing fftSignalProcessing = new FFTSignalProcessing(44100,
                ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, pinkNoise.length);
        fftSignalProcessing.addSample(pinkNoise);
        FFTSignalProcessing.ProcessingResult result = fftSignalProcessing.processSample(false,
                false,
                false);

        // Compute
        StandardDeviation standardDeviation = new StandardDeviation();
        double[] dArray = new double[result.dBaLevels.length];
        for(int i = 0; i < result.dBaLevels.length; i++) {
            dArray[i] = result.dBaLevels[i];
        }
        assertEquals(0, standardDeviation.evaluate(dArray), 0.25);
    }
}