package org.noise_planet.noisecapture;

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add, remove and list all measures using android private storage.
 */
public class MeasurementManager {
    private Context context;
    private Storage storage;
    private SQLiteDatabase database = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementManager.class);

    public MeasurementManager(Context context) {
        this.context = context;
        this.storage = new Storage(context);
        // Connect to local database
    }

    private void checkConnection() {
        if(database == null) {
            database = storage.getWritableDatabase();
        }
    }

    int addRecord() {
        checkConnection();
        ContentValues contentValues = new ContentValues();
        contentValues.put(Storage.Record.COLUMN_UTC, System.currentTimeMillis());
        try {
            return (int) database.insertOrThrow(Storage.Record.TABLE_NAME, null, contentValues);
        } catch (SQLException sqlException) {
            LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
            return -1;
        }
    }

    /**
     * Add a Leq header information
     * @param recordId Record identifier
     * @param leqTime Start leq capture time
     * @return Leq identifier
     */
    int addLeq(int recordId, long leqTime) {
        checkConnection();
        ContentValues contentValues = new ContentValues();
        contentValues.put(Storage.Leq.RECORD_ID, recordId);
        contentValues.put(Storage.Leq.LEQ_UTC, leqTime);
        try {
            return (int) database.insertOrThrow(Storage.Leq.TABLE_NAME, null, contentValues);
        } catch (SQLException sqlException) {
            LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
            return -1;
        }
    }

    /**
     * Add a Leq frequency value
     * @param leqId Leq identifier
     * @param frequency Frequency in Hz
     * @param spl Sound pressure value in dB(A)
     */
    void addLeqValue(int leqId, int frequency, float spl) {

    }
}
