package org.orbisgis.sos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Signal processing core
 */
public class CoreSignalProcessing {

    private int sampleRate;
    private double[] sampleBuffer;
    // Compute buffer length in order to reduce the discrepancies
    private static final int MINIMUM_COMPLETE_PERIOD = 80/5;

    public CoreSignalProcessing(int sampleRate) {
        if (sampleRate != 44100) {
            throw new IllegalArgumentException("Sampling rate is different from 44100 Hz");
        }
        this.sampleRate = sampleRate;
        this.sampleBuffer = new double[sampleRate * (MINIMUM_COMPLETE_PERIOD / (int)ThirdOctaveFrequencies.STANDARD_FREQUENCIES[0])];
        Arrays.fill(sampleBuffer, 0);
    }

    public void addSample(double[] sample) {
        double[] newBuffer = new double[sampleBuffer.length];
        System.arraycopy(sampleBuffer, sample.length, newBuffer, 0 ,sampleBuffer.length - sample.length);
        System.arraycopy(sample, 0 , newBuffer, sampleBuffer.length - sample.length ,sample.length);
        sampleBuffer = newBuffer;
    }


    /**
     *
     * @param samples
     * @return double[Freq][t]
     */
    public static double[][] filterSignal(double[] samples) {

        /**
         * Apply the A-weighting filter to the input signal
         */
        double[] aWeightedSignal = AWeighting.aWeightingSignal(samples);

        /**
         * Third octave filtering
         */
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
        double[][] filteredSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(aWeightedSignal);

        return filteredSignal;
    }


    public List<double[]> processAudio(int encoding, final int rate, InputStream inputStream, double leqPeriod) throws IOException {
        List<double[]> allLeq = new ArrayList<double[]>();
        double[] secondSample = new double[rate];
        int secondCursor = 0;
        byte[] buffer = new byte[4096];
        int read = 0;
        int totalRead = 0;
        // Read input signal up to buffer.length
        while ((read = inputStream.read(buffer)) != -1) {
            totalRead += read;
            // Convert bytes into double values. Samples array size is 8 times inferior than buffer size
            ShortBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, read).asShortBuffer();
            short[] samplesShort = new short[byteBuffer.capacity()];
            double[] samples = new double[samplesShort.length];
            byteBuffer.get(samplesShort);
            for (int i = 0; i < samplesShort.length; i++) {
                samples[i] = samplesShort[i] / 32768d;
            }
            int lengthRead = Math.min(samples.length, rate - secondCursor);
            // Copy sample fragment into second array
            System.arraycopy(samples, 0, secondSample, secondCursor, lengthRead);
            secondCursor += lengthRead;
            if (lengthRead < samples.length) {
                addSample(secondSample);
                allLeq.addAll(processSamples(leqPeriod));
                secondCursor = 0;
                // Copy remaining sample fragment into new second array
                int newLengthRead = samples.length - lengthRead;
                System.arraycopy(samples, lengthRead, secondSample, secondCursor, newLengthRead);
                secondCursor += newLengthRead;
            }
            if (secondCursor == rate) {
                addSample(secondSample);
                allLeq.addAll(processSamples(leqPeriod));
                secondCursor = 0;
            }
        }
        return allLeq;
    }

    /**
     * @param leqPeriod {@link AcousticIndicators#TIMEPERIOD_FAST} or {@link AcousticIndicators#TIMEPERIOD_SLOW}
     * @return List of
     */
    private List<double[]> processSamples(double leqPeriod) {
        int nbFrequencies = ThirdOctaveBandsFiltering.getStandardFrequencies().length;
        List<double[]> leq = new ArrayList<double[]>();
        int signalLength = sampleBuffer.length;
        double[][] filteredSignals;
        final int subSamplesLength = (int)(leqPeriod * sampleRate);      // Sub-samples length
        final int nbSubSamples = (int)(signalLength / subSamplesLength);
        /*
        A-weighting and third octave bands filtering
         */
        filteredSignals = CoreSignalProcessing.filterSignal(sampleBuffer);
        for(int idSample = 0; idSample < nbSubSamples; idSample++) {
            leq.add(new double[nbFrequencies]);
        }
        for (int idFreq = 0; idFreq < nbFrequencies; idFreq++) {
            double[] filteredSignal = new double[filteredSignals[0].length];
            System.arraycopy(filteredSignals[idFreq], 0, filteredSignal, 0, signalLength);
            double[] leqSamples = AcousticIndicators.getLeqT(filteredSignal, sampleRate, leqPeriod);
            for(int idSample = 0; idSample < nbSubSamples; idSample++) {
                leq.get(idSample)[idFreq] = leqSamples[idSample];
            }
            /*
            Calculation of the equivalent sound pressure level per third octave bands
             */
        }
        return leq;
    }

}