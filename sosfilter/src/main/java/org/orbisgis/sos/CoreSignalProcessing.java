package org.orbisgis.sos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreSignalProcessing.class);
    private int sampleRate;
    private double[] sampleBuffer;
    // Compute buffer length in order to reduce the discrepancies
    private static final int MINIMUM_COMPLETE_PERIOD = 80/5;

    public CoreSignalProcessing(int sampleRate) {
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


    public double[][] filterSignal(double[] samples) {

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
                secondCursor = 0;
            }
        }
        LOGGER.debug("Total read:" + totalRead);
        return allLeq;
    }

    /**
     * @param leqPeriod {@link AcousticIndicators#TIMEPERIOD_FAST} or {@link AcousticIndicators#TIMEPERIOD_SLOW}
     */
    private List<double[]> processSamples(double leqPeriod) {
        int nbFrequencies = ThirdOctaveBandsFiltering.getStandardFrequencies().length;
        List<double[]> leq = new ArrayList<double[]>();
        int signalLength = sampleBuffer.length;
        double[][] filteredSignals;
        /*
        A-weighting and third octave bands filtering
         */
        CoreSignalProcessing coreSignalProcessing = new CoreSignalProcessing(sampleRate);
        filteredSignals = coreSignalProcessing.filterSignal(sampleBuffer);

        for (int idFreq = 0; idFreq <= nbFrequencies; idFreq++) {
            double[] filteredSignal = new double[filteredSignals[0].length];
            System.arraycopy(filteredSignals[idFreq], 0, filteredSignal, 0, signalLength);

            /*
            Calculation of the equivalent sound pressure level per third octave bands
             */
            leq.add(AcousticIndicators.getLeqT(filteredSignal, sampleRate, leqPeriod));
        }
        return leq;
    }

}