package org.orbisgis.sos;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


/**
 * Created by G. Guillaume on 02/06/2015.
 * Unit tests concerning both the A-weighting and third octave bands filtering (mainly) of audio data
 */
public class ThirdOctaveBandsFilteringTest {

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
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
        List<ThirdOctaveBandsFiltering.FiltersParameters> filtersCoefficients = thirdOctaveBandsFiltering.getFilterParameters();
        Assert.assertEquals(32, filtersCoefficients.size());
    }

    /**
     * Unit test on third octave bands filtering a 1-second pink noise
     * @throws IOException
     */
    @Test
    public void testThirdOctaveBandsFiltering() throws IOException{

        /*
        Reference data (i.e. expected results)
         */

        int nbExpectedSamples = 44100;
        double[] standardFrequencies = ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES;
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
            List<Double> refData= new ArrayList();
            while (scanExpectedData.hasNext()) {
                refData.add(Double.parseDouble(scanExpectedData.next()));
            }
            scanExpectedData.close();
            for (int idT = 0; idT < nbExpectedSamples; idT++) {
                expectedFilteredSignal[idCtrFreq][idT] = refData.get(idT).doubleValue();
            }
        }

        // Double array containing the expected equivalent sound pressure levels of the audio signal
        double[] expectedLeq = new double[]{-6.2536288952690864, -8.9224519032469463, -8.2510880930132462, -9.4003324484153499, -8.0382590641823697, -6.814082180728497, -6.9162266944864061, -5.3301964319815118, -7.0973669866213065, -6.2965899956866345, -5.3078017689128814, -6.8795990415408594, -5.3277667783912595, -5.6278761344874884, -5.6970352863031, -6.2160336916659347, -5.7015316919145809, -5.7866266910773518, -6.0190061348242629, -6.1732453627428914, -5.9426324145047253, -6.2428616092913529, -5.8656603608320772, -5.4352349729066596, -5.6635779294813551, -5.6954504341153678, -5.1865800342603752, -5.0790653253417961, -4.6935101856572512, -4.765503741093208, -4.7147868664115666, -4.9323563222821489};

        /*
        Actual results
         */

        // Loading of the audio signal (i.e. the file pinknoise_1s.txt that refers to pinknoise_1s.wav)
        Scanner scanAudio = new Scanner(new File("src/test/resources/org/orbisgis/sos/pinknoise_1s.txt"));
        List<Double> inputSig= new ArrayList();
        while (scanAudio.hasNext()) {
            inputSig.add(Double.parseDouble(scanAudio.next()));
        }
        scanAudio.close();
        double[] audioSignalArr = new double[inputSig.size()];
        for (int idT = 0; idT < audioSignalArr.length; idT++) {
            audioSignalArr[idT] = inputSig.get(idT);
        }

        // Third octave bands filtering of the audio signal
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
        double[][] actualFilteredSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(audioSignalArr);

        // Equivalent sound pressure levels of the third octave bands filtered signals
        double[] actualLeq = new double[nbFrequencies];
        for (int idf = 0; idf < nbFrequencies; idf++) {
            actualLeq[idf] = AcousticIndicators.getLeq(actualFilteredSignal[idf]);
        }

        /*
        Comparisons of expected and actual results
         */

        // Comparison of expected and actual results
        Assert.assertArrayEquals(expectedFilteredSignal, actualFilteredSignal);

        // Comparison of expected and actual equivalent sound pressure levels
        Assert.assertArrayEquals(expectedLeq, actualLeq, 1E-3);
    }

    @Test
    public void testAWeightingAnThirdOctaveBandsFiltering() throws IOException{

        /*
        Reference data (i.e. expected results)
         */

        int nbExpectedSamples = 44100;
        double[] standardFrequencies = ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES;
        int nbFrequencies = standardFrequencies.length;

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
        double[] expectedLAeq = new double[]{-58.823366746199916, -57.411103403259737, -52.470297505531704, -49.004785317367229, -41.897908121810943, -37.330987418100001, -33.070404060472256, -27.937599116939168, -26.161183471504501, -22.306693915046981, -18.633466776366664, -17.72022287948035, -13.89977584781793, -12.301951032576017, -10.559414688636281, -9.5034089175215524, -7.6675678600608501, -6.6277333815003736, -6.0127170847103351, -5.5759839976091481, -4.9576870805312439, -5.0412837223810172, -4.6020305469466161, -4.2570338838010571, -4.7537949562959882, -5.2801486059526459, -5.6127883969334462, -6.8972448281621892, -8.7803132921304758, -12.628481580985344, -19.615200681068686, -34.091179733661974};

        // Double array containing the expected third octave bands attenuation
        double[] attenuationAWeighting = new double[]{-56.7, -50.5, -44.7, -39.4, -34.6, -30.2, -26.2, -22.5, -19.1, -16.1, -13.4, -10.9,  -8.6, -6.6, -4.8, -3.2, -1.9, -0.8, 0.0, 0.6, 1.0, 1.2, 1.3, 1.2, 1.0, 0.5, -0.1, -1.1, -2.5, -4.3, -6.6, -9.3};

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
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
        double[][] actualFilteredAWeightedSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(actualAWeightedSignal);

        // Third octave bands filtering of the input signal (i.e. unweighted)
        double[][] actualFilteredInputSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(audioSignalArr);


        // Attenuation of the A-weighting per third octave bands
//        FastFourierTransformer fastFourierTransformer = new FastFourierTransformer(DftNormalization.STANDARD);
//        FastFourierTransformer.transformInPlace(actualFilteredInputSignal, DftNormalization.STANDARD, TransformType.FORWARD);
//        for (int idF = 0; idF < nbFrequencies; idF++) {
//            FastFourierTransformer.transformInPlace(actualFilteredInputSignal, DftNormalization.STANDARD, TransformType.FORWARD);
//            Complex[] fftFilteredSignal = fastFourierTransformer.transform(actualFilteredInputSignal[idF], minFreq, maxFreq, 1024, TransformType.FORWARD);
////            Complex[] fftFilteredSignal = fastFourierTransformer.transform(actualFilteredInputSignal[idF], TransformType.FORWARD);
//            System.out.print(fftFilteredSignal);
//        }

        // Equivalent sound pressure levels of the third octave bands filtered A-weighted signals
        double[] actualLAeq = new double[nbFrequencies];
        for (int idf = 0; idf < nbFrequencies; idf++) {
            actualLAeq[idf] = AcousticIndicators.getLeq(actualFilteredAWeightedSignal[idf]);
        }

        // Equivalent sound pressure levels of the third octave bands filtered unweighted signals
        double[] actualLeq = new double[nbFrequencies];
        for (int idf = 0; idf < nbFrequencies; idf++) {
            actualLeq[idf] = AcousticIndicators.getLeq(actualFilteredInputSignal[idf]);
        }

        /*
        Comparisons of expected and actual results
         */

        // Comparison of expected and actual filtered signals
        Assert.assertArrayEquals(expectedFilteredSignal, actualFilteredAWeightedSignal);

        // Comparison of expected and actual equivalent sound pressure levels
        Assert.assertArrayEquals(expectedLAeq, actualLAeq, 1E-3);
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