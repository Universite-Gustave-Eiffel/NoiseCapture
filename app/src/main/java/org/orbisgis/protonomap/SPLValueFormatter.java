package org.orbisgis.protonomap;

import com.github.mikephil.charting.utils.ValueFormatter;

/**
 * Format value for spectrum histogram
 */
public class SPLValueFormatter implements ValueFormatter {

    @Override
    public String getFormattedValue(float value) {
        return String.valueOf((int)value);
    }
}
