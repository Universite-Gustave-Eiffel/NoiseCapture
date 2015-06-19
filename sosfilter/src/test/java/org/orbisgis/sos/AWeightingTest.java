package org.orbisgis.sos;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * Created by G. Guillaume on 03/06/2015.
 */
public class AWeightingTest {

    @Test
    public void testGetAWeightingCoefficients() {
        AWeighting aWeighting = new AWeighting();
        double[] numerator = aWeighting.numerator;
        double[] denominator = aWeighting.denominator;
        assertEquals(numerator.length, denominator.length);
    }
}




