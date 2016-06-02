package org.noise_planet.noisecapture;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Extract measurements from database and convert into packaged GeoJSON and properties file.
 */
public class MeasurementExport {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementExport.class);
    public static final String ZIP_FILENAME = "tracks/shared_tracks/track.zip";
    private MeasurementManager measurementManager;
    private Context context;
    public static final String PROPERTY_FILENAME  = "meta.properties";
    public static final String README_FILENAME  = "README.txt";
    public static final String GEOJSON_FILENAME  = "track.geojson";
    public static final String PROP_MANUFACTURER  = "device_manufacturer";
    public static final String PROP_PRODUCT  = "device_product";
    public static final String PROP_MODEL  = "device_model";
    public static final String PROP_TAGS  = "tags";
    public static final String PROP_UUID = "uuid"; // Random anonymous ID that link non identified user's measure.
    public static final String PROP_VERSION_NAME  = "version_name";
    public static final String PROP_BUILD_TIME  = "build_date";
    public static final String PROP_VERSION_INT  = "version_number";

    public MeasurementExport(Context context) {
        this.measurementManager = new MeasurementManager(context);
        this.context = context;
    }

    /**
     * Dump measurement into the specified writer
     * @param recordId Record identifier
     * @param outputStream Data output target
     * @throws IOException output error
     */
    public void exportRecord(Activity activity, int recordId, OutputStream outputStream) throws IOException {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        Storage.Record record = measurementManager.getRecord(recordId, false);

        // Property file
        Properties properties = new Properties();
        String versionName = "NONE";
        int versionCode = -1;
        try {
            PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            versionName = packageInfo.versionName;
            versionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
        }
        properties.setProperty(PROP_VERSION_NAME, versionName);
        properties.setProperty(PROP_BUILD_TIME, String.valueOf(BuildConfig.TIMESTAMP));
        properties.setProperty(PROP_VERSION_INT, String.valueOf(versionCode));
        properties.setProperty(PROP_MANUFACTURER, Build.MANUFACTURER);
        properties.setProperty(PROP_PRODUCT, Build.PRODUCT);
        properties.setProperty(PROP_MODEL, Build.MODEL);
        properties.setProperty(PROP_UUID, sharedPref.getString(PROP_UUID, ""));
        properties.setProperty(Storage.Record.COLUMN_UTC, String.valueOf(record.getUtc()));
        properties.setProperty(Storage.Record.COLUMN_LEQ_MEAN, String.format("%.02f", record.getLeqMean()));
        properties.setProperty(Storage.Record.COLUMN_TIME_LENGTH, String.valueOf(record.getTimeLength()));
        if(record.getPleasantness() != null) {
            properties.setProperty(Storage.Record.COLUMN_PLEASANTNESS, String.valueOf(record.getPleasantness()));
        }
        List<String> tags = measurementManager.getTags(recordId);
        StringBuilder tagsString = new StringBuilder();
        for(String tag : tags) {
            if(tagsString.length() != 0) {
                tagsString.append(",");
            }
            tagsString.append(tag);
        }
        properties.setProperty(PROP_TAGS, tagsString.toString());
        zipOutputStream.putNextEntry(new ZipEntry(PROPERTY_FILENAME));
        properties.store(zipOutputStream, "NoiseCapture export header file");
        zipOutputStream.closeEntry();

        // GeoJSON file

        List<MeasurementManager.LeqBatch> records =
                measurementManager.getRecordLocations(recordId, false);
        // If Memory problems switch to JSONWriter
        JSONObject main = new JSONObject();

        try {
            // Add CRS
            JSONObject crs = new JSONObject();
            JSONObject crsProperty = new JSONObject();
            crsProperty.put("name", "urn:ogc:def:crs:OGC:1.3:CRS84");
            crs.put("type", "name");
            crs.put("properties", crsProperty);
            main.put("crs", crs);

            // Add measures
            main.put("type", "FeatureCollection");
            List<JSONObject> features = new ArrayList<>(records.size());
            for (MeasurementManager.LeqBatch entry : records) {
                Storage.Leq leq = entry.getLeq();
                JSONObject feature = new JSONObject();
                feature.put("type", "Feature");

                if(leq.getAccuracy() > 0) {
                    // Add coordinate
                    JSONObject point = new JSONObject();
                    point.put("type", "Point");
                    point.put("coordinates", new JSONArray(Arrays.asList(
                            leq.getLongitude(), leq.getLatitude(), leq.getAltitude())));
                    feature.put("geometry", point);
                } else {
                    feature.put("geometry", JSONObject.NULL);
                }

                // Add properties
                JSONObject featureProperties = new JSONObject();
                featureProperties.put(Storage.Record.COLUMN_LEQ_MEAN ,entry.computeGlobalLeq());
                featureProperties.put(Storage.Leq.COLUMN_ACCURACY, leq.getAccuracy());
                featureProperties.put(Storage.Leq.COLUMN_LOCATION_UTC, leq.getLocationUTC());
                featureProperties.put(Storage.Leq.COLUMN_LEQ_UTC, leq.getLeqUtc());
                featureProperties.put(Storage.Leq.COLUMN_LEQ_ID, leq.getLeqId());
                //marker-color tag for geojson.io
                featureProperties.put("marker-color", String.format("#%06X",
                        (0xFFFFFF & Spectrogram.getColor((float)entry.computeGlobalLeq(), 45, 100))));
                if(leq.getBearing() != null) {
                    featureProperties.put(Storage.Leq.COLUMN_BEARING, leq.getBearing());
                }
                if(leq.getSpeed() != null) {
                    featureProperties.put(Storage.Leq.COLUMN_SPEED, leq.getSpeed());
                }
                for(Storage.LeqValue leqValue : entry.getLeqValues()) {
                    featureProperties.put("leq_"+leqValue.getFrequency(), leqValue.getSpl());
                }
                feature.put("properties", featureProperties);
                features.add(feature);
            }
            main.put("features", new JSONArray(features));
            zipOutputStream.putNextEntry(new ZipEntry(GEOJSON_FILENAME));
            Writer writer = new OutputStreamWriter(zipOutputStream);
            writer.write(main.toString(2));
            writer.flush();
            zipOutputStream.closeEntry();

            // Readme file
            zipOutputStream.putNextEntry(new ZipEntry(README_FILENAME));
            writer = new OutputStreamWriter(zipOutputStream);
            writer.write(activity.getString(R.string.export_zip_info));
            writer.flush();
            zipOutputStream.closeEntry();
        } catch (JSONException ex ) {
            throw new IOException(ex);
        } finally {
            zipOutputStream.close();
        }
    }


}
