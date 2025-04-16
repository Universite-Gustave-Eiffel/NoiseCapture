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

import java.util.Arrays;

import org.jtransforms.fft.FloatFFT_1D;

/**
 * Signal processing core
 */
public class FFTSignalProcessing {

    public enum WINDOW_TYPE { RECTANGULAR, HANN, TUKEY }
    public final int samplingRate;
    private float[] sampleBuffer;
    private int sampleBufferPosition=0;
    double[] standardFrequencies;
    double tukeyAlpha = 0.2;
    private final int windowSize;
    private FloatFFT_1D floatFFT_1D;
    // RMS level for 90 dB on 16 bits according to Android specification
    public static final double RMS_REFERENCE_90DB = 2500;
    // -22.35 dB Full Scale level of a 90 dB sinusoidal signal for PCM values
    public static final double DB_FS_REFERENCE = - (20 * Math.log10(RMS_REFERENCE_90DB/Short.MAX_VALUE)) + 90;
    private double refSoundPressure;
    private long sampleAdded = 0;

    public FFTSignalProcessing(int samplingRate, double[] standardFrequencies, int windowSize) {
        this.windowSize = windowSize;
        this.standardFrequencies = standardFrequencies;
        this.samplingRate = samplingRate;
        this.sampleBuffer = new float[windowSize];
        this.floatFFT_1D = new FloatFFT_1D(windowSize);
        setDbFsReference(DB_FS_REFERENCE);
    }

    public FFTSignalProcessing(int samplingRate, double[] standardFrequencies, int windowSize, double dbFsReference) {
        this.windowSize = windowSize;
        this.standardFrequencies = standardFrequencies;
        this.samplingRate = samplingRate;
        this.sampleBuffer = new float[windowSize];
        this.floatFFT_1D = new FloatFFT_1D(windowSize);
        setDbFsReference(dbFsReference);
    }

    public void setDbFsReference(double dbFsReference) {
        this.refSoundPressure = 1 / Math.pow(10, dbFsReference / 20);
    }

    public static double[] computeFFTCenterFrequency(int maxLimitation) {
        double[] allCenterFreq = ThirdOctaveBandsFiltering.getStandardFrequencies(ThirdOctaveBandsFiltering.FREQUENCY_BANDS.REDUCED);
        double[] retCenterFreq = new double[allCenterFreq.length];
        int retSize = 0;
        for(double centerFreq : allCenterFreq) {
            if(maxLimitation >= centerFreq) {
                retCenterFreq[retSize++] = centerFreq;
            }else {
                break;
            }
        }
        return Arrays.copyOfRange(retCenterFreq, 0, retSize);
    }

    /**
     * @return Time in seconds of sample buffer
     */
    public double getSampleDuration() {
        return windowSize / (double)samplingRate;
    }

    public int getWindowSize() {
        return windowSize;
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
    public float[] getSampleBuffer() {
        return sampleBuffer;
    }

    /**
     * Add an audio sample to the buffer.
     * 1000 Hz sinusoidal 2500 RMS for 16-bits audio samples yields a 90dB SPL value.
     * @param sample audio sample
     */
    public void addSample(float[] sample) {
        if(sample.length+sampleBufferPosition > sampleBuffer.length) {
            throw new IllegalArgumentException("Too much samples for configured window size");
        }
        System.arraycopy(sample,0, sampleBuffer, sampleBufferPosition, sample.length);
        sampleBufferPosition+=sample.length;
        sampleAdded+=sample.length;
    }

    public double computeRms() {
        return AcousticIndicators.computeRms(sampleBuffer);
    }

    public double computeGlobalLeq() {
        return AcousticIndicators.getLeq(sampleBuffer, refSoundPressure);
    }

    public double todBspl(double rms) {
        return AcousticIndicators.todBspl (rms,  refSoundPressure);
    }

    /**
     * Calculation of the equivalent sound pressure level per third octave bands
     * @see "http://stackoverflow.com/questions/18684948/how-to-measure-sound-volume-in-db-scale-android"
     * @return List of double array of equivalent sound pressure level per third octave bands
     */
    public ProcessingResult processSampleBuffer(WINDOW_TYPE window, boolean outputThinFrequency) {
        if(sampleBufferPosition > 0 && sampleBufferPosition != sampleBuffer.length) {
            throw new IllegalStateException("Sample window incomplete");
        }
        sampleBufferPosition = 0;
        float[] signal = Arrays.copyOf(sampleBuffer, sampleBuffer.length);
        double energyCorrection = signal.length;
        switch (window) {
            case HANN:
                energyCorrection = AcousticIndicators.hannWindow(signal);
                break;
            case TUKEY:
                energyCorrection = AcousticIndicators.tukeyWindow(signal, tukeyAlpha);
        }
        energyCorrection = 1.0 / Math.sqrt(energyCorrection / signal.length);
        floatFFT_1D.realForward(signal);
        final double freqByCell = samplingRate / (double)windowSize;
        float[] squareAbsoluteFFT = new float[signal.length / 2];
        //a[offa+2*k] = Re[k], 0<=k<n/2
        double sumRMS = 0;
        for(int k = 0; k < squareAbsoluteFFT.length; k++) {
            final float re = signal[k * 2];
            final float im = signal[k * 2 + 1];
            squareAbsoluteFFT[k] = re * re + im * im;
            sumRMS += squareAbsoluteFFT[k];
        }
        // Compute A weighted third octave bands
        float[] splLevels = thirdOctaveProcessing(squareAbsoluteFFT, false, energyCorrection);
        // Limit spectrum output by specified frequencies and convert to dBspl
        float[] spectrumSplLevels = null;
        if(outputThinFrequency) {
            spectrumSplLevels = new float[(int) (Math.min(samplingRate / 2.0,
                    standardFrequencies[standardFrequencies.length - 1]) /
                    freqByCell)];
            for (int i = 0; i < spectrumSplLevels.length; i++) {
                spectrumSplLevels[i] = (float) todBspl(squareAbsoluteFFTToRMS(squareAbsoluteFFT[i
                        ], squareAbsoluteFFT.length) * energyCorrection);
            }
        }
        return new ProcessingResult(sampleAdded,
                spectrumSplLevels == null ? null : Window.convertFloatToDouble(
                        spectrumSplLevels),
                Window.convertFloatToDouble(splLevels),
                todBspl(squareAbsoluteFFTToRMS(sumRMS, squareAbsoluteFFT.length)
                        * energyCorrection));
    }

    private double squareAbsoluteFFTToRMS(double squareAbsoluteFFT, int sampleSize) {
        return Math.sqrt(squareAbsoluteFFT / 2) / sampleSize;
    }

    public double getRefSoundPressure() {
        return refSoundPressure;
    }


    /**
     * Third-octave recombination method
     * @param squareAbsoluteFFT Narrow frequency array
     * @param thirdOctaveAWeighting True to apply a A weighting on bands
     * @return Third octave bands
     */
    public float[] thirdOctaveProcessing(float[] squareAbsoluteFFT, boolean thirdOctaveAWeighting, double energyCorrection) {
        final double freqByCell = samplingRate / (double)windowSize;
        float[] splLevels = new float[standardFrequencies.length];
        int thirdOctaveId = 0;
        int refFreq = Arrays.binarySearch(standardFrequencies, 1000);
        for(double fNominal : standardFrequencies) {
            // Compute lower and upper value of third-octave
            // NF-EN 61260
            // base 10
            double fCenter = Math.pow(10, (Arrays.binarySearch(standardFrequencies, fNominal) - refFreq)/10.) * 1000;
            final double fLower = fCenter * Math.pow(10, -1. / 20.);
            final double fUpper = fCenter * Math.pow(10, 1. / 20.);
            double sumVal = 0;
            int cellLower = (int)(Math.ceil(fLower / freqByCell));
            int cellUpper = Math.min(squareAbsoluteFFT.length - 1, (int) (Math.floor(fUpper / freqByCell)));
            for(int idCell = cellLower; idCell <= cellUpper; idCell++) {
                sumVal += squareAbsoluteFFT[idCell];
            }
            sumVal = todBspl(squareAbsoluteFFTToRMS(sumVal, squareAbsoluteFFT.length) * energyCorrection);
            if(thirdOctaveAWeighting) {
                // Apply A weighting
                int freqIndex = Arrays.binarySearch(
                        ThirdOctaveFrequencies.STANDARD_FREQUENCIES, fNominal);
                sumVal = (float) (sumVal + ThirdOctaveFrequencies.A_WEIGHTING[freqIndex]);
            }
            splLevels[thirdOctaveId] = (float) sumVal;
            thirdOctaveId++;
        }
        return splLevels;
    }

    /**
     * FFT processing result
     * TODO provide warning information about approximate value about 30 dB range from -18 dB to +12dB around 90 dB
     */
    public static final class ProcessingResult {
        double[] fftResult;
        double[] spl;
        double windowLeq;
        long id;
        double windowLaeq=0;

        public ProcessingResult(long id, double[] fftResult, double[] spl, double windowLeq) {
            this.fftResult = fftResult;
            this.spl = spl;
            this.windowLeq = windowLeq;
            this.id = id;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public double getWindowLaeq() {
            return windowLaeq;
        }

        public void setWindowLaeq(double windowLaeq) {
            this.windowLaeq = windowLaeq;
        }

        /**
         * Energetic avg of provided results.
         */
        public ProcessingResult(double windowCount, ProcessingResult... toMerge) {
            // Take the last processing result as reference because results are moved from
            // the right to the left in the array
            if(toMerge[toMerge.length - 1] != null) {
                id = toMerge[toMerge.length - 1].id;
                if(toMerge[toMerge.length - 1].fftResult != null) {
                    this.fftResult = new double[toMerge[toMerge.length - 1].fftResult.length];
                    for(ProcessingResult merge : toMerge) {
                        if(merge != null) {
                            for (int i = 0; i < fftResult.length; i++) {
                                fftResult[i] += Math.pow(10, merge.fftResult[i] / 10.);
                            }
                        }
                    }
                    for(int i = 0; i < fftResult.length; i++) {
                        fftResult[i] = (float)(10. * Math.log10(fftResult[i] / windowCount));
                    }
                }
                this.spl = new double[toMerge[toMerge.length - 1].spl.length];
                for(ProcessingResult merge : toMerge) {
                    if(merge != null) {
                        for (int i = 0; i < spl.length; i++) {
                            spl[i] += Math.pow(10, merge.spl[i] / 10.);
                        }
                    }
                }
                for(int i = 0; i < spl.length; i++) {
                    spl[i] = (float)(10. * Math.log10(spl[i] / windowCount));
                }
                double sum = 0;
                double sumLAeq=0;
                int sumCount=0;
                for(ProcessingResult merge : toMerge) {
                    if(merge != null) {
                        sumCount++;
                        sum += Math.pow(10, merge.getWindowLeq() / 10.);
                        sumLAeq += Math.pow(10, merge.getWindowLaeq() / 10.);
                    }
                }
                this.windowLeq = (float)(10. * Math.log10(sum / sumCount));
                this.windowLaeq = 10. * Math.log10(sumLAeq / sumCount);
            }
        }

        public double[] getFftResult() {
            return fftResult;
        }

        public double[] getSpl() {
            return spl;
        }

        public double getWindowLeq() {
            return windowLeq;
        }
    }
}