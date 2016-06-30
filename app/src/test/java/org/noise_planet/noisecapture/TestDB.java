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

package org.noise_planet.noisecapture;

import android.net.Uri;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit test of SQLLite db manager of NoiseCapture
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class TestDB {

    @Rule
    public TemporaryFolder folder= new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        folder.create();
    }

    @Test
    public void testCreate() throws URISyntaxException {
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

        measurementManager.updateRecordFinal(recordId, (float)leqBatch.computeGlobalLeq(), 2, 2.31f);


        Storage.Record incompleteRecord = measurementManager.getRecord(recordId);
        assertNull(incompleteRecord.getPleasantness());
        assertEquals((float)leqBatch.computeGlobalLeq(), incompleteRecord.getLeqMean(), 0.01);
        assertEquals(2.31f, incompleteRecord.getCalibrationGain(), 0.01);

        // Check update user input
        measurementManager.updateRecordUserInput(recordId, "This is a description",
                (short)2,new String[]{Storage.TAGS[0], Storage.TAGS[4]},
                Uri.fromFile(new File(TestDB.class.getResource("calibration.png").getFile())));
        Storage.Record record = measurementManager.getRecord(recordId);
        assertEquals(Uri.fromFile(new File(TestDB.class.getResource("calibration.png").getFile())),
                record.getPhotoUri());

        List<String> selectedTags = measurementManager.getTags(recordId);
        assertEquals(Storage.TAGS[0], selectedTags.get(0));
        assertEquals(Storage.TAGS[4], selectedTags.get(1));

    }

    @Test
    public void testExport() throws URISyntaxException, IOException {
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

        measurementManager.updateRecordFinal(recordId, (float)leqBatch.computeGlobalLeq(), 2, 2.31f);
        measurementManager.updateRecordUserInput(recordId, "This is a description",
                (short)2,new String[]{Storage.TAGS[0], Storage.TAGS[4]},
                Uri.fromFile(new File(TestDB.class.getResource("calibration.png").getFile())));

        // Export to zip file
        File testFile = folder.newFile("testexport.zip");
        History.doBuildZip(testFile, RuntimeEnvironment.application, recordId);

        // Check properties of zip file
        FileInputStream fileInputStream = new FileInputStream(testFile);
        Properties meta = null;
        try {
            ZipInputStream zipInputStream = new ZipInputStream(fileInputStream);
            ZipEntry zipEntry;
            while((zipEntry = zipInputStream.getNextEntry()) != null) {
                if ("meta.properties".equals(zipEntry.getName())) {
                    meta = new Properties();
                    meta.load(zipInputStream);
                }
            }
        } finally {
            fileInputStream.close();
        }
        assertNotNull(meta);
        assertEquals(2.31f, Float.valueOf(
                meta.getProperty(MeasurementExport.PROP_GAIN_CALIBRATION)), 0.01f);
        assertEquals((float)leqBatch.computeGlobalLeq(),
                Float.valueOf(meta.getProperty(Storage.Record.COLUMN_LEQ_MEAN)), 0.01f);
    }

}
