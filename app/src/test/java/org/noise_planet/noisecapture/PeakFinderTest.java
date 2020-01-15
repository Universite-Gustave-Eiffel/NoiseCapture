package org.noise_planet.noisecapture;

import org.junit.Test;
import org.noise_planet.noisecapture.util.PeakFinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;


import static org.junit.Assert.assertArrayEquals;

public class PeakFinderTest {

    @Test
    public void findPeaks() throws IOException {
        PeakFinder peakFinder = new PeakFinder();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(PeakFinderTest.class.getResourceAsStream("sunspot.dat")));
        long index = 1;
        while ((line = br.readLine()) != null) {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            int year = Integer.parseInt(tokenizer.nextToken());
            float value = Float.parseFloat(tokenizer.nextToken());
            peakFinder.add(index++, (double)value);
        }
        int[] expectedIndex = new int[]{5,  17,  27,  38,  50,  52,  61,  69,  78,  87, 102, 104, 116, 130, 137, 148, 160, 164, 170, 177, 183, 193, 198, 205, 207, 217, 228, 237, 247, 257, 268, 272, 279, 290, 299};
        List<PeakFinder.Element> results = peakFinder.getPeaks();
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }

    @Test
    public void findPeaksMinimumWidth() throws IOException {
        PeakFinder peakFinder = new PeakFinder();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(PeakFinderTest.class.getResourceAsStream("sunspot.dat")));
        long index = 1;
        while ((line = br.readLine()) != null) {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            int year = Integer.parseInt(tokenizer.nextToken());
            float value = Float.parseFloat(tokenizer.nextToken());
            peakFinder.add(index++, (double)value);
        }
        int[] expectedIndex = new int[]{5, 17 ,27, 38, 50, 61, 69, 78, 87, 104, 116, 130, 137, 148, 160, 170, 183, 193, 205, 217, 228, 237, 247, 257, 268, 279, 290, 299};
        List<PeakFinder.Element> results = PeakFinder.filter(peakFinder.getPeaks(),6);
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }

    @Test
    public void findPeaksIncreaseCondition() throws IOException {
        PeakFinder peakFinder = new PeakFinder();
        peakFinder.setMinIncreaseCount(3);
        double[] values = new double[] {4, 5, 7, 13, 10, 9, 9, 10, 4, 6, 7, 8, 11 , 3, 2, 2};
        long index = 0;
        for(double value : values) {
            peakFinder.add(index++, value);
        }
        int[] expectedIndex = new int[]{3, 12};
        List<PeakFinder.Element> results = peakFinder.getPeaks();
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }

    @Test
    public void findPeaksDecreaseCondition() throws IOException {
        PeakFinder peakFinder = new PeakFinder();
        peakFinder.setMinDecreaseCount(2);
        double[] values = new double[] {4, 5, 7, 13, 10, 9, 9, 10, 4, 6, 7, 8, 11 , 3, 2, 2};
        long index = 0;
        for(double value : values) {
            peakFinder.add(index++, value);
        }
        int[] expectedIndex = new int[]{3, 12};
        List<PeakFinder.Element> results = peakFinder.getPeaks();
        int[] got = new int[results.size()];
        for(int i=0; i < results.size(); i++) {
            got[i] = (int)results.get(i).index;
        }
        assertArrayEquals(expectedIndex, got);
    }
}