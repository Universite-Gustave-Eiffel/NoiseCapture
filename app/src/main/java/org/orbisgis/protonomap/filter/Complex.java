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
* Complex number.
*/
public final class Complex {                               // is final to allow inlining of methods at runtime

/**
* The imaginary unit i.
*/
public static final Complex  I = new Complex(0, 1);

/**
* A Complex representing 0.
*/
public static final Complex  ZERO = new Complex(0);

/**
* A Complex representing 1.
*/
public static final Complex  ONE = new Complex(1);

/**
* A Complex representing 2.
*/
public static final Complex  TWO = new Complex(2);

/**
* A Complex representing "NaN + NaN i".
*/
public static final Complex  NaN = new Complex(Double.NaN, Double.NaN);

/**
* A Complex representing "+INF + INF i"
*/
public static final Complex  INF = new Complex(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

private final double         re;                           // real part
private final double         im;                           // imaginary part

/**
* Constructs a Complex.
*
* @param re
*    The real part.
* @param im
*    The imaginary part.
*/
public Complex (double re, double im) {
   this.re = re;
   this.im = im; }

/**
* Constructs a Complex with a real part and 0 as the imaginary part.
*
* @param re
*    The real part.
*/
public Complex (double re) {
   this(re, 0); }

/**
* Returns the real part.
*/
public double re() {
   return re; }

/**
* Returns the imaginary part.
*/
public double im() {
   return im; }

/**
* Returns the real part.
* Verifies that <code>abs(im) <= eps</code> or <code>abs(im) <= eps * abs(re)</code>.
*/
public double toDouble (double eps) {
   double absIm = Math.abs(im);
   if (absIm > eps && absIm > Math.abs(re) * eps) {
      throw new RuntimeException("The imaginary part of the complex number is not neglectable small for the conversion to a real number. re=" + re + " im=" + im + " eps=" + eps + "."); }
   return re; }

/**
* Returns <code>true</code> if the real or imaginary part in not a number.
*/
public boolean isNaN() {
   return Double.isNaN(re) || Double.isNaN(im); }

/**
* Returns <code>true</code if the real or imaginary part is infinite.
*/
public boolean isInfinite() {
   return Double.isInfinite(re) || Double.isInfinite(im); }

//--- Static operations --------------------------------------------------------

/**
* Creates a Complex of length 1 and argument <code>arg</code>.
*/
public static Complex expj (double arg) {
   return new Complex(Math.cos(arg), Math.sin(arg)); }

/**
* Creates a Complex from polar coordinates.
*/
public static Complex fromPolar (double abs, double arg) {
   return new Complex(abs * Math.cos(arg), abs * Math.sin(arg)); }

//--- Unary operations ---------------------------------------------------------

/**
* Return the absolute value (magnitude, length, radius).
*/
public double abs() {
   return Math.hypot(re, im); }

/**
* Returns the argument (angle).
*/
public double arg() {
   return Math.atan2(im, re); }

/**
* Returns the conjugate.
*/
public Complex conj() {
   return new Complex(re, -im); }

/**
* Returns the negation (<code>-this</code>).
*/
public Complex neg() {
   return new Complex(-re, -im); }

/**
* Returns the reciprocal (<code>1 / this</code>, multiplicative inverse).
*/
public Complex reciprocal() {
   if (isNaN()) {
      return NaN; }
   if (isInfinite()) {
      return new Complex(0, 0); }
   double scale = re * re + im * im;
   if (scale == 0) {
      return INF; }
   return new Complex(re / scale, -im / scale); }

/**
* Returns the exponential function.
* (The Euler's number e raised to the power of this complex number).
*/
public Complex exp() {
   return fromPolar(Math.exp(re), im); }

/**
* Returns the natural logarithm (base e).
*/
public Complex log() {
   return new Complex(Math.log(abs()), arg()); }

/**
* Returns the square.
*/
public Complex sqr() {
   return new Complex(re * re - im * im, 2 * re * im); }

/**
* Returns one of the two square roots.
*/
public Complex sqrt() {
   if (re == 0 && im == 0) {
      return new Complex(0, 0); }
   double m = abs();
   return new Complex(Math.sqrt((m + re) / 2), Math.copySign(1, im) * Math.sqrt((m - re) / 2)); }
//
// Version with polar coordinates:
//    double m = Math.sqrt(abs());
//    double a = arg() / 2;
//    return fromPolar(m, a); }
//
// Version from Apache commons math (unclear, not yet verified):
//    if (r == 0 && i == 0) {
//       return new Complex(0, 0); }
//    double t = Math.sqrt((Math.abs(r) + abs()) / 2);
//    if (r >= 0) {
//       return new Complex(t, i / (2 * t)); }
//     else {
//       return new Complex(Math.abs(i) / (2 * t), Math.copySign(1, i) * t); }}

//--- Binary operations --------------------------------------------------------

/**
* Returns <code>this + x</code>;
*/
public Complex add (double x) {
   return new Complex(re + x, im); }

/**
* Returns <code>this + x</code>;
*/
public Complex add (Complex x) {
   return new Complex(re + x.re, im + x.im); }

/**
* Returns <code>this - x</code>;
*/
public Complex sub (double x) {
   return new Complex(re - x, im); }

/**
* Returns <code>this - x</code>;
*/
public Complex sub (Complex x) {
   return new Complex(re - x.re, im - x.im); }

/**
* Returns <code>x - y</code>;
*/
public static Complex sub (double x, Complex y) {
   return new Complex(x - y.re, -y.im); }

/**
* Returns <code>this * x</code>;
*/
public Complex mul (double x) {
   return new Complex(re * x, im * x); }

/**
* Returns <code>this * x</code>;
*/
public Complex mul (Complex x) {
   return new Complex(re * x.re - im * x.im, re * x.im + im * x.re); }

/**
* Returns <code>this / x</code>;
*/
public Complex div (double x) {
   return new Complex(re / x, im / x); }

/**
* Returns <code>this / x</code>;
*/
public Complex div (Complex x) {
   double m = x.re * x.re + x.im * x.im;
   return new Complex((re * x.re + im * x.im) / m, (im * x.re - re * x.im) / m); }

/**
* Returns <code>x / y</code>;
*/
public static Complex div (double x, Complex y) {
   double m = y.re * y.re + y.im * y.im;
   return new Complex(x * y.re / m, -x * y.im / m); }

/**
* Returns this raised to the power of <code>x</code>.
*/
public Complex pow (int x) {
   return fromPolar(Math.pow(abs(), x), arg() * x); }

/**
* Returns this raised to the power of <code>x</code>.
*/
public Complex pow (double x) {
   return log().mul(x).exp(); }

/**
* Returns this raised to the power of <code>x</code>.
*/
public Complex pow (Complex x) {
   return log().mul(x).exp(); }

//------------------------------------------------------------------------------

@Override
public boolean equals (Object obj) {
   if (this == obj) {
      return true; }
   if (!(obj instanceof Complex)) {
      return false; }
   Complex x = (Complex)obj;
   return re == x.re && im == x.im; }

@Override
public int hashCode() {
   long b1 = Double.doubleToLongBits(re);
   long b2 = Double.doubleToLongBits(im);
   return (int)(b1 ^ (b1 >>> 32) ^ b2 ^ (b2 >>> 32)); }

@Override public String toString() {
   return "(" + re + ", " + im + ")"; }

}
