package org.noise_planet.noisecapture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.text.DateFormat;
import java.util.Date;

/**
 * Handle database schema creation and upgrade
 */
public class Storage extends SQLiteOpenHelper {

    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 3;
    public static final String DATABASE_NAME = "Storage.db";
    private static final String ACTIVATE_FOREIGN_KEY = "PRAGMA foreign_keys=ON;";

    public Storage(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ACTIVATE_FOREIGN_KEY);
        db.execSQL(CREATE_RECORD);
        db.execSQL(CREATE_LEQ);
        db.execSQL(CREATE_LEQ_VALUE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade queries
        // Do not use static table and column names
        if(oldVersion == 1) {
            // Upgrade from 1 to version 2
            // Add record length attribute
            if (!db.isReadOnly()) {
                db.execSQL("ALTER TABLE record ADD COLUMN time_length INTEGER");
            }
            oldVersion = 2;
        }
        if(oldVersion == 2) {
            // Add gps speed and bearing attribute
            if (!db.isReadOnly()) {
                db.execSQL("ALTER TABLE leq ADD COLUMN speed FLOAT");
                db.execSQL("ALTER TABLE leq ADD COLUMN bearing FLOAT");
            }
            oldVersion = 3;
        }
    }


    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL(ACTIVATE_FOREIGN_KEY);
        }
    }

    private static Double getDouble(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getDouble(colIndex);
            }
        } else {
            return null;
        }
    }

    private static Float getFloat(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getFloat(colIndex);
            }
        } else {
            return null;
        }
    }

    public static class Record implements BaseColumns {
        public static final String TABLE_NAME = "record";
        public static final String COLUMN_ID = "record_id";
        public static final String COLUMN_UTC = "record_utc";
        public static final String COLUMN_UPLOAD_ID = "upload_id";
        public static final String COLUMN_LEQ_MEAN = "leq_mean";
        public static final String COLUMN_TIME_LENGTH = "time_length";

        private int id;
        private long utc;
        private String uploadId;
        private float leqMean;
        private int timeLength;

        public Record(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_ID)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_UTC)),
                    cursor.getString(cursor.getColumnIndex(COLUMN_UPLOAD_ID)),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_LEQ_MEAN)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_TIME_LENGTH)));
        }

        public Record(int id, long utc, String uploadId, float leqMean, int timeLength) {
            this.id = id;
            this.utc = utc;
            this.uploadId = uploadId;
            this.leqMean = leqMean;
            this.timeLength = timeLength;
        }

        /**
         * @return Record length in seconds
         */
        public int getTimeLength() {
            return timeLength;
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
            return DateFormat.getDateTimeInstance().format(new Date(utc));
        }

        public float getLeqMean() {
            return leqMean;
        }
    }

    public static final String CREATE_RECORD = "CREATE TABLE " + Record.TABLE_NAME +
            "("+Record.COLUMN_ID +" INTEGER PRIMARY KEY, " +
            Record.COLUMN_UTC +" LONG, " +
            Record.COLUMN_UPLOAD_ID + " TEXT, " +
            Record.COLUMN_LEQ_MEAN + " FLOAT" +
            Record.COLUMN_TIME_LENGTH + " INTEGER)";

    public static class Leq implements BaseColumns {
        public static final String TABLE_NAME = "leq";
        public static final String COLUMN_RECORD_ID = "record_id";
        public static final String COLUMN_LEQ_ID = "leq_id";
        public static final String COLUMN_LEQ_UTC = "leq_utc";
        public static final String COLUMN_LATITUDE = "latitude";
        public static final String COLUMN_LONGITUDE = "longitude";
        public static final String COLUMN_ALTITUDE = "altitude";
        public static final String COLUMN_ACCURACY = "accuracy"; // location precision estimation
        public static final String COLUMN_SPEED = "speed"; // device speed estimation
        public static final String COLUMN_BEARING = "bearing"; // device orientation estimation
        public static final String COLUMN_LOCATION_UTC = "location_utc"; // date of last obtained location

        private int recordId;
        private int leqId;
        private long leqUtc;
        private double latitude;
        private double longitude;
        private Double altitude;
        private Float speed;
        private Float bearing;
        private float accuracy;
        private long locationUTC;

        /**
         *
         * @param recordId Record id or -1 if unknown
         * @param leqId
         * @param leqUtc
         * @param latitude
         * @param longitude
         * @param altitude
         * @param speed
         * @param bearing
         * @param accuracy
         * @param locationUTC
         */
        public Leq(int recordId, int leqId, long leqUtc, double latitude, double longitude,
                   Double altitude, Float speed, Float bearing, float accuracy, long locationUTC) {
            this.recordId = recordId;
            this.leqId = leqId;
            this.leqUtc = leqUtc;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.speed = speed;
            this.bearing = bearing;
            this.accuracy = accuracy;
            this.locationUTC = locationUTC;
        }

        public Leq(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_RECORD_ID)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_LEQ_ID)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_LEQ_UTC)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE)),
                    getDouble(cursor, COLUMN_ALTITUDE),
                    getFloat(cursor, COLUMN_SPEED),
                    getFloat(cursor, COLUMN_BEARING),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_ACCURACY)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_LOCATION_UTC)));
        }


        public int getRecordId() {
            return recordId;
        }

        public int getLeqId() {
            return leqId;
        }

        public long getLeqUtc() {
            return leqUtc;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public Double getAltitude() {
            return altitude;
        }

        public float getAccuracy() {
            return accuracy;
        }

        /**
         * @return Device speed in ground m/s
         */
        public Float getSpeed() {
            return speed;
        }

        /**
         * @return Device orientation
         */
        public Float getBearing() {
            return bearing;
        }

        public long getLocationUTC() {
            return locationUTC;
        }
    }

    public static final String CREATE_LEQ = "CREATE TABLE " + Leq.TABLE_NAME + "(" +
            Leq.COLUMN_RECORD_ID + " INTEGER, " +
            Leq.COLUMN_LEQ_ID + " INTEGER PRIMARY KEY, " +
            Leq.COLUMN_LEQ_UTC + " LONG, " +
            Leq.COLUMN_LATITUDE + " DOUBLE, " +
            Leq.COLUMN_LONGITUDE + " DOUBLE, " +
            Leq.COLUMN_ALTITUDE + " DOUBLE, " +
            Leq.COLUMN_SPEED + " FLOAT, " +
            Leq.COLUMN_ACCURACY + " FLOAT, " +
            Leq.COLUMN_LOCATION_UTC + " LONG, " +
            "FOREIGN KEY(" + Leq.COLUMN_RECORD_ID + ") REFERENCES record("+Record.COLUMN_ID+") ON DELETE CASCADE)";

    public static class LeqValue implements BaseColumns {
        public static final String TABLE_NAME = "leq_value";
        public static final String COLUMN_LEQ_ID = "leq_id";
        public static final String COLUMN_FREQUENCY = "frequency";
        public static final String COLUMN_SPL = "spl"; // Spl value in dB(A)

        private final int leqId;
        private final int frequency;
        private final float spl;

        public LeqValue(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_LEQ_ID)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_FREQUENCY)),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_SPL)));
        }

        /**
         * @param leqId Leq Id or -1 if unknown
         * @param frequency Frequency in Hertz
         * @param spl Sound pressure value in dB(A)
         */
        public LeqValue(int leqId, int frequency, float spl) {
            this.leqId = leqId;
            this.frequency = frequency;
            this.spl = spl;
        }

        public int getLeqId() {
            return leqId;
        }

        public int getFrequency() {
            return frequency;
        }

        public float getSpl() {
            return spl;
        }
    }

    public static final String CREATE_LEQ_VALUE = "CREATE TABLE " + LeqValue.TABLE_NAME + "(" +
            LeqValue.COLUMN_LEQ_ID +" INTEGER, " +
            LeqValue.COLUMN_FREQUENCY +" INTEGER, " +
            LeqValue.COLUMN_SPL +" FLOAT, " +
            "PRIMARY KEY("+LeqValue.COLUMN_LEQ_ID +", "+LeqValue.COLUMN_FREQUENCY +"), " +
            "FOREIGN KEY("+LeqValue.COLUMN_LEQ_ID +") REFERENCES leq("+Leq.COLUMN_LEQ_ID+") ON DELETE CASCADE);";
}
