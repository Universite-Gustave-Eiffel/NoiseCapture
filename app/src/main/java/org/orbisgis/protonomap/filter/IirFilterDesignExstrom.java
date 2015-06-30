// This module is a Java port of the C source code from http://www.exstrom.com/journal/sigproc/
//
// Copyright notice for the Java port:
//
// Copyright 2013 Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
// www.source-code.biz, www.inventec.ch/chdh
//
// License: GPL, GNU General Public License, V2 or later, http://www.gnu.org/licenses/gpl.html
//
// This module is provided "as is", without warranties of any kind.
//
//==============================================================================
//
// Original copyright notice of the C sources:
//
// Copyright (C) 2003, 2004, 2005, 2007 Exstrom Laboratories LLC
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 2 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  A copy of the GNU General Public License is available on the internet at:
//
//  http://www.gnu.org/copyleft/gpl.html
//
//==============================================================================

// Notes for the C to Java port:
// - The names of the coefficients have been changed:
//     c -> B coefficients
//     d -> A coefficients
// - The unit of the relative filter cutoff frequencies has been changed from a fraction of Pi
//   to a fraction of 2*Pi.

package org.orbisgis.protonomap.filter;

/**
* Calculates the IIR filter coefficients of a Butterworth filter.
*
* <p>
* This class is a Java port of the C source code from
* <a href="http://www.exstrom.com/journal/sigproc/">www.exstrom.com/journal/sigproc</a>.
*
* <p>
* Note that, although there is no upper limit on the filter order, if the
* bandwidth of a bandpass or bandstop filter is small, the coefficients
* returned may not give the desired response due to numerical instability
* in the calculation. For small bandwidths you should always verify the
* frequency response using a program that calculates the frequency response
* out of the calculated filter coefficients.
* (see TestIirFilterTransferPlotExstrom.java)
*/
public class IirFilterDesignExstrom {

// Dummy constructor to suppress Javadoc.
private IirFilterDesignExstrom() {}

/**
* Designs a Butterworth filter and returns the IIR filter coefficients.
*
* <p>
* The cutoff frequencies are specified relative to the sampling rate and must be
* between 0 and 0.5.<br>
* The following formula can be used to calculate the relative frequency values:
* <pre>   frequencyInHz / samplingRateInHz</pre>
*
* @param filterPassType
*    The filter pass type (lowpass, highpass, bandpass or bandstop).
* @param filterOrder
*    The filter order.
* @param fcf1
*    The relative filter cutoff frequency for lowpass/highpass, lower cutoff frequency for bandpass/bandstop.
*    This value is relative to the sampling rate (see above for more details).
* @param fcf2
*    Ignored for lowpass/highpass, the relative upper cutoff frequency for bandpass/bandstop.
*    This value is relative to the sampling rate (see above for more details).
* @return
*    The IIR filter coefficients.
*/
public static IirFilterCoefficients design (FilterPassType filterPassType, int filterOrder, double fcf1, double fcf2) {
   if (filterOrder < 1) {
      throw new IllegalArgumentException("Invalid filterOrder."); }
   if (fcf1 <= 0 || fcf1 >= 0.5) {
      throw new IllegalArgumentException("Invalid fcf1."); }
   if (filterPassType == FilterPassType.bandpass || filterPassType == FilterPassType.bandstop) {
      if (fcf2 <= 0 || fcf2 >= 0.5) {
         throw new IllegalArgumentException("Invalid fcf2."); }}
   IirFilterCoefficients coeffs = new IirFilterCoefficients();
   coeffs.a = calculateACoefficients(filterPassType, filterOrder, fcf1, fcf2);
   double[] bUnscaled = calculateBCoefficients(filterPassType, filterOrder, fcf1, fcf2);
   double scalingFactor = calculateScalingFactor(filterPassType, filterOrder, fcf1, fcf2);
   coeffs.b = ArrayUtils.multiply(bUnscaled, scalingFactor);
   return coeffs; }

//--- A coefficients -----------------------------------------------------------

private static double[] calculateACoefficients (FilterPassType filterPassType, int filterOrder, double fcf1, double fcf2) {
   switch (filterPassType) {
      case lowpass: case highpass:
         return calculateACoefficients_lp_hp(filterOrder, fcf1);
      case bandpass: case bandstop:
         return calculateACoefficients_bp_bs(filterPassType, filterOrder, fcf1, fcf2);
      default:
         throw new AssertionError(); }}

// Calculates the A coefficients for a lowpass or highpass filter.
private static double[] calculateACoefficients_lp_hp (int n, double fcf) {
   double[] rcof = new double[2 * n];                      // binomial coefficients
   double theta = 2 * Math.PI * fcf;
   double st = Math.sin(theta);
   double ct = Math.cos(theta);

   for (int k = 0; k < n; k++) {
      double parg = Math.PI * (2 * k + 1) / (2 * n);       // pole angle
      double sparg = Math.sin(parg);
      double cparg = Math.cos(parg);
      double a = 1 + st * sparg;
      rcof[2 * k] = -ct / a;
      rcof[2 * k + 1] = -st * cparg / a; }

   double[] wcof = binomial_mult(n, rcof);

   double[] dcof = new double[n + 1];
   dcof[0] = 1;
   for (int k = 1; k <= n; k++) {
      dcof[k] = wcof[2 * k - 2]; }
   return dcof; }

// Calculates the A coefficients for a bandpass or bandstop filter.
private static double[] calculateACoefficients_bp_bs (FilterPassType filterPassType, int n, double f1f, double f2f) {
   double cp = Math.cos(2 * Math.PI * (f2f + f1f) / 2);
   double theta = 2 * Math.PI * (f2f - f1f) / 2;
   double st = Math.sin(theta);
   double ct = Math.cos(theta);
   double s2t = 2 * st * ct;                               // sine of 2*theta
   double c2t = 2 * ct * ct - 1;                           // cosine of 2*theta

   double[] rcof = new double[2 * n];                      // z^-2 coefficients
   double[] tcof = new double[2 * n];                      // z^-1 coefficients
   double flip = (filterPassType == FilterPassType.bandstop) ? -1 : 1;

   for (int k = 0; k < n; k++) {
      double parg = Math.PI * (2 * k + 1) / (2 * n);       // pole angle
      double sparg = Math.sin(parg);
      double cparg = Math.cos(parg);
      double a = 1 + s2t * sparg;
      rcof[2 * k]     = c2t / a;
      rcof[2 * k + 1] = s2t * cparg / a * flip;
      tcof[2 * k]     = -2 * cp * (ct + st * sparg) / a;
      tcof[2 * k + 1] = -2 * cp * st * cparg / a * flip; }

   double[] wcof = trinomial_mult(n, tcof, rcof);

   double[] dcof = new double[2 * n + 1];
   dcof[0] = 1;
   for (int k = 1; k <= 2 * n; k++) {
      dcof[k] = wcof[2 * k - 2]; }
   return dcof; }

//--- B coefficients -----------------------------------------------------------

// Calculates the B coefficients.
private static double[] calculateBCoefficients (FilterPassType filterPassType, int filterOrder, double fcf1, double fcf2) {
   switch (filterPassType) {
      case lowpass: {
         int[] a = calculateBCoefficients_lp(filterOrder);
         return ArrayUtils.toDouble(a); }
      case highpass: {
         int[] a = calculateBCoefficients_hp(filterOrder);
         return ArrayUtils.toDouble(a); }
      case bandpass: {
         int[] a = calculateBCoefficients_bp(filterOrder);
         return ArrayUtils.toDouble(a); }
      case bandstop: {
         return calculateBCoefficients_bs(filterOrder, fcf1, fcf2); }
      default:
         throw new AssertionError(); }}

// Calculates the B coefficients for a lowpass filter.
private static int[] calculateBCoefficients_lp (int n) {
   int[] ccof = new int[n + 1];
   ccof[0] = 1;
   ccof[1] = n;
   int m = n / 2;
   for (int i = 2; i <= m; i++) {
      ccof[i] = (n-i+1) * ccof[i-1] / i;
      ccof[n-i] = ccof[i]; }
   ccof[n-1] = n;
   ccof[n] = 1;
   return ccof; }

// Calculates the B coefficients for a highpass filter.
private static int[] calculateBCoefficients_hp (int n) {
   int[] ccof = calculateBCoefficients_lp(n);
   for (int i = 1; i <= n; i += 2) {
      ccof[i] = -ccof[i]; }
   return ccof; }

// Calculates the B coefficients for a bandpass filter.
private static int[] calculateBCoefficients_bp (int n) {
   int[] tcof = calculateBCoefficients_hp(n);
   int[] ccof = new int[2 * n + 1];
   for (int i = 0; i < n; i++) {
      ccof[2*i] = tcof[i];
      ccof[2*i+1] = 0; }
   ccof[2*n] = tcof[n];
   return ccof; }

// Calculates the B coefficients for a bandstop filter.
private static double[] calculateBCoefficients_bs (int n, double f1f, double f2f) {
   double alpha = -2 * Math.cos(2 * Math.PI * (f2f + f1f) / 2) / Math.cos(2 * Math.PI * (f2f - f1f) / 2);
   double[] ccof = new double[2 * n + 1];
   ccof[0] = 1;
   ccof[1] = alpha;
   ccof[2] = 1;
   for (int i = 1; i < n; i++) {
      ccof[2 * i + 2] += ccof[2 * i];
      for (int j = 2 * i; j > 1; j--) {
         ccof[j + 1] += alpha * ccof[j] + ccof[j - 1]; }
      ccof[2] += alpha * ccof[1] + 1;
      ccof[1] += alpha; }
   return ccof; }

//--- Scaling factor -----------------------------------------------------------

// Calculates the scaling factor.
// The scaling factor is what the B coefficients must be multiplied by so
// that the filter response has a maximum value of 1.
private static double calculateScalingFactor (FilterPassType filterPassType, int filterOrder, double fcf1, double fcf2) {
   switch (filterPassType) {
      case lowpass: case highpass:
         return calculateScalingFactor_lp_hp(filterPassType, filterOrder, fcf1);
      case bandpass: case bandstop:
         return calculateScalingFactor_bp_bs(filterPassType, filterOrder, fcf1, fcf2);
      default:
         throw new AssertionError(); }}

// Calculates the scaling factor for a lowpass or highpass filter.
private static double calculateScalingFactor_lp_hp (FilterPassType filterPassType, int n, double fcf) {
   double omega = 2 * Math.PI * fcf;
   double fomega = Math.sin(omega);
   double parg0 = Math.PI / (2 * n);                       // zeroth pole angle
   int m = n / 2;
   double sf = 1;                                          // scaling factor
   for (int k = 0; k < n/2; k++) {
      sf *= 1 + fomega * Math.sin((2 * k + 1) * parg0); }
   double fomega2;
   switch (filterPassType) {
      case lowpass: {
         fomega2 = Math.sin(omega / 2);
         if (n % 2 != 0) {
            sf *= fomega2 + Math.cos(omega / 2); }
         break; }
      case highpass: {
         fomega2 = Math.cos(omega / 2);
         if (n % 2 != 0) {
            sf *= fomega2 + Math.sin(omega / 2); }
         break; }
      default:
         throw new AssertionError(); }
   sf = Math.pow(fomega2, n) / sf;
   return sf; }

// Calculates the scaling factor for a bandpass or bandstop filter.
private static double calculateScalingFactor_bp_bs (FilterPassType filterPassType, int n, double f1f, double f2f) {
   double tt = Math.tan(2 * Math.PI * (f2f - f1f) / 2);    // tangent of theta
   double ctt_tt = (filterPassType == FilterPassType.bandpass) ? 1 / tt : tt; // cotangent or tangent of theta
   double sfr = 1;                                         // real part of the scaling factor
   double sfi = 0;                                         // imaginary part of the scaling factor
   for (int k = 0; k < n; k++) {
      double parg = Math.PI * (2 * k + 1) / (2 * n);       // pole angle
      double sparg = ctt_tt + Math.sin(parg);
      double cparg = Math.cos(parg);
      double a = (sfr + sfi) * (sparg - cparg);
      double b = sfr * sparg;
      double c = -sfi * cparg;
      sfr = b - c;
      sfi = a - b - c; }
   return 1 / sfr; }

//------------------------------------------------------------------------------

// Multiplies a series of binomials together and returns the coefficients of
// the resulting polynomial.
//
// The multiplication has the following form:
//
// (x + p[0]) * (x + p[1]) * ... * (x + p[n-1])
//
// The p[i] coefficients are assumed to be complex and are passed to the
// function as an array of doubles of length 2n.
//
// The resulting polynomial has the following form:
//
// x^n + a[0]*x^n-1 + a[1]*x^n-2 + ... +a[n-2]*x + a[n-1]
//
// The a[i] coefficients can in general be complex but should in most
// cases turn out to be real. The a[i] coefficients are returned by the
// function as an array of doubles of length 2n.
//
// Function arguments:
//  n  The number of binomials to multiply
//  p  Array of doubles where p[2i] (i=0...n-1) is assumed to be the
//     real part of the coefficient of the ith binomial and p[2i+1]
//     is assumed to be the imaginary part. The overall size of the
//     array is then 2n.
private static double[] binomial_mult (int n, double[] p) {
   double[] a = new double[2 * n];
   for (int i = 0; i < n; i++) {
      for (int j = i; j > 0; j--) {
         a[2*j] += p[2*i] * a[2*(j-1)] - p[2*i+1] * a[2*(j-1)+1];
         a[2*j+1] += p[2*i] * a[2*(j-1)+1] + p[2*i+1] * a[2*(j-1)]; }
      a[0] += p[2*i];
      a[1] += p[2*i+1]; }
   return a; }

// Multiplies a series of trinomials together and returns the coefficients
// of the resulting polynomial.
//
// The multiplication has the following form:
//
// (x^2 + b[0]x + c[0]) * (x^2 + b[1]x + c[1]) * ... * (x^2 + b[n-1]x + c[n-1])
//
// The b[i] and c[i] coefficients are assumed to be complex and are passed
// to the function as arrays of doubles of length 2n. The real part of the
// coefficients are stored in the even numbered elements of the array and
// the imaginary parts are stored in the odd numbered elements.
//
// The resulting polynomial has the following form:
//
// x^2n + a[0]*x^2n-1 + a[1]*x^2n-2 + ... +a[2n-2]*x + a[2n-1]
//
// The a[i] coefficients can in general be complex but should in most cases
// turn out to be real. The a[i] coefficients are returned by the function as
// an array of doubles of length 4n. The real and imaginary parts are stored,
// respectively, in the even and odd elements of the array.
//
// Function arguments:
//  n  The number of trinomials to multiply
//  b  An array of doubles of length 2n.
//  c  An array of doubles of length 2n.
private static double[] trinomial_mult (int n, double[] b, double[] c) {
   double[] a = new double[4 * n];

   a[2] = c[0];
   a[3] = c[1];
   a[0] = b[0];
   a[1] = b[1];

   for (int i = 1; i < n; i++) {
      a[2*(2*i+1)]   += c[2*i]*a[2*(2*i-1)]   - c[2*i+1]*a[2*(2*i-1)+1];
      a[2*(2*i+1)+1] += c[2*i]*a[2*(2*i-1)+1] + c[2*i+1]*a[2*(2*i-1)];

      for (int j = 2 * i; j > 1; j--) {
         a[2*j]   += b[2*i] * a[2*(j-1)]   - b[2*i+1] * a[2*(j-1)+1] + c[2*i] * a[2*(j-2)]   - c[2*i+1] * a[2*(j-2)+1];
         a[2*j+1] += b[2*i] * a[2*(j-1)+1] + b[2*i+1] * a[2*(j-1)]   + c[2*i] * a[2*(j-2)+1] + c[2*i+1] * a[2*(j-2)]; }

      a[2] += b[2*i] * a[0] - b[2*i+1] * a[1] + c[2*i];
      a[3] += b[2*i] * a[1] + b[2*i+1] * a[0] + c[2*i+1];
      a[0] += b[2*i];
      a[1] += b[2*i+1]; }

   return a; }

}
