package org.orbisgis.sos;


import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static junit.framework.Assert.assertEquals;

/**
 * Created by G. Guillaume on 02/06/2015.
 */
public class ThirdOctaveBandsFilteringTest {

    private int findIndexElementInArray(double[] arr, double val) {
        int index = -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == val) {
                index = i;
            }
        }
        return index;
    }

    @Test
    public void testReadCsv() {
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
        List<ThirdOctaveBandsFiltering.FiltersParameters> filtersCoefficients = thirdOctaveBandsFiltering.getFilterParameters();
        assertEquals(32, filtersCoefficients.size());
    }

    @Test
    public void testThirdOctaveBandsFiltering() throws IOException{

        int samplingFrequency = 44100;
        double[] standardFrequencies = ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES;

        // Reference third octave bands filtered signals
        File filesPath = new File("src/test/resources/org/orbisgis/sos/");
        final String fileNameRoot = "pinknoise_1s_3rd_oct_";
        File[] foundFiles = filesPath.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(fileNameRoot);
            }
        });
        // Double array containing the reference data
        double[][] refFilteredSignal = new double[standardFrequencies.length][samplingFrequency];
        for (File file : foundFiles) {
            String fileName = file.getName();
            // Standard nominal center frequency of the third octave band
            double ctrFreq = Double.parseDouble(fileName.substring(fileNameRoot.length(), fileName.indexOf("Hz")));
            int idCtrFreq = findIndexElementInArray(standardFrequencies, ctrFreq);
            Scanner scanRef = new Scanner(file);
            List<Double> refData= new ArrayList();
            while (scanRef.hasNext()) {
                refData.add(Double.parseDouble(scanRef.next()));
            }
            scanRef.close();
            for (int iter = 0; iter < refFilteredSignal.length; iter++) {
                refFilteredSignal[idCtrFreq][iter] = refData.get(iter).doubleValue();
            }
        }

        // Third octave bands filtering of the audio file signal (i.e. the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
        Scanner scanWav = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
        List<Double> inputSig= new ArrayList();
        while (scanWav.hasNext()) {
            inputSig.add(Double.parseDouble(scanWav.next()));
        }
        scanWav.close();
        double[] inputSigArr = new double[inputSig.size()];
        for (int iter = 0; iter < inputSigArr.length; iter++) {
            inputSigArr[iter] = inputSig.get(iter);
        }
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
        double[][] newFilteredSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(inputSigArr);

        // Comparison of expected and actual results
        for (int idFreq = 0; idFreq < standardFrequencies.length; idFreq++) {
            for (int iter = 0; iter < refFilteredSignal.length; iter++) {
                assertEquals(refFilteredSignal[idFreq][iter], newFilteredSignal[idFreq][iter]);
            }
        }
    }

    @Test
    public void testAWeightingAnThirdOctaveBandsFiltering() throws IOException{

        int samplingFrequency = 44100;
        double[] standardFrequencies = ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES;

        // Reference third octave bands filtered signals
        File filesPath = new File("src/test/resources/org/orbisgis/sos/");
        final String fileNameRoot = "pinknoise_1s_A_weighted_3rd_oct_";
        File[] foundFiles = filesPath.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(fileNameRoot);
            }
        });
        // Double array containing the reference data
        double[][] refFilteredSignal = new double[standardFrequencies.length][samplingFrequency];
        for (File file : foundFiles) {
            String fileName = file.getName();
            // Standard nominal center frequency of the third octave band
            double ctrFreq = Double.parseDouble(fileName.substring(fileNameRoot.length(), fileName.indexOf("Hz")));
            int idCtrFreq = findIndexElementInArray(standardFrequencies, ctrFreq);
            Scanner scanRef = new Scanner(file);
            List<Double> refData= new ArrayList();
            while (scanRef.hasNext()) {
                refData.add(Double.parseDouble(scanRef.next()));
            }
            scanRef.close();
            for (int iter = 0; iter < refFilteredSignal.length; iter++) {
                refFilteredSignal[idCtrFreq][iter] = refData.get(iter).doubleValue();
            }
        }

        // A-weigthing of the audio file signal (i.e. the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
        Scanner scanWav = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
        List<Double> inputSig= new ArrayList();
        while (scanWav.hasNext()) {
            inputSig.add(Double.parseDouble(scanWav.next()));
        }
        scanWav.close();
        double[] inputSigArr = new double[inputSig.size()];
        for (int iter = 0; iter < inputSigArr.length; iter++) {
            inputSigArr[iter] = inputSig.get(iter);
        }
        double[] aWeightedSig = AWeighting.aWeightingSignal(inputSigArr);

        // Third octave bands filtering of the A-weighted signal
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
        double[][] newFilteredSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(aWeightedSig);

        // Comparison of expected and actual results
        for (int idFreq = 0; idFreq < standardFrequencies.length; idFreq++) {
            for (int iter = 0; iter < refFilteredSignal.length; iter++) {
                assertEquals(refFilteredSignal[idFreq][iter], newFilteredSignal[idFreq][iter]);
            }
        }
    }

//    @Test
//    public void testAWeightingAndThirdOctaveBandsFiltering() throws IOException{
//        InputStream inputStream = ThirdOctaveBandsFilteringTest.class.getResourceAsStream("pinknoise_1s.raw");
//        final int rate = 44100;
//        CoreSignalProcessing signalProcessing = new CoreSignalProcessing(rate);
//        List<double[]> leqs = signalProcessing.processAudio(16, rate, inputStream, AcousticIndicators.TIMEPERIOD_SLOW);
//        inputStream.close();
//        assertEquals(10, leqs.size());
//        // Unweighted third octave equivalent sound pressure level for
//        double[] leqRef = {-53.872676381609217, -55.87433108731183, -56.102001834375947, -56.651847653661996, -55.289516298409289, -54.767405528150483, -54.060603238008994, -52.885425635653711, -54.495784740125657, -53.816702276810076, -52.806225014358823, -54.377272271458622, -52.819299939410143, -53.074556962714574, -53.174970566980207, -53.717401356432759, -53.195585249811693, -53.284929972396654, -53.51318652335577, -53.676714386306898, -53.43679296946263, -53.746934655198366, -53.3663312476275, -52.940145856745872, -53.177643555058516, -53.219824374381744, -52.689166864077592, -52.599381584509388, -52.195459970595628, -52.263024804075435, -52.212591174446146, -52.417135786021582};
//        double[] leqARef = {-110.05194796833435, -105.73527124964647, -100.30558380079043, -96.500674427552894, -89.476697831748723, -85.028528324870621, -80.285028523545776, -75.582113608605283, -73.563836439945931, -69.839954437213777, -66.126730864976906, -65.206724358850977, -61.388273320091088, -59.74362326335681, -58.038255155623546, -57.004234491441643, -55.162074773246282, -54.127238943655989, -53.505257179317915, -53.080080046655489, -52.451515944238416, -52.546166534504366, -52.102525465026631, -51.762277931457838, -52.268331554635957, -52.804263826003563, -53.115392542280816, -54.416129182032044, -56.284600266910864, -60.137956695875772, -67.12582525468919, -81.660727292707037};
//        assertArrayEquals(leqARef, leqs.get(0), 1e-6);
//    }

}