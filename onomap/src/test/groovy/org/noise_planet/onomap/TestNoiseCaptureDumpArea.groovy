/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */
package org.noise_planet.onomap

import groovy.json.JsonSlurper
import groovy.sql.Sql
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.noise_planet.onomap.sensitive.nc_dump_records
import org.noise_planet.onomap.sensitive.nc_feed_stats
import org.noise_planet.onomap.sensitive.nc_parse
import org.noise_planet.onomap.sensitive.nc_process

import java.nio.file.Path
import java.util.zip.ZipInputStream

import static org.junit.jupiter.api.Assertions.*

/**
 * Test parsing of zip file using H2GIS database
 */
class TestNoiseCaptureDumpArea extends JdbcTestCase {

  @BeforeEach
  void setUp() {
    initDb()
    installGadmAndTimeZone()
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

  @Test
  void testTracksExport(@TempDir Path folder) {
    // Parse file to database
    new nc_parse().processFile(connection,
      new File(TestNoiseCaptureDumpArea.getResource("track_f7ff7498-ddfd-46a3-ab17-36a96c01ba1b.zip").file))
    new nc_parse().processFile(connection,
      new File(TestNoiseCaptureDumpArea.getResource("track_f720018a-a5db-4859-bd7d-377d29356c6f.zip").file))
    Sql.LOG.level = java.util.logging.Level.SEVERE
    // Insert measure data
    // insert records
    File tmpFolder = folder.toFile()

    def createdFiles = new nc_dump_area().exec(connection, [  startLatitude:43.104708,
                                                              startLongitude:12.384801,
                                                              stopLatitude:43.11107,
                                                              stopLongitude:12.392135,
                                                              exportTracks:1,
                                                              exportMeasures:1,
                                                              exportAreas:1,
                                                              emailNotification:'contact@noise-planet.org'])
  }
}
