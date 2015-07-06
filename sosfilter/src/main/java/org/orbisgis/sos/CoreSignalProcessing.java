package org.orbisgis.sos;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Signal processing core
 */
public class CoreSignalProcessing {

    public int samplingRate;
    private double[] sampleBuffer;
    private ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands;

    public CoreSignalProcessing(int samplingRate, ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands) {
        this.frequencyBands = frequencyBands;
        if (samplingRate != 44100) {
            throw new IllegalArgumentException("Illegal sampling rate: expected 44100Hz, got " + samplingRate + "Hz");
        }
        this.samplingRate = samplingRate;
        this.sampleBuffer = new double[(int) (samplingRate * ThirdOctaveBandsFiltering.getSampleBufferDuration(frequencyBands))];
        Arrays.fill(sampleBuffer, 0);
    }

    /**
     * @return Internal sample buffer where length depends on minimal frequency.
     */
    public double[] getSampleBuffer() {
        return sampleBuffer;
    }

    /**
     * Add an audio sample to the buffer
     * @param sample audio sample
     */
    public void addSample(double[] sample) {
        double[] newBuffer = new double[sampleBuffer.length];
        System.arraycopy(sampleBuffer, sample.length, newBuffer, 0 , sampleBuffer.length - sample.length);
        System.arraycopy(sample, 0 , newBuffer, sampleBuffer.length - sample.length ,sample.length);
        sampleBuffer = newBuffer;
    }


    /**
     * A-weigthing and third octave bands filtering of the time signal
     * @param signal time signal
     * @param samplingRate
     * @return double array of shape [frequency x time] A-weighted and third octave bands filtered time signals for each
     * nominal center frequency
     */
    public static double[][] filterSignal(double[] signal, int samplingRate, ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands) {

        /**
         * Apply the A-weighting filter to the input signal
         */
        double[] aWeightedSignal = AWeighting.aWeightingSignal(signal);

        /**
         * Third octave bands filtering of the A-weighted signal
         */
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, frequencyBands);
        double[][] filteredSignal = thirdOctaveBandsFiltering.thirdOctaveFiltering(aWeightedSignal);

        return filteredSignal;
    }

    /**
     *
     * @param buffer
     * @param length Length from the start to convert
     * @return Processed
     */
    public static double[] convertBytesToDouble(byte[] buffer, int length) {
        ShortBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, length).asShortBuffer();
        short[] samplesShort = new short[byteBuffer.capacity()];
        double[] samples = new double[samplesShort.length];
        byteBuffer.get(samplesShort);
        for (int i = 0; i < samplesShort.length; i++) {
            if (samplesShort[i] > 0) {
                samples[i] = Math.min(1, samplesShort[i] / ((double) Short.MAX_VALUE));
            } else {
                samples[i] = Math.max(-1, samplesShort[i] / (-(double) Short.MIN_VALUE));
            }
        }
        return samples;
    }

    /**
     * Process applied to the audio stream
     * @param encoding encoding
     * @param rate sampling [Hz] {@link ThirdOctaveBandsFiltering#STANDARD_FREQUENCIES_REDUCED} or {@link ThirdOctaveBandsFiltering#STANDARD_FREQUENCIES_FULL}
     * @param inputStream input audio stream
     * @param leqPeriod time period over which the equivalent sound pressure level is computed over [s] {@link AcousticIndicators#TIMEPERIOD_FAST} or {@link AcousticIndicators#TIMEPERIOD_SLOW}
     * @return list of double array of equivalent sound pressure levels
     * @throws IOException
     */
    public List<double[]> processAudio(int encoding, final int rate, InputStream inputStream, double leqPeriod) throws IOException {
        List<double[]> allLeq = new ArrayList<double[]>();
        double[] secondSample = new double[rate];
        int secondCursor = 0;
        byte[] buffer = new byte[4096];
        int read;
        // Read input signal up to buffer.length
        while ((read = inputStream.read(buffer)) != -1) {
            // Convert bytes into double values. Samples array size is 8 times inferior than buffer size
            double[] samples = convertBytesToDouble(buffer, read);
            int lengthRead = Math.min(samples.length, rate - secondCursor);
            // Copy sample fragment into second array
            System.arraycopy(samples, 0, secondSample, secondCursor, lengthRead);
            secondCursor += lengthRead;
            if (lengthRead < samples.length) {
                addSample(secondSample);
                allLeq.addAll(processSample(leqPeriod));
                secondCursor = 0;
                // Copy remaining sample fragment into new second array
                int newLengthRead = samples.length - lengthRead;
                System.arraycopy(samples, lengthRead, secondSample, secondCursor, newLengthRead);
                secondCursor += newLengthRead;
            }
            if (secondCursor == rate) {
                addSample(secondSample);
                allLeq.addAll(processSample(leqPeriod));
                secondCursor = 0;
            }
        }
        return allLeq;
    }

    /**
     * Calculation of the equivalent sound pressure level per third octave bands
     * @param leqPeriod time period over which the equivalent sound pressure level is computed over [s] {@link AcousticIndicators#TIMEPERIOD_FAST} or {@link AcousticIndicators#TIMEPERIOD_SLOW}
     * @return List of double array of equivalent sound pressure level per third octave bands
     */
    public List<double[]> processSample(double leqPeriod) throws FileNotFoundException {
        int signalLength = sampleBuffer.length;
        double sampleDuration = (double)(signalLength / samplingRate);
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, frequencyBands);
        double[] standardFrequencies = thirdOctaveBandsFiltering.getStandardFrequencies(samplingRate, sampleDuration);
        int nbFrequencies = standardFrequencies.length;
        List<double[]> leq = new ArrayList<double[]>();
        double[][] filteredSignals;
        final int subSamplesLength = (int)(leqPeriod * samplingRate);      // Sub-samples length
        final int nbSubSamples = signalLength / subSamplesLength;
        /*
        A-weighting and third octave bands filtering
         */
        filteredSignals = filterSignal(sampleBuffer, samplingRate, frequencyBands);
        for(int idSample = 0; idSample < nbSubSamples; idSample++) {
            leq.add(new double[nbFrequencies]);
        }
        /*
        Calculation of the equivalent sound pressure level per third octave bands
         */
        for (int idFreq = 0; idFreq < nbFrequencies; idFreq++) {
            double[] filteredSignal = new double[filteredSignals[0].length];
            System.arraycopy(filteredSignals[idFreq], 0, filteredSignal, 0, signalLength);
            double[] leqSamples = AcousticIndicators.getLeqT(filteredSignal, samplingRate, leqPeriod);
            for(int idSample = 0; idSample < nbSubSamples; idSample++) {
                leq.get(idSample)[idFreq] = leqSamples[idSample];
            }
        }
        return leq;
    }

    public static void writeDoubleArrInCSVFile(double[] data, String fileName) throws IOException {
        BufferedWriter br = null;
        try {
            br = new BufferedWriter(new FileWriter(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        StringBuilder sb = new StringBuilder();
        for (Double element : data) {
            sb.append(element.toString());
            sb.append("\n");
        }

        if (br != null) {
            br.write(sb.toString());
            br.close();
        }
    }

}