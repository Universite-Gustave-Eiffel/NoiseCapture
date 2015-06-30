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
* Plynomial utility routines.
*
* <p>
* In this module, polynomial coefficients are stored in arrays ordered in descending powers.
* When an awway a[] contains the coefficients, the polynomial has the following form:<br>
* <code> a[0] * x^n + a[1] * x^(n-1) + ... a[n-1] * x + a[n] </code>
*
*/
public class PolynomialUtils {

// Dummy constructor to suppress Javadoc.
private PolynomialUtils() {}

/**
* Structure for the coefficients of a rational fraction (a fraction of two polynomials) with real coefficients.
*
* <p>
* Reference:
* <a href="http://en.wikipedia.org/wiki/Algebraic_fraction#Rational_fractions">Wikipedia</a>
*/
public static class RationalFraction {
   public double[]           top;                          // top polynomial coefficients
   public double[]           bottom; }                     // bottom polynomial coefficients

/**
* Computes the value of a polynomial with real coefficients.
*
* @param a
*    The coefficients of the polynomial, ordered in descending powers.
* @param x
*    The x value for which the polynomial is to be evaluated.
* @return
*    The result.
*/
public static Complex evaluate (double[] a, Complex x) {
   if (a.length == 0) {
      throw new IllegalArgumentException(); }
   Complex sum = new Complex(a[0]);
   for (int i = 1; i < a.length; i++) {
      sum = sum.mul(x).add(a[i]); }
   return sum; }

/**
* Computes the value of a rational fraction.
* Returns <code>evaluate(f.top, x) / evaluate(f.bottom, x)</code>.
*/
public static Complex evaluate (RationalFraction f, Complex x) {
   Complex v1 = evaluate(f.top, x);
   Complex v2 = evaluate(f.bottom, x);
   return v1.div(v2); }

/**
* Multiplies two polynomials with real coefficients.
*/
public static double[] multiply (double[] a1, double[] a2) {
   int n1 = a1.length - 1;
   int n2 = a2.length - 1;
   int n3 = n1 + n2;
   double[] a3 = new double[n3 + 1];
   for (int i = 0; i <= n3; i++) {
      double t = 0;
      int p1 = Math.max(0, i - n2);
      int p2 = Math.min(n1, i);
      for (int j = p1; j <= p2; j++) {
         t += a1[n1 - j] * a2[n2 - i + j]; }
      a3[n3 - i] = t; }
   return a3; }

/**
* Multiplies two polynomials with complex coefficients.
*/
public static Complex[] multiply (Complex[] a1, Complex[] a2) {
   int n1 = a1.length - 1;
   int n2 = a2.length - 1;
   int n3 = n1 + n2;
   Complex[] a3 = new Complex[n3 + 1];
   for (int i = 0; i <= n3; i++) {
      Complex t = Complex.ZERO;
      int p1 = Math.max(0, i - n2);
      int p2 = Math.min(n1, i);
      for (int j = p1; j <= p2; j++) {
         t = t.add( a1[n1 - j].mul( a2[n2 - i + j] )); }
      a3[n3 - i] = t; }
   return a3; }

/**
* Forward deflation of a polynomial with a known zero.
* Divides the polynomial with coefficients <code>a[]</code> by <code>(x - z)</code>,
* where <code>z</code> is a zero of the polynomial.
*
* @param a
*    Coefficients of the polynomial.
* @param z
*    A known zero of the polynomial
* @param eps
*    The maximum value allowed for the absolute real and imaginary parts of the remainder, or 0 to ignore the reminder.
* @return
*    The coefficients of the resulting polynomial.
*/
public static Complex[] deflate (Complex[] a, Complex z, double eps) {
   int n = a.length - 1;
   Complex[] a2 = new Complex[n];
   a2[0] = a[0];
   for (int i = 1; i < n; i++) {
      a2[i] = z.mul(a2[i - 1]).add(a[i]); }
   Complex remainder = z.mul(a2[n - 1]).add(a[n]);
   if (eps > 0 && (Math.abs(remainder.re()) > eps || Math.abs(remainder.im()) > eps)) {
      throw new RuntimeException("Polynom deflatation failed, remainder = " + remainder + "."); }
   return a2; }

/**
* Computes the coefficients of a polynomial from it's complex zeros.
*
* @param zeros
*    The zeros of the polynomial.
*    The polynomial formula is:
*    <code> (x - zero[0]) * (x - zero[1]) * ... (x - zero[n - 1])
* @return
*    The coefficients of the expanded polynomial, ordered in descending powers.
*/
public static Complex[] expand (Complex[] zeros) {
   int n = zeros.length;
   if (n == 0) {
      return new Complex[]{Complex.ONE}; }
   Complex[] a = new Complex[]{Complex.ONE, zeros[0].neg()};   // start with (x - zeros[0])
   for (int i = 1; i < n; i++) {
      Complex[] a2 = new Complex[]{Complex.ONE, zeros[i].neg()};
      a = multiply(a, a2); }                                   // multiply factor (x - zeros[i]) into coefficients
   return a; }

}
