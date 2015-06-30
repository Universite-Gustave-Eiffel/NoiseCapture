// This is a Java port of Fortran source code by Michael A. Jenkins.
//
// Copyright notice for the Java port:
//
// Copyright 2013 Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
// www.source-code.biz, www.inventec.ch/chdh, chdh@inventec.ch
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
// The original Fortran source code was published in 1975 in "ACM Transactions
// on Mathematical Software" (TOMS).
//  http://dl.acm.org/citation.cfm?id=355643&coll=ACM&dl=ACM
// Author: Michael A. Jenkins
//
// And it's part of the "ACM Collected Algorithms":
//  http://www.netlib.org/toms/
//  http://www.netlib.org/toms/493
// License: ACM Software Copyright and License Agreement
//  http://www.acm.org/publications/policies/softwarecrnotice/
//
//==============================================================================

// Notes for the Fortran to Java port:
// - Array indices in Fortran are from 1 to n, whereas in Java they are from 0 to n-1.
//   In order to use the same indexes for the internal arrays, we ignore the a[0] elements.
// - There is no "go to" in Java, so I had to rearrange parts of the program flow.
// - The many global variables are unpleasant. I removed some of them, but there are
//   still plenty of undocumented global variables.

package org.orbisgis.protonomap.filter;

/**
* A root finder for polynomials with real coefficients.
* It uses the Jenkins-Traub algorithm to find the zeros of a polynomial.
*
* <p>
* Reference: <a href="http://en.wikipedia.org/wiki/Jenkins%E2%80%93Traub_algorithm">Wikipedia</a>.
*/
public class PolynomialRootFinderJenkinsTraub {

// Dummy constructor to suppress Javadoc.
private PolynomialRootFinderJenkinsTraub() {}

/**
* Finds the zeros of a real polynomial.
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
public static Complex[] findRoots (double[] coeffs) {
   GlobalEnvironment env = new GlobalEnvironment();
   return env.rpoly(coeffs); }

//--- GlobalEnvironment routines -----------------------------------------------

private static class GlobalEnvironment {

// Global constants:
private static final double  eta = 2.22E-16;
   // The maximum relative representation error which can be described as the smallest
   // positive floating point number such that 1 + eta is greater than 1.
   // See http://en.wikipedia.org/wiki/Machine_epsilon
private static final double  base = 2;                     // the base of the floating-point number system used
private static final double  infin = Float.MAX_VALUE;
   // According to the Fortran source this should be the largest floating-point number.
   // In Java this would be Double.MAX_VALUE.
   // But Double.MAX_VALUE is about 1.8E308 and the original Fortran value is 4.3E68.
   // So we use Float.MAX_VALUE instead.
private static final double  smalno = Float.MIN_NORMAL;
   // According to the Fortran source this should be the smallest positive floating-point number.
   // In Java this would be Double.MIN_NORMAL.
   // But Double.MIN_NORMAL is about 2.2E-308 and the original Fortran value is 1.0E-45.
   // Using Double.MIN_NORMAL leads to errors in the zeros of about 1E-8.
   // So we use Float.MIN_NORMAL instead.
private static final double  are = eta;
private static final double  mre = eta;
   // are and mre refer to the unit error in + and * respectively.
   // They are assumed to be the same as eta.
private static final double  rotationAngleDeg = 94;
   // Why 94 degrees? I guess it's a heuristic value. Please tell me (chdh@inventec.ch) if you know better.
private static final double  rotationAngle = rotationAngleDeg / 360 * 2 * Math.PI;
private static final double  cosr = Math.cos(rotationAngle);
private static final double  sinr = Math.sin(rotationAngle);

// Global variables:
private int       n;
private int       nn;                                      // is always n + 1
private double[]  p, qp, k, qk;
private double    u, v, a, b, c, d, a1, a2, a3, a6, a7, e, f, g, h;
private Complex   sz;                                      // small zero
private Complex   lz;                                      // large zero

private Complex[] rpoly (double[] coeffs) {
   // coeffs[] uses indexes from 0 to degree.
   final int degree = coeffs.length - 1;
   Complex[] zeros = new Complex[degree];                  // zeros[] uses indexes from 0 to degree-1
   final double lo = smalno / eta;
   // initialization of constants for shift rotation
   double xx = Math.sqrt(0.5);
   double yy = -xx;
   n = degree;
   if (coeffs[0] == 0) {
      throw new IllegalArgumentException("The leading coefficient must not be zero."); }
   // Remove the zeros at the origin if any.
   while (n > 0 && coeffs[n] == 0) {
      zeros[degree - n] = Complex.ZERO;
      n--; }
   nn = n + 1;
   // Make a copy of the coefficients.
   p = new double[nn + 1];                                 // p[] uses indexes from 1 to n + 1
   for (int i = 1; i <= nn; i++) {
      p[i] = coeffs[i - 1]; }
   // Start the algorithm for one zero.
   while (true) {                                          // main loop
      // System.out.println("Main loop " + n + p[1] + " " + p[2]);
      if (n < 1) {
         return zeros; }
      if (n == 1) {
         // Calculate the final zero.
         zeros[degree - 1] = new Complex(-p[2] / p[1]);
         return zeros; }
      if (n == 2) {
         // Calculate the final pair of zeros.
         Complex[] temp1 = quad(p[1], p[2], p[3]);
         zeros[degree - 2] = temp1[0];
         zeros[degree - 1] = temp1[1];
         return zeros; }
      // Find largest and smallest moduli of coefficients.
      double max = 0;
      double min = infin;
      for (int i = 1; i <= nn; i++) {
         double x = Math.abs(p[i]);
         if (x > max) {
            max = x; }
         if (x != 0 && x < min){
            min = x; }}
      // Scale if there are large or very small coefficients.
      // Computes a scale factor to multiply the coefficients of the polynomial.
      // The scaling is done to avoid overflow and to avoid undetected underflow
      // interfering with the convergence criterion.
      // The factor is a power of the base.
      double sc = lo / min;
      if (sc == 0) {
         sc = smalno; }
      if ((sc > 1 && infin / sc >= max) || (sc <= 1 && max > 10)) {
         double l = Math.log(sc) / Math.log(base) + 0.5;
         double factor = Math.pow(base, l);
         if (factor != 1) {
            for (int i = 1; i <= nn; i++) {
               p[i] = factor * p[i]; }}}
      // Compute lower bound on moduli of zeros.
      double[] pt = new double[nn + 1];
      for (int i = 1; i <= nn; i++) {
         pt[i] = Math.abs(p[i]); }
      pt[nn] = -pt[nn];
      // Compute upper estimate of bound.
      double x = Math.exp( ( Math.log(-pt[nn]) - Math.log(pt[1]) ) / n);
      if (pt[n] != 0) {
         // If Newton step at the origin is better, use it.
         double xm = -pt[nn] / pt[n];
         if (xm < x) {
            x = xm; }}
      // Chop the interval (0,x) until ff <= 0.
      while (true) {
         double xm = x * 0.1;
         double ff = pt[1];
         for (int i = 2; i <= nn; i++) {
            ff = ff * xm + pt[i]; }
         if (ff <= 0) {
            break; }
         x = xm; }
      double dx = x;
      // Do Newton iteration until x converges to two decimal places.
      while (Math.abs(dx / x) > 0.005) {
         double ff = pt[1];
         double df = ff;
         for (int i = 2; i <= n; i++) {
            ff = ff * x + pt[i];
            df = df * x + ff; }
         ff = ff * x + pt[nn];
         dx = ff / df;
         x = x - dx; }
      double bnd = x;
      // Compute the derivative as the initial k polynomial and do 5 steps with no shift.
      int nm1 = n - 1;
      k = new double[n + 1];
      for (int i = 2; i <= n; i++) {
         k[i] = (nn - i) * p[i] / n; }
      k[1] = p[1];
      double aa = p[nn];
      double bb = p[n];
      boolean zerok = k[n] == 0;
      for (int jj = 1; jj <= 5; jj++) {
         double cc = k[n];
         if (zerok) {
            // Use unscaled form of recurrence.
            for (int i = 1; i <= nm1; i++) {
               int j = nn - i;
               k[j] = k[j - 1]; }
            k[1] = 0;
            zerok = k[n] == 0; }
          else {
            // Use scaled form of recurrence if value of k at 0 is nonzero.
            double t = -aa / cc;
            for (int i = 1; i <= nm1; i++) {
               int j = nn - i;
               k[j] = t * k[j-1] + p[j]; }
            k[1] = p[1];
            zerok = Math.abs(k[n]) <= Math.abs(bb) * eta * 10; }}
      // Save k for restarts with new shifts.
      double[] temp = new double[n + 1];
      for (int i = 1; i <= n; i++) {
         temp[i] = k[i]; }
      // Loop to select the quadratic corresponding to each new shift.
      int cnt = 1;
      while (true) {
         // Quadratic corresponds to a double shift to a non-real point and its complex conjugate.
         // The point has modulus bnd and amplitude rotated by 94 degrees from the previous shift.
         double xxx = cosr * xx - sinr * yy;
         yy = sinr * xx + cosr * yy;
         xx = xxx;
         double sr = bnd * xx;
         // si = bnd * yy;                                 // (si is never used)
         u = -2 * sr;
         v = bnd;
         // Second stage calculation, fixed quadratic.
         qp = new double[nn + 1];
         qk = new double[n + 1];
         int nz = fxshfr(20 * cnt, sr);
         if (nz > 0) {                                     // one or two zeros have been found
            // The second stage jumps directly to one of the third stage iterations and returns here if successful.
            // Deflate the polynomial, store the zero or zeros and return to the main algorithm.
            zeros[degree - n] = sz;
            if (nz > 1) {
               zeros[degree - n + 1] = lz; }
            nn = nn - nz;
            n = nn - 1;
            for (int i = 1; i <= nn; i++) {
               p[i] = qp[i]; }
            qp = null;                                     // just to be sure that qp is no longer used from here on
            qk = null;                                     // just to make sure that qk is no longer used from here on
            break; }                                       // continue with main loop, after zeros hav been found
         // If the iteration is unsuccessful another quadratic is chosen after restoring k.
         for (int i = 1; i <= n; i++) {
            k[i] = temp[i]; }
         // Failure if no convergence with 20 shifts.
         if (cnt++ > 20) {
            throw new RuntimeException("No convergence."); }}}}

// Computes up to l2 fixed shift k-polynomials, testing for convergence in the linear
// or quadratic case. Initiates one of the variable shift iterations and returns with
// the number of zeros found.
//
// @param l2
//    limit of fixed shift steps.
// @return
//    the number of zeros found.
private int fxshfr (int l2, double sr) {
   double ots = 0;
   double otv = 0;
   double betav = 0.25;
   double betas = 0.25;
   double oss = sr;
   double ovv = v;
   // Evaluate polynomial by synthetic division.
   double[] temp1 = new double[2];
   quadsd(nn, u, v, p, qp, temp1);
   a = temp1[0];
   b = temp1[1];
   int type = calcsc();
   for (int j = 1; j <= l2; j++) {
      // Calculate next k polynomial and estimate v.
      nextk(type);
      type = calcsc();
      double[] temp2 = newest(type);
      double ui = temp2[0];
      double vi = temp2[1];
      double vv = vi;
      // Estimate s
      double ss = 0;
      if (k[n] != 0) {
         ss = -p[nn] / k[n]; }
      double tv = 1;
      double ts = 1;
      if (j != 1 && type != 3) {
         // Compute relative measures of convergence of s and v sequences.
         if (vv != 0) {
            tv = Math.abs((vv - ovv) / vv); }
         if (ss != 0) {
            ts = Math.abs((ss - oss) / ss); }
         // If decreasing, multiply two most recent convergence measures.
         double tvv = (tv < otv) ? tv * otv : 1;
         double tss = (ts < ots) ? ts * ots : 1;
         // Compare with convergence criteria.
         boolean vpass = tvv < betav;
         boolean spass = tss < betas;
         if (spass || vpass) {
            // At least one sequence has passed the convergence test. Store variables before iterating.
            double svu = u;
            double svv = v;
            double[] svk = new double[n + 1];
            for (int i = 1; i <= n; i++) {
               svk[i] = k[i]; }
            double s = ss;
            // Choose iteration according to the fastest converging sequence.
            boolean vtry = false;
            boolean stry = false;
            int state = (spass && (!vpass || tss < tvv)) ? 40 : 20;
            while (state != 70) {
               // State machine to implement the Fortran spaghetti code.
               // The state numbers correspond to the labels within the Fortran source code.
               switch (state) {
                  case 20: {
                     int nz = quadit(ui, vi);
                     if (nz > 0) {
                        return nz; }
                     // Quadratic iteration has failed. Flag that it has been tried and decrease
                     // the convergence criterion.
                     vtry = true;
                     betav = betav * 0.25;
                     // Try linear iteration if it has not been tried and the s sequence is converging.
                     if (stry || !spass) {
                        state = 50;
                        break; }
                     for (int i = 1; i <= n; i++) {
                        k[i] = svk[i]; }
                     state = 40;
                     break; }
                  case 40: {
                     RealitOut realitOut = realit(s);
                     if (realitOut.nz > 0) {
                        return realitOut.nz; }
                     s = realitOut.sss;
                     // Linear iteration has failed. Flag that it has been tried and decrease the
                     // convergence criterion.
                     stry = true;
                     betas *= 0.25;
                     if (realitOut.iflag) {
                        state = 50;
                        break; }
                     // If linear iteration signals an almost double real zero attempt quadratic interation.
                     ui = -(s + s);
                     vi = s * s;
                     state = 20;
                     break; }
                  case 50: {
                     // Restore variables.
                     u = svu;
                     v = svv;
                     for (int i = 1; i <= n; i++) {
                        k[i] = svk[i]; }
                     // Try quadratic iteration if it has not been tried and the v sequence is converging.
                     if (vpass && !vtry) {
                        state = 20;
                        break; }
                     // Recompute qp and scalar values to continue the second stage.
                     double[] temp3 = new double[2];
                     quadsd(nn, u, v, p, qp, temp3);
                     a = temp3[0];
                     b = temp3[1];
                     type = calcsc();
                     state = 70;
                     break; }
                  default:
                     throw new AssertionError(); }}}}
      ovv = vv;
      oss = ss;
      otv = tv;
      ots = ts; }
   return 0; }

// Variable-shift k-polynomial iteration for a quadratic factor converges only if the zeros are
// equimodular or nearly so.
// uu,vv - coefficients of starting quadratic
// Returns the number of zeros found.
private int quadit (double uu, double vv) {
   boolean tried = false;
   double omp = 0;
   double relstp = 0;
   u = uu;
   v = vv;
   int j = 0;
   // main loop
   while (true) {
      Complex[] zeros = quad(1, u, v);
      sz = zeros[0];
      lz = zeros[1];
      // Return if roots of the quadratic are real and not close to multiple or
      // nearly equal and of opposite sign.
      // 2013-07-29 chdh: There is no test whether the roots are real?
      if (Math.abs(Math.abs(sz.re()) - Math.abs(lz.re())) > 0.01 * Math.abs(lz.re())) {
         return 0; }
      // Evaluate polynomial by quadratic synthetic division.
      double[] temp1 = new double[2];
      quadsd(nn, u, v, p, qp, temp1);
      a = temp1[0];
      b = temp1[1];
      double mp = Math.abs(a - sz.re() * b) + Math.abs(sz.im() * b);
      // Compute a rigorous bound on the rounding error in evaluting p.
      double zm = Math.sqrt(Math.abs(v));
      double ee = 2 * Math.abs(qp[1]);
      double t = -sz.re() * b;
      for (int i = 2; i <= n; i++) {
         ee = ee * zm + Math.abs(qp[i]); }
      ee = ee * zm + Math.abs(a + t);
      ee = (5 * mre + 4 * are) * ee -
           (5 * mre + 2 * are) * (Math.abs(a + t) + Math.abs(b) * zm) +
           2 * are * Math.abs(t);
      // Iteration has converged sufficiently if the polynomial value is less than 20 times this bound.
      if (mp <= 20 * ee) {
         return 2; }
      j++;
      // Stop iteration after 20 steps.
      if (j > 20) {
         return 0; }
      if (j >= 2 && relstp <= 0.01 && mp >= omp && !tried) {
         // A cluster appears to be stalling the convergence.
         // Five fixed shift steps are taken with a u,v close to the cluster.
         if (relstp < eta) {
            relstp = eta; }
         relstp = Math.sqrt(relstp);
         u = u - u * relstp;
         v = v + v * relstp;
         double[] temp2 = new double[2];
         quadsd(nn, u, v, p, qp, temp2);
         a = temp2[0];
         b = temp2[1];
         for (int i = 1; i <= 5; i++) {
           int type = calcsc();
           nextk(type); }
         tried = true;
         j = 0; }                                          // reset loop counter
      omp = mp;
      // Calculate next k polynomial and new u and v.
      int type1 = calcsc();
      nextk(type1);
      int type2 = calcsc();
      double[] temp2 = newest(type2);
      double ui = temp2[0];
      double vi = temp2[1];
      // If vi is zero the iteration is not converging.
      if (vi == 0) {
         return 0; }
      relstp = Math.abs((vi - v) / vi);
      u = ui;
      v = vi; }}

private static class RealitOut {                           // output parameters for realit()
   double                    sss;                          // starting iterate
   int                       nz;                           // number of zeros found
   boolean                   iflag;                        // flag to indicate a pair of zeros near real axis.
   RealitOut (double sss, int nz, boolean iflag) {
      this.sss = sss; this.nz = nz; this.iflag = iflag; }}

// Variable-shift h polynomial iteration for a real zero.
private RealitOut realit (double sss) {
   double omp = 0;
   double t = 0;
   double s = sss;
   int j = 0;
   while (true) {                                          // main loop
      double pv = p[1];
      // Evaluate p at s.
      qp[1] = pv;
      for (int i = 2; i <= nn; i++) {
         pv = pv * s + p[i];
         qp[i] = pv; }
      double mp = Math.abs(pv);
      // Compute a rigorous bound on the error in evaluating p.
      double ms = Math.abs(s);
      double ee = (mre / (are + mre)) * Math.abs(qp[1]);
      for (int i = 2; i <= nn; i++) {
         ee = ee * ms + Math.abs(qp[i]); }
      // Iteration has converged sufficiently if the polynomial value is less than 20 times this bound.
      if (mp <= 20 * ((are + mre) * ee - mre * mp)) {
         sz = new Complex(s);
         return new RealitOut(sss, 1, false); }
      j++;
      // Stop iteration after 10 steps.
      if (j > 10) {
         return new RealitOut(sss, 0, false); }
      if (j >= 2 && Math.abs(t) <= 0.001 * Math.abs(s - t) && mp > omp) {
         // A cluster of zeros near the real axis has been encountered return with iflag set to
         // initiate a quadratic iteration.
         return new RealitOut(s, 0, true); }
            // (here s is used instead of sss for the output)
      // The following comment seems to be out of place in the original Fortrag code:
      //    Return if the polynomial value has increased significantly.
      omp = mp;
      // Compute t, the next polynomial, and the new iterate.
      double kv = k[1];
      qk[1] = kv;
      for (int i = 2; i <= n; i++) {
         kv = kv * s + k[i];
         qk[i] = kv; }
      if (Math.abs(kv) <= Math.abs(k[n]) * 10 * eta) {
         // Use unscaled form.
         k[1] = 0;
         for (int i = 2; i <= n; i++) {
            k[i] = qk[i - 1]; }}
       else {
         // Use the scaled form of the recurrence if the value of k at s is nonzero.
         t = -pv / kv;
         k[1] = qp[1];
         for (int i = 2; i <= n; i++) {
            k[i] = t * qk[i - 1] + qp[i]; }}
      kv = k[1];
      for (int i = 2; i <= n; i++) {
         kv = kv * s + k[i]; }
      t = 0;
      if (Math.abs(kv) > Math.abs(k[n]) * 10 * eta) {
         t = -pv / kv; }
      s = s + t; }}

// this routine calculates scalar quantities used to compute the next k
// polynomial and new estimates of the quadratic coefficients.
// Returns an integer (type) indicating how the calculations are
// normalized to avoid overflow.
private int calcsc() {
   double[] temp = new double[2];
   quadsd(n, u, v, k, qk, temp);                           // synthetic division of k by the quadratic 1,u,v
   c = temp[0];
   d = temp[1];
   if (Math.abs(c) <= Math.abs(k[n]) * 100 * eta || Math.abs(d) <= Math.abs(k[n - 1]) * 100 * eta) {
      return 3; }                                          // type=3 indicates the quadratic is almost a factor c of k
   if (Math.abs(d) < Math.abs(c)) {
      e = a / c;
      f = d / c;
      g = u * e;
      h = v * b;
      a3 = a * e + (h / c + g) * b;
      a1 = b - a * (d / c);
      a7 = a + g * d + h * f;
      return 1; }                                          // type=1 indicates that all formulas are divided by c
    else {
      e = a / d;
      f = c / d;
      g = u * b;
      h = v * b;
      a3 = (a + g) * e + h * (b / d);
      a1 = b * f - a;
      a7 = (f + u) * a + h;
      return 2; }}                                         // type=2 indicates that all formulas are divided by d

// Computes the next k polynomials using scalars computed in calcsc.
private void nextk (int type) {
   if (type == 3) {                                        // use unscaled form of the recurrence
      k[1] = 0;
      k[2] = 0;
      for (int i = 3; i <= n; i++) {
         k[i] = qk[i - 2]; }
      return; }
   double temp = (type == 1) ? b : a;
   if (Math.abs(a1) > Math.abs(temp) * eta * 10) {
      // Use scaled form of the recurrence.
      a7 = a7 / a1;
      a3 = a3 / a1;
      k[1] = qp[1];
      k[2] = qp[2] - a7 * qp[1];
      for (int i = 3; i <= n; i++) {
         k[i] = a3 * qk[i - 2] - a7 * qp[i - 1] + qp[i]; }}
    else {
      // If a1 is nearly zero then use a special form of the recurrence.
      k[1] = 0;
      k[2] = -a7 * qp[1];
      for (int i = 3; i <= n; i++) {
         k[i] = a3 * qk[i - 2] - a7 * qp[i-1]; }}}

// Compute new estimates of the quadratic coefficients using the scalars computed in calcsc.
private double[] newest (int type) {
   // Use formulas appropriate to setting of type.
   if (type == 3) {                                        // if type=3 the quadratic is zeroed
      return new double[]{0, 0}; }
   double a4, a5;
   if (type == 2) {
      a4 = (a + g) * f + h;
      a5 = (f + u) * c + v * d; }
    else {
      a4 = a + u * b + h * f;
      a5 = c + (u + v * f) * d; }
   // Evaluate new quadratic coefficients.
   double b1 = -k[n] / p[nn];
   double b2 = -(k[n - 1] + b1 * p[n]) / p[nn];
   double c1 = v * b2 * a1;
   double c2 = b1 * a7;
   double c3 = b1 * b1 * a3;
   double c4 = c1 - c2 - c3;
   double temp = a5 + b1 * a4 - c4;
   if (temp == 0) {
      return new double[]{0, 0}; }
   double uu = u - (u * (c3 + c2) + v * (b1 * a1 + b2 * a7)) / temp;
   double vv = v * (1 + c4 / temp);
   return new double[]{uu, vv}; }

} // end class GlobalEnvironment

//--- Routines outside of GlobalEnvironment ------------------------------------

// Divides p by the quadratic 1,u,v placing the quotient in q and the remainder in rem[0] and rem[1].
// Output parameters: q, ab.
private static void quadsd (int nn, double u, double v, double p[], double[] q, double[] rem) {
   double b = p[1];
   q[1] = b;
   double a = p[2] - u * b;
   q[2] = a;
   for (int i = 3; i <= nn; i++) {
      double c = p[i] - u * a - v * b;
      q[i] = c;
      b = a;
      a = c; }
   rem[0] = a;
   rem[1] = b; }

// Calculate the zeros of the quadratic a * z^2 + b * z + c.
// Returns an array containing the smaller zero and the larger zero.
// The quadratic formula is used, modified to avoid overflow.
private static Complex[] quad (double a, double b, double c) {
   if (a == 0 && b == 0) {
      return new Complex[]{new Complex(0), new Complex(0)}; }
   if (a == 0) {
      return new Complex[]{new Complex(-c / b), new Complex(0)}; }  // only one zero
   if (c == 0) {
      return new Complex[]{new Complex(0), new Complex(-b / a)}; }
   // Compute discriminant, avoiding overflow.
   double b2 = b / 2;
   double e, d;
   if (Math.abs(b2) < Math.abs(c)) {
      double e1 = (c >= 0) ? a : -a;
      e = b2 * (b2 / Math.abs(c)) - e1;
      d = Math.sqrt(Math.abs(e)) * Math.sqrt(Math.abs(c)); }
    else {
      e = 1 - (a / b2) * (c / b2);
      d = Math.sqrt(Math.abs(e)) * Math.abs(b2); }
   if (e >= 0) {                                           // real zeros
      double d2 = (b2 >= 0) ? -d : d;
      double lr = (-b2 + d2) / a;                          // larger real zero
      double sr = (lr != 0) ? c / lr / a : 0;              // smaller real zero
      return new Complex[]{new Complex(sr), new Complex(lr)}; }
    else {                                                 // complex conjugate zeros
      Complex z1 = new Complex(-b2 / a, Math.abs(d / a));
      return new Complex[]{z1, z1.conj()}; }}

}
