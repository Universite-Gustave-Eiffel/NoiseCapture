package org.noise_planet.acousticmodem;

import org.junit.Test;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;

/**
 *
 */
public class AcousticModemTest {
    private static final int[] UT_FREQUENCIES = new int[]{
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[11],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[12],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[13],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[14],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[15],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[16],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[17],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[18]
    };

    @Test
    public void TestEncodeDecode() throws Exception {
        String messageInput = "U1_76.8";
        AcousticModem acousticModem = new AcousticModem(new Settings(44100, 0.250, Settings.wordsFrom8frequencies(UT_FREQUENCIES)));

    }
}