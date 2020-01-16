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

package org.noise_planet.noisecapture.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


/**
 * PeakFinder will find highest values
 * This class results are equivalent with Octave(R) findpeaks function
 */
public class PeakFinder {
    private boolean increase = true;
    private double oldVal = Double.MIN_VALUE;
    private long oldIndex = 0;
    boolean added = false;
    private List<Element> peaks = new ArrayList<>();
    private int increaseCount = 0;
    private int decreaseCount = 0;
    private int minIncreaseCount = -1;
    private int minDecreaseCount = -1;

    public List<Element> getPeaks() {
        return peaks;
    }

    public void clearPeaks(long upTo) {
        while(!peaks.isEmpty() && peaks.get(0).index < upTo) {
            peaks.remove(0);
        }
    }

    /**
     * @return Remove peaks where increase steps count are less than this number
     */
    public int getMinIncreaseCount() {
        return minIncreaseCount;
    }

    /**
     * @param minIncreaseCount Remove peaks where increase steps count are less than this number
     */
    public void setMinIncreaseCount(int minIncreaseCount) {
        this.minIncreaseCount = minIncreaseCount;
    }

    /**
     * @return Remove peaks where decrease steps count are less than this number
     */
    public int getMinDecreaseCount() {
        return minDecreaseCount;
    }

    /**
     * @return Remove peaks where decrease steps count are less than this number
     */
    public void setMinDecreaseCount(int minDecreaseCount) {
        this.minDecreaseCount = minDecreaseCount;
    }

    public boolean add(Long index, double value) {
        boolean ret = false;
        double diff = value - oldVal;
        // Detect switch from increase to decrease/stall
        if(diff <= 0 && increase) {
            if(increaseCount >= minIncreaseCount) {
                peaks.add(new Element(oldIndex, oldVal));
                added = true;
                ret = true;
            }
        } else if(diff > 0 && !increase) {
            // Detect switch from decreasing to increase
            if(added && minDecreaseCount != -1 && decreaseCount < minDecreaseCount) {
                peaks.remove(peaks.size() - 1);
                added = false;
            }
        }
        increase = diff > 0;
        if(increase) {
            increaseCount++;
            decreaseCount = 0;
        } else {
            decreaseCount++;
            if(decreaseCount > minDecreaseCount) {
                added = false;
            }
            increaseCount=0;
        }
        oldVal = value;
        oldIndex = index;
        return ret;
    }

    /**
     * Remove peaks where distance to other peaks are less than provided argument
     * @param minWidth Minium width in index
     */
    public static List<Element> filter(List<Element> peaks, int minWidth) {
        // Sort peaks by value
        List<Element> sortedPeaks = new ArrayList<>(peaks);
        Collections.sort(sortedPeaks);
        for(int i = 0; i < sortedPeaks.size(); i++) {
            Element topPeak = sortedPeaks.get(i);
            int j = i + 1;
            while(j < sortedPeaks.size()) {
                Element otherPeak = sortedPeaks.get(j);
                if(Math.abs(otherPeak.index - topPeak.index) <= minWidth) {
                    sortedPeaks.remove(j);
                } else {
                    j += 1;
                }
            }
        }
        // Sort peaks by index
        Collections.sort(sortedPeaks, new ElementSortByIndex());
        return sortedPeaks;
    }

    /**
     * Remove peaks where value is less than provided argument
     * @param minValue Minium peak value
     */
    public static List<Element> filter(List<Element> peaks, double minValue) {
        // Sort peaks by value
        List<Element> filteredPeaks = new ArrayList<>(peaks);
        int j = 0;
        while(j < filteredPeaks.size()) {
            Element peak = filteredPeaks.get(j);
            if(peak.value < minValue) {
                filteredPeaks.remove(j);
            } else {
                j += 1;
            }
        }
        return filteredPeaks;
    }

    public static class Element implements Comparable<Element> {
        public final long index;
        public final double value;

        public Element(long index, double value) {
            this.index = index;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Element element = (Element) o;
            return index == element.index &&
                    Double.compare(element.value, value) == 0;
        }

        // Natural order is Descendant values
        @Override
        public int compareTo(Element element) {
            return Double.compare(element.value, this.value);
        }

        @Override
        public int hashCode() {
            return Long.valueOf(index).hashCode() + Double.valueOf(value).hashCode();
        }
    }

    public static class ElementSortByIndex implements Comparator<Element> {
        @Override
        public int compare(Element element, Element t1) {
            return Long.valueOf(element.index).compareTo(t1.index);
        }
    }

}
