/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

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
import java.util.Locale;
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
    public static final String PROP_GAIN_CALIBRATION  = "gain_calibration";
    public static final String PROP_UUID = "uuid"; // Random anonymous ID that link non identified user's measure.
    public static final String PROP_VERSION_NAME  = "version_name";
    public static final String PROP_BUILD_TIME  = "build_date";
    public static final String PROP_VERSION_INT  = "version_number";
    public static final String PROP_USER_PROFILE  = "user_profile";

    public MeasurementExport(Context context) {
        this.measurementManager = new MeasurementManager(context);
        this.context = context;
    }


    public static String getColorFromLevel(double spl) {
        if(spl <35) {
            return "#82A6AD";
        }else if(spl <40) {
            return "#A0BABF";
        }else if(spl <45) {
            return "#B8D6D1";
        }else if(spl <50) {
            return "#CEE4CC";
        }else if(spl <55) {
            return "#E2F2BF";
        }else if(spl <60) {
            return "#F3C683";
        }else if(spl <65) {
            return "#E87E4D";
        }else if(spl <70) {
            return "#CD463E";
        }else if(spl <75) {
            return "#A11A4D";
        }else if(spl <80) {
            return "#75085C";
        } else {
            return "#430A4A";
        }
    }
    /**
     * Convert list of measurements into GeoJSON feature array
     * @param records Measurements array
     * @param outputNullGeometry If true empty geometry are exported (accuracy <= 0)
     * @param fullProperties If false only the mean LAeq are written, otherwise all available data are written.
     * @return An array of GeoJSON feature objects.
     * @throws JSONException
     */
    public static JSONArray recordsToGeoJSON(List<MeasurementManager.LeqBatch> records, boolean outputNullGeometry, boolean fullProperties) throws JSONException {
        List<JSONObject> features = new ArrayList<>(records.size());
        for (MeasurementManager.LeqBatch entry : records) {
            Storage.Leq leq = entry.getLeq();
            if(!outputNullGeometry && !(leq.getAccuracy() > 0)) {
                continue;
            }
            JSONObject feature = new JSONObject();
            feature.put("type", "Feature");

            if (leq.getAccuracy() > 0) {
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
            double lAeq = entry.computeGlobalLeq();
            featureProperties.put(Storage.Record.COLUMN_LEQ_MEAN, Float.valueOf((float) lAeq));
            //marker-color tag for geojson.io and leaflet map
            featureProperties.put("marker-color", getColorFromLevel(lAeq));
            if (fullProperties) {
                featureProperties.put(Storage.Leq.COLUMN_ACCURACY, Float.valueOf(leq.getAccuracy
                        ()));
                featureProperties.put(Storage.Leq.COLUMN_LOCATION_UTC, leq.getLocationUTC());
                featureProperties.put(Storage.Leq.COLUMN_LEQ_UTC, leq.getLeqUtc());
                featureProperties.put(Storage.Leq.COLUMN_LEQ_ID, leq.getLeqId());
                if (leq.getBearing() != null) {
                    featureProperties.put(Storage.Leq.COLUMN_BEARING, leq.getBearing());
                }
                if (leq.getSpeed() != null) {
                    featureProperties.put(Storage.Leq.COLUMN_SPEED, leq.getSpeed());
                }
                for (Storage.LeqValue leqValue : entry.getLeqValues()) {
                    featureProperties.put("leq_" + leqValue.getFrequency(), Float.valueOf
                            (leqValue.getSpl()));
                }
            }
            feature.put("properties", featureProperties);
            features.add(feature);
        }
        return new JSONArray(features);
    }

    /**
     * Dump measurement into the specified writer
     * @param recordId Record identifier
     * @param outputStream Data output target
     * @throws IOException output error
     */
    public void exportRecord(int recordId, OutputStream outputStream,boolean exportReadme) throws IOException {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        Storage.Record record = measurementManager.getRecord(recordId);

        // Property file
        Properties properties = new Properties();
        String versionName = "NONE";
        int versionCode = -1;
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
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
        properties.setProperty(Storage.Record.COLUMN_LEQ_MEAN, String.format(Locale.US, "%.02f", record.getLeqMean()));
        properties.setProperty(PROP_GAIN_CALIBRATION, String.format(Locale.US, "%.02f", record.getCalibrationGain()));
        properties.setProperty(Storage.Record.COLUMN_TIME_LENGTH, String.valueOf(record.getTimeLength()));
        if(record.getPleasantness() != null) {
            properties.setProperty(Storage.Record.COLUMN_PLEASANTNESS, String.valueOf(record.getPleasantness()));
        }
        if(record.getNoisePartyTag() != null && !record.getNoisePartyTag().isEmpty()) {
            properties.setProperty(Storage.Record.COLUMN_NOISEPARTY_TAG, record.getNoisePartyTag());
        }
        properties.setProperty(PROP_USER_PROFILE, sharedPref.getString
                ("settings_user_noise_knowledge", "NONE"));
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
                measurementManager.getRecordLocations(recordId, false, 0);
        // If Memory problems switch to JSONWriter
        JSONObject main = new JSONObject();

        try {

            // Add measures
            main.put("type", "FeatureCollection");
            main.put("features", recordsToGeoJSON(records, true, true));
            zipOutputStream.putNextEntry(new ZipEntry(GEOJSON_FILENAME));
            Writer writer = new OutputStreamWriter(zipOutputStream);
            writer.write(main.toString(2));
            writer.flush();
            zipOutputStream.closeEntry();

            if(exportReadme) {
                // Readme file
                zipOutputStream.putNextEntry(new ZipEntry(README_FILENAME));
                writer = new OutputStreamWriter(zipOutputStream);
                writer.write(context.getString(R.string.export_zip_info));
                writer.flush();
                zipOutputStream.closeEntry();
            }
        } catch (JSONException ex ) {
            throw new IOException(ex);
        } finally {
            zipOutputStream.finish();
        }
    }


}
