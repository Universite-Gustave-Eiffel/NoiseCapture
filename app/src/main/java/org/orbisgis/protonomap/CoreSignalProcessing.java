package org.orbisgis.protonomap;

import android.util.Log;

import org.orbisgis.protonomap.filter.ArrayUtils;
import org.orbisgis.protonomap.filter.FilterPassType;
import org.orbisgis.protonomap.filter.IirFilter;
import org.orbisgis.protonomap.filter.IirFilterCoefficients;
import org.orbisgis.protonomap.filter.IirFilterDesignExstrom;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
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
    private PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    private double[] spectrum;
    public static String PROP_SPECTRUM = "PROP_SPECTRUM";
    private final ThirdOctaveFrequencies.LowHigh[] thrdOctFreqs;
    private IirFilterCoefficients[] freqFilter = new IirFilterCoefficients[ThirdOctaveFrequencies.STANDARD_FREQUENCIES.length];
    /*
    Filter function implemented as a direct form II transposed structure
    y(n) = b(1)*x(n) + b(2)*x(n-1) + ... + b(nb+1)*x(n-nb) - a(2)*y(n-1) - ... - a(na+1)*y(n-na)
     */
    public void filter(double [] b,double [] a, List<Double> inputVector, List<Double> outputVector){
        double rOutputY = 0.0;
        int maxB = Math.min(b.length, inputVector.size());
        int maxA = Math.min(a.length, outputVector.size());
        for (int i = 0; i < maxB; i++) {
            rOutputY += b[i]*inputVector.get(inputVector.size() - i - 1);
        }
        for (int i = 0; i+1 < maxA; i++) {
            rOutputY -= a[i + 1]*outputVector.get(outputVector.size() - i - 1);
        }
        outputVector.add(rOutputY);
    }

    /*
    Equivalent sound pressure level
     */
    public static double getLeq(double[] inputSignal, double timePeriod, double pRef) {
        double rmsValue = 0.0;
        double splValue = 0.0;
        for (int i = 1; i < inputSignal.length; i++) {
            rmsValue += inputSignal[i] * inputSignal[i];    // Math.pow(inputSignal[i], 2.);
        }
        rmsValue = Math.sqrt(rmsValue / timePeriod);
        splValue = 20 * Math.log10(rmsValue / pRef);
        return splValue;
    }

    public CoreSignalProcessing(int sampleRate) {
        this.sampleRate = sampleRate;
        this.thrdOctFreqs = new ThirdOctaveFrequencies.LowHigh[ThirdOctaveFrequencies.STANDARD_FREQUENCIES.length];
        int filterOrder = 3;
        /*
        Loop over third octave bands
         */
        for (int idFreq = 0; idFreq < thrdOctFreqs.length; idFreq++) {
            thrdOctFreqs[idFreq] = ThirdOctaveFrequencies.getLatFreqs(idFreq);
            double fCfLow = thrdOctFreqs[idFreq].low / sampleRate;
            double fCfHigh = thrdOctFreqs[idFreq].high / sampleRate;
            IirFilterCoefficients filterCoefs = IirFilterDesignExstrom.design(FilterPassType.bandpass, filterOrder, fCfLow, fCfHigh);
            freqFilter[idFreq] = filterCoefs;
        }
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
            for (int i = 0; i < samplesShort.length; i++) {
                samples[i] = samplesShort[i] / 32768d;
            }
            int lengthRead = Math.min(samples.length, rate - secondCursor);
            // Copy sample fragment into second array
            System.arraycopy(samples, 0, secondSample, secondCursor, lengthRead);
            secondCursor += lengthRead;
            if (lengthRead < samples.length) {
                setSamples(secondSample);
                secondCursor = 0;
                // Copy remaining sample fragment into new second array
                int newLengthRead = samples.length - lengthRead;
                System.arraycopy(samples, lengthRead, secondSample, secondCursor, newLengthRead);
                secondCursor += newLengthRead;
            }
            if (secondCursor == rate) {
                setSamples(secondSample);
                secondCursor = 0;
            }
        }
        Log.i("Debug", "Total read:" + totalRead);
    }

    /**
     * @param samples 1sec samples
     */
    public void setSamples(double[] samples) {
        //double[] frequencies = ThirdOctaveFrequencies.STANDARD_FREQUENCIES;
        double[] spl = new double[ThirdOctaveFrequencies.STANDARD_FREQUENCIES.length];
        double pRef = 0.00002;      // Reference sound pressure level (Pa)
        double[] filteredSignal = new double[samples.length];
        /*
        Loop over third octave bands frequencies
         */
        for (int idFreq = 0; idFreq<freqFilter.length; idFreq++) {
            ArrayList<Double> inputSignal = new ArrayList<Double>(samples.length);
            ArrayList<Double> outputSignal = new ArrayList<Double>(samples.length);
            for (double sample : samples) {
                inputSignal.add(sample);
                filter(freqFilter[idFreq].b, freqFilter[idFreq].a, inputSignal, outputSignal);
            }
            for (int i = 0; i<samples.length; i++) {
                filteredSignal[i] = outputSignal.get(i);
            }
            /*
            Calculation of the equivalent sound pressure level per third octave band
             */
            spl[idFreq] = getLeq(filteredSignal, filteredSignal.length, pRef);
        }
        setSpectrum(Arrays.copyOf(spl, 8));
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
}

