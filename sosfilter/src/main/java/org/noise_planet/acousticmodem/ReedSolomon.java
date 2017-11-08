/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */
package org.noise_planet.acousticmodem;

/**
 * Reed Solomon Encoder/Decoder
 *
 * Copyright Henry Minsky (hqm@alum.mit.edu) 1991-2009
 *
 * This software library is licensed under terms of the GNU GENERAL
 * PUBLIC LICENSE
 *
 * RSCODE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RSCODE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rscode.  If not, see <http://www.gnu.org/licenses/>.

 * Commercial licensing is available under a separate license, please
 * contact author for details.
 *
 * Source code is available at http://rscode.sourceforge.net
 */


/**
 * Implementation of a Reed-Solomon error correction algorithm.
 */
public class ReedSolomon {
    /* Encoder parity bytes */
    int pBytes[];

    /* Decoder syndrome bytes */
    int synBytes[];

    /* generator polynomial */
    int genPoly[];

    /* This is one of 14 irreducible polynomials
     * of degree 8 and cycle length 255. (Ch 5, pp. 275, Magnetic Recording)
     * The high order 1 bit is implicit
     * x^8 + x^4 + x^3 + x^2 + 1
     */
    int kPPoly = 0x1D;

    private int gexp[] = new int[512];
    private int glog[] = new int[256];

    public ReedSolomon(int kParityBytes) {
        this.kParityBytes = kParityBytes;
        this.kMaxDeg = kParityBytes * 2;
        pBytes = new int[kMaxDeg];
        synBytes = new int[kMaxDeg];
        genPoly = new int[kMaxDeg * 2];
    }

    void init_exp_table() {
        int i, z;
        int pinit, p1, p2, p3, p4, p5, p6, p7, p8;

        pinit = p2 = p3 = p4 = p5 = p6 = p7 = p8 = 0;
        p1 = 1;

        gexp[0] = 1;
        gexp[255] = gexp[0];
        glog[0] = 0; /* shouldn't log[0] be an error? */

        for (i = 1; i < 256; i++) {
            pinit = p8;
            p8 = p7;
            p7 = p6;
            p6 = p5;
            p5 = p4 ^ pinit;
            p4 = p3 ^ pinit;
            p3 = p2 ^ pinit;
            p2 = p1;
            p1 = pinit;
            gexp[i] = p1 + p2 * 2 + p3 * 4 + p4 * 8 + p5 * 16 + p6 * 32 + p7 * 64 + p8 * 128;
            gexp[i + 255] = gexp[i];
        }

        for (i = 1; i < 256; i++) {
            for (z = 0; z < 256; z++) {
                if (gexp[z] == i) {
                    glog[i] = z;
                    break;
                }
            }
        }
    }

    /* multiplication using logarithms */
    int gmult(int a, int b) {
        int i, j;
        if (a == 0 || b == 0)
            return (0);
        i = glog[a];
        j = glog[b];
        return (gexp[i + j]);
    }

    int ginv(int elt) {
        return (gexp[255 - glog[elt]]);
    }

    /* Initialize lookup tables, polynomials, etc. */
    public void initialize_ecc() {
		/* Initialize the galois field arithmetic tables */
        init_exp_table();

		/* Compute the encoder generator polynomial */
        compute_genpoly(kParityBytes, genPoly);
    }


    public final int kParityBytes;
    /* maximum degree of various polynomials */
    public final int kMaxDeg;

    void zero_fill_from(byte[] buf, int from, int to) {
        int i;
        for (i = from; i < to; i++)
            buf[i] = 0;
    }

    /* Append the parity bytes onto the end of the message */
    void build_codeword(byte[] msg, int nbytes, byte[] codeword) {
        int i;

        for (i = 0; i < nbytes; i++)
            codeword[i] = msg[i];

        for (i = 0; i < kParityBytes; i++) {
            codeword[i + nbytes] = (byte) pBytes[kParityBytes - 1 - i];
        }
    }

    /**********************************************************
     * Reed Solomon Decoder
     *
     * Computes the syndrome of a codeword. Puts the results into the synBytes[]
     * array.
     */

    public void decode_data(byte[] codeword, int nbytes) {
        int i, j, sum;
        for (j = 0; j < kParityBytes; j++) {
            sum = 0;
            for (i = 0; i < nbytes; i++) {
                // !!!: byte-ify
                sum = (0xFF & (int)codeword[i]) ^ gmult(gexp[j + 1], sum);
            }
            synBytes[j] = sum;
        }
    }

    /* Check if the syndrome is zero */
    int check_syndrome() {
        int i, nz = 0;
        for (i = 0; i < kParityBytes; i++) {
            if (synBytes[i] != 0) {
                nz = 1;
                break;
            }
        }
        return nz;
    }

    void zero_poly(int poly[]) {
        int i;
        for (i = 0; i < kMaxDeg; i++)
            poly[i] = 0;
    }


    /* polynomial multiplication */
    void mult_polys(int dst[], int p1[], int p2[]) {
        int i, j;
        int tmp1[] = new int[kMaxDeg * 2];

        for (i = 0; i < (kMaxDeg * 2); i++)
            dst[i] = 0;

        for (i = 0; i < kMaxDeg; i++) {
            for (j = kMaxDeg; j < (kMaxDeg * 2); j++)
                tmp1[j] = 0;

			/* scale tmp1 by p1[i] */
            for (j = 0; j < kMaxDeg; j++)
                tmp1[j] = gmult(p2[j], p1[i]);
			/* and mult (shift) tmp1 right by i */
            for (j = (kMaxDeg * 2) - 1; j >= i; j--)
                tmp1[j] = tmp1[j - i];
            for (j = 0; j < i; j++)
                tmp1[j] = 0;

			/* add into partial product */
            for (j = 0; j < (kMaxDeg * 2); j++)
                dst[j] ^= tmp1[j];
        }
    }

    void copy_poly(int dst[], int src[]) {
        int i;
        for (i = 0; i < kMaxDeg; i++)
            dst[i] = src[i];
    }


	/*
	 * Create a generator polynomial for an n byte RS code. The coefficients are
	 * returned in the genPoly arg. Make sure that the genPoly array which is
	 * passed in is at least n+1 bytes long.
	 */

    void compute_genpoly(int nbytes, int genpoly[]) {
        int i;
        int tp[] = new int[256], tp1[] = new int[256];

		/* multiply (x + a^n) for n = 1 to nbytes */

        zero_poly(tp1);
        tp1[0] = 1;

        for (i = 1; i <= nbytes; i++) {
            zero_poly(tp);
            tp[0] = gexp[i]; /* set up x+a^n */
            tp[1] = 1;

            mult_polys(genpoly, tp, tp1);
            copy_poly(tp1, genpoly);
        }
    }

	/*
	 * Simulate a LFSR with generator polynomial for n byte RS code. Pass in a
	 * pointer to the data array, and amount of data.
	 *
	 * The parity bytes are deposited into pBytes[], and the whole message and
	 * parity are copied to dest to make a codeword.
	 */

    public void encode_data(byte[] msg, int nbytes, byte[] codeword) {
        int i;
        int LFSR[] = new int[kParityBytes + 1], dbyte, j;

        for (i = 0; i < kParityBytes + 1; i++)
            LFSR[i] = 0;

        for (i = 0; i < nbytes; i++) {
            // !!!: byte-ify
            dbyte = ((msg[i] ^ LFSR[kParityBytes - 1]) & 0xFF);
            for (j = kParityBytes - 1; j > 0; j--) {
                LFSR[j] = LFSR[j - 1] ^ gmult(genPoly[j], dbyte);
            }
            LFSR[0] = gmult(genPoly[0], dbyte);
        }

        for (i = 0; i < kParityBytes; i++)
            pBytes[i] = LFSR[i];

        build_codeword(msg, nbytes, codeword);
    }
}
