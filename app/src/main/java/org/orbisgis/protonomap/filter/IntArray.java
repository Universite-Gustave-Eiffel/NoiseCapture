// Copyright 2004 - 2013 Christian d'Heureuse, Inventec Informatik AG, Zurich, Switzerland
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
* A dynamic array of integers.
*
* This class is similar to <code>ArrayList&lt;Integer&gt;</code> and <code>Vector&lt;Integer&gt;</code>,
* but instead of <code>Integer</code> it uses the primitive data type <code>int</code>
* to store the integer values.
*/
public final class IntArray {                    // ("final" is used to allow optimization of method calls)

private static final int defaultInitialCapacity = 16;

private int[]            a;
private int              size;
private int              initialCapacity;

public IntArray() {
   this(defaultInitialCapacity); }

public IntArray (int initialCapacity) {
   this.initialCapacity = initialCapacity; }

public IntArray (int[] a2) {
   this();
   addAll(a2); }

public void clear() {
   setSize(0); }

public int size() {
   return size; }

public void setSize (int newSize) {
   ensureCapacity(newSize);
   size = newSize; }

public void add (int element) {
   ensureCapacity(size + 1);
   a[size++] = element; }

public void addAll (int[] a2) {
   int p = size;
   setSize(size + a2.length);
   System.arraycopy(a2, 0, a, p, a2.length); }

public void set (int index, int element) {
   a[index] = element; }

public int get (int index) {
   return a[index]; }

public void ensureCapacity (int minCapacity) {
   int oldCapacity = (a == null) ? 0 : a.length;
   if (oldCapacity >= minCapacity) {
      return; }
   int newCapacity = Math.max(Math.max(minCapacity, 2 * oldCapacity), initialCapacity);
   if (newCapacity <= 0) {
      return; }
   int[] newArray = new int[newCapacity];
   if (size > 0) {
      System.arraycopy(a, 0, newArray, 0, size); }
   a = newArray; }

//--- Secondary methods --------------------------------------------------------

public int[] toArray() {
   int[] a2 = new int[size];
   if (size > 0) {
      System.arraycopy(a, 0, a2, 0, size); }
   return a2; }

public String toString (String delimiter) {
   StringBuilder s = new StringBuilder(512);
   for (int p = 0; p < size; p++) {
      if (p > 0) {
         s.append(delimiter); }
      s.append(Integer.toString(a[p])); }
   return s.toString(); }

@Override
public String toString() {
   return toString(" "); }

public static IntArray parse (String s) {
   IntArray a = parseOrNull(s);
   if (a == null) {
      a = new IntArray(0); }
   return a; }

/**
* Same as parse(), but returns null if the array would be empty.
*/
public static IntArray parseOrNull (String s) {
   IntArray a = null;
   int p = 0;
   while (true) {
      int p0 = skipListDelimiters(s, p, true);
      if (p0 >= s.length()) {
         break; }
      p = skipListDelimiters(s, p0, false);
      int i = Integer.parseInt(s.substring(p0, p));
      if (a == null) {
         a = new IntArray(); }
      a.add(i); }
   return a; }

private static int skipListDelimiters (String s, int p, boolean mode) {
   while (p < s.length()) {
      char c = s.charAt(p);
      if ((Character.isWhitespace(c) || c == ',' || c == ';' || c == '+') != mode) {
         break; }
      p++; }
   return p; }

@Override
public boolean equals (Object o) {
   if (o == this) {
      return true; }
   if (!(o instanceof IntArray)) {
      return false; }
   IntArray ia2 = (IntArray)o;
   if (ia2.size != size) {
      return false; }
   if (size == 0) {
      return true; }
   int[] a2 = ia2.a;
   if (size >= 16) {                                       // speed optimization: compare first, middle and last elements
      if (a[0] != a2[0] || a[size/2] != a2[size/2] || a[size-1] != a2[size-1]) {
         return false; }}
   for (int i = 0; i < size; i++) {
      if (a[i] != a2[i]) {
         return false; }}
   return true; }

/**
* Bitwise unpacks an integer array from a byte array.
*/
public static IntArray unpack (byte[] b, int p0, int bitsPerEntry, int n) {
   IntArray a = new IntArray(n);
   a.setSize(n);
   int vMask = (1<<bitsPerEntry)-1;
   int ip = p0;
   int op = 0;
   int w = 0;
   int bits = 0;
   while (op < n) {
      if (bits >= bitsPerEntry) {
         int v = (w >>> (bits - bitsPerEntry)) & vMask;
         a.set(op++, v);
         bits -= bitsPerEntry; }
       else {
         w = ((w & 0x7fffff) << 8) | (b[ip++] & 0xff);
         bits += 8; }}
   return a; }

} // end class IntArray
