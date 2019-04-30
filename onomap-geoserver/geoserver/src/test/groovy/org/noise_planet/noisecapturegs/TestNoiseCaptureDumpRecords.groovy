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
 * Copyright (C) 2007-2016 - IFSTTAR - LAE
 * Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
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

package org.noise_planet.noisecapturegs

import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.junit.Before

import java.sql.Statement
import java.util.zip.ZipInputStream

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureDumpRecords extends JdbcTestCase {

    @Before
    void setUp() {
        super.setUp()
        Statement st = connection.createStatement()
        // Init schema
        st.execute(new File(TestNoiseCaptureDumpRecords.class.getResource("inith2.sql").getFile()).text)
        // Load timezone file
        st.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        st.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        // ut_deps has been derived from https://www.data.gouv.fr/fr/datasets/contours-des-departements-francais-issus-d-openstreetmap/ (c) osm
        // See ut_deps.txt for more details
        st.execute("CALL GEOJSONREAD('"+TestNoiseCaptureProcess.getResource("ut_deps.geojson").file+"', 'GADM28');")
    }

    // Avoid JSonSlurper to close the input stream
    private static class UnClosableInputStream extends InputStream {
        private InputStream decoratedInputStream;

        UnClosableInputStream(InputStream decoratedInputStream) {
            this.decoratedInputStream = decoratedInputStream
        }

        @Override
        int read(byte[] bytes) throws IOException {
            return decoratedInputStream.read(bytes)
        }

        @Override
        int read(byte[] bytes, int i, int i1) throws IOException {
            return decoratedInputStream.read(bytes, i, i1)
        }

        @Override
        long skip(long l) throws IOException {
            return decoratedInputStream.skip(l)
        }

        @Override
        int available() throws IOException {
            return decoratedInputStream.available()
        }

        @Override
        void close() throws IOException {
            // Ignore
        }

        @Override
        void mark(int i) {
            decoratedInputStream.mark(i)
        }

        @Override
        void reset() throws IOException {
            decoratedInputStream.reset()
        }

        @Override
        boolean markSupported() {
            return decoratedInputStream.markSupported()
        }

        @Override
        int read() throws IOException {
            return decoratedInputStream.read()
        }
    }

    void testTracksExport() {
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_a23261b3-b569-4363-95be-e5578d694238.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f720018a-a5db-4859-bd7d-377d29356c6f.zip").file))
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Insert measure data
        // insert records
        File tmpFolder = folder.newFolder()
        List<String> createdFiles = new nc_dump_records().getDump(connection,tmpFolder, true, false, false)
        assertEquals(2, createdFiles.size())
        assertEquals("France.zip", new File((String)createdFiles.get(0)).getName())
        assertEquals("Italy.zip", new File((String)createdFiles.get(1)).getName())

        assertTrue(new File((String)createdFiles.get(0)).exists())
        // Load Json
        new ZipInputStream(new FileInputStream(createdFiles.get(0))).withStream { zipInputStream ->

            assertEquals("France_Pays de la Loire_Loire-Atlantique.tracks.geojson",zipInputStream.getNextEntry().getName())
            def result = new JsonSlurper().parse(new UnClosableInputStream(zipInputStream), "UTF-8");
            assertNotNull(result)
            // Check content first file
            assertEquals(1, result.features.size())
            assertEquals("Polygon", result.features[0].geometry.type)
            assertEquals(5, result.features[0].geometry.coordinates[0].size())
            assertEquals("2016-06-09T14:16:58+02:00", result.features[0].properties.time_ISO8601)
            assertEquals(69, result.features[0].properties.pleasantness)
            assertEquals(1465474618000, result.features[0].properties.time_epoch)
            assertEquals(["test", "indoor", "silent"], result.features[0].properties.tags)
            // Check content second file in the zip file
            assertEquals("France_Poitou-Charentes_Charente-Maritime.tracks.geojson",zipInputStream.getNextEntry().getName())
            result = new JsonSlurper().parse(new UnClosableInputStream(zipInputStream), "UTF-8");
            assertNotNull(result)
            // Check content
            assertEquals(1, result.features.size())
            assertEquals("Polygon", result.features[0].geometry.type)
            assertEquals(5, result.features[0].geometry.coordinates[0].size())
            assertEquals("2017-01-24T17:49:11+01:00", result.features[0].properties.time_ISO8601)
            assertNull(result.features[0].properties.pleasantness)
            assertEquals(["test", "chatting", "human"], result.features[0].properties.tags)
            assertEquals(1485276551000, result.features[0].properties.time_epoch)
            def coordinates = [[[-1.15651469, 46.14685535], [-1.15651469, 46.1482328], [-1.1534035, 46.1482328], [-1.1534035, 46.14685535], [-1.15651469, 46.14685535]]]
            assertEquals(coordinates, result.features[0].geometry.coordinates)

            // Check content of second file

        }

        new ZipInputStream(new FileInputStream(createdFiles.get(1))).withStream { zipInputStream ->
            assertEquals("Italy_Umbria_Perugia.tracks.geojson", zipInputStream.getNextEntry().getName())
            def result = new JsonSlurper().parse(new UnClosableInputStream(zipInputStream), "UTF-8");
            assertNotNull(result)
            // Check content first file
            assertEquals(1, result.features.size())
            assertEquals("Polygon", result.features[0].geometry.type)
            assertEquals(5, result.features[0].geometry.coordinates[0].size())
            assertEquals("2016-10-12T08:33:49+02:00", result.features[0].properties.time_ISO8601)
            assertNull(result.features[0].properties.pleasantness)
            assertEquals(1476254029000, result.features[0].properties.time_epoch)
            assertEquals(["test", "footsteps", "human"], result.features[0].properties.tags)
        }
    }

    void testMeasurementExport() {
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_a23261b3-b569-4363-95be-e5578d694238.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f720018a-a5db-4859-bd7d-377d29356c6f.zip").file))
        Sql.LOG.level = java.util.logging.Level.SEVERE

        File tmpFolder = folder.newFolder()
        List<String> createdFiles = new nc_dump_records().getDump(connection,tmpFolder, false, true, false)
        assertEquals(2, createdFiles.size())




        assertTrue(new File((String)createdFiles.get(0)).exists())
        // Load Json
        new ZipInputStream(new FileInputStream(createdFiles.get(0))).withStream { zipInputStream ->
            assertEquals("France_Pays de la Loire_Loire-Atlantique.points.geojson", zipInputStream.getNextEntry().getName())
            def result = new JsonSlurper().parse(new UnClosableInputStream(zipInputStream), "UTF-8");
            assertNotNull(result)
            // Check content first file
            assertEquals(66, result.features.size())
            assertEquals("Point", result.features[0].geometry.type)
            assertEquals(3, result.features[0].geometry.coordinates.size())
            assertEquals(14.19, (Double)result.features[0].properties.accuracy, 0.01)
            assertEquals(0.14, (Double)result.features[0].properties.speed, 0.01)
            assertEquals(0.0, (Double)result.features[0].properties.orientation, 0.01)
            assertEquals(1, (Double)result.features[0].properties.pk_track)
            assertEquals("2016-06-09T14:17:19+02:00", result.features[0].properties.time_ISO8601)
            assertEquals(1465474639000, result.features[0].properties.time_epoch)
            assertEquals("2016-06-09T14:17:21+02:00", result.features[0].properties.time_gps_ISO8601)
            assertEquals(1465474641000, result.features[0].properties.time_gps_epoch)
            // Check content second file in the zip file
            assertEquals("France_Poitou-Charentes_Charente-Maritime.points.geojson",zipInputStream.getNextEntry().getName())
            result = new JsonSlurper().parse(new UnClosableInputStream(zipInputStream), "UTF-8");
            assertNotNull(result)
            // Check content second file
            assertEquals(218, result.features.size())
            assertEquals("Point", result.features[0].geometry.type)
            assertEquals(3, result.features[0].geometry.coordinates.size())
            assertEquals(4.0, (Double)result.features[0].properties.accuracy, 0.01)
            assertEquals(1.102, (Double)result.features[0].properties.speed, 0.01)
            assertEquals(79.2, (Double)result.features[0].properties.orientation, 0.01)
            assertEquals(2, (Double)result.features[0].properties.pk_track)
            assertEquals("2017-01-24T17:49:11+01:00", result.features[0].properties.time_ISO8601)
            assertEquals(1485276551000, result.features[0].properties.time_epoch)
            assertEquals("2017-01-24T17:49:08+01:00", result.features[0].properties.time_gps_ISO8601)
            assertEquals(1485276548000, result.features[0].properties.time_gps_epoch)
        }

        new ZipInputStream(new FileInputStream(createdFiles.get(1))).withStream { zipInputStream ->
            assertEquals("Italy_Umbria_Perugia.points.geojson", zipInputStream.getNextEntry().getName())
            def result = new JsonSlurper().parse(new UnClosableInputStream(zipInputStream), "UTF-8");
            assertNotNull(result)
            // Check content first file
            assertEquals(432, result.features.size())
            assertEquals("Point", result.features[0].geometry.type)
            assertEquals(12.38, (double)result.features[0].geometry.coordinates[0], 0.01)
            assertEquals(43.10, (double)result.features[0].geometry.coordinates[1], 0.01)
            assertEquals(490.16, (double)result.features[0].geometry.coordinates[2], 0.01)
            assertEquals(3, result.features[0].geometry.coordinates.size())
            assertEquals(11.582, (Double)result.features[0].properties.accuracy, 0.01)
            assertEquals(1.19, (Double)result.features[0].properties.speed, 0.01)
            assertEquals(0.0, (Double)result.features[0].properties.orientation, 0.01)
            assertEquals(3, (Double)result.features[0].properties.pk_track)
            assertEquals("2016-10-12T08:33:56+02:00", result.features[0].properties.time_ISO8601)
            assertEquals(1476254036000, result.features[0].properties.time_epoch)
            assertEquals("2016-10-12T08:33:56+02:00", result.features[0].properties.time_gps_ISO8601)
            assertEquals(1476254036000, result.features[0].properties.time_gps_epoch)
        }
    }

    void testHexaExport() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Feed reference levels
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("gevfit_of_stations.txt").toURI(), "stations")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_mu.txt").toURI(), "time_matrix_mu")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_sigma.txt").toURI(), "time_matrix_sigma")
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_a23261b3-b569-4363-95be-e5578d694238.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f720018a-a5db-4859-bd7d-377d29356c6f.zip").file))
        // convert to hexagons
        assertEquals(43, new nc_process().process(connection, 10))
        File tmpFolder = folder.newFolder()
        List<String> createdFiles = new nc_dump_records().getDump(connection,tmpFolder, false, false, true)
        assertEquals(2, createdFiles.size())
        createdFiles.sort()
        assertTrue(new File((String)createdFiles.get(0)).exists())
        // Load Json
        new ZipInputStream(new FileInputStream(createdFiles.get(0))).withStream { zipInputStream ->
            assertEquals("France_Pays de la Loire_Loire-Atlantique.areas.geojson", zipInputStream.getNextEntry().getName())
            def result = new JsonSlurper().parse(new UnClosableInputStream(zipInputStream), "UTF-8");
            assertNotNull(result)
            // Check content first file
            assertEquals(4, result.features.size())
            // Search for a specific hexagon
            assertEquals("Polygon", result.features[0].geometry.type)
            assertEquals(7, result.features[0].geometry.coordinates[0].size())
            assertEquals(-139656, result.features[0].properties.cell_q);
            assertEquals(265210, result.features[0].properties.cell_r);
            assertEquals("2016-06-09T14:17:25+02:00", result.features[0].properties.first_measure_ISO_8601);
            assertEquals(1465474645000, result.features[0].properties.first_measure_epoch);
            assertEquals(62.37, (Double)result.features[0].properties.la50, 0.01);
            assertEquals(69.73, (Double)result.features[0].properties.laeq, 0.01);
            assertEquals("2016-06-09T14:18:02+02:00", result.features[0].properties.last_measure_ISO_8601);
            assertEquals(1465474682000, result.features[0].properties.last_measure_epoch);
            assertEquals(72, result.features[0].properties.leq_profile.size());
            assertEquals(69.0, (Double)result.features[0].properties.mean_pleasantness, 0.01);
            assertEquals(30, result.features[0].properties.measure_count);
            assertEquals(69.7, (double)result.features[0].properties.leq_profile[14], 0.1);
        }
        new ZipInputStream(new FileInputStream(createdFiles.get(1))).withStream { zipInputStream ->
            assertEquals("Italy_Umbria_Perugia.areas.geojson", zipInputStream.getNextEntry().getName())
            def result = new JsonSlurper().parse(new UnClosableInputStream(zipInputStream), "UTF-8");
            assertNotNull(result)
            // Check content first file
            assertEquals(21, result.features.size())
            assertEquals("Polygon", result.features[0].geometry.type)
            assertEquals(7, result.features[0].geometry.coordinates[0].size())
            assertEquals(-65335, result.features[0].properties.cell_q);
            assertEquals(236823, result.features[0].properties.cell_r);
            assertEquals("2016-10-12T08:38:58+02:00", result.features[0].properties.first_measure_ISO_8601);
            assertEquals(1476254338000, result.features[0].properties.first_measure_epoch);
            assertEquals(65.80, (Double)result.features[0].properties.la50, 0.01);
            assertEquals(66.43, (Double)result.features[0].properties.laeq, 0.01);
            assertEquals("2016-10-12T08:39:04+02:00", result.features[0].properties.last_measure_ISO_8601);
            assertEquals(1476254344000, result.features[0].properties.last_measure_epoch);
            assertEquals(72, result.features[0].properties.leq_profile.size());
            assertNull(result.features[0].properties.mean_pleasantness);
            assertEquals(7, result.features[0].properties.measure_count);
        }
    }

    void testExportDayFilter() {
        Sql.LOG.level = java.util.logging.Level.SEVERE
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f720018a-a5db-4859-bd7d-377d29356c6f.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_a23261b3-b569-4363-95be-e5578d694238.zip").file))
        new nc_process().process(connection, 15.0f)

        // Check with all record done today and filter 1 day

        Sql sql = new Sql(connection)
        // Change records date
        sql.execute("UPDATE noisecapture_track SET record_utc = NOW()::date")
        // Insert measure data
        // insert records
        File tmpFolder = folder.newFolder()
        List<String> createdFiles = new nc_dump_records().getDump(connection,tmpFolder, true, true, true, 1)
        assertEquals(2, createdFiles.size())
        assertEquals("France.zip", new File((String)createdFiles.get(0)).getName())
        assertEquals("Italy.zip", new File((String)createdFiles.get(1)).getName())

        // Check with all records older than the set filter

        // Change records date
        sql.execute("UPDATE noisecapture_track SET record_utc = NOW()::date - 7")
        createdFiles = new nc_dump_records().getDump(connection,tmpFolder, true, true, true, 1)
        assertEquals(0, createdFiles.size())
    }
}
