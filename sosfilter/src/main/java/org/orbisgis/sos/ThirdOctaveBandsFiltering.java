package org.orbisgis.sos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;

import java.io.*;
import java.util.*;

/**
 * Created by G. Guillaume on 02/06/2015.
 */
public class ThirdOctaveBandsFiltering {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThirdOctaveBandsFiltering.class);
    private List<FiltersParameters> filterParameters = new ArrayList<FiltersParameters>(STANDARD_FREQUENCIES.length);

    public ThirdOctaveBandsFiltering() {

        csvLoad(ThirdOctaveBandsFiltering.class.getResourceAsStream("Third_oct_filters_coefts.csv"));

    }

    private void csvLoad(InputStream csvFile) {

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


    public static final double[] getStandardFrequencies() {
        return STANDARD_FREQUENCIES;
    }

    /**
     * Standard center frequencies of third octave bands
     */
    public static final double[] STANDARD_FREQUENCIES = new double[]{16, 20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6000, 8000, 10000, 12500, 16000, 20000};

    /**
     * @return List of parameters used to filter the signal.
     */
    public List<FiltersParameters> getFilterParameters() {
        return filterParameters;
    }

    /**
     * Center frequency of third octave bands
     * @param idFreq center frequency index
     */
    private static double getCtrFreq(int idFreq) {
        return Math.pow(10., 3.) * Math.pow(2., (idFreq-18) / 3.);
    }

    /**
     * sosfilter_double(double [] signal, int nsamp, FiltersParameters filterParams, double [][] states)
     * @param signal Raw time input signal
     * @param nsamp Number of samples in the signal
     * @param filterParams Third octave band filter coefficients
     * @param states State variables array
     */
    private ReturnFilterData sosfilter_double(double [] signal, int nsamp, FiltersParameters filterParams, double [][] states){
        int k = 0;
        // Loop on the cascaded filtering stages
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

            for (int n = 0; n < nsamp; ++n){
                double w0 = signal[n];
                w0 = w0 - a1*w1 - a2*w2;
                double yn = b0*w0 + b1*w1 + b2*w2;
                w2 = w1;
                w1 = w0;
                signal[n] = yn;
            }
            states[0][k] = w1;
            states[1][k] = w2;
            k++;
        }
        return new ReturnFilterData(signal, states);
    }

    private double[] reverse2dArray(double[] arr){
        double[] reversedArray = new double[arr.length];
        for(int i = 0; i < arr.length; i++) {
            reversedArray[arr.length - i] = arr[i];
        }
        return reversedArray;
    }

    /**
     * applySosFilter(double[] signal, FiltersParameters coefficients)
     * @param signal Raw time input signal
     * @param idFreq Central frequency index
     * @return Third octave band filtered signal
     */
    private double[] applySosFilter(double[] signal, int idFreq){
        double [][] states = new double [2][4];
        int nSamp = signal.length;
        FiltersParameters filtParams = this.filterParameters.get(idFreq);
        Arrays.fill(states, 0.);
        // Backward filtering
        double[] reversedSignal = reverse2dArray(signal);
        ReturnFilterData backwardFiltSig = sosfilter_double(reversedSignal, nSamp, filtParams, states);
        double[] backFiltSig = backwardFiltSig.getFilteredSig();
        double[][] backFiltStates = backwardFiltSig.getStates();
        // Forward  filtering
        double[] reversedFilteredSignal = reverse2dArray(backFiltSig);
        ReturnFilterData forwardFiltSig = sosfilter_double(reversedFilteredSignal, nSamp, filtParams, backFiltStates);
        return forwardFiltSig.getFilteredSig();
    }

    /**
     * Third octave filtering
     * @param signal Raw time input signal
     */
    public double[][] thirdOctaveFiltering(double[] signal){
        int nsamp = signal.length;
        int nfreqs = STANDARD_FREQUENCIES.length;
        double [][] filtSigs = new double[nfreqs][nsamp];
        for (int idf = 0; idf < nfreqs; idf++){
            double[] filtSig = applySosFilter(signal, idf);
            for (int it = 0; it<nsamp; it++){
                filtSigs[idf][it] = filtSig[it];
            }
        }
        return filtSigs;
    }


    public static class StageParameters {

        public final double[] coefficients; // Filter coefficients for one frequency and one stage

        public StageParameters(double[] coefficients) {
            this.coefficients = coefficients;
        }
    }

    public static class FiltersParameters {
        public final double frequency;
        public final List<StageParameters> stages = new ArrayList<StageParameters>();

        public FiltersParameters(double frequency) {
            this.frequency = frequency;
        }
    }


    public static class ReturnFilterData
    {
        private double[] filtSig;
        private double[][] states;
        public ReturnFilterData(double[] filtSig, double[][] states)
        {
            this.filtSig = filtSig;
            this.states = states;

        }
        public double[] getFilteredSig() { return filtSig; }
        public double[][] getStates() { return states; }
    }
}