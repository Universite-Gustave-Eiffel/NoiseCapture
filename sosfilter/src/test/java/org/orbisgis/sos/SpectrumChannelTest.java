package org.orbisgis.sos;

import com.fasterxml.jackson.databind.ObjectMapper;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;

public class SpectrumChannelTest extends TestCase {

    public void test1000hzSinWave() throws IOException {
        float[] signal = SOSSignalProcessing.makeFloatSinWave(48000, 5.0,
                0.5, 1000.0);
        ConfigurationSpectrumChannel configuration;
        try (InputStream s = SpectrumChannel.class.getResourceAsStream(
                "config_48000_third_octave.json")) {
            ObjectMapper objectMapper = new ObjectMapper();
            configuration = objectMapper.readValue(s, ConfigurationSpectrumChannel.class);
        }
        SpectrumChannel spectrumChannel = new SpectrumChannel();
        spectrumChannel.loadConfiguration(configuration, true);
        double[] bandsLeq = spectrumChannel.processSamples(signal);
        double expectedLeq = AcousticIndicators.getLeq(signal, 1.0);
        assertEquals(expectedLeq, bandsLeq[spectrumChannel.getNominalFrequency().indexOf(1000.0)],
                0.01);
    }

}