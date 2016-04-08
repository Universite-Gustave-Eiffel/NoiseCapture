package org.noise_planet.noisecapture;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import org.orbisgis.sos.LeqStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Add, remove and list all measures using android private storage.
 */
public class MeasurementManager {
    private Storage storage;
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementManager.class);

    public MeasurementManager(Context context) {
        this.storage = new Storage(context);
        // Connect to local database
    }

    public List<Storage.Record> getRecords() {
        List<Storage.Record> records = new ArrayList<>();
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT * FROM "+Storage.Record.TABLE_NAME +
                    " ORDER BY " + Storage.Record.COLUMN_ID, null);
            try {
                while (cursor.moveToNext()) {
                    records.add(new Storage.Record(cursor));
                }
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
        return records;
    }

    /**
     * @return Record
     */
    public int addRecord() {
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
     * Fetch all leq values of a record
     * @param recordId Record identifier
     * @param frequency frequency
     * @param leqs Leq value by time and frequency in the same order of frequency list
     * @return True if recordId has been found
     */
    public boolean getRecordValues(int recordId, List<Integer> frequency, List<Float[]> leqs) {
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT L." + Storage.Leq.COLUMN_LEQ_ID + ", LV." +
                    Storage.LeqValue.COLUMN_FREQUENCY + ", LV." + Storage.LeqValue.COLUMN_SPL +
                    " FROM " + Storage.Leq.TABLE_NAME + " L, " + Storage.LeqValue.TABLE_NAME +
                    " LV WHERE L." + Storage.Leq.COLUMN_RECORD_ID + " = ? AND L." +
                    Storage.Leq.COLUMN_LEQ_ID + " = LV." + Storage.LeqValue.COLUMN_LEQ_ID +
                    " ORDER BY L." + Storage.Leq.COLUMN_LEQ_ID + ", " +
                    Storage.LeqValue.COLUMN_FREQUENCY, new String[]{String.valueOf(recordId)});
            try {
                List<Float> leqArray = new ArrayList<>();
                int lastId = -1;
                while (cursor.moveToNext()) {
                    Storage.LeqValue leqValue = new Storage.LeqValue(cursor);
                    if(lastId != leqValue.getLeqId() && !leqArray.isEmpty()) {
                        leqs.add(leqArray.toArray(new Float[leqArray.size()]));
                        leqArray.clear();
                    }
                    lastId = leqValue.getLeqId();
                    leqArray.add(leqValue.getSpl());
                    if(!frequency.contains(leqValue.getFrequency())) {
                        frequency.add(leqValue.getFrequency());
                    }
                }
                return lastId != -1;
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
    }

    public Storage.Record getRecord(int recordId) {
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT * FROM " + Storage.Record.TABLE_NAME +
                    " WHERE " + Storage.Record.COLUMN_ID + " = ?", new String[]{String.valueOf(recordId)});
            try {
                if (cursor.moveToNext()) {
                    return new Storage.Record(cursor);
                }
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
        return null;
    }

    public void updateRecordLeqMean(int recordId, float leqMean) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Storage.Record.COLUMN_UTC, System.currentTimeMillis());
            contentValues.put(Storage.Record.COLUMN_UPLOAD_ID, "");
            try {
                database.execSQL("UPDATE "+Storage.Record.TABLE_NAME+" SET "+
                        Storage.Record.COLUMN_LEQ_MEAN+" = ? WHERE "+Storage.Record.COLUMN_ID+" = ?",
                        new Object[]{leqMean, recordId});
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
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
    public int addLeq(int recordId, long leqTime) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Storage.Leq.COLUMN_RECORD_ID, recordId);
            contentValues.put(Storage.Leq.COLUMN_LEQ_UTC, leqTime);
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
            contentValues.put(Storage.LeqValue.COLUMN_LEQ_ID, leqId);
            contentValues.put(Storage.LeqValue.COLUMN_FREQUENCY, frequency);
            contentValues.put(Storage.LeqValue.COLUMN_SPL, spl);
            try {
                database.insertOrThrow(Storage.LeqValue.TABLE_NAME, null, contentValues);
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
            }
        } finally {
            database.close();
        }
    }
}
