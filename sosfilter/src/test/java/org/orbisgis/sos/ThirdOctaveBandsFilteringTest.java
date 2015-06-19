package org.orbisgis.sos;


import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * Created by G. Guillaume on 02/06/2015.
 */
public class ThirdOctaveBandsFilteringTest {

    @Test
    public void testReadCsv() {
        ThirdOctaveBandsFiltering thirdOctaveBandsFiltering = new ThirdOctaveBandsFiltering();
        List<ThirdOctaveBandsFiltering.FiltersParameters> filtersCoefficients = thirdOctaveBandsFiltering.getFilterParameters();
        assertEquals(128, filtersCoefficients.size());
    }

    @Test
    public void testThirdOctaveBandsFiltering() {
        InputStream inputStream = ThirdOctaveBandsFilteringTest.class.getResourceAsStream("pinknoise_44.1k_-6dBFS_5s_raw");
        try {
            final int rate = 44100;
            CoreSignalProcessing signalProcessing = new CoreSignalProcessing(rate);
            signalProcessing.processAudio(16, rate, inputStream);
        } catch (IOException ex) {
            Log.e(ex.getLocalizedMessage(),"Processing failed", ex);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ex) {
                Log.e(ex.getLocalizedMessage(),"Close file failed", ex);
            }
        }
    }

}