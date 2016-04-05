package org.orbisgis.sos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;

import java.io.*;
import java.util.*;

/**
 * Created by G. Guillaume on 02/06/2015.
 * Third octave bands filtering of a time signal
 * This module applies an third octave bands filters according to the standard IEC-61260 "Electroacoustics - Octave-band and fractional-octave-band filters" (2001)
 * @see <a href="http://siggigue.github.io/pyfilterbank/splweighting.html">http://siggigue.github.io/pyfilterbank/splweighting.html</a>
 * @see <a href="http://www.mathworks.com/matlabcentral/fileexchange/69-octave/content//octave/oct3dsgn.m">http://www.mathworks.com/matlabcentral/fileexchange/69-octave/content//octave/oct3dsgn.m</a>
 */
public class ThirdOctaveBandsFiltering {

    public enum FREQUENCY_BANDS {REDUCED, FULL};
    private int expectedSampleLength;
    private int samplingRate;
    private final double[] standardFrequencies;

    private static final Logger LOGGER = LoggerFactory.getLogger(ThirdOctaveBandsFiltering.class);
    private List<FiltersParameters> filterParameters;

    /**
     * Standard center frequencies of third octave bands
     * STANDARD_FREQUENCIES_REDUCED corresponds with a reduced array of standard third octave bands frequencies in the range [100Hz, 20kHz]
     * STANDARD_FREQUENCIES_FULL corresponds with the array of standard third octave bands frequencies in the range [100Hz, 20kHz]
     */
    private static final double[] STANDARD_FREQUENCIES_REDUCED = new double[]{100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000};
    private static final double[] STANDARD_FREQUENCIES_FULL = new double[]{16, 20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000};
    private static final double[] STANDARD_OCTAVE_FREQUENCIES_REDUCED = new double[]{125, 250, 500, 1000, 2000, 4000, 8000, 16000};

    /**
     * Third octave bands filtering constructor
     */
    public ThirdOctaveBandsFiltering(int samplingRate, FREQUENCY_BANDS frequency_bands) {
        this.samplingRate = samplingRate;
        this.standardFrequencies = getStandardFrequencies(frequency_bands);
        String csvFileName;
        if (frequency_bands == FREQUENCY_BANDS.FULL) {
            // Third octave bands filtering over the full standards frequency bands (i.e. [16Hz-20kHz]) requires a
            // 5-seconds duration input signal
            this.expectedSampleLength = samplingRate * 5;
            csvFileName = "Third_oct_filters_coefts_44100Hz_16Hz-20kHz";
        } else {
            // Third octave bands filtering over the full standards frequency bands (i.e. [100Hz-20kHz]) requires a
            // 1-second duration input signal
            this.expectedSampleLength = samplingRate;
            csvFileName = "Third_oct_filters_coefts_44100Hz_100Hz-20kHz.csv";
        }
        filterParameters = new ArrayList<FiltersParameters>(standardFrequencies.length);
        getFiltersParameters(ThirdOctaveBandsFiltering.class.getResourceAsStream(csvFileName));
    }

    public static double[] getStandardFrequencies(FREQUENCY_BANDS frequency_bands) {
        if (frequency_bands == FREQUENCY_BANDS.FULL) {
            return STANDARD_FREQUENCIES_FULL;
        } else {
            return STANDARD_FREQUENCIES_REDUCED;
        }
    }

    public static double getSampleBufferDuration(FREQUENCY_BANDS frequency_bands) {
        if (frequency_bands == FREQUENCY_BANDS.FULL) {
            return 5.;
        } else {
            return 1.;
        }
    }

    /**
     * Get the array of standard third octave bands frequencies
     * @param samplingRate sampling rate [Hz]
     * @param sampleDuration sample duration [s]
     * @return array of standard third octave bands frequencies
     */
    public final double[] getStandardFrequencies(int samplingRate, double sampleDuration) {
        double[] standFrequencies = new double[0];
        if (samplingRate == 44100 && sampleDuration == 1.) {
            standFrequencies = STANDARD_FREQUENCIES_REDUCED;
        }
        else if (samplingRate == 44100 && sampleDuration == 5.) {
            standFrequencies = STANDARD_FREQUENCIES_FULL;
        }
        return standFrequencies;
    }


    /**
     * Load the .csv file containing the third octave bands filters parameters
     * @param csvFile input stream of the parameters file
     */
    private void getFiltersParameters(InputStream csvFile) {
        BufferedReader inputStream = new BufferedReader(new InputStreamReader(csvFile));
        try {
            String line;
            FiltersParameters lastParam = null;
            while ((line = inputStream.readLine()) != null) {
                StringTokenizer splitter = new StringTokenizer(line, ",");
                double frequency = Double.valueOf(splitter.nextToken());
                splitter.nextToken();
                double[] params = new double[5];
                int i = 0;
                while(splitter.hasMoreTokens()) {
                    params[i++] = Double.valueOf(splitter.nextToken());
                }
                if(lastParam != null && lastParam.frequency != frequency) {
                    filterParameters.add(lastParam);
                    lastParam = new FiltersParameters(frequency);
                }
                if(lastParam == null) {
                    lastParam = new FiltersParameters(frequency);
                }
                StageParameters stageParameters = new StageParameters(params);
                lastParam.stages.add(stageParameters);
            }
            filterParameters.add(lastParam);
        } catch (IOException ex) {
            LOGGER.error("Error while reading filter filterParameters", ex);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                // Ignore error
            }
        }
    }

    /**
     * @return List of parameters used to filter the signal.
     */
    public List<FiltersParameters> getFilterParameters() { return filterParameters; }

    /**
     * Second-order recursive linear filtering
     * @param signal Raw time input signal
     * @param filterParams Third octave band filter coefficients
     * @param states State variables array
     */
    private void sosFiltering(final double[] signal, FiltersParameters filterParams, double[][] states){

        // Loop on the cascaded filtering stages
        int k = 0;
        for (StageParameters stage: filterParams.stages){
            double w1 = states[0][k];
            double w2 = states[1][k];

            // Feedforward coefficients
            final double b0 = stage.coefficients[0];
            final double b1 = stage.coefficients[1];
            final double b2 = stage.coefficients[2];

            // Feedback coefficients
            final double a1 = stage.coefficients[3];
            final double a2 = stage.coefficients[4];

            // Second-order recursive linear filtering
            for (int idT = 0; idT < signal.length; ++idT){
                final double w0 = signal[idT] - a1*w1 - a2*w2;
                signal[idT] = b0*w0 + b1*w1 + b2*w2;
                w2 = w1;
                w1 = w0;
            }
            states[0][k] = w1;
            states[1][k] = w2;
            k++;
        }
    }

    /**
     * Reverse a double 2D array
     * @param arr double 2D array
     * @return the reverses double 2D array
     */
    private double[] reverse2dArray(double[] arr){
        double[] reversedArray = new double[arr.length];
        for(int id = 0; id < arr.length; id++) {
            reversedArray[arr.length - id - 1] = arr[id];
        }
        return reversedArray;
    }

    /**
     * Signal backward and forward filtering by second-order recursive linear
     * @param signal Raw time input signal
     * @param idFreq Index of the central frequency
     * @return Third octave band filtered signal
     */
    private double[] applySosFilter(double[] signal, int idFreq){
        // Check the audio input sample duration
        //if(signal.length != expectedSampleLength) {
        //    throw new IllegalArgumentException("Illegal audio sample duration: expected " + expectedSampleLength + ", got " + signal.length);
        //}
        double [][] states = new double [2][4];
        FiltersParameters filtParams = this.filterParameters.get(idFreq);

        // Backward filtering
        double[] reversedSignal = reverse2dArray(signal);
        sosFiltering(reversedSignal, filtParams, states);

        // Forward filtering
        double[] reversedBackFilteredSignal = reverse2dArray(reversedSignal);
        sosFiltering(reversedBackFilteredSignal, filtParams, states);

        return reversedBackFilteredSignal;
    }

    /**
     * Third octave filtering
     * @param signal Raw time input signal
     */
    public double[][] thirdOctaveFiltering(double[] signal){
        int signalLength = signal.length;
        int nbFreqs = standardFrequencies.length;
        double [][] filteredSignals = new double[nbFreqs][signalLength];
        for (int idf = 0; idf < nbFreqs; idf++){
            filteredSignals[idf] = applySosFilter(signal, idf);
        }
        return filteredSignals;
    }

    /*
    public double[][] thirdOctaveFiltering(double[] signal){
        int nbFreqs = standardFrequencies.length;
        double [][] filteredSignals = new double[nbFreqs][];
        double[] decimateSignal;
        filteredSignals[(nbFreqs - 1)] = applySosFilter(signal, nbFreqs - 1);
        filteredSignals[(nbFreqs - 1) - 1] = applySosFilter(signal, nbFreqs - 2);
        filteredSignals[(nbFreqs - 1) - 2] = applySosFilter(signal, nbFreqs - 3);
        for (int idf = 3; idf < nbFreqs; idf+=3){
            filteredSignals[(nbFreqs - 1) - idf] = applySosFilter(signal, nbFreqs - 4);
            filteredSignals[(nbFreqs - 1) - idf - 1] = applySosFilter(signal, nbFreqs - 5);
            filteredSignals[(nbFreqs - 1) - idf - 2] = applySosFilter(signal, nbFreqs - 6);
            // Decimation signal by two factor
            decimateSignal = new double[signal.length / 2];
            for(int i = 0; i < decimateSignal.length; i++) {
                decimateSignal[i] = (signal[i * 2] + signal[i * 2 + 1]) / 2;
            }
            signal = decimateSignal;
        }
        return filteredSignals;
    }
    */
    /**
     * Cascade stage parameters
     */
    public static class StageParameters {

        // Filter coefficients for one frequency and one stage
        public final double[] coefficients;

        public StageParameters(double[] coefficients) {
            this.coefficients = coefficients;
        }
    }

    /**
     * Third octave filters parameters
     */
    public static class FiltersParameters {
        public final double frequency;
        public final List<StageParameters> stages = new ArrayList<StageParameters>();

        public FiltersParameters(double frequency) {
            this.frequency = frequency;
        }
    }
}