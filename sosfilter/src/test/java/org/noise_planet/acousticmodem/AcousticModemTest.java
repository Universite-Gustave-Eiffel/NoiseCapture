package org.noise_planet.acousticmodem;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

/**
 *
 */
public class AcousticModemTest {

    @Test
    public void TestEncodeDecode() throws Exception {
        String messageInput = "U1_76.8";
        AcousticModem acousticModem = new AcousticModem(new Settings());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        acousticModem.encodeStream(new ByteArrayInputStream(messageInput.getBytes()), baos);

        byte[] samples = baos.toByteArray();
        acousticModem.decode(, samples);
    }
}