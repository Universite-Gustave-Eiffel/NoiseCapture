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
* The coefficients for an IIR filter.
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
*
*/
public class IirFilterCoefficients {

/**
* A coefficients, applied to output values (negative).
*/
public double[]              a;

/**
* B coefficients, applied to input values.
*/
public double[]              b;

}
