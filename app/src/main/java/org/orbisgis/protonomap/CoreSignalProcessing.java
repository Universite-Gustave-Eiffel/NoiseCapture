package org.orbisgis.protonomap;

import android.util.Log;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * Signal processing core
 */
public class CoreSignalProcessing {

    private int sampleRate;
    private PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    private double[] spectrum;
    public static String PROP_SPECTRUM = "PROP_SPECTRUM";

    /**
     * Third octave frequencies
     */
    private static class getThrdOctFreqs{

        /** Standard center frequencies of third octave bands
         * @param
         */

        private static int idFreq;
        private static double fCtr;
        private static double fLow;
        private static double fHigh;

        /** Exact frequency for the third octave band
         * @param idFreq center frequency index (int)
         */
        private static double ctrFreq(ThirdOctaveFrequencies frequencies) {
            idFreq = frequencies.idFreq;
            fCtr = frequencies.fCtr;
            fCtr = Math.pow(10., 3.) * Math.pow(2., (idFreq-18) / 3.);
            return fCtr;
        }

        /** Factor applied on the exact center frequency for calculating lateral frequencies
         * @param idFreq center frequency index (int)
         */
        private static double latFreqsFactor() {
            double latFreqFactor = Math.pow(2., 1./6.);
            return latFreqFactor;
        }

        /** Lower lateral frequency for the third octave band
         * @param idFreq center frequency index (int)
         */
        private static double lowLatFreq(ThirdOctaveFrequencies frequencies) {
            idFreq = frequencies.idFreq;
            fCtr = frequencies.fCtr;
            fLow = frequencies.fLow;
            double latFreqFactor = latFreqsFactor();
            fLow = fCtr / latFreqFactor;
            return fLow;
        }

        /** Higher lateral frequency for the third octave band
         * @param idFreq center frequency index (int)
         */
        private static double highLatFreq(ThirdOctaveFrequencies frequencies) {
            idFreq = frequencies.idFreq;
            fCtr = frequencies.fCtr;
            fHigh = frequencies.fHigh;
            double latFreqFactor = latFreqsFactor();
            fHigh = fCtr * latFreqFactor;
            return fHigh;
        }
    }

        /** Third octave band center and lateral frequencies
         * @param
         * @return Two-dimensional array with rows corresponding to third octave bands and 3 columns corresponding to the lower (column 0), center (column 1) and higher (column 2) frequencies of the third octave band respectively
         */
        private static double getThrdOctFreqs(ThirdOctaveBandFrequencies frequencies) {
            idFreq = frequencies.idFreq;
            fCtr = frequencies.fCtr;
            fLow = frequencies.fLow;
            fHigh = frequencies.fHigh;
    //        int nbFreqs = stdnFreqs.length;
    //        double[] thrdOctFreqs = new double[nbFreqs];

            fLow = lowLatFreq(idFreq);
            fCtr = ctrFreq(idFreq);
            fHigh = highLatFreq(idFreq);

    //        for (int idFreq = 0; idFreq <= nbFreqs; idFreq++) {
    //            thrdOctFreqs[idFreq] = lowLatFreq(idFreq);    // Lower lateral frequency
    //            thrdOctFreqs[idFreq] = ctrFreq();       // Center frequency
    //            thrdOctFreqs[idFreq] = highLatFreq(idFreq);   // Higher lateral frequency            }
    //        }
    //        return thrdOctFreqs;
        }


        private static final double[][] thrdOctFreqs;
    //    private static final IirFilterCoefficients thrdOctCoefts;


        static {
            thrdOctFreqs = getThrdOctFreqs();
    //        thrdOctCoefts = thrdOctDsgn(thrdOctFreqs[0][1], sampleRate);
        }

        public CoreSignalProcessing(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        /**
         * @return Last computed spectrum
         */
        public double[] getSpectrum() {
            return spectrum;
        }

        public void processAudio(int encoding, final int rate, InputStream inputStream) throws IOException {
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
                for(int i = 0; i < samplesShort.length; i++) {
                    samples[i] = samplesShort[i] / 32768d;
                }
                int lengthRead = Math.min(samples.length, rate - secondCursor);
                // Copy sample fragment into second array
                System.arraycopy(samples, 0, secondSample, secondCursor, lengthRead);
                secondCursor+=lengthRead;
                if(lengthRead < samples.length) {
                    setSamples(secondSample);
                    secondCursor = 0;
                    // Copy remaining sample fragment into new second array
                    int newLengthRead = samples.length - lengthRead;
                    System.arraycopy(samples, lengthRead, secondSample, secondCursor, newLengthRead);
                    secondCursor+=newLengthRead;
                }
                if(secondCursor == rate) {
                    setSamples(secondSample);
                    secondCursor = 0;
                }
            }
            Log.i("Debug","Total read:" + totalRead);
        }

        /**
         * @param samples 1sec samples
         */
        public void setSamples(double[] samples) {
            setSpectrum(Arrays.copyOf(samples, 8));
        }

        public void addPropertyChangeListener(String property, PropertyChangeListener listener) {
            listeners.addPropertyChangeListener(property, listener);
        }

        public void removePropertyChangeListener(PropertyChangeListener listener) {
            listeners.removePropertyChangeListener(listener);
        }

        private void setSpectrum(double[] newSpectrum) {
            double[] oldSpectrum = spectrum;
            spectrum = newSpectrum;
            listeners.firePropertyChange(PROP_SPECTRUM, oldSpectrum, spectrum);
        }




    //    /** Third octave band center and lateral frequencies
    //     * @param stdnThrdOctCtrFreqs Standard third octave band center frequencies (Hz)
    //     * @return Two-dimensional array with rows corresponding to third octave bands and 3 columns corresponding to the lower (column 0), center (column 1) and higher (column 2) frequencies of the third octave band respectively
    //     */
    //    private static double[][] setThrdOctFreqs(double[] stdnThrdOctCtrFreqs) {
    //        int nbFreqs = stdnThrdOctCtrFreqs.length;
    //        double[][] thrdOctFreqs = new double[nbFreqs][3];
    //        double fd = Math.pow(2., 1./6.);
    //        double fc;
    //        for (int idFreq = 0; idFreq <= nbFreqs; idFreq++) {
    //            fc = Math.pow(10., 3.) * Math.pow(2., (idFreq-18) / 3.);
    //            thrdOctFreqs[idFreq][0]  = fc / fd;     // Lower frequency for the third octave band
    //            thrdOctFreqs[idFreq][1] = fc;           // Center frequency for the third octave band
    //            thrdOctFreqs[idFreq][2] = fc * fd;      // Higher frequency for the third octave band
    //        }
    //        return thrdOctFreqs;
    //    }







    //    /** Design of the third octave band filter (Butterworth filter).
    //     * @param fc Standard center frequency (Hz)
    //     * @param sampleRate Sampling frequency (Hz)
    //     */
    //    private static double[][] thrdOctDsgn(double fc, double sampleRate) {
    //        int filtOrder = 3;      // Filter order
    //        double pi = 3.14159265358979;
    //        double Qr = fc/(fHigh-fLow);
    //        double Qd = (pi/2./filtOrder) / (Math.sin(pi/2/filtOrder))*Qr;
    //        double alpha = (1. + Math.sqrt(1.+4.* Math.pow(Qd, 2.) )) / 2. / Qd;
    //        double w = fc / (sampleRate/2.);
    //        double wLow = w / alpha;
    //        double wHigh = w * alpha;
    //        IirFilterCoefficients coeffs = new IirFilterCoefficients();
    //        coeffs = IirFilterDesignExstrom.design(bandpass, filtOrder, wLow, wHigh);
    //        return coeffs;
    //    }

}
