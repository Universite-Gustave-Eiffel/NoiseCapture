package org.noise_planet.noisecapture;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import org.json.JSONException;
import org.junit.Test;
import org.noise_planet.noisecapture.util.PeakFinder;
import org.noise_planet.noisecapture.util.TrafficNoiseEstimator;
import org.orbisgis.sos.FFTSignalProcessing;
import org.orbisgis.sos.Window;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrafficNoiseEstimatorTest {


    @Test
    public void testDetectTrafficPeaks() throws IOException {
        TrafficNoiseEstimator trafficNoiseEstimator = new TrafficNoiseEstimator();
        double[] laeqs = getLAeqs(TrafficNoiseEstimatorTest.class.getResourceAsStream("traffic_50kmh_3m.ogg"), false);
        laeqs = trafficNoiseEstimator.fastToSlowLeqMax(laeqs);
        List<PeakFinder.Element> peaks = trafficNoiseEstimator.getNoisePeaks(laeqs);


        //for (PeakFinder.Element el : peaks) {
        //    System.out.println(String.format(Locale.ROOT, "Find peak at %.3f s spl:  %.2f dB(A)", el.index / 1000.0, el.value));
        //}

        double[] expectedPeakTime = new double[] {1.75, 7.88, 13.4, 20.5, 31.6, 44.1, 58.5};
        for(double v : expectedPeakTime) {
            boolean found = false;
            for (PeakFinder.Element el : peaks) {
                if (Math.abs(el.index - v) <= 250) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }


    @Test
    public void testDetectTrafficPeaksMixed() throws IOException {
        TrafficNoiseEstimator trafficNoiseEstimator = new TrafficNoiseEstimator();
        double[] laeqs = getLAeqs(TrafficNoiseEstimatorTest.class.getResourceAsStream("traffic_50kmh_3m_mixed.ogg"), false);

        trafficNoiseEstimator.loadConstants(TrafficNoiseEstimatorTest.class.getResourceAsStream("coefficients_cnossos.json"));


        TrafficNoiseEstimator.Estimation estimation = trafficNoiseEstimator.getMedianPeak(trafficNoiseEstimator.fastToSlowLeqMax(laeqs));

        assertEquals(67.35, estimation.medianPeak, 0.1);
        assertEquals(4, estimation.numberOfPassby);

        trafficNoiseEstimator.setDistance(3.5);
        trafficNoiseEstimator.setSpeed(65.0);

        double gain = trafficNoiseEstimator.computeGain(estimation.medianPeak);

        assertEquals(14.1, gain, 0.1);

    }

    public static double[] getLAeqs(InputStream oggFile, boolean printValues) throws IOException {
        try (BufferedInputStream fos = new BufferedInputStream(oggFile)) {
            OrbisReader reader = new OrbisReader(fos);
            reader.setDebugMode(false);
            reader.run();
            int lastPushIndex = 0;
            DataInputStream dos = new DataInputStream(new ByteArrayInputStream(reader.getBytes()));
            Window window = new Window(FFTSignalProcessing.WINDOW_TYPE.RECTANGULAR,
                    reader.jorbisInfo.rate, FFTSignalProcessing.computeFFTCenterFrequency(16000),
                    0.125, true, 1, false);
            short[] buffer = new short[(int) (window.getWindowTime() * reader.jorbisInfo.rate)];
            List<Float> laeqsList = new ArrayList<>();
            while (dos.available() > Short.SIZE / Byte.SIZE) {
                try {
                    int sampleLen = Math.min(window.getMaximalBufferSize(), buffer.length);
                    for (int i = 0; i < sampleLen; i++) {
                        buffer[i] = dos.readShort();
                    }
                    window.pushSample(Arrays.copyOf(buffer, sampleLen));
                    if (window.getWindowIndex() != lastPushIndex) {
                        lastPushIndex = window.getWindowIndex();
                        FFTSignalProcessing.ProcessingResult res = window.getLastWindowMean();
                        float t = lastPushIndex * 0.125f;
                        laeqsList.add(res.getGlobaldBaValue());
                        if(printValues) {
                            System.out.println(String.format(Locale.ROOT, "%.3f  %.2f", t, res.getGlobaldBaValue()));
                        }
                        window.cleanWindows();
                    }
                } catch (EOFException ex) {
                    break;
                }
            }
            double[] laeqs = new double[laeqsList.size()];
            for (int i = 0; i < laeqsList.size(); i++) {
                laeqs[i] = laeqsList.get(i);
            }
            return laeqs;
        }
    }

    @Test
    public void findPeaksIncreaseDecreaseCondition() throws IOException {
        TrafficNoiseEstimator trafficNoiseEstimator = new TrafficNoiseEstimator();

        List<Double> leqs = new ArrayList<>();
        List<Double> peakOk = Arrays.asList(4.0, 11.0, 17.0, 13.0, 10.0);
        List<Double> noise = Arrays.asList(0.7, 1.0, 1.5, 1.8, 1.1, 1.7, 1.5, 1.1);
        leqs.addAll(noise);
        leqs.addAll(noise);
        leqs.addAll(noise);
        leqs.addAll(peakOk);
        leqs.addAll(noise);
        leqs.addAll(noise);
        leqs.addAll(peakOk);
        leqs.addAll(noise);
        leqs.addAll(peakOk);
        leqs.addAll(peakOk);
        leqs.addAll(noise);
        leqs.addAll(noise);
        leqs.addAll(peakOk);
        leqs.addAll(noise);
        leqs.addAll(noise);
        int index = 0;
        double[] maxLeqs = new double[leqs.size()];
        for(double value : leqs) {
            maxLeqs[index++] = value;
        }
        List<PeakFinder.Element> els = trafficNoiseEstimator.getNoisePeaks(maxLeqs);
        assertEquals(5, els.size());
    }

    public static void writeByteToFile(String path, byte[] signal) throws IOException {
        OutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(path), Short.MAX_VALUE);
        try {
            for(int i = 0; i < signal.length; i++) {
                fileOutputStream.write(signal[i]);
            }
        } finally {
            fileOutputStream.close();
        }
    }

    /**
     * From tutorial page
     * http://www.jcraft.com/jorbis/tutorial/Tutorial.html
     */
    public static class OrbisReader {
        // If you wish to debug this source, please set the variable below to true.
        private boolean debugMode = true;

        private InputStream inputStream = null;

        /*
         * We need a buffer, it's size, a count to know how many bytes we have read
         * and an index to keep track of where we are. This is standard networking
         * stuff used with read().
         */
        byte[] buffer = null;
        int bufferSize = 2048;
        int count = 0;
        int index = 0;

        /*
         * JOgg and JOrbis require fields for the converted buffer. This is a buffer
         * that is modified in regards to the number of audio channels. Naturally,
         * it will also need a size.
         */
        byte[] convertedBuffer;
        int convertedBufferSize;

        // A three-dimensional an array with PCM information.
        private float[][][] pcmInfo;

        // The index for the PCM information.
        private int[] pcmIndex;

        // Here are the four required JOgg objects...
        private Packet joggPacket = new Packet();
        private Page joggPage = new Page();
        private StreamState joggStreamState = new StreamState();
        private SyncState joggSyncState = new SyncState();

        // ... followed by the four required JOrbis objects.
        private DspState jorbisDspState = new DspState();
        private Block jorbisBlock = new Block(jorbisDspState);
        private Comment jorbisComment = new Comment();
        private Info jorbisInfo = new Info();

        private ByteArrayOutputStream bos = new ByteArrayOutputStream();
        /**
         * The constructor; will configure the <code>InputStream</code>.
         */
        OrbisReader(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        void clear() {
            bos = new ByteArrayOutputStream();
        }

        public byte[] getBytes() {
            return bos.toByteArray();
        }

        /**
         * This method is probably easiest understood by looking at the body.
         * However, it will - if no problems occur - call methods to initialize the
         * JOgg JOrbis libraries, read the header, initialize the sound system, read
         * the body of the stream and clean up.
         */
        public void run() throws IOException {
            // Check that we got an InputStream.
            if (inputStream == null) {
                System.err.println("We don't have an input stream and therefor "
                        + "cannot continue.");
                return;
            }

            // Initialize JOrbis.
            initializeJOrbis();

            /*
             * If we can read the header, we try to inialize the sound system. If we
             * could initialize the sound system, we try to read the body.
             */
            if (readHeader()) {
                if (initializeSound()) {
                    readBody();
                }
            }

            // Afterwards, we clean up.
            cleanUp();
        }

        /**
         * Initializes JOrbis. First, we initialize the <code>SyncState</code>
         * object. After that, we prepare the <code>SyncState</code> buffer. Then
         * we "initialize" our buffer, taking the data in <code>SyncState</code>.
         */
        private void initializeJOrbis() {
            debugOutput("Initializing JOrbis.");

            // Initialize SyncState
            joggSyncState.init();

            // Prepare the to SyncState internal buffer
            joggSyncState.buffer(bufferSize);

            /*
             * Fill the buffer with the data from SyncState's internal buffer. Note
             * how the size of this new buffer is different from bufferSize.
             */
            buffer = joggSyncState.data;

            debugOutput("Done initializing JOrbis.");
        }

        public boolean isDebugMode() {
            return debugMode;
        }

        public void setDebugMode(boolean debugMode) {
            this.debugMode = debugMode;
        }

        /**
         * This method reads the header of the stream, which consists of three
         * packets.
         *
         * @return true if the header was successfully read, false otherwise
         */
        private boolean readHeader() {
            debugOutput("Starting to read the header.");

            /*
             * Variable used in loops below. While we need more data, we will
             * continue to read from the InputStream.
             */
            boolean needMoreData = true;

            /*
             * We will read the first three packets of the header. We start off by
             * defining packet = 1 and increment that value whenever we have
             * successfully read another packet.
             */
            int packet = 1;

            /*
             * While we need more data (which we do until we have read the three
             * header packets), this loop reads from the stream and has a big
             * <code>switch</code> statement which does what it's supposed to do in
             * regards to the current packet.
             */
            while (needMoreData) {
                // Read from the InputStream.
                try {
                    count = inputStream.read(buffer, index, bufferSize);
                } catch (IOException exception) {
                    System.err.println("Could not read from the input stream.");
                    System.err.println(exception);
                }

                // We let SyncState know how many bytes we read.
                joggSyncState.wrote(count);

                /*
                 * We want to read the first three packets. For the first packet, we
                 * need to initialize the StreamState object and a couple of other
                 * things. For packet two and three, the procedure is the same: we
                 * take out a page, and then we take out the packet.
                 */
                switch (packet) {
                    // The first packet.
                    case 1: {
                        // We take out a page.
                        switch (joggSyncState.pageout(joggPage)) {
                            // If there is a hole in the data, we must exit.
                            case -1: {
                                System.err.println("There is a hole in the first "
                                        + "packet data.");
                                return false;
                            }

                            // If we need more data, we break to get it.
                            case 0: {
                                break;
                            }

                            /*
                             * We got where we wanted. We have successfully read the
                             * first packet, and we will now initialize and reset
                             * StreamState, and initialize the Info and Comment
                             * objects. Afterwards we will check that the page
                             * doesn't contain any errors, that the packet doesn't
                             * contain any errors and that it's Vorbis data.
                             */
                            case 1: {
                                // Initializes and resets StreamState.
                                joggStreamState.init(joggPage.serialno());
                                joggStreamState.reset();

                                // Initializes the Info and Comment objects.
                                jorbisInfo.init();
                                jorbisComment.init();

                                // Check the page (serial number and stuff).
                                if (joggStreamState.pagein(joggPage) == -1) {
                                    System.err.println("We got an error while "
                                            + "reading the first header page.");
                                    return false;
                                }

                                /*
                                 * Try to extract a packet. All other return values
                                 * than "1" indicates there's something wrong.
                                 */
                                if (joggStreamState.packetout(joggPacket) != 1) {
                                    System.err.println("We got an error while "
                                            + "reading the first header packet.");
                                    return false;
                                }

                                /*
                                 * We give the packet to the Info object, so that it
                                 * can extract the Comment-related information,
                                 * among other things. If this fails, it's not
                                 * Vorbis data.
                                 */
                                if (jorbisInfo.synthesis_headerin(jorbisComment,
                                        joggPacket) < 0) {
                                    System.err.println("We got an error while "
                                            + "interpreting the first packet. "
                                            + "Apparantly, it's not Vorbis data.");
                                    return false;
                                }

                                // We're done here, let's increment "packet".
                                packet++;
                                break;
                            }
                        }

                        /*
                         * Note how we are NOT breaking here if we have proceeded to
                         * the second packet. We don't want to read from the input
                         * stream again if it's not necessary.
                         */
                        if (packet == 1) break;
                    }

                    // The code for the second and third packets follow.
                    case 2:
                    case 3: {
                        // Try to get a new page again.
                        switch (joggSyncState.pageout(joggPage)) {
                            // If there is a hole in the data, we must exit.
                            case -1: {
                                System.err.println("There is a hole in the second "
                                        + "or third packet data.");
                                return false;
                            }

                            // If we need more data, we break to get it.
                            case 0: {
                                break;
                            }

                            /*
                             * Here is where we take the page, extract a packet and
                             * and (if everything goes well) give the information to
                             * the Info and Comment objects like we did above.
                             */
                            case 1: {
                                // Share the page with the StreamState object.
                                joggStreamState.pagein(joggPage);

                                /*
                                 * Just like the switch(...packetout...) lines
                                 * above.
                                 */
                                switch (joggStreamState.packetout(joggPacket)) {
                                    // If there is a hole in the data, we must exit.
                                    case -1: {
                                        System.err
                                                .println("There is a hole in the first"
                                                        + "packet data.");
                                        return false;
                                    }

                                    // If we need more data, we break to get it.
                                    case 0: {
                                        break;
                                    }

                                    // We got a packet, let's process it.
                                    case 1: {
                                        /*
                                         * Like above, we give the packet to the
                                         * Info and Comment objects.
                                         */
                                        jorbisInfo.synthesis_headerin(
                                                jorbisComment, joggPacket);

                                        // Increment packet.
                                        packet++;

                                        if (packet == 4) {
                                            /*
                                             * There is no fourth packet, so we will
                                             * just end the loop here.
                                             */
                                            needMoreData = false;
                                        }

                                        break;
                                    }
                                }

                                break;
                            }
                        }

                        break;
                    }
                }

                // We get the new index and an updated buffer.
                index = joggSyncState.buffer(bufferSize);
                buffer = joggSyncState.data;

                /*
                 * If we need more data but can't get it, the stream doesn't contain
                 * enough information.
                 */
                if (count == 0 && needMoreData) {
                    System.err.println("Not enough header data was supplied.");
                    return false;
                }
            }

            debugOutput("Finished reading the header.");

            return true;
        }

        /**
         * This method starts the sound system. It starts with initializing the
         * <code>DspState</code> object, after which it sets up the
         * <code>Block</code> object. Last but not least, it opens a line to the
         * source data line.
         *
         * @return true if the sound system was successfully started, false
         * otherwise
         */
        private boolean initializeSound() {
            debugOutput("Initializing synthesis.");

            // This buffer is used by the decoding method.
            convertedBufferSize = bufferSize * 2;
            convertedBuffer = new byte[convertedBufferSize];

            // Initializes the DSP synthesis.
            jorbisDspState.synthesis_init(jorbisInfo);

            // Make the Block object aware of the DSP.
            jorbisBlock.init(jorbisDspState);

            /*
             * We create the PCM variables. The index is an array with the same
             * length as the number of audio channels.
             */
            pcmInfo = new float[1][][];
            pcmIndex = new int[jorbisInfo.channels];

            debugOutput("Done initializing synthesis.");

            return true;
        }

        /**
         * This method reads the entire stream body. Whenever it extracts a packet,
         * it will decode it by calling <code>decodeCurrentPacket()</code>.
         */
        private void readBody() throws IOException {
            debugOutput("Reading the body.");

            /*
             * Variable used in loops below, like in readHeader(). While we need
             * more data, we will continue to read from the InputStream.
             */
            boolean needMoreData = true;

            while (needMoreData) {
                switch (joggSyncState.pageout(joggPage)) {
                    // If there is a hole in the data, we just proceed.
                    case -1: {
                        debugOutput("There is a hole in the data. We proceed.");
                    }

                    // If we need more data, we break to get it.
                    case 0: {
                        break;
                    }

                    // If we have successfully checked out a page, we continue.
                    case 1: {
                        // Give the page to the StreamState object.
                        joggStreamState.pagein(joggPage);

                        // If granulepos() returns "0", we don't need more data.
                        if (joggPage.granulepos() == 0) {
                            needMoreData = false;
                            break;
                        }

                        // Here is where we process the packets.
                        processPackets:
                        while (true) {
                            switch (joggStreamState.packetout(joggPacket)) {
                                // Is it a hole in the data?
                                case -1: {
                                    debugOutput("There is a hole in the data, we "
                                            + "continue though.");
                                }

                                // If we need more data, we break to get it.
                                case 0: {
                                    break processPackets;
                                }

                                /*
                                 * If we have the data we need, we decode the
                                 * packet.
                                 */
                                case 1: {
                                    decodeCurrentPacket();
                                }
                            }
                        }

                        /*
                         * If the page is the end-of-stream, we don't need more
                         * data.
                         */
                        if (joggPage.eos() != 0) needMoreData = false;
                    }
                }

                // If we need more data...
                if (needMoreData) {
                    // We get the new index and an updated buffer.
                    index = joggSyncState.buffer(bufferSize);
                    buffer = joggSyncState.data;

                    // Read from the InputStream.
                    try {
                        count = inputStream.read(buffer, index, bufferSize);
                    } catch (Exception e) {
                        System.err.println(e);
                        return;
                    }

                    // We let SyncState know how many bytes we read.
                    joggSyncState.wrote(count);

                    // There's no more data in the stream.
                    if (count == 0) needMoreData = false;
                }
            }
            debugOutput("Done reading the body.");
        }

        /**
         * A clean-up method, called when everything is finished. Clears the
         * JOgg/JOrbis objects and closes the <code>InputStream</code>.
         */
        private void cleanUp() {
            debugOutput("Cleaning up.");

            // Clear the necessary JOgg/JOrbis objects.
            joggStreamState.clear();
            jorbisBlock.clear();
            jorbisDspState.clear();
            jorbisInfo.clear();
            joggSyncState.clear();

            // Closes the stream.
            try {
                if (inputStream != null) inputStream.close();
            } catch (Exception e) {
            }

            debugOutput("Done cleaning up.");
        }

        /**
         * Decodes the current packet and sends it to the audio output line.
         */
        private void decodeCurrentPacket() throws IOException {
            int samples;

            // Check that the packet is a audio data packet etc.
            if (jorbisBlock.synthesis(joggPacket) == 0) {
                // Give the block to the DspState object.
                jorbisDspState.synthesis_blockin(jorbisBlock);
            }

            // We need to know how many samples to process.
            int range;

            /*
             * Get the PCM information and count the samples. And while these
             * samples are more than zero...
             */
            while ((samples = jorbisDspState.synthesis_pcmout(pcmInfo, pcmIndex))
                    > 0) {
                // We need to know for how many samples we are going to process.
                if (samples < convertedBufferSize) {
                    range = samples;
                } else {
                    range = convertedBufferSize;
                }

                // For each channel...
                for (int i = 0; i < jorbisInfo.channels; i++) {
                    int sampleIndex = i * 2;

                    // For every sample in our range...
                    for (int j = 0; j < range; j++) {
                        /*
                         * Get the PCM value for the channel at the correct
                         * position.
                         */
                        int value = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (int) (pcmInfo[0][i][pcmIndex[i] + j] * Short.MAX_VALUE)));

                        /*
                         * Take our value and split it into two, one with the last
                         * byte and one with the first byte.
                         */
                        convertedBuffer[sampleIndex] = (byte) (value >>> 8);
                        convertedBuffer[sampleIndex + 1] = (byte) (value);

                        /*
                         * Move the sample index forward by two (since that's how
                         * many values we get at once) times the number of channels.
                         */
                        sampleIndex += 2 * (jorbisInfo.channels);
                    }
                }

                // Write the buffer to the audio output line.
                //outputLine.write(convertedBuffer, 0, 2 * jorbisInfo.channels
                //        * range);
                bos.write(convertedBuffer, 0, 2 * jorbisInfo.channels * range);

                // Update the DspState object.
                jorbisDspState.synthesis_read(range);
            }
        }

        /**
         * This method is being called internally to output debug information
         * whenever that is wanted.
         *
         * @param output the debug output information
         */
        private void debugOutput(String output) {
            if (debugMode) System.out.println("Debug: " + output);
        }
    }
}