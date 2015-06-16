package org.orbisgis.sos;


import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * Created by molene on 02/06/2015.
 */
public class SosFilteringTest {

    @Test
    public void testReadCsv() {
        SosFiltering sosFiltering = new SosFiltering();
        List<SosFiltering.FiltersParameters> filtersCoefficients = sosFiltering.getFilterParameters();
        assertEquals(128, filtersCoefficients.size());
    }

}