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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.h2.Driver
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.sql.Connection
import java.sql.Statement
import java.sql.Timestamp
import java.util.zip.GZIPInputStream

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureDumpRecords extends GroovyTestCase {
    static Connection connection;


    @Rule
    public static TemporaryFolder folder= new TemporaryFolder(new File("build"));

    @BeforeClass
    void setUp() {
        folder.create()
        connection = Driver.load().connect("jdbc:h2:"+new File(folder.newFolder(),"test;MODE=PostgreSQL").getAbsolutePath(), null)
        Statement st = connection.createStatement()
        // Init spatial
        st.execute("CREATE ALIAS IF NOT EXISTS H2GIS_EXTENSION FOR \"org.h2gis.ext.H2GISExtension.load\";\n" +
                "CALL H2GIS_EXTENSION();")
        // Init schema
        st.execute(new File(TestNoiseCaptureDumpRecords.class.getResource("inith2.sql").getFile()).text)
        // Load timezone file
        st.execute("CALL FILE_TABLE('"+TestNoiseCaptureProcess.getResource("tz_world.shp").file+"', 'TZ_WORLD');")
        st.execute("CREATE SPATIAL INDEX ON TZ_WORLD(THE_GEOM)")
        // ut_deps has been derived from https://www.data.gouv.fr/fr/datasets/contours-des-departements-francais-issus-d-openstreetmap/ (c) osm
        // See ut_deps.txt for more details
        st.execute("CALL GEOJSONREAD('"+TestNoiseCaptureProcess.getResource("ut_deps.geojson").file+"', 'GADM28');")
    }

    @After
    void tearDown() {
        connection.close();
        folder.delete()
    }

    static File ungzipFile(File file, String newFileName) {
        byte[] buffer = new byte[1024];
        File outFile = new File(file.getParentFile(), newFileName);
        new FileInputStream(file).withStream { gzipFile ->
            new GZIPInputStream(gzipFile).withStream { gzis ->
                new FileOutputStream(outFile).withStream { gzipOutFile ->
                    int len;
                    while ((len = gzis.read(buffer)) > 0) {
                        gzipOutFile.write(buffer, 0, len);
                    }
                    gzipOutFile.flush()
                }
            }
        }
        return outFile;
    }


    void testTracksExport() {
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_a23261b3-b569-4363-95be-e5578d694238.zip").file))
        Sql.LOG.level = java.util.logging.Level.SEVERE
        Sql sql = new Sql(connection)
        // Insert measure data
        // insert records
        File tmpFolder = folder.newFolder()
        List<String> createdFiles = new nc_dump_records().getDump(connection,tmpFolder, true, false, false)
        assertEquals(2, createdFiles.size())
        assertEquals("France_Pays de la Loire_Loire-Atlantique.tracks.geojson.gz", new File((String)createdFiles.get(0)).getName())
        assertEquals("France_Poitou-Charentes_Charente-Maritime.tracks.geojson.gz", new File((String)createdFiles.get(1)).getName())

        assertTrue(new File((String)createdFiles.get(0)).exists())
        // Load GeoJSON file
        File uncompressedFile = ungzipFile(new File(createdFiles.get(0)), "testdump.geojson")
        // Load Json
        def result = new JsonSlurper().parse(uncompressedFile, "UTF-8");
        assertNotNull(result)
        // Check content first file
        assertEquals(1, result.features.size())
        assertEquals("Polygon", result.features[0].geometry.type)
        assertEquals(5, result.features[0].geometry.coordinates[0].size())
        assertEquals("2016-06-09T14:16:58+02:00", result.features[0].properties.time_ISO8601)
        assertEquals(69, result.features[0].properties.pleasantness)
        assertEquals(1465474618000, result.features[0].properties.time_epoch)
        assertEquals(["test", "indoor", "silent"], result.features[0].properties.tags)
        // Check content second file
        assertTrue(new File((String)createdFiles.get(1)).exists())
        // Load GeoJSON file
        uncompressedFile = ungzipFile(new File(createdFiles.get(1)), "testdump.geojson")
        // Load Json
        result = new JsonSlurper().parse(uncompressedFile, "UTF-8");
        assertNotNull(result)
        // Check content
        assertEquals(1, result.features.size())
        assertEquals("Polygon", result.features[0].geometry.type)
        assertEquals(5, result.features[0].geometry.coordinates[0].size())
        assertEquals("2017-01-24T17:49:11+01:00", result.features[0].properties.time_ISO8601)
        assertNull(result.features[0].properties.pleasantness)
        assertEquals(["test", "chatting", "human"], result.features[0].properties.tags)
        assertEquals(1485276551000, result.features[0].properties.time_epoch)
        def coordinates = [[[-1.15651469, 46.14685535], [-1.1534035, 46.14685535], [-1.1534035, 46.1482328], [-1.15651469, 46.1482328], [-1.15651469, 46.14685535]]]
        assertEquals(coordinates, result.features[0].geometry.coordinates)

    }

    void testHexaExport() {

        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("gevfit_of_stations.txt").toURI(), "stations")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_mu.txt").toURI(), "time_matrix_mu")
        new nc_feed_stats().processInput(connection,
                TestNoiseCaptureProcess.getResource("delta_matrix_sigma.txt").toURI(), "time_matrix_sigma")
        // Parse file to database
        new nc_parse().processFile(connection,
                new File(TestNoiseCaptureDumpRecords.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
        // convert to hexagons
        new nc_process().process(connection, 10)
    }
}
