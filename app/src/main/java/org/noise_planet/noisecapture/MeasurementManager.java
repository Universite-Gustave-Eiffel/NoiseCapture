package org.noise_planet.noisecapture;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Add, remove and list all measures using android private storage.
 */
public class MeasurementManager {
    private Context context;
    private Storage storage;
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementManager.class);

    public MeasurementManager(Context context) {
        this.context = context;
        this.storage = new Storage(context);
        // Connect to local database
    }

    public List<Record> getRecords() {
        List<Record> records = new ArrayList<>();
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            try(Cursor cursor = database.rawQuery("SELECT * FROM "+Storage.Record.TABLE_NAME +
                    " ORDER BY " + Storage.Record.COLUMN_ID, null)) {
                while (cursor.moveToNext()) {
                    records.add(new Record());
                }
            }
        } finally {
            database.close();
        }
        return records;
    }

    int addRecord() {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Storage.Record.COLUMN_UTC, System.currentTimeMillis());
            contentValues.put(Storage.Record.COLUMN_UPLOAD_ID, "");
            try {
                return (int) database.insertOrThrow(Storage.Record.TABLE_NAME, null, contentValues);
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
                return -1;
            }
        } finally {
            database.close();
        }
    }

    /**
     * Add a Leq header information
     * @param recordId Record identifier
     * @param leqTime Start leq capture time
     * @return Leq identifier
     */
    int addLeq(int recordId, long leqTime) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Storage.Leq.RECORD_ID, recordId);
            contentValues.put(Storage.Leq.LEQ_UTC, leqTime);
            try {
                return (int) database.insertOrThrow(Storage.Leq.TABLE_NAME, null, contentValues);
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
                return -1;
            }
        } finally {
            database.close();
        }
    }

    /**
     * Add a Leq frequency value
     * @param leqId Leq identifier
     * @param frequency Frequency in Hz
     * @param spl Sound pressure value in dB(A)
     */
    void addLeqValue(int leqId, int frequency, float spl) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Storage.LeqValue.LEQ_ID, leqId);
            contentValues.put(Storage.LeqValue.FREQUENCY, frequency);
            contentValues.put(Storage.LeqValue.SPL, spl);
            try {
                database.insertOrThrow(Storage.LeqValue.TABLE_NAME, null, contentValues);
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
            }
        } finally {
            database.close();
        }
    }

    public static class Record {
        private Storage storage;

    }
}
