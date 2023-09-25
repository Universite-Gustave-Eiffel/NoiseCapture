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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Random;

/**
 * Overlaps the window of signal processing.
 */

public class Window {
    public final FFTSignalProcessing.WINDOW_TYPE window;
    private static final Logger LOGGER = LoggerFactory.getLogger(Window.class);
    private FFTSignalProcessing signalProcessing;
    int sampleRate;
    // processed sample index
    private int lastProcessedSpectrum = 0;
    // added samples
    private int pushedSamples;
    private int windowSize;
    private boolean aWeighting;
    private boolean outputThinFrequency;
    private double overlap = 0;
    private FFTSignalProcessing.ProcessingResult[] windowResults;
    private SpectrumChannel spectrumChannel;

    public Window(FFTSignalProcessing.WINDOW_TYPE window, int samplingRate, double[] standardFrequencies,
                  double windowTime, boolean aWeighting,
                  double dbFsReference,boolean outputThinFrequency) {

        this(window, samplingRate, standardFrequencies, windowTime, aWeighting, dbFsReference,
                outputThinFrequency, 0);
    }

    /**
     *
     * @param window
     * @param samplingRate
     * @param standardFrequencies
     * @param windowTime
     * @param aWeighting
     * @param dbFsReference
     * @param outputThinFrequency
     * @param overlap TODO Fix window overlapping
     */
    public Window(FFTSignalProcessing.WINDOW_TYPE window, int samplingRate, double[] standardFrequencies,
                  double windowTime, boolean aWeighting,
                  double dbFsReference,boolean outputThinFrequency, double overlap) {
        this.overlap = overlap;
        this.sampleRate = samplingRate;
        this.signalProcessing = new FFTSignalProcessing(samplingRate, standardFrequencies,
                (int)(samplingRate * windowTime), dbFsReference);
        this.window = window;
        this.windowSize = (int)(samplingRate * windowTime);
        this.windowResults = new FFTSignalProcessing.ProcessingResult[(int)(Math.round(1 / (1 - overlap)))];
        this.outputThinFrequency = outputThinFrequency;
        setAWeighting(aWeighting);
    }

    public void setDbFsReference(double dbFsReference) {
        signalProcessing.setDbFsReference(dbFsReference);
    }

    public boolean isOutputThinFrequency() {
        return outputThinFrequency;
    }

    private void loadFilter() {
        // Load analyser
        String configuration = "config_44100_third_octave.json";
        if(sampleRate == 48000) {
            configuration = "config_48000_third_octave.json";
        }
        try (InputStream s = SpectrumChannel.class.getResourceAsStream(configuration)) {
            ObjectMapper objectMapper = new ObjectMapper();
            ConfigurationSpectrumChannel configurationInstance = objectMapper.
                    readValue(s, ConfigurationSpectrumChannel.class);
            spectrumChannel = new SpectrumChannel();
            spectrumChannel.loadConfiguration(configurationInstance, true);
        } catch (IOException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
    }

    public void setAWeighting(boolean aWeighting) {
        this.aWeighting = aWeighting;
        if(aWeighting) {
            loadFilter();
        }
    }

    public FFTSignalProcessing.WINDOW_TYPE getWindowType() {
        return window;
    }

    public boolean isAWeighting() {
        return aWeighting;
    }

    public double getWindowTime() {
        return signalProcessing.getSampleDuration();
    }

    /**
     * Process the current window
     */
    private void processWindow() {
        lastProcessedSpectrum = pushedSamples;
        double lAeq = 0;
        if(aWeighting && spectrumChannel != null) {
            float[] samples = signalProcessing.getSampleBuffer();
            double dbGain = 20 * Math.log10(1/signalProcessing.getRefSoundPressure());
            lAeq = spectrumChannel.processSamplesWeightA(samples) + dbGain;
        }
        FFTSignalProcessing.ProcessingResult result = signalProcessing.processSampleBuffer(window, true);
        result.setWindowLaeq(lAeq);
        // Move result by 1 backward
        if(windowResults.length > 1) {
            System.arraycopy(windowResults, 1, windowResults, 0, windowResults.length - 1);
        }
        windowResults[windowResults.length - 1] = result;
    }

    /**
     * Remove stored windows
     */
    public void cleanWindows() {
        Arrays.fill(windowResults, null);
    }

    /**
     * @return The sum of overlaps windows, null if not available
     */
    public FFTSignalProcessing.ProcessingResult getLastWindowMean() {
        if(windowResults.length > 1) {
            return new FFTSignalProcessing.ProcessingResult(1, windowResults);
        } else {
            return windowResults[0];
        }
    }

    /**
     * @return False if a window mean is available
     */
    public boolean isCacheEmpty() {
        for(FFTSignalProcessing.ProcessingResult windowCache : windowResults) {
            if(windowCache != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return The non-overlaped window index
     */
    public int getWindowIndex() {
        return lastProcessedSpectrum / windowSize;
    }

    public double getOverlap() {
        return overlap;
    }

    /**
     * @return The maximal buffer size provided in pushSample in order to not skip a result
     */
    public int getMaximalBufferSize() {
        return Math.max(0, (int)(windowSize * (1 - overlap)) - (pushedSamples - lastProcessedSpectrum));
    }

    /**
     * Push samples. If the
     * @param buffer Audio signal. If the length is superior than the
     * @return The last result, null if the pushed samples was not enough to get a leq.
     */
    public void pushSample(float[] buffer) {
        signalProcessing.addSample(buffer);
        pushedSamples += buffer.length;
        if(pushedSamples - lastProcessedSpectrum >= (int)(windowSize * (1 - overlap))) {
            processWindow();
        }
    }


    public static double[] convertShortToDouble(short[] samplesShort) {
        double[] samples = new double[samplesShort.length];
        for (int i = 0; i < samplesShort.length; i++) {
            samples[i] = samplesShort[i];
        }
        return samples;
    }

    public static double[] convertFloatToDouble(float[] samplesFloat) {
        double[] samples = new double[samplesFloat.length];
        for (int i = 0; i < samplesFloat.length; i++) {
            samples[i] = samplesFloat[i];
        }
        return samples;
    }

    public static float[] convertShortToFloat(short[] samplesShort) {
        return convertShortToFloat(samplesShort, true);
    }

    public static float[] convertShortToFloat(short[] samplesShort, boolean rescale) {
        float[] samples = new float[samplesShort.length];
        float scale = rescale ? 32768.0f : 1.0f;
        for (int i = 0; i < samplesShort.length; i++) {
            samples[i] = samplesShort[i] / scale;
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
            short[] signal = convertBytesToShort(buffer, buffer.length, byteOrder);
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


    public static float[] makeFloatSinWave(double sampleRate, double duration, double rms,
                                           double frequency) {
        float[] signal = new float[(int)(sampleRate*duration)];
        double peak = rms*Math.sqrt(2);
        for(int i=0; i<signal.length; i++) {
            double t = i / sampleRate;
            signal[i] = (float)(Math.sin(2 * Math.PI * frequency * t) * peak);
        }
        return signal;
    }

    public static short[] makeSinWave(double sampleRate, double duration, double rms,
                                      double frequency) {
        short[] signal = new short[(int)(sampleRate*duration)];
        for(int i=0; i<signal.length; i++) {
            double t = i / sampleRate;
            signal[i] = (short) Math.max(-Short.MAX_VALUE, Math.min(Short.MAX_VALUE ,
                    (Math.sin(2 * Math.PI * frequency * t) * rms)));
        }
        return signal;
    }

    public static short[] makeWhiteNoise(int samples, short rms, long seed) {
        short[] signal = new short[samples];
        Random random = new Random(seed);
        for(int i=0; i < samples; i++) {
            signal[i] = (short) (random.nextGaussian() * rms);
        }
        return signal;
    }

    public static short[] makePinkNoise(int samples, short peak, long seed) {
        // https://ccrma.stanford.edu/~jos/sasp/Example_Synthesis_1_F_Noise.html
        final double[] b = new double[] {0.049922035, -0.095993537, 0.050612699, -0.004408786};
        final double[] a = new double[] {1, -2.494956002,   2.017265875,  -0.522189400};
        final int nt60 = 1430; // nT60 = round(log(1000)/(1-max(abs(roots(A))))); % T60 est.
        float[] v = new float[samples + nt60];
        Random random = new Random(seed);
        for(int i=0; i < v.length; i++) {
            v[i] = (float)(random.nextGaussian() * peak);
        }
        DigitalFilter filter = new DigitalFilter(b, a);
        float[] x = new float[v.length];
        filter.filter(v, x);
        short[] signal = new short[samples];
        for(int i=nt60; i < x.length; i++) {
            signal[i - nt60] = (short) (x[i]);
        }
        return signal;
    }
}
