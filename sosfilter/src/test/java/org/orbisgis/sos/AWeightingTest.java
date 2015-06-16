package org.orbisgis.sos;

import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * Created by molene on 03/06/2015.
 */
public class AWeightingTest {

    @Test
    public void testLoadCoefts() {
        AWeighting aWeighting= new AWeighting();
        List<AWeighting.AWeightingCoefts> numerator = aWeighting.getNumerator();
        List<AWeighting.AWeightingCoefts> denominator = aWeighting.getDenominator();
        assertEquals(numerator.size(), denominator.size());
    }
}




