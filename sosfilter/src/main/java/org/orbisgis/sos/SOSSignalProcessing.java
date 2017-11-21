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
import java.util.Random;


/**
 * Signal processing core
 */
public class SOSSignalProcessing {

    public final int samplingRate;
    private double[] sampleBuffer;
    private ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands;
    double[] standardFrequencies;
    boolean Aweighting = true;

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

    public boolean isAweighting() {
        return Aweighting;
    }

    public void setAweighting(boolean aweighting) {
        Aweighting = aweighting;
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
    public static double[][] filterSignal(boolean Aweigthing, double[] signal, int samplingRate, ThirdOctaveBandsFiltering.FREQUENCY_BANDS frequencyBands) {

        /**
         * Apply the A-weighting filter to the input signal
         */
        if(Aweigthing) {
            signal = AWeighting.aWeightingSignal(signal);
        }

        /**
         * Third octave bands filtering of the A-weighted signal
         */
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering(samplingRate, frequencyBands);

        return thirdOctaveBandsFiltering.thirdOctaveFiltering(signal);
    }

    public static double[] convertShortToDouble(short[] samplesShort) {
        double[] samples = new double[samplesShort.length];
        for (int i = 0; i < samplesShort.length; i++) {
            samples[i] = samplesShort[i];
        }
        return samples;
    }

    /**
     *
     * @param buffer
     * @param length Length from the start to convert
     * @return Processed
     */
    public static double[] convertBytesToDouble(byte[] buffer, int length, ByteOrder byteOrder) {
        return convertShortToDouble(convertBytesToShort(buffer, length, byteOrder));
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
        filteredSignals = filterSignal(isAweighting(), sampleBuffer, samplingRate, frequencyBands);

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


    public static short[] makeWhiteNoise(int samples, short rms, long seed) {
        short[] signal = new short[samples];
        Random random = new Random(seed);
        for(int i=0; i < samples; i++) {
            signal[i] = (short) (random.nextGaussian() * rms);
        }
        return signal;
    }

    public static short[] makePinkNoise(int samples, short rms, long seed) {
        // https://ccrma.stanford.edu/~jos/sasp/Example_Synthesis_1_F_Noise.html
        final double[] b = new double[] {0.049922035, -0.095993537, 0.050612699, -0.004408786};
        final double[] a = new double[] {1, -2.494956002,   2.017265875,  -0.522189400};
        final int nt60 = 1430; // nT60 = round(log(1000)/(1-max(abs(roots(A))))); % T60 est.
        double[] v = new double[samples + nt60];
        Random random = new Random(seed);
        for(int i=0; i < v.length; i++) {
            v[i] = random.nextGaussian() * rms;
        }
        double[] x = filter(b, a, v);
        short[] signal = new short[samples];
        for(int i=nt60; i < x.length; i++) {
            signal[i - nt60] = (short) (x[i]);
        }
        return signal;
    }

    public static double[] filter(double[] b, double[] a, double[] X){

        //Checks if these conditions are met otherwise it
        //will return the original input x
        if(Double.compare(a[0],0) != 0 && (a.length >= b.length)){

            int n = b.length;

            //Filter delay filled with zeros
            double[] z = new double[n];

            //The filtered signal filled with zeros
            double[] Y = new double[X.length];

            //Divide b and a by first coefficient of a
            divideEach(b, a[0]);
            divideEach(a, a[0]);

            for(int m = 0; m < Y.length; m++){

                //Calculates the filtered value using
                //Y[m] = b[0] * X[m] + z[0]
                Y[m] = b[0] * X[m] + z[0];

                for(int i= 1; i < n; i++){

                    //Previous filter delays recalculated by
                    //z[i-1] = b[i] * X[m] + z[i] - a[i] * Y[m]
                    z[i-1] = (b[i] * X[m] + z[i] ) - a[i] * Y[m];

                }

            }

            //The filtered signal
            return Y;
        }

        //Returns original signal when conditions not met
        return X;
    }

    private static void divideEach(double[] array, double divisor){
        for(int i = 0; i < array.length; i++){
            array[i] = array[i] / divisor;
        }
    }
}