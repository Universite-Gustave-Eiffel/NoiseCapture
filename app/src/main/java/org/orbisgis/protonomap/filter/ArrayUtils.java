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

import java.util.Arrays;
import java.util.Comparator;

/**
* Array manipulation utilities.
*/
public class ArrayUtils {

// Dummy constructor to suppress Javadoc.
private ArrayUtils() {}

//--- Constructors -------------------------------------------------------------

/**
* Returns an array of <code>n</code> <code>Complex</code> zeros.
*/
public static Complex[] zeros (int n) {
   Complex[] a = new Complex[n];
   for (int i = 0; i < n; i++) {
      a[i] = Complex.ZERO; }
   return a; }

//--- Type conversion ----------------------------------------------------------

/**
* Converts a <code>Complex</code> array into a <code>double</code> array.
* Verifies that all imaginary parts are equal or below <code>eps</code>.
*/
public static double[] toDouble (Complex[] a, double eps) {
   double[] a2 = new double[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i].toDouble(eps); }
   return a2; }

/**
* Converts an integer array into a <code>double</code> array.
**/
public static double[] toDouble (int[] a) {
   double[] a2 = new double[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i]; }
   return a2; }

/**
* Converts a <code>Double</code> (Object) array into a <code>double</code> array.
**/
public static double[] toDouble (Double[] a) {
   double[] a2 = new double[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i]; }
   return a2; }

/**
* Converts a <code>double</code> array into a <code>Complex</code> array.
*/
public static Complex[] toComplex (double[] a) {
   Complex[] a2 = new Complex[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = new Complex(a[i]); }
   return a2; }

/**
* Converts a <code>double</code> array into a <code>Double</code> (Object) array.
*/
public static Double[] toObject (double[] a) {
   Double[] a2 = new Double[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i]; }
   return a2; }

//--- Arithmetic ---------------------------------------------------------------

/**
* Returns a new array where each element of the array <code>a</code> is multiplied with the factor <code>f</code>.
*/
public static double[] multiply (double[] a, double f) {
   double[] a2 = new double[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i] * f; }
   return a2; }

/**
* Returns a new array where each element of the array <code>a</code> is multiplied with the factor <code>f</code>.
*/
public static Complex[] multiply (Complex[] a, double f) {
   Complex[] a2 = new Complex[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i].mul(f); }
   return a2; }

/**
* Returns a new array where each element of the array <code>a</code> is divided by <code>f</code>.
*/
public static double[] divide (double[] a, double f) {
   double[] a2 = new double[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i] / f; }
   return a2; }

/**
* Returns a new array where each element of the array <code>a</code> is divided by <code>f</code>.
*/
public static Complex[] divide (Complex[] a, double f) {
   Complex[] a2 = new Complex[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i].div(f); }
   return a2; }

//--- Reorder / sort -----------------------------------------------------------

/**
* Returns a reverse copy of the passed array.
*/
public static double[] reverse (double[] a) {
   double[] a2 = new double[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[a.length - 1 - i]; }
   return a2; }

/**
* Returns a copy of a double array, sorted by magnitude (absolute value).
*/
public static double[] sortByMagnitude (double[] a) {
   Double[] a2 = toObject(a);
   Arrays.sort(a2, new Comparator<Double>() {
      public int compare (Double o1, Double o2) {
         return Double.compare(Math.abs(o1.doubleValue()), Math.abs(o2.doubleValue())); }
      public boolean equals (Object obj) {
         throw new AssertionError(); }});
   double[] a3 = toDouble(a2);
   return a3; }

/**
* Returns a copy of a Complex array, sorted by imaginary and real parts.
*/
public static Complex[] sortByImRe (Complex[] a) {
   Complex[] a2 = copy(a);                                 // we have to make a copy, because the sort is in-place
   Arrays.sort(a2, new Comparator<Complex>() {
      public int compare (Complex o1, Complex o2) {
         if (o1.im() < o2.im()) {
            return -1; }
          else if (o1.im() > o2.im()) {
            return 1; }
         return Double.compare(o1.re(), o2.re()); }
      public boolean equals (Object obj) {
         throw new AssertionError(); }});
   return a2; }

/**
* Returns a copy of a Complex array, sorted by abs(im) | -im | re.
*/
public static Complex[] sortByAbsImNegImRe (Complex[] a) {
   Complex[] a2 = copy(a);                                 // we have to make a copy, because the sort is in-place
   Arrays.sort(a2, new Comparator<Complex>() {
      public int compare (Complex o1, Complex o2) {
         double absIm1 = Math.abs(o1.im());
         double absIm2 = Math.abs(o2.im());
         if (absIm1 < absIm2) {
            return -1; }
          else if (absIm1 > absIm2) {
            return 1; }
         if (o1.im() > o2.im()) {
            return -1; }
          else if (o1.im() < o2.im()) {
            return 1; }
         return Double.compare(o1.re(), o2.re()); }
      public boolean equals (Object obj) {
         throw new AssertionError(); }});
   return a2; }

private static Complex[] copy (Complex[] a) {
   Complex[] a2 = new Complex[a.length];
   for (int i = 0; i < a.length; i++) {
      a2[i] = a[i]; }
   return a2; }

//--- Text conversion ----------------------------------------------------------

/**
* Returns a string representation of an array.
*/
public static String toString (double[] a) {
   StringBuilder s = new StringBuilder();
   s.append("[");
   for (int i = 0; i < a.length; i++) {
      if (i > 0) {
         s.append(" "); }
      s.append(a[i]); }
   s.append("]");
   return s.toString(); }

/**
* Returns a string representation of an array.
*/
public static String toString (Complex[] a) {
   StringBuilder s = new StringBuilder();
   s.append("[");
   for (int i = 0; i < a.length; i++) {
      if (i > 0) {
         s.append(" "); }
      s.append(a[i]); }
   s.append("]");
   return s.toString(); }

}
