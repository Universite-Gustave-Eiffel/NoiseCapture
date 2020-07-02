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
import android.util.Base64;
import android.util.Base64OutputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Communicate with WPS server in order to upload measurements
 */
public class MeasurementUploadWPS {
    Activity activity;
    public static final String BASE_URL = "https://onomap-gs.noise-planet.org";
    public static final String CHECK_UPLOAD_AVAILABILITY = "https://onomap-gs.noise-planet.org/geoserver/ows?service=wps&version=1.0.0&request=GetCapabilities";

    public MeasurementUploadWPS(Activity activity) {
        this.activity = activity;
    }

    public void uploadRecord(int recordId) throws IOException {
        MeasurementExport measurementExport = new MeasurementExport(activity);
        MeasurementManager measurementManager = new MeasurementManager(activity);

        // Check if this record has not been already uploaded
        //Storage.Record record = measurementManager.getRecord(recordId);
        //if(!record.getUploadId().isEmpty()) {
        //    throw new IOException(activity.getText(R.string.error_already_uploaded).toString());
        //}

        URL url;
        url = new URL(BASE_URL + "/geoserver/wps");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Content-Type", "text/xml");
        conn.setReadTimeout(15000);
        conn.setConnectTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);


        OutputStream os = conn.getOutputStream();
        try {
            // Copy beginning of WPS query XML file
            InputStream inputStream = activity.getResources().openRawResource(R.raw.wps_begin);
            try {
                byte buf[] = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
            } finally {
                inputStream.close();
            }
            // Copy content of zip file
            Base64OutputStream base64OutputStream = new Base64OutputStream(os, Base64.NO_CLOSE | Base64.NO_WRAP);
            try {
                measurementExport.exportRecord(recordId, base64OutputStream, false);
            } finally {
                base64OutputStream.close();
            }
            // Copy end of WPS query XML file
            inputStream = activity.getResources().openRawResource(R.raw.wps_end);
            try {
                byte buf[] = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
            } finally {
                inputStream.close();
            }
        } finally {
            os.close();
        }
        int responseCode=conn.getResponseCode();

        if (responseCode == HttpsURLConnection.HTTP_OK) {
            String line;
            BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder uuid = new StringBuilder();
            while ((line=br.readLine()) != null) {
                uuid.append(line);
            }
            // Update Track UUID
            Pattern pattern = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            Matcher matcher = pattern.matcher(uuid.toString());
            if(matcher.matches()) {
                measurementManager.updateRecordUUID(recordId, uuid.toString());
            } else {
                throw new IOException("Illegal track UUID :"+uuid.toString());
            }
        } else {
            // Transfer failed
            throw new IOException("Failed to transfer measurement "
                    + conn.getResponseMessage()+" [code:"+responseCode+"]");
        }

    }
}
