package org.noise_planet.noisecapture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit test of SQLLite db manager of NoiseCapture
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class TestDB {

    @Test
    public void testCreate() {
        MeasurementManager measurementManager =
                new MeasurementManager(RuntimeEnvironment.application);

        int recordId = measurementManager.addRecord();
        Storage.Leq leq = new Storage.Leq(recordId, -1, System.currentTimeMillis(), 12, 15, 50.d,
                15.f, 4.f, 4.5f,System.currentTimeMillis());
        List<Storage.LeqValue> leqValues = new ArrayList<Storage.LeqValue>();

        leqValues .add(new Storage.LeqValue(-1, 125, 65));
        leqValues .add(new Storage.LeqValue(-1, 250, 55));
        leqValues .add(new Storage.LeqValue(-1, 500, 56));
        leqValues .add(new Storage.LeqValue(-1, 1000, 58));
        leqValues .add(new Storage.LeqValue(-1, 2000, 48));
        leqValues .add(new Storage.LeqValue(-1, 4000, 49));
        leqValues .add(new Storage.LeqValue(-1, 8000, 45));
        leqValues .add(new Storage.LeqValue(-1, 16000, 41));
        MeasurementManager.LeqBatch leqBatch = new MeasurementManager.LeqBatch(leq,leqValues);
        measurementManager.addLeqBatch(leqBatch);
        leq = new Storage.Leq(recordId, -1, System.currentTimeMillis(), 12.01, 15.02, 51.d,
                12.f, 3.02f, 5f,System.currentTimeMillis());
        leqBatch = new MeasurementManager.LeqBatch(leq,leqValues);
        measurementManager.addLeqBatch(leqBatch);

        // Fetch data
        List<MeasurementManager.LeqBatch> storedLeq =
                measurementManager.getRecordLocations(recordId, true);
        assertEquals(2, storedLeq.size());
        MeasurementManager.LeqBatch checkLeq = storedLeq.remove(0);
        assertEquals(15, checkLeq.getLeq().getLongitude(), 0.01);
        assertEquals(12, checkLeq.getLeq().getLatitude(), 0.01);
        assertEquals(50, checkLeq.getLeq().getAltitude(), 0.01);
        checkLeq = storedLeq.remove(0);
        assertEquals(15.02, checkLeq.getLeq().getLongitude(), 0.01);
        assertEquals(12.01, checkLeq.getLeq().getLatitude(), 0.01);
        assertEquals(51, checkLeq.getLeq().getAltitude(), 0.01);

        // Check update ending measure

        measurementManager.updateRecordFinal(recordId, 49, 2);


        // Check update user input
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeFile(
                TestDB.class.getResource("calibration.png").getPath(),options);
        measurementManager.updateRecordUserInput(recordId, "This is a description",
                (short)2,new int[]{0, 1, 4},bitmap , Uri.parse("") );
        Storage.Record record = measurementManager.getRecord(recordId, true);
        assertTrue(record.getThumbnail() != null);
        Bitmap thumbFromDb = record.getThumbnail();
        assertEquals(bitmap.getHeight(), thumbFromDb.getHeight());
        assertEquals(bitmap.getWidth(), thumbFromDb.getWidth());
        assertEquals(bitmap.getPixel(5,5), thumbFromDb.getPixel(5,5));

    }

}
