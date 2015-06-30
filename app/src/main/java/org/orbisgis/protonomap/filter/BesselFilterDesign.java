// Copyright 2013 Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
// www.source-code.biz, www.inventec.ch/chdh
//
// This module is multi-licensed and may be used under the terms
// of any of the following licenses:
//
//  EPL, Eclipse Public License, V1.0 or later, http://www.eclipse.org/legal
//  LGPL, GNU Lesser General Public License, V2.1 or later, http://www.gnu.org/licenses/lgpl.html
//
// Please contact the author if you need another license.
// This module is provided "as is", without warranties of any kind.

package org.orbisgis.protonomap.filter;


/**
* Bessel filter design routines.
*/
public class BesselFilterDesign {

// Dummy default constructor to suppress Javadoc.
private BesselFilterDesign() {}

/**
* Returns the polynomial coefficients for the Bessel polynomial of the given order.
*
* <p>
* Reference: <a href="http://en.wikipedia.org/wiki/Bessel_polynomials">Wikipedia</a>.
*
* @param n
*    The order of the polynomial.
* @return
*    An array <code>a[]</code> with the coefficients, ordered in descending powers, for:
*    <code> a[0] * x^n + a[1] * x^(n-1) + ... a[n-1] * x + a[n] </code>
*/
public static double[] computePolynomialCoefficients (int n) {
   double m = 1;
   for (int i = 1; i <= n; i++) {
      m = m * (n + i) / 2; }
   double[] a = new double[n + 1];
   a[0] = m;
   a[n] = 1;
   for (int i = 1; i < n; i++) {
      a[i] = a[i - 1] * 2 * (n - i + 1) / (2 * n - i + 1) / i; }
   return a; }

/**
* Evaluates the transfer function of a Bessel filter.
* The gain is normalized to 1 for DC.
*
* @param polyCoeffs
*    Coefficients of the reverse Bessel polynomial.
* @param s
*    Complex frequency.
* @return
*    A complex number that corresponds to the gain and phase of the filter output.
*/
public static Complex transferFunction (double[] polyCoeffs, Complex s) {
   PolynomialUtils.RationalFraction f = new PolynomialUtils.RationalFraction();
   f.top = new double[]{polyCoeffs[polyCoeffs.length - 1]}; // to normalize gain at DC
   f.bottom = polyCoeffs;
   return PolynomialUtils.evaluate(f, s); }

/**
* Computes the normalized gain of a Bessel filter at a given frequency.
*
* @param polyCoeffs
*    Coefficients of the reverse Bessel polynomial.
* @param w
*    Relative frequency.
* @return
*    The normalized gain.
*/
public static double computeGain (double[] polyCoeffs, double w) {
   Complex s = new Complex(0, w);
   Complex t = transferFunction(polyCoeffs, s);
   return t.abs(); }

/**
* This method uses appoximation to find the frequency for a given gain.
* It is used to find the 3dB cutoff frequency, which is then used as the scaling factor
* for the frequency normalization of the filter.
*
* <p>
* If anyone knows a better way than approximation to find the 3dB cuttoff frequency,
* please let me know (chdh@inventec.ch).
*
* @param polyCoeffs
*    Coefficients of the reverse Bessel polynomial.
* @param gain
*    A normalized gain.
* @return
*    The relative frequency.
*/
public static double findFrequencyForGain (double[] polyCoeffs, double gain) {
   final double eps = 1E-15;
   if (gain > (1 - 1E-6) || gain < 1E-6) {
      throw new IllegalArgumentException(); }
   int ctr;
   // Find starting point for lower frequency.
   double wLo = 1;                                         // wLo = lower bound of search range
   ctr = 0;
   while (computeGain(polyCoeffs, wLo) < gain) {
      wLo /= 2;
      if (ctr++ > 100) {
         throw new AssertionError(); }}
   // Find starting point for upper frequency.
   double wHi = 1;                                         // wHi = upper bound of search range
   ctr = 0;
   while (computeGain(polyCoeffs, wHi) > gain) {
      wHi *= 2;
      if (ctr++ > 100) {
         throw new AssertionError(); }}
   // Approximation loop:
   ctr = 0;
   while (true) {
      if (wHi - wLo < eps) {
         break; }
      double wm = (wHi + wLo) / 2;
      double gm = computeGain(polyCoeffs, wm);
      if (gm > gain) {
         wLo = wm; }
       else {
         wHi = wm; }
      if (ctr++ > 1000) {
         throw new AssertionError("No convergence."); }}
   return wLo; }

/**
* Returns the frequency normalization scaling factor for a Bessel filter.
* This factor is used to normalize the filter coefficients so that the
* gain at the relative frequency 1 is -3dB.
* (To be exact, we use 1/sqrt(2) instead of -3dB).
*
* @param polyCoeffs
*    Coefficients of the reverse Bessel polynomial.
* @return
*    The frequency normalization scaling factor.
*/
public static double findFrequencyScalingFactor (double[] polyCoeffs) {
   double dB3 = 1 / Math.sqrt(2);                          // about -3dB
   return findFrequencyForGain(polyCoeffs, dB3); }

/**
* Returns the frequency normalized s-plane poles for a Bessel filter.
*
* @param n
*    The filter order.
* @return
*    The complex poles of the filter,
*/
public static Complex[] computePoles (int n) {
   double[] besselPolyCoeffs = computePolynomialCoefficients(n);
   double[] polyCoeffs = ArrayUtils.reverse(besselPolyCoeffs);
   double scalingFactor = findFrequencyScalingFactor(polyCoeffs);
   Complex[] poles = PolynomialRootFinderJenkinsTraub.findRoots(polyCoeffs);
   Complex[] scaledPoles = ArrayUtils.divide(poles, scalingFactor);
   return scaledPoles; }

}
