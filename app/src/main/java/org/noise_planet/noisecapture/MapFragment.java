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

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;



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
    private MapFragmentAvailableListener mapFragmentAvailableListener;
    private List<String> cachedCommands = new ArrayList<>();

    public void setMapFragmentAvailableListener(MapFragmentAvailableListener mapFragmentAvailableListener) {
        this.mapFragmentAvailableListener = mapFragmentAvailableListener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if(view == null) {
            // Inflate the layout for this fragment
            view = inflater.inflate(R.layout.fragment_measurement_map, container, false);
            leaflet = (WebView) view.findViewById(R.id.measurement_webmapview);
            leaflet.clearCache(true);
            leaflet.clearHistory();
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
                    if(mapFragmentAvailableListener != null) {
                        mapFragmentAvailableListener.onPageLoaded(MapFragment.this);
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(browserIntent);
                    return true;
                }
            });
            if(mapFragmentAvailableListener != null) {
                mapFragmentAvailableListener.onMapFragmentAvailable(this);
            }
        }
        return view;
    }

    /**
     * @return The WebView control
     */
    public WebView getWebView() {
        return leaflet;
    }

    public void loadUrl(String url) {
        if(leaflet != null) {
            leaflet.loadUrl(url);
        }
    }

    public boolean runJs(String js) {
        if(leaflet != null && pageLoaded.get()) {
            // Run cached commands before this new command
            cachedCommands.add(js);
            while(!cachedCommands.isEmpty()) {
                js = cachedCommands.remove(0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    leaflet.evaluateJavascript(js, null);
                } else {
                    leaflet.loadUrl("javascript:" + js);
                }
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

    public void addMeasurement(LatLng location, String htmlColor) {
        if(lastPt != null) {
            float[] result = new float[3];
            Location.distanceBetween(lastPt.lat, lastPt.lng, location.lat, location.lng, result);
            if(result[0] < ignoreNewPointDistanceDelta) {
                return;
            }
        }
        lastPt = location;
        String command = "addMeasurementPoint(["+location.getLat()+","+location.getLng()+"], '"+htmlColor+"')";
        if(!runJs(command)) {
            cachedCommands.add(command);
        }
    }

    public void cleanMeasurementPoints() {
        runJs("userMeasurementPoints.clearLayers()");
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

    public interface MapFragmentAvailableListener {
        void onMapFragmentAvailable(MapFragment mapFragment);
        void onPageLoaded(MapFragment mapFragment);
    }

}
