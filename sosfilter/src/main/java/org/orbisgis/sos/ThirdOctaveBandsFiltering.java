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


    /**
     * Standard center frequencies of third octave bands
     * STANDARD_FREQUENCIES_REDUCED corresponds with a reduced array of standard third octave bands frequencies in the range [100Hz, 20kHz]
     * STANDARD_FREQUENCIES_FULL corresponds with the array of standard third octave bands frequencies in the range [100Hz, 20kHz]
     */
    private static final double[] STANDARD_FREQUENCIES_REDUCED = new double[]{100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000};
    private static final double[] STANDARD_FREQUENCIES_FULL = new double[]{16, 20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000};

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
        return standFrequencies; }


    public int samplingRate;
    public double sampleDuration;
    public final double[] STANDARD_FREQUENCIES = getStandardFrequencies(samplingRate, sampleDuration);

    /**
     * Exact nominal center frequencies of third octave bands
     * @param idCtrFreq center frequency index
     */
    private static double getCtrFreq(int idCtrFreq) { return Math.pow(10., 3.) * Math.pow(2., (idCtrFreq -18) / 3.); }


    private static final Logger LOGGER = LoggerFactory.getLogger(ThirdOctaveBandsFiltering.class);
    private List<FiltersParameters> filterParameters = new ArrayList<FiltersParameters>(STANDARD_FREQUENCIES.length);

    /**
     * Third octave bands filtering constructor
     */
    public ThirdOctaveBandsFiltering(int samplingRate, double sampleLength) {
        int sampleRate = 44100;
        String csvRootName = "Third_oct_filters_coefts";
        String strSamplingFrequency = String.valueOf(sampleRate) + "Hz";
        String strLenSecs = String.valueOf((int)AcousticIndicators.TIMEPERIOD_SLOW) + "s";
        String csvFileName = csvRootName + "_" + strSamplingFrequency + "_" + strLenSecs + ".csv";
        getFiltersParameters(ThirdOctaveBandsFiltering.class.getResourceAsStream(csvFileName));
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
                int casc = Integer.valueOf(splitter.nextToken());
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
    private ReturnFilterData sosFiltering(double[] signal, FiltersParameters filterParams, double[][] states){

        // Loop on the cascaded filtering stages
        int k = 0;
        for (StageParameters stage: filterParams.stages){
            double w1 = states[0][k];
            double w2 = states[1][k];

            // Feedforward coefficients
            double b0 = stage.coefficients[0];
            double b1 = stage.coefficients[1];
            double b2 = stage.coefficients[2];

            // Feedback coefficients
            double a1 = stage.coefficients[3];
            double a2 = stage.coefficients[4];

            // Second-order recursive linear filtering
            for (int idT = 0; idT < signal.length; ++idT){
                double w0 = signal[idT];
                w0 = w0 - a1*w1 - a2*w2;
                double yn = b0*w0 + b1*w1 + b2*w2;
                w2 = w1;
                w1 = w0;
                signal[idT] = yn;
            }
            states[0][k] = w1;
            states[1][k] = w2;
            k++;
        }
        return new ReturnFilterData(signal, states);
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
        double [][] states = new double [2][4];
        FiltersParameters filtParams = this.filterParameters.get(idFreq);
        for (int idRow = 0; idRow < states.length; idRow++) { Arrays.fill(states[idRow], 0.); }

        // Backward filtering
        double[] reversedSignal = reverse2dArray(signal);
        ReturnFilterData backwardFiltering = sosFiltering(reversedSignal, filtParams, states);
        double[] backFilteredSignal = backwardFiltering.getFilteredSig();
        double[][] backFilteredStates = backwardFiltering.getStates();

        // Forward filtering
        double[] reversedBackFilteredSignal = reverse2dArray(backFilteredSignal);
        ReturnFilterData forwardFilteredSignal = sosFiltering(reversedBackFilteredSignal, filtParams, backFilteredStates);

        return forwardFilteredSignal.getFilteredSig();
    }

    /**
     * Third octave filtering
     * @param signal Raw time input signal
     */
    public double[][] thirdOctaveFiltering(double[] signal){
        int signalLength = signal.length;
        int nbFreqs = STANDARD_FREQUENCIES.length;
        double [][] filteredSignals = new double[nbFreqs][signalLength];
        for (int idf = 0; idf < nbFreqs; idf++){
            double[] filteredSignal = applySosFilter(signal, idf);
            for (int idT = 0; idT< signalLength; idT++){ filteredSignals[idf][idT] = filteredSignal[idT]; }
        }
        return filteredSignals;
    }


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

    /**
     * Return third octave filtered signals and states
     */
    public static class ReturnFilterData
    {
        private double[] filteredSignal;
        private double[][] states;

        public ReturnFilterData(double[] filteredSignal, double[][] states)
        {
            this.filteredSignal = filteredSignal;
            this.states = states;

        }

        public double[] getFilteredSig() { return filteredSignal; }
        public double[][] getStates() { return states; }
    }
}