/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

package org.orbisgis.sos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;

import java.io.*;
import java.util.*;

/**
 * Created by G. Guillaume on 02/06/2015.
 * Third octave bands filtering of a time signal
 * This module applies an third octave bands filters according to the standard IEC-61260 "Electroacoustics - Octave-band and fractional-octave-band filters" (2001)
 * @see <a href="http://siggigue.github.io/pyfilterbank/splweighting.html">http://siggigue.github.io/pyfilterbank/splweighting.html</a>
 * @see <a href="http://www.mathworks.com/matlabcentral/fileexchange/69-octave/content//octave/oct3dsgn.m">http://www.mathworks.com/matlabcentral/fileexchange/69-octave/content//octave/oct3dsgn.m</a>
 */
public class ThirdOctaveBandsFiltering {
    public enum FREQUENCY_BANDS {REDUCED, FULL};
    private static final Logger LOGGER = LoggerFactory.getLogger(ThirdOctaveBandsFiltering.class);

    /**
     * Standard center frequencies of third octave bands
     * STANDARD_FREQUENCIES_REDUCED corresponds with a reduced array of standard third octave bands frequencies in the range [100Hz, 20kHz]
     * STANDARD_FREQUENCIES_FULL corresponds with the array of standard third octave bands frequencies in the range [100Hz, 20kHz]
     */
    public static final double[] STANDARD_FREQUENCIES_REDUCED = new double[]{100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000};
    public static final double[] STANDARD_FREQUENCIES_FULL = new double[]{16, 20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000};
    public static final double[] STANDARD_OCTAVE_FREQUENCIES_REDUCED = new double[]{125, 250, 500, 1000, 2000, 4000, 8000, 16000};

    public static double[] getStandardFrequencies(FREQUENCY_BANDS frequency_bands) {
        if (frequency_bands == FREQUENCY_BANDS.FULL) {
            return STANDARD_FREQUENCIES_FULL;
        } else {
            return STANDARD_FREQUENCIES_REDUCED;
        }
    }

    public static double getSampleBufferDuration(FREQUENCY_BANDS frequency_bands) {
        if (frequency_bands == FREQUENCY_BANDS.FULL) {
            return 5.;
        } else {
            return 1.;
        }
    }

}