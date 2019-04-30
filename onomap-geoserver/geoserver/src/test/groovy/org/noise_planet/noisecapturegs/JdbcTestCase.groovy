package org.noise_planet.noisecapturegs

import org.h2gis.functions.factory.H2GISDBFactory
import org.h2gis.utilities.SFSUtilities
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.sql.Connection

class JdbcTestCase  extends GroovyTestCase {
    Connection connection;


    @Ignore
    void testVoid() {
    }

    @Rule
    public TemporaryFolder folder= new TemporaryFolder(new File("build"));

    @Before
    void setUp() {
        folder.create()
        connection = SFSUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(UUID.randomUUID().toString(), true, "MODE=PostgreSQL"))
    }

    @After
    void tearDown() {
        connection.close();
        folder.delete()
    }
}
