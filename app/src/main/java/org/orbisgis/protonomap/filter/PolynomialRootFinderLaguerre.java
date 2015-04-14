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
//==============================================================================
//
// This module is based on source code by Anthony J. Fisher, University of York,
// which in turn is based on info or code from a "Numerical Recipes" book.
//
// The original C source code by Tony Fisher in zroots.c has no explicit license
// amd the file is no longer available in the Internet.
// Tony Fisher died on February 2000. We can assume that his intention was to make his
// source code freely available.
// See also: http://sk3w.se/svx/deb_new/svxlink-11.03/async/audio/fidmkf.h

package org.orbisgis.protonomap.filter;


/**
* A root finder for polynomials with complex coefficients.
* It uses Laguerre's method to find the zeros of a polynomial.
*
* <p>
* This class is based on C source code by Anthony J. Fisher.
*
* <p>
* Reference: <a href="http://en.wikipedia.org/wiki/Laguerre%27s_method">Wikipedia</a>.
*/
public class PolynomialRootFinderLaguerre {

private static final double EPSS = 1E-14;

// Dummy constructor to suppress Javadoc.
private PolynomialRootFinderLaguerre() {}

/**
* Finds the zeros of a real polynomial.
* The coefficients are copied into a complex array and then the complex <code>findRoots()</code> is called.
*
* @param coeffs
*    The polynomial coefficients in order of decreasing powers.
* @return
*    The zeros of the polynomial.
*/
public static Complex[] findRoots (double[] coeffs) {
   return findRoots(ArrayUtils.toComplex(coeffs)); }

/**
* Finds the zeros of a complex polynomial.
*
* <p>
* The polynomial has the form:
* <code> coeffs[0] * x^n + coeffs[1] * x^(n-1) + ... coeffs[n-1] * x + coeffs[n] </code>
*
* @param coeffs
*    The polynomial coefficients in order of decreasing powers.
* @return
*    The zeros of the polynomial.
*/
public static Complex[] findRoots (Complex[] coeffs) {
   int n = coeffs.length - 1;
   Complex[] zeros = new Complex[n];
   Complex[] a = coeffs;
   for (int i = 0; i < n; i++) {
      Complex zero;
      int ctr = 0;
      while (true) {
         Complex startX = (ctr == 0) ? new Complex(0) : randomStart();
         zero = laguer(a, startX);
         if (zero != null) {
            break; }
         if (ctr++ > 1000) {
            throw new RuntimeException("Root finding aborted in random loop."); }}
      zeros[i] = zero;
      a = PolynomialUtils.deflate(a, zero, 0); }
   // Polish the roots found in the first pass.
   for (int i = 0; i < n; i++) {
      zeros[i] = laguer(coeffs, zeros[i]);
      if (zeros[i] == null) {
         throw new RuntimeException("Polish failed."); }}
  return zeros; }

private static Complex laguer (Complex[] a, Complex startX) {
   int n = a.length - 1;
   Complex cn = new Complex(n);
   Complex x = startX;
   for (int iter = 0; iter < 80; iter++) {
      Complex b = a[0];
      double err = b.abs();
      Complex d = Complex.ZERO;
      Complex f = Complex.ZERO;
      double absX = x.abs();
      for (int i = 1; i <= n; i++) {
         f = x.mul(f).add(d);
         d = x.mul(d).add(b);
         b = x.mul(b).add(a[i]);
         err = b.abs() + absX * err; }
      err *= EPSS;
      if (b.abs() <= err) {
         return x; }
      Complex g = d.div(b);
      Complex g2 = g.mul(g);
      Complex h = g2.sub(Complex.TWO.mul(f.div(b)));
      Complex sq = cn.sub(Complex.ONE).mul( cn.mul(h).sub(g2) ).sqrt();
      Complex gp = g.add(sq);
      Complex gm = g.sub(sq);
      double abp = gp.abs();
      double abm = gm.abs();
      if (abp < abm) {
         gp = gm; }
      Complex dx;
      if (abp > 0 || abm > 0) {
         dx = cn.div(gp); }
       else {
         dx = new Complex(Math.log(1 + absX), iter + 1).exp(); }
      x = x.sub(dx); }
   return null; }                                          // too many iterations

private static Complex randomStart() {
   return new Complex(Math.random() * 2 - 1, Math.random() * 2 - 1); }

}
