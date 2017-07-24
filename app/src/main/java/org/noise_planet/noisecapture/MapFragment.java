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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.noise_planet.noisecapture.MainActivity.getNEcatColors;


/**
 * Map tab content on measurement activity
 */
public class MapFragment extends Fragment {
    private View view;
    private WebView leaflet;
    private boolean isLocationLayerAdded = false;
    private final AtomicBoolean pageLoaded = new AtomicBoolean(false);
    private double ignoreNewPointDistanceDelta = 1;
    private LatLng lastPt;
    public int[] NE_COLORS;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    int[] getNE_COLORS(Context context) {
        if(NE_COLORS == null) {
            Resources res = context.getResources();
            NE_COLORS = new int[]{res.getColor(R.color.R1_SL_level),
                    res.getColor(R.color.R2_SL_level),
                    res.getColor(R.color.R3_SL_level),
                    res.getColor(R.color.R4_SL_level),
                    res.getColor(R.color.R5_SL_level)};
        }
        return NE_COLORS;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if(view == null) {
            // Inflate the layout for this fragment
            view = inflater.inflate(R.layout.fragment_measurement_map, container, false);
            leaflet = (WebView) view.findViewById(R.id.measurement_webmapview);
            WebSettings webSettings = leaflet.getSettings();
            webSettings.setJavaScriptEnabled(true);
            WebSettings settings = leaflet.getSettings();
            settings.setAppCachePath(new File(getContext().getCacheDir(), "webview").getPath());
            settings.setAppCacheEnabled(true);
            leaflet.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    pageLoaded.set(true);
                }
            });
            leaflet.loadUrl("file:///android_asset/html/map_measurement.html");
        }
        return view;
    }

    public boolean runJs(String js) {
        if(leaflet != null && pageLoaded.get()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                leaflet.evaluateJavascript(js, null);
            } else {
                leaflet.loadUrl("javascript:" + js);
            }
            return true;
        }
        return false;
    }

    public void updateLocationMarker(LatLng newLocation, double precision) {
        if(runJs("updateLocation(["+newLocation.getLat()+","+newLocation.getLng()+"], "+precision+")")) {
            if (!isLocationLayerAdded) {
                addLocationMarker();
                isLocationLayerAdded = true;
            }
        }
    }

    private void addLocationMarker() {
        runJs("userLocationLayer.addTo(map)");
    }

    public void addMeasurement(Context context, LatLng location, double spl) {
        if(lastPt != null) {
            float[] result = new float[3];
            Location.distanceBetween(lastPt.lat, lastPt.lng, location.lat, location.lng, result);
            if(result[0] < ignoreNewPointDistanceDelta) {
                return;
            }
        }
        lastPt = location;
        int nc= MainActivity.getNEcatColors(spl);    // Choose the color category in function of the sound level
        String htmlColor = String.format("#%06X",
                (0xFFFFFF & getNE_COLORS(context)[nc]));
        runJs("addMeasurementPoint(["+location.getLat()+","+location.getLng()+"], '"+htmlColor+"')");
    }

    public void removeLocationMarker() {
        runJs("userLocationLayer.removeFrom(map)");
    }

    public static final class LatLng {
        double lat;
        double lng;
        double alt = 0;

        public LatLng(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public LatLng(double lat, double lng, double alt) {
            this.lat = lat;
            this.lng = lng;
            this.alt = alt;
        }

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }
    }
    /**
     *
     *


     updateLocation([47.65,-2.76], 18)

     userLocationLayer.addTo(map)

     userLocationLayer.removeFrom(map)


     * */
}
