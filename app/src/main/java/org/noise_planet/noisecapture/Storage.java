package org.noise_planet.noisecapture;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

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

    public static abstract class Record implements BaseColumns {
        public static final String TABLE_NAME = "record";
        public static final String COLUMN_ID = "record_id";
        public static final String COLUMN_UTC = "record_utc";
    }

    public static final String CREATE_RECORD = "CREATE TABLE " + Record.TABLE_NAME +
            "("+Record.COLUMN_ID +" INTEGER PRIMARY KEY, " +
            Record.COLUMN_UTC +" LONG)";

    public static abstract class Leq implements BaseColumns {
        public static final String TABLE_NAME = "leq";
        public static final String RECORD_ID = "record_id";
        public static final String LEQ_ID = "leq_id";
        public static final String LEQ_UTC = "leq_utc";
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";
        public static final String ALTITUDE = "altitude";
        public static final String ACCURACY = "accuracy"; // location precision estimation
        public static final String LOCATION_UTC = "location_utc"; // date of last obtained location
    }

    public static final String CREATE_LEQ = "CREATE TABLE " + Leq.TABLE_NAME + "(" +
            Leq.RECORD_ID + " INTEGER, " +
            Leq.LEQ_ID + " INTEGER AUTO_INCREMENT, " +
            Leq.LEQ_UTC + " LONG, " +
            Leq.LATITUDE + " DOUBLE, " +
            Leq.LONGITUDE + " DOUBLE, " +
            Leq.ALTITUDE + " DOUBLE, " +
            Leq.ACCURACY + " FLOAT, " +
            Leq.LOCATION_UTC + " LONG, " +
            "PRIMARY KEY(" + Record.COLUMN_ID + ", " + Leq.LEQ_ID + "), " +
            "FOREIGN KEY(" + Record.COLUMN_ID + ") REFERENCES record)";

    public static abstract class LeqValue implements BaseColumns {
        public static final String TABLE_NAME = "leq_value";
        public static final String LEQ_ID = "leq_id";
        public static final String FREQUENCY = "frequency";
        public static final String SPL = "spl"; // Spl value in dB(A)
    }

    public static final String CREATE_LEQ_VALUE = "CREATE TABLE " + LeqValue.TABLE_NAME + "(" +
            LeqValue.LEQ_ID+" INTEGER, " +
            LeqValue.FREQUENCY+"frequency INTEGER, " +
            LeqValue.SPL+"spl FLOAT, " +
            "PRIMARY KEY("+LeqValue.LEQ_ID+", "+LeqValue.FREQUENCY+"), " +
            "FOREIGN KEY("+LeqValue.LEQ_ID+") REFERENCES leq);";
}
