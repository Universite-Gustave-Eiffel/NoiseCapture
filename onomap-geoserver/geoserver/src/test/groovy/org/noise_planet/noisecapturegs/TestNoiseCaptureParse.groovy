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

import org.h2.Driver
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.nio.file.Path
import java.sql.Connection
import java.sql.Statement

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureParse  extends GroovyTestCase {
    Connection connection;


    @Rule
    public TemporaryFolder folder= new TemporaryFolder(new File("."));

    @Before
    void setUp() {
        folder.create()
        connection = Driver.load().connect("jdbc:h2:"+new File(folder.newFolder(),"test").getAbsolutePath(), null)
        Statement st = connection.createStatement()
        // Init spatial
        st.execute("CREATE ALIAS IF NOT EXISTS H2GIS_EXTENSION FOR \"org.h2gis.ext.H2GISExtension.load\";\n" +
                "CALL H2GIS_EXTENSION();")
        // Init schema
        st.execute(new File(TestNoiseCaptureParse.class.getResource("inith2.sql").getFile()).text)
    }

    @After
    void tearDown() {
        connection.close();
    }

    void testParse1() {
        new nc_parse().processFile(connection, new File(TestNoiseCaptureParse.getResource("track1.zip").file))

        // Read db; check content
        
    }
}
