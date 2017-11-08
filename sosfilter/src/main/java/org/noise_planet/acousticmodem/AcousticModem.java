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
package org.noise_planet.acousticmodem;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Acoustic modem.
 */
public class AcousticModem {
    private Settings settings;

    public AcousticModem(Settings settings) {
        this.settings = settings;
    }


    public Settings getSettings() {
        return settings;
    }

    /**
     * encodeStream is the public function of class Encoder.
     *
     * @param input  the stream of bytes to encode
     * @param output the stream of audio samples representing the input,
     *               prefixed with an hail signal and a calibration signal
     */
    public void encodeStream(InputStream input, OutputStream output)
            throws IOException {


        byte[] zeros = new byte[settings.kSamplesPerDuration];

        //write out the hail and calibration sequences
        output.write(zeros);
        output.write(getHailSequence());
        output.write(getCalibrationSequence());

        //now write the data
        int read = 0;
        byte[] buff = new byte[settings.kBytesPerDuration];
        while ((read = input.read(buff)) == settings.kBytesPerDuration) {
            output.write(encodeDuration(buff));
        }
        if (read > 0) {
            for (int i = read; i < settings.kBytesPerDuration; i++) {
                buff[i] = 0;
            }
            output.write(encodeDuration(buff));
        }
    }

    /**
     * @param output the stream of audio samples representing the SOS hail
     */
    public void generateSOS(OutputStream output) throws IOException {
        byte[] zeros = new byte[settings.kSamplesPerDuration];
        output.write(zeros);
        output.write(getSOSSequence());
    }

    /**
     * @param bitPosition the position in the kBytesPerDuration wide byte array for which you want a frequency
     * @return the frequency in which to sound to indicate a 1 for this bitPosition
     * NOTE!: This blindly assumes that bitPosition is in the range [0 - 7]
     */

    public int getFrequency(int bitPosition) {
        return settings.kFrequencies[bitPosition];
    }

    /* Computes the CRC-8-CCITT checksum on array of byte data, length len */
    public static byte crc_8_ccitt(byte[] msg, int len) {
        int crc = (byte) 0xFF; // initial value
        int polynomial = 0x07; // (0, 1, 2) : 0x07 / 0xE0 / 0x83

        boolean bit, c7;
        for (int b = 0; b < len; b++) {
            for (int i = 0; i < 8; i++) {
                bit = ((msg[b] >> (7 - i) & 1) == 1);
                c7 = ((crc >> 7 & 1) == 1);
                crc <<= 1;
                if (c7 ^ bit)
                    crc ^= polynomial;
            }
        }
        crc &= 0xffff;

        return (byte) crc;
    }


    /**
     * @param input an array of bytes to generate CRC for
     * @return the same array of bytes with its CRC appended at the end
     */
    public static byte[] appendCRC(byte[] input) {
        byte[] output = new byte[input.length + 1];
        byte crc8 = crc_8_ccitt(input, input.length);
        for (int i = 0; i < input.length; i++) {
            output[i] = input[i];
        }
        output[input.length] = crc8;

        return output;
    }

    /**
     * @param input a kBytesPerDuration long array of bytes to encode
     * @return an array of audio samples of type AudioUtil.kDefaultFormat
     */
    private byte[] encodeDuration(byte[] input) {
        double[] signal = new double[settings.kSamplesPerDuration];
        for (int j = 0; j < settings.kBytesPerDuration; j++) {
            for (int k = 0; k < settings.kBitsPerByte; k++) {
                if (((input[j] >> k) & 0x1) == 0) {
                    //no need to go through encoding a zero
                    continue;
                }

                //add a sinusoid of getFrequency(j), amplitude kAmplitude and duration kDuration
                double innerMultiplier = getFrequency((j * settings.kBitsPerByte) + k)
                        * (1 / settings.kSamplingFrequency) * 2 * Math.PI;
                for (int l = 0; l < signal.length; l++) {
                    signal[l] = signal[l] + (settings.kAmplitude * Math.cos(innerMultiplier * l));
                }
            }
        }

        return getByteArrayFromDoubleArray(smoothWindow(signal));
    }

    /**
     * @param sequence the array of floats to return as a shifted and clipped array of bytes
     * @return byte[i] = sequence[i] * Constants.kFloatToByteShift cast to a byte
     * Note!: This doesn't handle cast/conversion issues, so don't use this unless you understand the code
     */
    public byte[] getByteArrayFromDoubleArray(double[] sequence) {
        byte[] result = new byte[sequence.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) ((sequence[i] * settings.kFloatToByteShift) - 1);
        }
        return result;
    }

    /**
     * @return audio samples for a duration of the hail frequency, Constants.kSOSFrequency
     */
    private byte[] getSOSSequence() {
        double[] signal = new double[settings.kSamplesPerDuration];
        //add a sinusoid of the hail frequency, amplitude kAmplitude and duration kDuration
        double innerMultiplier = settings.kSOSFrequency * (1 / settings.kSamplingFrequency) * 2 * Math.PI;
        for (int l = 0; l < signal.length; l++) {
            signal[l] = /*kAmplitude **/ Math.cos(innerMultiplier * l);
        }
        return getByteArrayFromDoubleArray(smoothWindow(signal));
    }

    /**
     * @return audio samples for a duration of the hail frequency, Constants.kHailFrequency
     */
    private byte[] getHailSequence() {
        double[] signal = new double[settings.kSamplesPerDuration];
        //add a sinusoid of the hail frequency, amplitude kAmplitude and duration kDuration
        double innerMultiplier = settings.kHailFrequency * (1 / settings.kSamplingFrequency) * 2 * Math.PI;
        for (int l = 0; l < signal.length; l++) {
            signal[l] = /*kAmplitude **/ Math.cos(innerMultiplier * l);
        }
        return getByteArrayFromDoubleArray(smoothWindow(signal));
    }

    /**
     * @return audio samples (of length 2 * kSamplesPerDuration), used to calibrate the decoding
     */
    private byte[] getCalibrationSequence() {
        byte[] results = new byte[2 * settings.kSamplesPerDuration];
        byte[] inputBytes1 = new byte[settings.kBytesPerDuration];
        byte[] inputBytes2 = new byte[settings.kBytesPerDuration];
        for (int i = 0; i < settings.kBytesPerDuration; i++) {
            inputBytes1[i] = (byte) 0xAA; // 10101010
            inputBytes2[i] = (byte) 0x55; // 01010101
        }

        //encode inputBytes1 and 2 in sequence
        byte[] partialResult = encodeDuration(inputBytes1);
        for (int k = 0; k < settings.kSamplesPerDuration; k++) {
            results[k] = partialResult[k];
        }
        partialResult = encodeDuration(inputBytes2);
        for (int k = 0; k < settings.kSamplesPerDuration; k++) {
            results[k + settings.kSamplesPerDuration] = partialResult[k];
        }

        return results;
    }

    /**
     * About smoothwindow.
     * This is a data set in with the following form:
     * <p>
     * |
     * 1 |  +-------------------+
     * | /                     \
     * |/                       \
     * +--|-------------------|--+---
     * 0.01              0.09  0.1  time
     * <p>
     * It is used to smooth the edges of the signal in each duration
     */
    private static double[] smoothWindow(double[] input) {
        double[] smoothWindow = new double[input.length];
        double minVal = 0;
        double maxVal = 0;
        int peaks = (int) (input.length * 0.1);
        double steppingValue = 1 / (double) peaks;
        for (int i = 0; i < smoothWindow.length; i++) {
            if (i < peaks) {
                smoothWindow[i] = input[i] * (steppingValue * i) /* / magicScalingNumber*/;
            } else if (i > input.length - peaks) {
                smoothWindow[i] = input[i] * (steppingValue * (input.length - i - 1)) /* / magicScalingNumber */;
            } else {
                //don't touch the middle values
                smoothWindow[i] = input[i] /* / magicScalingNumber */;
            }
            if (smoothWindow[i] < minVal) {
                minVal = smoothWindow[i];
            }
            if (smoothWindow[i] > maxVal) {
                maxVal = smoothWindow[i];
            }
        }
        return smoothWindow;
    }

    /**
     * This isn't used at the moment, but it does sound nice
     */
    private static double[] blackmanSmoothWindow(double[] input) {
        double[] smoothWindow = new double[input.length];
        double steppingValue = 2 * Math.PI / (input.length - 1);
        double maxVal = 0;
        double minVal = 0;
        for (int i = 0; i < smoothWindow.length; i++) {
            smoothWindow[i] = (input[i] * (0.42 - 0.5 * Math.cos(steppingValue * i) +
                    0.08 * Math.cos(steppingValue * i))) * 3.5;
            if (smoothWindow[i] < minVal) {
                minVal = smoothWindow[i];
            }
            if (smoothWindow[i] > maxVal) {
                maxVal = smoothWindow[i];
            }
        }
        return smoothWindow;
    }



    /**
     * @param signal the audio samples to search
     * @param signalStrengths this will be filled in with the strengths for each frequency (NOTE THIS SIDE EFFECT)
     * @param granularity a correlation will be determined every granularity samples (lower is slower)
     * @return the index in signal of the key sequence, or -1 if it wasn't found (in which case signalStrengths is trashed)
     */
    public int findKeySequence(byte[] signal, double[] signalStrengths, int granularity, int keyFrequency){
        int maxCorrelationIndex = -1;
        double maxCorrelation = -1;
        double minSignal = 0.003;
        double acceptedSignal = 0.01;
        int i=0;
        for(i = 0; i <= signal.length - settings.kSamplesPerDuration; i += granularity){
            //test the correlation
            byte[] partialSignal = Arrays.copyOfRange(signal, i, i + settings.kSamplesPerDuration);
            double corr = complexDetect(partialSignal, keyFrequency);
            if (corr > maxCorrelation){
                maxCorrelation = corr;
                maxCorrelationIndex = i;
            }
            if(granularity <= 0){
                break;
            }
        }

        if (maxCorrelation < acceptedSignal && maxCorrelation > -1){
            maxCorrelationIndex = -1;
        }

        return maxCorrelationIndex;
    }

    /**
     * @param startSignals the signal strengths of each of the frequencies
     * @param samples the samples
     * @return the decoded bytes
     */
    public byte[] decode(double[] startSignals, byte[] samples){
        return decode(startSignals, getSignalStrengths(samples));
    }

    /**
     * @param startSignals the signal strengths of each of the frequencies
     * @param signal the signal strengths for each frequency for each duration [strength][duration index]
     * SIDE EFFECT: THE signal PARAMETER WILL BE SCALED BY THE STARTSIGNALS
     * @return the decoded bytes
     */
    private byte[] decode(double[] startSignals, double[][] signal){
        //normalize to the start signals
        for(int i = 0; i < (settings.kBitsPerByte * settings.kBytesPerDuration); i++){
            for(int j = 0; j < signal[i].length; j++){
                signal[i][j] = signal[i][j] / startSignals[i];
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(int i = 0; i < signal[0].length; i++){
            for(int k = 0; k < settings.kBytesPerDuration; k++){
                byte value = 0;
                for(int j = 0; j < settings.kBitsPerByte; j++){
                    if(signal[(k * settings.kBitsPerByte) + j][i] > 0.4){ // detection threshold
                        value = (byte)(value | ( 1 << j));
                    } else {
                    }
                }
                baos.write(value);
            }
        }

        return baos.toByteArray();

    }

    /**
     * @param input audio sample array
     * @return the signal strengths of each frequency in each duration: [signal strength][duration index]
     */
    private double[][] getSignalStrengths(byte[] input){
        //detect the signal strength of each frequency in each duration
        int durations = input.length / settings.kSamplesPerDuration;

        // rows are durations, cols are bit strengths
        double[][] signal = new double[settings.kBitsPerByte * settings.kBytesPerDuration][durations];

        //for each duration, check each bit for representation in the input
        for(int i=0; i < durations; i++){
            //separate this duration's input into its own array
            byte[] durationInput = Arrays.copyOfRange(input, i * settings.kSamplesPerDuration, (i * settings.kSamplesPerDuration) + settings.kSamplesPerDuration);

            //for each bit represented, detect
            for(int j = 0; j < settings.kBitsPerByte * settings.kBytesPerDuration; j++){
                signal[j][i] =
                        complexDetect(durationInput, getFrequency(j));
            }
        }
        return signal;
    }

    public void getKeySignalStrengths(byte[] signal, double[] signalStrengths){
        byte[] partialSignal = Arrays.copyOfRange(signal, 0, settings.kSamplesPerDuration);
        for(int j = 1; j < settings.kBitsPerByte * settings.kBytesPerDuration; j += 2){
            signalStrengths[j] = complexDetect(partialSignal, getFrequency(j));
        }

        byte[] partialSignal2 = Arrays.copyOfRange(signal, settings.kSamplesPerDuration, settings.kSamplesPerDuration + settings.kSamplesPerDuration);
        for(int j = 0; j < settings.kBitsPerByte * settings.kBytesPerDuration; j += 2){
            signalStrengths[j] = complexDetect(partialSignal2, getFrequency(j));
        }
    }

    /**
     * @param input array of bytes with CRC appended at the end
     * @return true if appended CRC == calculated CRC, false otherwise
     */
    public static boolean crcCheckOk(byte[] input) {
        return input[input.length - 1] == crc_8_ccitt(input, input.length - 1);
    }

    public static byte[] removeCRC(byte[] input) {
        return Arrays.copyOfRange(input, 0, input.length - 2);
    }

    // original implementation from ask-simple-java :
    private double complexDetect(byte[] signal, double frequency){
        double realSum = 0;
        double imaginarySum = 0;
        double u = 2 * Math.PI * frequency / settings.kSamplingFrequency;
        // y = e^(ju) = cos(u) + j * sin(u)

        for(int i = 0; i < signal.length; i++){
            realSum = realSum + (Math.cos(i * u) * (signal[i]/(float)settings.kFloatToByteShift));
            imaginarySum = imaginarySum + (Math.sin(i * u) * (signal[i]/(float)settings.kFloatToByteShift));
        }
        double realAve = realSum/signal.length;
        double imaginaryAve = imaginarySum/signal.length;
        return Math.sqrt( (realAve * realAve) + (imaginaryAve * imaginaryAve) );
    }
}
