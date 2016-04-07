package org.noise_planet.noisecapture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.util.Date;

/**
 * Handle database schema creation and upgrade
 */
public class Storage extends SQLiteOpenHelper {

    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Storage.db";

    public Storage(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_RECORD);
        db.execSQL(CREATE_LEQ);
        db.execSQL(CREATE_LEQ_VALUE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public static class Record implements BaseColumns {
        public static final String TABLE_NAME = "record";
        public static final String COLUMN_ID = "record_id";
        public static final String COLUMN_UTC = "record_utc";
        public static final String COLUMN_UPLOAD_ID = "upload_id";
        public static final String COLUMN_LEQ_MEAN = "leq_mean";

        private int id;
        private long utc;
        private String uploadId;
        private float leqMean;

        public Record(Cursor cursor) {
            id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
            utc = cursor.getLong(cursor.getColumnIndex(COLUMN_UTC));
            uploadId = cursor.getString(cursor.getColumnIndex(COLUMN_UPLOAD_ID));
            leqMean = cursor.getFloat(cursor.getColumnIndex(COLUMN_LEQ_MEAN));
        }

        /**
         * @return Upload identifier, empty if not uploaded
         */
        public String getUploadId() {
            return uploadId;
        }

        /**
         * @return Local storage identifier
         */
        public int getId() {
            return id;
        }

        /**
         * @return Record time
         */
        public long getUtc() {
            return utc;
        }

        public String getUtcDate() {
            return new Date(utc).toString();
        }

        public float getLeqMean() {
            return leqMean;
        }
    }

    public static final String CREATE_RECORD = "CREATE TABLE " + Record.TABLE_NAME +
            "("+Record.COLUMN_ID +" INTEGER PRIMARY KEY, " +
            Record.COLUMN_UTC +" LONG, " +
            Record.COLUMN_UPLOAD_ID + " TEXT, " +
            Record.COLUMN_LEQ_MEAN + " FLOAT)";

    public static class Leq implements BaseColumns {
        public static final String TABLE_NAME = "leq";
        public static final String COLUMN_RECORD_ID = "record_id";
        public static final String COLUMN_LEQ_ID = "leq_id";
        public static final String COLUMN_LEQ_UTC = "leq_utc";
        public static final String COLUMN_LATITUDE = "latitude";
        public static final String COLUMN_LONGITUDE = "longitude";
        public static final String COLUMN_ALTITUDE = "altitude";
        public static final String COLUMN_ACCURACY = "accuracy"; // location precision estimation
        public static final String COLUMN_LOCATION_UTC = "location_utc"; // date of last obtained location
    }

    public static final String CREATE_LEQ = "CREATE TABLE " + Leq.TABLE_NAME + "(" +
            Leq.COLUMN_RECORD_ID + " INTEGER, " +
            Leq.COLUMN_LEQ_ID + " INTEGER AUTO_INCREMENT, " +
            Leq.COLUMN_LEQ_UTC + " LONG, " +
            Leq.COLUMN_LATITUDE + " DOUBLE, " +
            Leq.COLUMN_LONGITUDE + " DOUBLE, " +
            Leq.COLUMN_ALTITUDE + " DOUBLE, " +
            Leq.COLUMN_ACCURACY + " FLOAT, " +
            Leq.COLUMN_LOCATION_UTC + " LONG, " +
            "PRIMARY KEY(" + Record.COLUMN_ID + ", " + Leq.COLUMN_LEQ_ID + "), " +
            "FOREIGN KEY(" + Record.COLUMN_ID + ") REFERENCES record)";

    public static class LeqValue implements BaseColumns {
        public static final String TABLE_NAME = "leq_value";
        public static final String COLUMN_LEQ_ID = "leq_id";
        public static final String COLUMN_FREQUENCY = "frequency";
        public static final String COLUMN_SPL = "spl"; // Spl value in dB(A)
    }

    public static final String CREATE_LEQ_VALUE = "CREATE TABLE " + LeqValue.TABLE_NAME + "(" +
            LeqValue.COLUMN_LEQ_ID +" INTEGER, " +
            LeqValue.COLUMN_FREQUENCY +" INTEGER, " +
            LeqValue.COLUMN_SPL +" FLOAT, " +
            "PRIMARY KEY("+LeqValue.COLUMN_LEQ_ID +", "+LeqValue.COLUMN_FREQUENCY +"), " +
            "FOREIGN KEY("+LeqValue.COLUMN_LEQ_ID +") REFERENCES leq);";
}
