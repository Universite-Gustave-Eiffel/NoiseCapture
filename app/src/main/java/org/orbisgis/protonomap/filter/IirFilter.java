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
* An IIR (infinite impulse response) filter.
*
* <p>
* Filter schema: <a href="http://commons.wikimedia.org/wiki/File:IIR_Filter_Direct_Form_1.svg">Wikipedia</a>
*
* <p>
* Formula:
* <pre>
*    y[i] = x[i] * b[0]  +  x[i-1] * b[1]  +  x[i-2] * b[2]  +  ...
*                        -  y[i-1] * a[1]  -  y[i-2] * a[2]  -  ...
* </pre>
* (x = input, y = output, a and b = filter coefficients, a[0] must be 1)
*/
public class IirFilter {

private int                  n1;                           // size of input delay line
private int                  n2;                           // size of output delay line
private double[]             a;                            // A coefficients, applied to output values (negative)
private double[]             b;                            // B coefficients, applied to input values

private double[]             buf1;                         // input signal delay line (ring buffer)
private double[]             buf2;                         // output signal delay line (ring buffer)
private int                  pos1;                         // current ring buffer position in buf1
private int                  pos2;                         // current ring buffer position in buf2

/**
* Creates an IIR filter.
*
* @param coeffs
*    The A and B coefficients. a[0] must be 1.
**/
public IirFilter (IirFilterCoefficients coeffs) {
   a = coeffs.a;
   b = coeffs.b;
   if (a.length < 1 || b.length < 1 || a[0] != 1.0) {
      throw new IllegalArgumentException("Invalid coefficients."); }
   n1 = b.length - 1;
   n2 = a.length - 1;
   buf1 = new double[n1];
   buf2 = new double[n2]; }

/**
* Processes one input signal value and returns the next output signal value.
*/
public double step (double inputValue) {
   double acc = b[0] * inputValue;
   for (int j = 1; j <= n1; j++) {
      int p = (pos1 + n1 - j) % n1;
      acc += b[j] * buf1[p]; }
   for (int j = 1; j <= n2; j++) {
      int p = (pos2 + n2 - j) % n2;
      acc -= a[j] * buf2[p]; }
   if (n1 > 0) {
      buf1[pos1] = inputValue;
      pos1 = (pos1 + 1) % n1; }
   if (n2 > 0) {
      buf2[pos2] = acc;
      pos2 = (pos2 + 1) % n2; }
   return acc; }

}
