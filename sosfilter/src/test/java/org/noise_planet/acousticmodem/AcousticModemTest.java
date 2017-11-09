package org.noise_planet.acousticmodem;

import org.junit.Test;
import org.orbisgis.sos.FFTSignalProcessing;
import org.orbisgis.sos.SOSSignalProcessing;
import org.orbisgis.sos.ThirdOctaveBandsFiltering;
import org.orbisgis.sos.Window;

import java.util.Arrays;

/**
 *
 */
public class AcousticModemTest {
    private static final int[] UT_FREQUENCIES = new int[]{
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[11],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[12],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[13],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[14],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[15],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[16],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[17],
            (int)ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED[18]
    };
    public static final double REF_SOUND_PRESSURE = 1 / Math.pow(10, FFTSignalProcessing.DB_FS_REFERENCE / 20);
    @Test
    public void TestEncodeDecode() throws Exception {
        String messageInput = "U1_76.8";

        // Convert data into audio signal
        AcousticModem acousticModem = new AcousticModem(new Settings(44100, 0.150, Settings.wordsFrom8frequencies(UT_FREQUENCIES)));
        byte[] data = messageInput.getBytes();
        int signalLength = acousticModem.getSignalLength(data, 0, data.length);
        short[] signal = new short[signalLength];
        acousticModem.wordsToSignal(data, 0, data.length, signal, 0, (short) 2500);

        // Convert audio signal back to data

        Window window = new Window(Window.WINDOW_TYPE.RECTANGULAR, 44100, ThirdOctaveBandsFiltering.STANDARD_FREQUENCIES_REDUCED, 0.125, false, REF_SOUND_PRESSURE, false);

        int idSampleStart = 0;
        int packetSize = 1024;
        int lastPushIndex = 0;
        while (idSampleStart < signal.length) {
            // Compute sub-sample size in order to not skip samples
            int sampleLen = Math.min(window.getMaximalBufferSize(), packetSize);
            short[] samples = Arrays.copyOfRange(signal, idSampleStart, Math.min(signal.length, idSampleStart + sampleLen));
            idSampleStart += samples.length;

            window.pushSample(samples);
            if (window.getWindowIndex() != lastPushIndex) {
                lastPushIndex = window.getWindowIndex();
                FFTSignalProcessing.ProcessingResult res = window.getLastWindowMean();
                

                window.cleanWindows();
            }
        }


    }
}