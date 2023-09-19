package org.orbisgis.sos;

import com.fasterxml.jackson.databind.ObjectMapper;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

public class SpectrumChannelTest extends TestCase {

    public static double toW(double spl) {
        return Math.pow(10, spl / 20);
    }

    /**
     * @param w p/p0 value
     * @return dBSPL
     */
    public static double toSpl(double w) {
        return 20 * Math.log10(w);
    }

    public void test1000hzSinWave() throws IOException {
        double expectedLeq = -3;
        float[] signal = Window.makeFloatSinWave(48000, 5.0,
                Math.pow(10, expectedLeq/20.0), 1000.0);
        ConfigurationSpectrumChannel configuration;
        try (InputStream s = SpectrumChannel.class.getResourceAsStream(
                "config_48000_third_octave.json")) {
            ObjectMapper objectMapper = new ObjectMapper();
            configuration = objectMapper.readValue(s, ConfigurationSpectrumChannel.class);
        }
        SpectrumChannel spectrumChannel = new SpectrumChannel();
        spectrumChannel.loadConfiguration(configuration, true);
        double[] bandsLeq = spectrumChannel.processSamples(signal);
        assertEquals(expectedLeq, bandsLeq[spectrumChannel.getNominalFrequency().indexOf(1000.0)],
                0.01);
    }

    public void testSpeak() throws IOException {
        double expectedBA = -33.761;
        double expectedBC = -28.763;
        // Reference spectrum
        // generated using acoustics.standards.iec_61672_1_2013.time_averaged_sound_level
        double[] refSpl = {-65.997, -68.064, -66.276, -43.342, -31.927, -37.280, -47.332, -35.327,
                -42.683, -42.909, -48.506, -49.105, -52.900, -52.152, -52.804, -52.348, -52.313,
                -53.387, -52.532, -53.735, -53.556, -58.004, -66.453};
        float[] signal;
        try(InputStream inputStream = SpectrumChannelTest.class.getResourceAsStream(
                "speak_44100Hz_16bitsPCM_10s.raw")) {
            assert inputStream != null;
            signal = Window.convertShortToFloat(
                    Window.loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN));
        }
        // Load band filter
        ConfigurationSpectrumChannel configuration;
        try (InputStream s = SpectrumChannel.class.getResourceAsStream(
                "config_44100_third_octave.json")) {
            ObjectMapper objectMapper = new ObjectMapper();
            configuration = objectMapper.readValue(s, ConfigurationSpectrumChannel.class);
        }
        SpectrumChannel spectrumChannel = new SpectrumChannel();
        spectrumChannel.loadConfiguration(configuration, true);
        double[] computedSpl = spectrumChannel.processSamples(signal);
        assertEquals(refSpl.length, computedSpl.length);
        for(int i=0; i < refSpl.length; i++) {
            // use error delta in linear scale
            assertEquals(toW(refSpl[i]), toW(computedSpl[i]),  12e-4);
        }
        double LAeq = spectrumChannel.processSamplesWeightA(signal);
        assertEquals(expectedBA, LAeq, 0.01);
        double LCeq = spectrumChannel.processSamplesWeightC(signal);
        assertEquals(expectedBC, LCeq, 0.01);
    }
}