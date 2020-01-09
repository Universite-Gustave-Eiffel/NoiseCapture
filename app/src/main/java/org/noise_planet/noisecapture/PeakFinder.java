package org.noise_planet.noisecapture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class PeakFinder {
    private boolean increase = true;
    private double oldVal = Double.MIN_VALUE;
    private long oldIndex = 0;
    private List<Element> peaks = new ArrayList<>();

    public List<Element> getPeaks() {
        return peaks;
    }

    public void clearPeaks(long upTo) {
        while(!peaks.isEmpty() && peaks.get(0).index < upTo) {
            peaks.remove(0);
        }
    }

    public boolean add(Long index, double value) {
        boolean ret = false;
        double diff = value - oldVal;
        // Detect switch from increase to decrease/stall
        if(diff <= 0 && increase) {
            peaks.add(new Element(oldIndex, oldVal));
            ret = true;
        }
        increase = diff > 0;
        oldVal = value;
        oldIndex = index;
        return ret;
    }

    /**
     * Remove peaks where distance to other peaks are less than provided argument
     * @param minWidth Minium width in index
     */
    public List<Element> filter(int minWidth) {
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
        Collections.sort(sortedPeaks, new ElementSortByIndex());
        return sortedPeaks;
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
