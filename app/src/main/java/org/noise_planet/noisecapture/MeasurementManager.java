package org.noise_planet.noisecapture;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import org.orbisgis.sos.LeqStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
     * Delete all data associated with a record
     * @param recordId Record identifier
     */
    public void deleteRecord(int recordId) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            database.delete(Storage.Record.TABLE_NAME, Storage.Record.COLUMN_ID + " = ?",
                    new String[]{String.valueOf(recordId)});
        } finally {
            database.close();
        }
    }

    /**
     * Delete all data associated with a record
     * @param recordIds Record identifiers
     */
    public void deleteRecords(Collection<Integer> recordIds) {
        StringBuilder param = new StringBuilder();
        String[] paramValue = new String[recordIds.size()];
        int index = 0;
        for(int recordId : recordIds) {
            paramValue[index++] = String.valueOf(recordId);
            if(param.length() != 0) {
                param.append(",");
            }
            param.append("?");
        }
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            database.delete(Storage.Record.TABLE_NAME, Storage.Record.COLUMN_ID +
                            " IN ("+param.toString()+")", paramValue);
        } finally {
            database.close();
        }
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
                // Add last leq
                if(!leqArray.isEmpty()) {
                    leqs.add(leqArray.toArray(new Float[leqArray.size()]));
                    leqArray.clear();
                }
                return lastId != -1;
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
    }


    /**
     * Fetch all leq that hold a coordinate
     * @param recordId Record identifier
     * @param latLong Array of Latitude/Longitude
     */
    public boolean getRecordLocations(int recordId, List<double[]> latLong, List<Double> leqs) {
        SQLiteDatabase database = storage.getReadableDatabase();
        try {
            Cursor cursor = database.rawQuery("SELECT L." + Storage.Leq.COLUMN_LEQ_ID + ", LV." +
                    Storage.LeqValue.COLUMN_FREQUENCY + ", LV." + Storage.LeqValue.COLUMN_SPL +
                            ", L." + Storage.Leq.COLUMN_LATITUDE + ", L." +
                            Storage.Leq.COLUMN_LONGITUDE +
                    " FROM " + Storage.Leq.TABLE_NAME + " L, " + Storage.LeqValue.TABLE_NAME +
                    " LV WHERE L." + Storage.Leq.COLUMN_RECORD_ID + " = ? AND L." +
                    Storage.Leq.COLUMN_LEQ_ID + " = LV." + Storage.LeqValue.COLUMN_LEQ_ID +
                    " AND L."+ Storage.Leq.COLUMN_ACCURACY+" > 0 ORDER BY L." + Storage.Leq.COLUMN_LEQ_ID + ", " +
                    Storage.LeqValue.COLUMN_FREQUENCY, new String[]{String.valueOf(recordId)});
            try {
                int lastId = -1;
                double[] lastLatLong = null;
                double sumLeq = 0;
                int latIndex = cursor.getColumnIndex(Storage.Leq.COLUMN_LATITUDE);
                int longIndex = cursor.getColumnIndex(Storage.Leq.COLUMN_LONGITUDE);
                while (cursor.moveToNext()) {
                    Storage.LeqValue leqValue = new Storage.LeqValue(cursor);
                    if(lastId != leqValue.getLeqId() && lastId != -1) {
                        latLong.add(lastLatLong);
                        leqs.add(10 * Math.log10(sumLeq));
                        sumLeq = 0;
                        lastLatLong = null;
                    }
                    lastId = leqValue.getLeqId();
                    if(lastLatLong == null) {
                        lastLatLong = new double[]{cursor.getDouble(latIndex),
                                cursor.getDouble(longIndex)};
                    }
                    sumLeq += Math.pow(10, leqValue.getSpl() / 10);
                }
                // Add last leq
                if(lastLatLong != null) {
                    latLong.add(lastLatLong);
                    leqs.add(10 * Math.log10(sumLeq));
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
                database.execSQL("UPDATE " + Storage.Record.TABLE_NAME + " SET " +
                        Storage.Record.COLUMN_LEQ_MEAN + " = ? WHERE " +
                        Storage.Record.COLUMN_ID + " = ?", new Object[]{leqMean, recordId});
            } catch (SQLException sqlException) {
                LOGGER.error(sqlException.getLocalizedMessage(), sqlException);
            }
        } finally {
            database.close();
        }

    }

    /**
     * Add multiple leq records
     * @param leqBatches List of Leq to add
     */
    public void addLeqBatches(List<LeqBatch> leqBatches) {
        SQLiteDatabase database = storage.getWritableDatabase();
        try {
            database.beginTransaction();
            SQLiteStatement leqStatement = database.compileStatement(
                    "INSERT INTO " + Storage.Leq.TABLE_NAME + "(" +
                            Storage.Leq.COLUMN_RECORD_ID + "," +
                            Storage.Leq.COLUMN_LEQ_UTC + "," +
                            Storage.Leq.COLUMN_LATITUDE  + "," +
                            Storage.Leq.COLUMN_LONGITUDE  + "," +
                            Storage.Leq.COLUMN_ALTITUDE  + "," +
                            Storage.Leq.COLUMN_ACCURACY  + "," +
                            Storage.Leq.COLUMN_LOCATION_UTC +
                            ") VALUES (?, ?,?,?,?,?,?)");
            SQLiteStatement leqValueStatement = database.compileStatement("INSERT INTO " +
                    Storage.LeqValue.TABLE_NAME + " VALUES (?,?,?)");
            for(LeqBatch leqBatch : leqBatches) {
                Storage.Leq leq = leqBatch.getLeq();
                leqStatement.clearBindings();
                leqStatement.bindLong(1, leq.getRecordId());
                leqStatement.bindLong(2, leq.getLeqUtc());
                leqStatement.bindDouble(3, leq.getLatitude());
                leqStatement.bindDouble(4, leq.getLongitude());
                leqStatement.bindDouble(5, leq.getAltitude());
                leqStatement.bindDouble(6, leq.getAccuracy());
                leqStatement.bindDouble(7, leq.getLocationUTC());
                long leqId = leqStatement.executeInsert();
                for(Storage.LeqValue leqValue : leqBatch.getLeqValues()) {
                    leqValueStatement.clearBindings();
                    leqValueStatement.bindLong(1, leqId);
                    leqValueStatement.bindLong(2, leqValue.getFrequency());
                    leqValueStatement.bindDouble(3, leqValue.getSpl());
                    leqValueStatement.execute();
                }
            }
            database.setTransactionSuccessful();
            database.endTransaction();
        } finally {
            database.close();
        }
    }

    /**
     * Add a leq record
     * @param leqBatch Leq values
     */
    public void addLeqBatch(LeqBatch leqBatch) {
        addLeqBatches(Collections.singletonList(leqBatch));
    }

    public static class LeqBatch {
        private Storage.Leq leq;
        private List<Storage.LeqValue> leqValues;

        public LeqBatch(Storage.Leq leq, List<Storage.LeqValue> leqValues) {
            this.leq = leq;
            this.leqValues = leqValues;
        }

        public Storage.Leq getLeq() {
            return leq;
        }

        public List<Storage.LeqValue> getLeqValues() {
            return leqValues;
        }
    }

}
