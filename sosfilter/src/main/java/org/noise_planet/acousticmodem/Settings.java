package org.noise_planet.acousticmodem;

import java.util.*;

/**
 * Settings for encoding and decoding streams
 */
public class Settings {

    final int samplingRate;

    final double wordTimeLength;

    final int wordLength;

    // Minimum dB between powerful two frequencies over the third frequency to be recognised as a word
    private float minimalSignalToNoiseLevel  = 5;

    static final int[] DTMF_FREQUENCIES = new int[] {697, 770, 852, 941, 1209, 1336, 1477, 1633};

    // hexadecimal DTMF tones (each tone is simply the sum of two sine waves.)
    static final int[][] DTMF_WORDS = new int[][]{
            {697, 1209}, // 0->1
            {697, 1336}, // 1->2
            {697, 1477}, // 2->3
            {697, 1633}, // 3->A
            {770, 1209}, // 4->4
            {770, 1336}, // 5->5
            {770, 1477}, // 6->6
            {770, 1633}, // 7->B
            {852, 1209}, // 8->7
            {852, 1336}, // 9->8
            {852, 1477}, // A->9
            {852, 1633}, // B->C
            {941, 1209}, // C->*
            {941, 1336}, // D->0
            {941, 1477}, // E->#
            {941, 1633}, // F->D
         };

    public Integer getWordFromFrequencyTuple(int freqA, int freqB) {
        return frequencyTupleToWordIndex.get(new FrequencyIndex(freqA, freqB));
    }

    final int[][] words;

    final Integer[] frequencies;

    private final Map<FrequencyIndex, Integer> frequencyTupleToWordIndex;

    /**
     *
     * @param samplingRate Sample rate of signal
     * @param wordTimeLength Time in seconds of a word
     * @param words As the same output of wordsFrom8frequencies or DTMF_WORDS
     * @param frequencies Frequencies supplied for AcousticModel{@link #wordsFrom8frequencies(int[])}
     */
    public Settings(int samplingRate, double wordTimeLength, int[][] words) {
        this.samplingRate = samplingRate;
        this.wordTimeLength = wordTimeLength;
        this.wordLength = (int)(samplingRate * wordTimeLength);
        if(words.length != 16) {
            throw new IllegalArgumentException("Should be limited to 16 words type");
        }
        this.words = words;
        frequencyTupleToWordIndex = new HashMap<>();
        int idWord = 0;
        Set<Integer> frequenciesSet = new HashSet<>(8);
        for(int[] word : words) {
            frequenciesSet.add(word[0]);
            frequenciesSet.add(word[1]);
            frequencyTupleToWordIndex.put(new FrequencyIndex(word[0], word[1]), idWord++);
        }
        this.frequencies = frequenciesSet.toArray(new Integer[frequenciesSet.size()]);
        Arrays.sort(frequencies);

    }

    /**
     * Create word dictionary from 8 frequencies values (combine two frequencies to make a word)
     * @param baseFrequencies 8 frequencies
     * @return words dict for Settings constructor
     */
    public static int[][] wordsFrom8frequencies(int[] baseFrequencies) {
        int[][] outWordDict = new int[16][2];
        for(int i = 0; i < 4; i++) { // row
            for(int j = 0; j < 4; j++) { // column
                outWordDict[i*4 + j][0] = baseFrequencies[i];
                outWordDict[i*4 + j][1] = baseFrequencies[j + 4];
            }
        }
        return outWordDict;
    }

    /**
     * @return Minimum dB between powerful two frequencies over the third frequency to be recognised as a word
     */
    public float getMinimalSignalToNoiseLevel() {
        return minimalSignalToNoiseLevel;
    }

    /**
     * @param minimalSignalToNoiseLevel Minimum dB between powerful two frequencies over the third frequency to be recognised as a word
     */
    public void setMinimalSignalToNoiseLevel(float minimalSignalToNoiseLevel) {
        this.minimalSignalToNoiseLevel = minimalSignalToNoiseLevel;
    }

    private static class FrequencyIndex {
        private final int freqA;
        private final int freqB;

        public FrequencyIndex(int freqA, int freqB) {
            this.freqA = Math.min(freqA, freqB);
            this.freqB = Math.max(freqA, freqB);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FrequencyIndex that = (FrequencyIndex) o;

            if (freqA != that.freqA) return false;
            return freqB == that.freqB;
        }

        @Override
        public int hashCode() {
            int result = freqA;
            result = 31 * result + freqB;
            return result;
        }

        public int getFreqA() {
            return freqA;
        }

        public int getFreqB() {
            return freqB;
        }
    }
}
