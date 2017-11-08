package org.noise_planet.acousticmodem;

/**
 * Settings for encoding and decoding streams
 */
public class Settings {

    public final double kLowFrequency; //the lowest frequency used
    public final double kFrequencyStep; //the distance between frequencies

    public final int kBytesPerDuration; //how wide is the data stream
    // (rps - that is, the pre-encoding data stream, not the audio)
    // (rps - kFrequencies must be this long)

    public final int kBitsPerByte = 8; //unlikely to change, I know

    // Amplitude of each frequency in a frame.
    public final double kAmplitude; /* (1/8) */

    // Sampling frequency (number of sample values per second)
    public final double kSamplingFrequency; //rps - reduced to 11025 from 22050
    // to enable the decoder to keep up with the audio on Motorola CLIQ

    // Sound duration of encoded byte (in seconds)
    public final double kDuration; // rps - increased from 0.1 to improve reliability on Android

    // Number of samples per duration
    public final int kSamplesPerDuration;

    //This is used to convert the floats of the encoding to the bytes of the audio
    public final int kFloatToByteShift;

    // The length, in durations, of the hail sequence
    public final int kDurationsPerHail;

    // The frequency used in the initial hail of the key
    public final int kHailFrequency;

    //The frequencies we use for each of the 8 bits
    public final int kBaseFrequency = 1000;
    public final int[] kFrequencies = {kBaseFrequency,   //1000
            (int)(kBaseFrequency * (float)27/24), //1125
            (int)(kBaseFrequency * (float)30/24), //1250
            (int)(kBaseFrequency * (float)36/24), //1500
            (int)(kBaseFrequency * (float)40/24), //1666
            (int)(kBaseFrequency * (float)48/24), //2000
            (int)(kBaseFrequency * (float)54/24), //2250
            (int)(kBaseFrequency * (float)60/24)};//2500

    //The length, in durations, of the hail sequence
    public final int kDurationsPerSOS;

    // The frequency used to signal for help
    public final int kSOSFrequency;

    // The length, in durations, of some session timing jitters
    public final int kPlayJitter;
    public final int kSetupJitter;

    // The length, in durations, of the CRC
    public final int kDurationsPerCRC;

    public Settings(double kLowFrequency, double kFrequencyStep, int kBytesPerDuration, double kAmplitude, double kSamplingFrequency, double kDuration, int kFloatToByteShift, int kDurationsPerHail, int kHailFrequency, int kDurationsPerSOS, int kSOSFrequency, int kPlayJitter, int kSetupJitter, int kDurationsPerCRC) {
        this.kLowFrequency = kLowFrequency;
        this.kFrequencyStep = kFrequencyStep;
        this.kBytesPerDuration = kBytesPerDuration;
        this.kAmplitude = kAmplitude;
        this.kSamplingFrequency = kSamplingFrequency;
        this.kDuration = kDuration;
        this.kFloatToByteShift = kFloatToByteShift;
        this.kDurationsPerHail = kDurationsPerHail;
        this.kHailFrequency = kHailFrequency;
        this.kDurationsPerSOS = kDurationsPerSOS;
        this.kSOSFrequency = kSOSFrequency;
        this.kPlayJitter = kPlayJitter;
        this.kSetupJitter = kSetupJitter;
        this.kDurationsPerCRC = kDurationsPerCRC;
        kSamplesPerDuration = (int)(kSamplingFrequency * kDuration);
    }

    public Settings() {
        kLowFrequency = 600;
        kFrequencyStep = 50;
        kBytesPerDuration = 1;
        kAmplitude = 0.125d;
        kSamplingFrequency = 22050;
        kDuration = 0.2;
        kFloatToByteShift = 128;
        kDurationsPerHail = 3;
        kHailFrequency = 3000;
        kDurationsPerSOS = 1;
        kSOSFrequency = (int)(kBaseFrequency * (float)30/24);
        kPlayJitter = 5;
        kSetupJitter = 10;
        kDurationsPerCRC = 1;
        kSamplesPerDuration = (int)(kSamplingFrequency * kDuration);
    }
}
