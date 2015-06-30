// This is a Java port of C source code by Anthony J. Fisher.
//
// Copyright notice for the Java port:
//
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
//
//------------------------------------------------------------------------------
//
// This module is based on mkfilter.c by Anthony J. Fisher, University of York, September 1992.
// http://www-users.cs.york.ac.uk/~fisher/
//
// The original C source code in mkfilter.c has no explicit license,
// nor were there any licence terms indicated on the website.
// (see http://web.archive.org/web/20000815222724/http://www-users.cs.york.ac.uk/~fisher/)
// Tony Fisher died on February 2000. We can assume that his intention was to make his
// source code freely available.
// See also: http://sk3w.se/svx/deb_new/svxlink-11.03/async/audio/fidmkf.h

package org.orbisgis.protonomap.filter;

/**
* Calculates the coefficients of an IIR filter.
*
* <p>
* This class is based on a Java port of C source code by Anthony J. Fisher.
*/
public class IirFilterDesignFisher {

private static class PolesAndZeros {
   public Complex[]          poles;
   public Complex[]          zeros; }

// Dummy constructor to suppress Javadoc.
private IirFilterDesignFisher() {}

/**
* Returns the s-plane poles for a prototype LP filter.
*
* <p>
* Wikipedia reference:
*  <a href="http://en.wikipedia.org/wiki/Prototype_filter">Prototype filter"</a>
*
* @param filterCharacteristicsType
*    The filter characteristics type.
*    The following filters are implemented: Butterworth, Chebyshev (type 1), Bessel.
* @param filterOrder
*    The filter order.
* @param ripple
*    Passband ripple in dB. Must be negative. Only used for Chebyshev filter, ignored for other filters.
* @return
*    The s-plane poles.
*/
private static Complex[] getPoles (FilterCharacteristicsType filterCharacteristicsType, int filterOrder, double ripple) {
   switch (filterCharacteristicsType) {
      case bessel: {
         Complex[] poles = BesselFilterDesign.computePoles(filterOrder);
         // poles = ArrayUtils.sortByAbsImNegImRe(poles);
            // Sorting has no influence on the IIR filter coefficients, but it ensures that we get the same
            // intermediate results as in the original version.
         return poles; }
      case butterworth: {
         Complex[] poles = new Complex[filterOrder];
         for (int i = 0; i < filterOrder; i++) {
            double theta = (filterOrder / 2.0 + 0.5 + i) * Math.PI / filterOrder;
            poles[i] = Complex.expj(theta); }
         return poles; }
      case chebyshev: {
         if (ripple >= 0.0) {
            throw new IllegalArgumentException("Chebyshev ripple must be negative."); }
         Complex[] poles = getPoles(FilterCharacteristicsType.butterworth, filterOrder, 0);
         double rip = Math.pow(10, -ripple / 10);          // ( "/10" is correct here, because we need the square)
         double eps = Math.sqrt(rip - 1);
         double y = asinh(1.0 / eps) / filterOrder;
         if (y <= 0) {
            throw new AssertionError(); }
         double sinhY = Math.sinh(y);
         double coshY = Math.cosh(y);
         for (int i = 0; i < filterOrder; i++) {
            poles[i] = new Complex(poles[i].re() * sinhY, poles[i].im() * coshY); }
         return poles; }
      default:
         throw new UnsupportedOperationException("Filter characteristics type " + filterCharacteristicsType + " not yet implemented."); }}

/**
* Transforms the s-plane poles of the prototype filter into the s-plane poles and zeros
* for a filter with the specified pass type and cutoff frequencies.
*
* <p>
* The cutoff frequencies are specified relative to the sampling rate and must be
* between 0 and 0.5.
*
* @param poles
*    The s-plane poles of the prototype LP filter.
* @param filterPassType
*    The filter pass type (Lowpass, highpass, bandpass, bandstop).
* @param fcf1
*    The relative filter cutoff frequency for lowpass/highpass, lower cutoff frequency for bandpass/bandstop.
* @param fcf2
*    Ignored for lowpass/highpass, the relative upper cutoff frequency for bandpass/bandstop,
* @param preWarp
*    <code>true</code> to enable prewarping of the cutoff frequencies (for a later bilinear transform from s-plane to z-plane),
*    <code>false</code> to skip prewarping (for a later matched Z-transform).
* @return
*    The s-plane poles and zeros for the specific filter.
*/
private static PolesAndZeros normalize (Complex[] poles, FilterPassType filterPassType, double fcf1, double fcf2, boolean preWarp) {
   int n = poles.length;
   boolean fcf2IsRelevant = filterPassType == FilterPassType.bandpass || filterPassType == FilterPassType.bandstop;
   if (fcf1 <= 0 || fcf1 >= 0.5) {
      throw new IllegalArgumentException("Invalid fcf1."); }
   if (fcf2IsRelevant && (fcf2 <= 0 || fcf2 >= 0.5)) {
      throw new IllegalArgumentException("Invalid fcf2."); }
   double fcf1Warped = Math.tan(Math.PI * fcf1) / Math.PI;
   double fcf2Warped = fcf2IsRelevant ? Math.tan(Math.PI * fcf2) / Math.PI : 0;
   double w1 = 2 * Math.PI * (preWarp ? fcf1Warped : fcf1);
   double w2 = 2 * Math.PI * (preWarp ? fcf2Warped : fcf2);
   switch (filterPassType) {
      case lowpass: {
         PolesAndZeros sPlane = new PolesAndZeros();
         sPlane.poles = ArrayUtils.multiply(poles, w1);
         sPlane.zeros = new Complex[0];                    // no zeros
         return sPlane; }
      case highpass: {
         PolesAndZeros sPlane = new PolesAndZeros();
         sPlane.poles = new Complex[n];
         for (int i = 0; i < n; i++) {
            sPlane.poles[i] = Complex.div(w1, poles[i]); }
         sPlane.zeros = ArrayUtils.zeros(n);               // n zeros at (0, 0)
         return sPlane; }
      case bandpass: {
         double w0 = Math.sqrt(w1 * w2);
         double bw = w2 - w1;
         PolesAndZeros sPlane = new PolesAndZeros();
         sPlane.poles = new Complex[2 * n];
         for (int i = 0; i < n; i++) {
            Complex hba = poles[i].mul(bw / 2);
            Complex temp = Complex.sub(1, Complex.div(w0, hba).sqr()).sqrt();
            sPlane.poles[i]     = hba.mul(temp.add(1));
            sPlane.poles[n + i] = hba.mul(Complex.sub(1, temp)); }
         sPlane.zeros = ArrayUtils.zeros(n);               // n zeros at (0, 0)
         return sPlane; }
     case bandstop: {
         double w0 = Math.sqrt(w1 * w2);
         double bw = w2 - w1;
         PolesAndZeros sPlane = new PolesAndZeros();
         sPlane.poles = new Complex[2 * n];
         for (int i = 0; i < n; i++) {
            Complex hba = Complex.div(bw / 2, poles[i]);
            Complex temp = Complex.sub(1, Complex.div(w0, hba).sqr()).sqrt();
            sPlane.poles[i]     = hba.mul(temp.add(1));
            sPlane.poles[n + i] = hba.mul(Complex.sub(1, temp)); }
         sPlane.zeros = new Complex[2 * n];
         for (int i = 0; i < n; i++) {                     // 2n zeros at (0, +-w0)
            sPlane.zeros[i]     = new Complex(0,  w0);
            sPlane.zeros[n + i] = new Complex(0, -w0); }
         return sPlane; }
      default:
         throw new UnsupportedOperationException("Filter pass type " + filterPassType + " not yet implemented."); }}

private enum SToZMappingMethod {
   bilinearTransform, matchedZTransform }

/**
* Maps the poles and zeros from the s-plane to the z-plane.
*
* <p>
* Wikipedia references:
*  <a href="http://en.wikipedia.org/wiki/Bilinear_transform">Bilinear transform"</a>
*  <a href="http://en.wikipedia.org/wiki/Matched_Z-transform_method">Matched Z-transform method</a>
*
* @param sPlane
*    Poles and zeros of the s-plane.
* @param SToZMappingMethod
*    The mapping method to be used.
* @return
*    Poles and zeros of the z-plane.
*/
private static PolesAndZeros MapSPlaneToZPlane (PolesAndZeros sPlane, SToZMappingMethod sToZMappingMethod) {
   switch (sToZMappingMethod) {
      case bilinearTransform: {
         PolesAndZeros zPlane = new PolesAndZeros();
         zPlane.poles = doBilinearTransform(sPlane.poles);
         Complex[] a = doBilinearTransform(sPlane.zeros);
         zPlane.zeros = extend(a, sPlane.poles.length, new Complex(-1));  // extend zeros with -1 to the number of poles
         return zPlane; }
      case matchedZTransform: {
         PolesAndZeros zPlane = new PolesAndZeros();
         zPlane.poles = doMatchedZTransform(sPlane.poles);
         zPlane.zeros = doMatchedZTransform(sPlane.zeros);
         return zPlane; }
      default:
         throw new UnsupportedOperationException("Mapping method " + sToZMappingMethod + " not yet implemented."); }}

private static Complex[] doBilinearTransform (Complex[] a) {
   Complex[] a2 = new Complex[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = doBilinearTransform(a[i]); }
   return a2; }

private static Complex doBilinearTransform (Complex x) {
   return x.add(2).div(Complex.sub(2, x)); }

private static Complex[] extend (Complex[] a, int n, Complex fill) {
   if (a.length >= n) {
      return a; }
   Complex[] a2 = new Complex[n];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i]; }
   for (int i = a.length; i < n; i++) {
      a2[i] = fill; }
   return a2; }

private static Complex[] doMatchedZTransform (Complex[] a) {
   Complex[] a2 = new Complex[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i].exp(); }
   return a2; }

/**
* Given the z-plane poles and zeros, compute the rational fraction (the top and bottom polynomial
* coefficients) of the filter transfer function in Z.
*
* @param zPlane
*    The poles and zeros of the z-plane.
* @return
*    Coefficients of the rational fraction of the Z transfer function.
*/
private static PolynomialUtils.RationalFraction computeTransferFunction (PolesAndZeros zPlane) {
   Complex[] topCoeffsComplex    = PolynomialUtils.expand(zPlane.zeros);
   Complex[] bottomCoeffsComplex = PolynomialUtils.expand(zPlane.poles);
   final double eps = 1E-10;
   PolynomialUtils.RationalFraction tf = new PolynomialUtils.RationalFraction();
   tf.top    = ArrayUtils.toDouble(topCoeffsComplex,    eps);
   tf.bottom = ArrayUtils.toDouble(bottomCoeffsComplex, eps);
      // If the toDouble() conversion fails because the coffficients are not real numbers, the poles
      // and zeros are not complex conjugates.
   return tf; }

/**
* Computes the filter gain for a filter transfer function in Z.
*
* @param tf
*    Coefficients of the filter transfer function in Z.
* @param filterPassType
*    The filter pass type (Lowpass, highpass, bandpass, bandstop).
* @param fcf1
*    The relative lower cutoff frequency for bandpass. Ignored for other filter pass types.
* @param fcf2
*    the relative upper cutoff frequency for bandpass. Ignored for other filter pass types.
* @return
*    For lowpass: The gain at DC.
*    For highpass: The gain at samplingRate/2.
*    For bandpass: The gain at the center frequency.
*    For bandstop: sqrt( [gain at DC] * [gain at samplingRate/2] )
*/
private static double computeGain (PolynomialUtils.RationalFraction tf, FilterPassType filterPassType, double fcf1, double fcf2) {
   switch (filterPassType) {
      case lowpass: {
         return computeGainAt(tf, Complex.ONE); }          // DC gain
      case highpass: {
         return computeGainAt(tf, new Complex(-1)); }      // gain at Nyquist frequency
      case bandpass: {
         double centerFreq = (fcf1 + fcf2) / 2;
         Complex w = Complex.expj(2 * Math.PI * centerFreq);
         return computeGainAt(tf, w); }                    // gain at center frequency
      case bandstop: {
         double dcGain = computeGainAt(tf, Complex.ONE);
         double hfGain = computeGainAt(tf, new Complex(-1));
         return Math.sqrt(dcGain * hfGain); }
      default: {
         throw new RuntimeException("Unsupported filter pass type."); }}}

private static double computeGainAt (PolynomialUtils.RationalFraction tf, Complex w) {
   return PolynomialUtils.evaluate(tf, w).abs(); }

/**
* Returns the IIR filter coefficients for a transfer function.
* Normalizes the coefficients so that a[0] is 1.
*
* @param tf
*    Coefficients of the filter transfer function in Z.
* @return
*    The IIR filter coefficients (A and B coefficients).
*/
private static IirFilterCoefficients computeIirFilterCoefficients (PolynomialUtils.RationalFraction tf) {
   // Note that compared to the original C code by Tony Fisher the order of the A/B coefficients
   // is reverse and the A coefficients are negated.
   double scale = tf.bottom[0];
   IirFilterCoefficients coeffs = new IirFilterCoefficients();
   coeffs.a = ArrayUtils.divide(tf.bottom, scale);
   coeffs.a[0] = 1;                                        // to ensure that it's exactly 1
   coeffs.b = ArrayUtils.divide(tf.top, scale);
   return coeffs; }

/**
* Designs an IIR filter and returns the IIR filter coefficients.
*
* <p>
* The cutoff frequencies are specified relative to the sampling rate and must be
* between 0 and 0.5.<br>
* The following formula can be used to calculate the relative frequency values:
* <pre>   frequencyInHz / samplingRateInHz</pre>
*
* <p>
* For Bessel filters, matched Z-transform is used to design the filter.
*
* @param filterPassType
*    The filter pass type (Lowpass, highpass, bandpass, bandstop).
* @param filterCharacteristicsType
*    The filter characteristics type.
*    The following filters are implemented: Butterworth, Chebyshev (type 1), Bessel.
* @param filterOrder
*    The filter order.
* @param ripple
*    Passband ripple in dB. Must be negative. Only used for Chebyshev filter, ignored for other filters.
* @param fcf1
*    The relative filter cutoff frequency for lowpass/highpass, lower cutoff frequency for bandpass/bandstop.
*    This value is relative to the sampling rate (see above for more details).
* @param fcf2
*    Ignored for lowpass/highpass, the relative upper cutoff frequency for bandpass/bandstop,
*    This value is relative to the sampling rate (see above for more details).
* @return
*    The IIR filter coefficients.
*/
public static IirFilterCoefficients design (FilterPassType filterPassType, FilterCharacteristicsType filterCharacteristicsType,
      int filterOrder, double ripple, double fcf1, double fcf2) {
   Complex[] poles = getPoles(filterCharacteristicsType, filterOrder, ripple);
   SToZMappingMethod sToZMappingMethod = (filterCharacteristicsType == FilterCharacteristicsType.bessel) ? SToZMappingMethod.matchedZTransform : SToZMappingMethod.bilinearTransform;
   boolean preWarp = sToZMappingMethod == SToZMappingMethod.bilinearTransform;
   PolesAndZeros sPlane = normalize(poles, filterPassType, fcf1, fcf2, preWarp);
   // System.out.println(ArrayUtils.toString(sPlane.poles));
   PolesAndZeros zPlane = MapSPlaneToZPlane(sPlane, sToZMappingMethod);
   // System.out.println(ArrayUtils.toString(zPlane.zeros));
   // System.out.println(ArrayUtils.toString(zPlane.poles));
   PolynomialUtils.RationalFraction tf = computeTransferFunction(zPlane);
   double gain = computeGain(tf, filterPassType, fcf1, fcf2);
   // System.out.println("gain=" + gain);
   IirFilterCoefficients coeffs = computeIirFilterCoefficients(tf);
   // System.out.println(ArrayUtils.toString(coeffs.a));
   // System.out.println(ArrayUtils.toString(coeffs.b));
   coeffs.b = ArrayUtils.divide(coeffs.b, gain);           // gain normalization
   return coeffs; }

private static double asinh (double x) {
   return Math.log(x + Math.sqrt(1 + x * x)); }

}
