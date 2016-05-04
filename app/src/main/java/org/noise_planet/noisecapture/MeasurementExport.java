package org.noise_planet.noisecapture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Extract measurements from database and convert into packaged GeoJSON and properties file.
 */
public class MeasurementExport {
    public static final String ZIP_FILENAME = "tracks/shared_tracks/track.zip";
    private MeasurementManager measurementManager;
    private Context context;
    public static final String PROPERTY_FILENAME  = "meta.properties";
    public static final String GEOJSON_FILENAME  = "track.json";
    public static final String PROP_MANUFACTURER  = "DEVICE_MANUFACTURER";
    public static final String PROP_PRODUCT  = "DEVICE_PRODUCT";
    public static final String PROP_MODEL  = "DEVICE_MODEL";
    public static final String PROP_UUID = "UUID"; // Random anonymous ID that link non identified user's measure.

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
    public void exportRecord(int recordId, OutputStream outputStream) throws IOException {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        Storage.Record record = measurementManager.getRecord(recordId);

        // Property file
        Properties properties = new Properties();
        properties.setProperty(PROP_MANUFACTURER, Build.MANUFACTURER);
        properties.setProperty(PROP_PRODUCT, Build.PRODUCT);
        properties.setProperty(PROP_MODEL, Build.MODEL);
        properties.setProperty(PROP_UUID, sharedPref.getString(PROP_UUID, ""));
        properties.setProperty(Storage.Record.COLUMN_UTC, String.valueOf(record.getUtc()));
        properties.setProperty(Storage.Record.COLUMN_LEQ_MEAN, String.format("%.02f", record.getLeqMean()));
        properties.setProperty(Storage.Record.COLUMN_TIME_LENGTH, String.valueOf(record.getTimeLength()));
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

                // Add coordinate
                JSONObject point = new JSONObject();
                point.put("type", "Point");
                if(leq.getAccuracy() > 0) {
                    point.put("coordinates", new JSONArray(Arrays.asList(leq.getLatitude(),
                            leq.getLongitude(), leq.getAltitude())));
                } else {
                    point.put("coordinates", new JSONArray(Collections.emptyList()));
                }
                feature.put("geometry", point);

                // Add properties
                JSONObject featureProperties = new JSONObject();
                featureProperties.put(Storage.Record.COLUMN_LEQ_MEAN ,entry.computeGlobalLeq());
                featureProperties.put(Storage.Leq.COLUMN_ACCURACY, leq.getAccuracy());
                featureProperties.put(Storage.Leq.COLUMN_LOCATION_UTC, leq.getLocationUTC());
                featureProperties.put(Storage.Leq.COLUMN_LEQ_UTC, leq.getLeqUtc());
                featureProperties.put(Storage.Leq.COLUMN_LEQ_ID, leq.getLeqId());
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
        } catch (JSONException ex ) {
            throw new IOException(ex);
        } finally {
            zipOutputStream.close();
        }
    }


}
