package org.orbisgis.sos;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Signal processing core
 */
public class SOSSignalProcessing {

    public final int samplingRate;
    private double[] sampleBuffer;
    private ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands;
    double[] standardFrequencies;

    public SOSSignalProcessing(int samplingRate, ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands) {
        this.frequencyBands = frequencyBands;
        if (samplingRate != 44100) {
            throw new IllegalArgumentException("Illegal sampling rate: expected 44100Hz, got " + samplingRate + "Hz");
        }
        this.samplingRate = samplingRate;
        this.sampleBuffer = new double[(int) (samplingRate * ThirdOctaveBandsFiltering.getSampleBufferDuration(frequencyBands))];
        this.standardFrequencies = ThirdOctaveBandsFiltering.getStandardFrequencies(frequencyBands);
        Arrays.fill(sampleBuffer, 0);
    }

    /**
     * @return Time in seconds of sample buffer
     */
    public double getSampleDuration() {
        return ThirdOctaveBandsFiltering.getSampleBufferDuration(frequencyBands);
    }

    /**
     * @return Computed frequencies
     */
    public double[] getStandardFrequencies() {
        return Arrays.copyOf(standardFrequencies, standardFrequencies.length);
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
    public static double[] convertBytesToDouble(byte[] buffer, int length, ByteOrder byteOrder) {
        short[] samplesShort = convertBytesToShort(buffer, length, byteOrder);
        double[] samples = new double[samplesShort.length];
        for (int i = 0; i < samplesShort.length; i++) {
            if (samplesShort[i] > 0) {
                samples[i] = Math.min(1, samplesShort[i] / ((double) Short.MAX_VALUE));
            } else {
                samples[i] = Math.max(-1, samplesShort[i] / (-(double) Short.MIN_VALUE));
            }
        }
        return samples;
    }

    public static short[] loadShortStream(InputStream inputStream, ByteOrder byteOrder) throws IOException {
        short[] fullArray = new short[0];
        byte[] buffer = new byte[4096];
        int read;
        // Read input signal up to buffer.length
        while ((read = inputStream.read(buffer)) != -1) {
            // Convert bytes into double values. Samples array size is 8 times inferior than buffer size
            if (read < buffer.length) {
                buffer = Arrays.copyOfRange(buffer, 0, read);
            }
            short[] signal = SOSSignalProcessing.convertBytesToShort(buffer, buffer.length, byteOrder);
            short[] nextFullArray = new short[fullArray.length + signal.length];
            if(fullArray.length > 0) {
                System.arraycopy(fullArray, 0, nextFullArray, 0, fullArray.length);
            }
            System.arraycopy(signal, 0, nextFullArray, fullArray.length, signal.length);
            fullArray = nextFullArray;
        }
        return fullArray;
    }


    public static short[] convertBytesToShort(byte[] buffer, int length, ByteOrder byteOrder) {
        ShortBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, length).order(byteOrder).asShortBuffer();
        short[] samplesShort = new short[byteBuffer.capacity()];
        byteBuffer.order();
        byteBuffer.get(samplesShort);
        return samplesShort;
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
    public List<double[]> processAudio(int encoding, final int rate, InputStream inputStream, double leqPeriod, double refSoundPressure, ByteOrder byteOrder) throws IOException {
        List<double[]> allLeq = new ArrayList<double[]>();
        double[] secondSample = new double[rate];
        int secondCursor = 0;
        byte[] buffer = new byte[4096];
        int read;
        // Read input signal up to buffer.length
        while ((read = inputStream.read(buffer)) != -1) {
            // Convert bytes into double values. Samples array size is 8 times inferior than buffer size
            double[] samples = convertBytesToDouble(buffer, read, byteOrder);
            int lengthRead = Math.min(samples.length, rate - secondCursor);
            // Copy sample fragment into second array
            System.arraycopy(samples, 0, secondSample, secondCursor, lengthRead);
            secondCursor += lengthRead;
            if (lengthRead < samples.length) {
                addSample(secondSample);
                allLeq.add(processSample(refSoundPressure));
                secondCursor = 0;
                // Copy remaining sample fragment into new second array
                int newLengthRead = samples.length - lengthRead;
                System.arraycopy(samples, lengthRead, secondSample, secondCursor, newLengthRead);
                secondCursor += newLengthRead;
            }
            if (secondCursor == rate) {
                addSample(secondSample);
                allLeq.add(processSample(refSoundPressure));
                secondCursor = 0;
            }
        }
        return allLeq;
    }

    /**
     * Calculation of the equivalent sound pressure level per third octave bands
     * @return List of double array of equivalent sound pressure level per third octave bands
     */
    public double[] processSample(double refSoundPressure) {
        int signalLength = sampleBuffer.length;
        int nbFrequencies = standardFrequencies.length;
        double[][] filteredSignals;
        double[] ret = new double[standardFrequencies.length];
        /*
        A-weighting and third octave bands filtering
         */
        filteredSignals = filterSignal(sampleBuffer, samplingRate, frequencyBands);

        /*
        Calculation of the equivalent sound pressure level per third octave bands
         */
        for (int idFreq = 0; idFreq < nbFrequencies; idFreq++) {
            //for(double signal : filteredSignals[idFreq]) {
            //    System.out.println(standardFrequencies[idFreq]+", "+signal);
            //}
            ret[idFreq] = AcousticIndicators.getLeq(filteredSignals[idFreq], refSoundPressure);
        }
        return ret;
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