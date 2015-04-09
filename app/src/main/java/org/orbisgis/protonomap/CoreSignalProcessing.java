package org.orbisgis.protonomap;

import android.util.Log;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
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
}
