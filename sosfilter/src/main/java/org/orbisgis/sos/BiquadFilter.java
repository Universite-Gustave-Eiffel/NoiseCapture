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

/**
 * A digital biquad filter is a second order recursive linear filter,
 *  containing two poles and two zeros. "Biquad" is an abbreviation of "bi-quadratic",
 *   which refers to the fact that in the Z domain, its transfer function is
 *   the ratio of two quadratic functions
 * It is a conversion of https://github.com/SonoMKR/sonomkr-core/blob/master/src/biquadfilter.cpp
 * @author Nicolas Fortin, Université Gustave Eiffel
 * @author Valentin Le Bescond, Université Gustave Eiffel
 */
public class BiquadFilter {
    double[] b0;
    double[] b1;
    double[] b2;
    double[] a1;
    double[] a2;
    double[] delay1;
    double[] delay2;

    public BiquadFilter(double[] b0, double[] b1, double[] b2, double[] a1, double[] a2) {
        this.b0 = b0;
        this.b1 = b1;
        this.b2 = b2;
        this.a1 = a1;
        this.a2 = a2;
        this.delay1 = new double[b0.length];
        this.delay2 = new double[b0.length];
        assert b0.length == b1.length &&
                b1.length == b2.length &&
                b2.length == a1.length &&
                a1.length == a2.length;
    }

    public void reset() {
        this.delay1 = new double[b0.length];
        this.delay2 = new double[b0.length];
    }

    public double filterThenLeq(float[] samples) {
        double square_sum = 0.0;
        double output_acc;
        for(int i=0; i < samples.length; i++) {
            double input_acc = samples[i];
            for(int j=0; j < b0.length; j++) {
                input_acc -= delay1[j] * a1[j];
                input_acc -= delay2[j] * a2[j];
                output_acc = input_acc * b0[j];
                output_acc += delay1[j] * b1[j];
                output_acc += delay2[j] * b2[j];
                delay2[j] = delay1[j];
                delay1[j] = input_acc;
                input_acc = output_acc;
            }
            square_sum += input_acc * input_acc;
        }
        return 10 * Math.log10(square_sum / samples.length);
    }

    public void filterSlice(float[] samplesIn, float[] samplesOut, int subsampling_factor) {
        int samples_out_index = 0;
        double output_acc;
        for(int i=0; i < samplesIn.length; i++) {
            double input_acc = samplesIn[i];
            for(int j=0; j < b0.length; j++) {
                input_acc -= delay1[j] * a1[j];
                input_acc -= delay2[j] * a2[j];
                output_acc = input_acc * b0[j];
                output_acc += delay1[j] * b1[j];
                output_acc += delay2[j] * b2[j];
                delay2[j] = delay1[j];
                delay1[j] = input_acc;
                input_acc = output_acc;
            }
            if(i % subsampling_factor == 0) {
                samplesOut[samples_out_index] = (float)input_acc;
                samples_out_index++;
            }
        }
    }
}
