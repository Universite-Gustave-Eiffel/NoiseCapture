package org.orbisgis.sos;


import org.junit.Test;

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

}