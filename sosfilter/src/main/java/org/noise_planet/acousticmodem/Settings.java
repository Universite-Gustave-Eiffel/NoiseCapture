package org.noise_planet.acousticmodem;

/**
 * Settings for encoding and decoding streams
 */
public class Settings {

    final int samplingRate;

    final double wordTimeLength;

    final int wordLength;

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


    final int[][] words;

    /**
     *
     * @param samplingRate Sample rate of signal
     * @param wordTimeLength Time in seconds of a word
     * @param words As the same output of wordsFrom8frequencies or DTMF_WORDS
     */
    public Settings(int samplingRate, double wordTimeLength, int[][] words) {
        this.samplingRate = samplingRate;
        this.wordTimeLength = wordTimeLength;
        this.wordLength = (int)(samplingRate * wordTimeLength);
        if(words.length != 16) {
            throw new IllegalArgumentException("Should be limited to 16 words type");
        }
        this.words = words;
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
                outWordDict[i*j + i][0] = baseFrequencies[i];
                outWordDict[i*j + i][1] = baseFrequencies[j + 4];
            }
        }
        return outWordDict;
    }
}
