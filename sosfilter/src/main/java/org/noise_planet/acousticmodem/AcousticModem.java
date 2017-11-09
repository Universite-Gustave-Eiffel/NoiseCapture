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
 * Acoustic modem.
 */
public class AcousticModem {
    private Settings settings;

    public AcousticModem(Settings settings) {
        this.settings = settings;
    }


    public Settings getSettings() {
        return settings;
    }

    private void copyTone(int wordId, short[] out, int outIndex, short toneRms) {
        int freqA = settings.words[wordId][0];
        int freqB = settings.words[wordId][1];
        for (int s = 0; s < settings.wordLength; s++) {
            double t = s * (1 / (double) settings.samplingRate);
            out[outIndex + s] = (short) (Math.sin(2 * Math.PI * freqA * t) * (toneRms) + Math.sin(2 * Math.PI * freqB * t) * (toneRms));
        }
    }

    /**
     * Compute size of signal length for the provided data
     *
     * @param source       Source data
     * @param sourceIndex  First location to read in source data
     * @param sourceLength Read this size from source data
     * @return size of signal length for the provided data
     */
    public int getSignalLength(byte[] source, int sourceIndex, int sourceLength) {
        int lastWord = 0;
        int bufferLength = 0;
        for (int idByte = sourceIndex; idByte < sourceIndex + sourceLength; idByte++) {
            int v = source[idByte] & 0xFF;
            int wordA = v >>> 4;
            bufferLength += settings.wordLength;
            if (idByte > 0 && lastWord != wordA) {
                // If the same word has to be sent we add a blank word
                bufferLength += settings.wordLength;
            }
            lastWord = wordA;
            int wordB = v & 0x0F;
            bufferLength += settings.wordLength;
            if (lastWord != wordB) {
                // If the same word has to be sent we add a blank word
                bufferLength += settings.wordLength;
            }
            lastWord = wordB;
        }
        return bufferLength + settings.wordLength;
    }

    /**
     * Converts each byte into two words (hexa) then write the signal in the out array.
     *
     * @param source       Source data
     * @param sourceIndex  First location to read in source data
     * @param sourceLength Read this size from source data
     * @param out          Out array. The size must be greater than outIndex + ((sourceLength + 1) * 2 * settings.wordLength)
     * @param outIndex     Write words signal onto out beginning with this position
     * @param toneRms      Power of words signal output
     * @throws IllegalArgumentException If array size does not fit with parameters
     */
    public void wordsToSignal(byte[] source, int sourceIndex, int sourceLength, short[] out, int outIndex, short toneRms) throws IllegalArgumentException {
        if (sourceIndex + sourceLength > source.length) {
            throw new IllegalArgumentException("Source buffer length is " + source.length + " but request " + (sourceIndex + sourceLength - 1));
        }
        if (outIndex + getSignalLength(source, sourceIndex, sourceLength) > out.length) {
            throw new IllegalArgumentException("Output buffer length is too short" + out.length);
        }
        int lastWord = 0;
        for (int idByte = sourceIndex; idByte < sourceIndex + sourceLength; idByte++) {
            int v = source[idByte] & 0xFF;
            int wordA = v >>> 4;
            copyTone(wordA, out, outIndex, toneRms);
            outIndex += settings.wordLength;
            if (idByte > 0 && lastWord != wordA) {
                // If the same word has to be sent we add a blank word
                for (int i = outIndex; i < outIndex + settings.wordLength; i++) {
                    out[i] = 0;
                }
                outIndex += settings.wordLength;
            }
            lastWord = wordA;
            int wordB = v & 0x0F;
            copyTone(wordB, out, outIndex, toneRms);
            outIndex += settings.wordLength;
            if (idByte > 0 && lastWord != wordB) {
                // If the same word has to be sent we add a blank word
                for (int i = outIndex; i < outIndex + settings.wordLength; i++) {
                    out[i] = 0;
                }
                outIndex += settings.wordLength;
            }
            lastWord = wordB;
        }
        // Finish with a blank word
        for (int i = outIndex; i < outIndex + settings.wordLength; i++) {
            out[i] = 0;
        }
    }

}
