package org.noise_planet.noisecapture.util;

import com.github.mikephil.charting.utils.PercentFormatter;

/**
 * Do not show 0% labels
 */
public class CustomPercentFormatter extends PercentFormatter {
    public CustomPercentFormatter() {
    }

    @Override
    public String getFormattedValue(float value) {
        if(value > 0.00001) {
            return super.getFormattedValue(value);
        } else {
            return "";
        }
    }
}
