/*
 *  This file is part of the NoiseCapture application and OnoMap system.
 *
 *  The 'OnoMaP' system is led by Lab-STICC and Univ Eiffel - UMRAE and generates noise maps via
 *  citizen-contributed noise data.
 *
 *  This application is co-funded by the ENERGIC-OD Project (European Network for
 *  Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 *  (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 *  PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 *  Community. The application work is also supported by the French geographic portal GEOPAL of the
 *  Pays de la Loire region (http://www.geopal.org).
 *
 *  Copyright (C) Univ Eiffel - UMRAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 *  NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 3 of
 *  the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 *  it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 *  more details.You should have received a copy of the GNU General Public License along with this
 *  program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 *  Boston, MA 02110-1301  USA or see For more information,  write to Université Gustave Eiffel,
 *  14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *   or write to scientific.computing@univ-eiffel.fr
 */

package org.noise_planet.noisecapture;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.LruCache;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final String REFERER_URL = "https://noise-planet.org";
    private static final int MAX_CACHE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB in-memory cache

    private static final LruCache<String, CachedResponse> responseCache =
            new LruCache<String, CachedResponse>(MAX_CACHE_SIZE_BYTES) {
                @Override
                protected int sizeOf(String key, CachedResponse value) {
                    return value.getData() != null ? value.getData().length : 0;
                }
            };

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
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
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
                    if (url == null) return false;
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    if (browserIntent.resolveActivity(getContext().getPackageManager()) != null) {
                        try {
                            startActivity(browserIntent);
                            return true;
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(getContext(), R.string.webview_error_open_link, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), R.string.webview_error_unsupported_link, Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null) {
                        WebResourceResponse response = handleRequest(request.getUrl(), request.getRequestHeaders());
                        if (response != null) {
                            return response;
                        }
                    }
                    return super.shouldInterceptRequest(view, request);
                }

                @Override
                @SuppressWarnings("deprecation")
                public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                    if (url != null) {
                        WebResourceResponse response = handleRequest(Uri.parse(url), null);
                        if (response != null) {
                            return response;
                        }
                    }
                    return super.shouldInterceptRequest(view, url);
                }
            });
            if(mapFragmentAvailableListener != null) {
                mapFragmentAvailableListener.onMapFragmentAvailable(this);
            }
        }
        return view;
    }

    /**
     * Intercepts HTTP/HTTPS requests from the WebView and injects the "Referer" header
     * set to "https://noise-planet.org". Required for map tile providers (such as CartoDB API)
     * enforcing HTTP Referrer domain whitelisting.
     * Responses are cached locally to reduce network usage on tile reload / panning.
     *
     * @param uri The requested resource URI
     * @param requestHeaders Headers sent by the WebView
     * @return The intercepted WebResourceResponse with custom Referer header, or null to let WebView handle it
     */
    WebResourceResponse handleRequest(Uri uri, Map<String, String> requestHeaders) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return null;
        }
        String urlString = uri.toString();

        // 1. Memory cache check
        CachedResponse cached = responseCache.get(urlString);

        // 2. Disk cache check
        if (cached == null && getContext() != null) {
            cached = loadFromDiskCache(getContext(), urlString);
            if (cached != null) {
                responseCache.put(urlString, cached);
            }
        }

        if (cached != null) {
            return createWebResourceResponse(cached);
        }

        // 3. Network fetch
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            if (requestHeaders != null) {
                for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            connection.setRequestProperty("Referer", REFERER_URL);

            int responseCode = connection.getResponseCode();
            String contentType = connection.getContentType();
            String mimeType = "application/octet-stream";
            String encoding = null;
            if (contentType != null) {
                String[] parts = contentType.split(";");
                mimeType = parts[0].trim();
                for (int i = 1; i < parts.length; i++) {
                    String part = parts[i].trim();
                    if (part.toLowerCase().startsWith("charset=")) {
                        encoding = part.substring("charset=".length()).trim();
                    }
                }
            }

            InputStream inputStream = (responseCode >= 400) ? connection.getErrorStream() : connection.getInputStream();
            if (inputStream == null) {
                return null;
            }

            byte[] data = readAllBytes(inputStream);

            Map<String, String> responseHeaders = new HashMap<>();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
                    if (field.getKey() != null && !field.getValue().isEmpty()) {
                        responseHeaders.put(field.getKey(), field.getValue().get(0));
                    }
                }
            }
            String message = connection.getResponseMessage();
            if (message == null || message.isEmpty()) {
                message = "OK";
            }

            CachedResponse responseToCache = new CachedResponse(mimeType, encoding, responseCode, message, responseHeaders, data);

            if (responseCode == 200) {
                responseCache.put(urlString, responseToCache);
                if (getContext() != null) {
                    saveToDiskCache(getContext(), urlString, responseToCache);
                }
            }

            return createWebResourceResponse(responseToCache);
        } catch (Exception e) {
            return null;
        }
    }

    private WebResourceResponse createWebResourceResponse(CachedResponse cached) {
        ByteArrayInputStream dataStream = new ByteArrayInputStream(cached.getData());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new WebResourceResponse(
                    cached.getMimeType(),
                    cached.getEncoding(),
                    cached.getStatusCode(),
                    cached.getReasonPhrase(),
                    cached.getHeaders(),
                    dataStream
            );
        } else {
            return new WebResourceResponse(
                    cached.getMimeType(),
                    cached.getEncoding(),
                    dataStream
            );
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private static String getCacheKeyHash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(url.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    private static void saveToDiskCache(Context context, String url, CachedResponse response) {
        try {
            File cacheDir = new File(context.getCacheDir(), "map_tiles");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File cacheFile = new File(cacheDir, getCacheKeyHash(url));
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(cacheFile));
            dos.writeUTF(response.getMimeType() != null ? response.getMimeType() : "");
            dos.writeUTF(response.getEncoding() != null ? response.getEncoding() : "");
            dos.writeInt(response.getStatusCode());
            dos.writeUTF(response.getReasonPhrase() != null ? response.getReasonPhrase() : "");
            dos.writeInt(response.getData().length);
            dos.write(response.getData());
            dos.close();
        } catch (Exception ignored) {
        }
    }

    private static CachedResponse loadFromDiskCache(Context context, String url) {
        try {
            File cacheFile = new File(new File(context.getCacheDir(), "map_tiles"), getCacheKeyHash(url));
            if (!cacheFile.exists()) {
                return null;
            }
            DataInputStream dis = new DataInputStream(new FileInputStream(cacheFile));
            String mimeType = dis.readUTF();
            String encoding = dis.readUTF();
            int statusCode = dis.readInt();
            String reasonPhrase = dis.readUTF();
            int length = dis.readInt();
            byte[] data = new byte[length];
            dis.readFully(data);
            dis.close();
            if (mimeType.isEmpty()) mimeType = null;
            if (encoding.isEmpty()) encoding = null;
            if (reasonPhrase.isEmpty()) reasonPhrase = "OK";
            return new CachedResponse(mimeType, encoding, statusCode, reasonPhrase, new HashMap<String, String>(), data);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearResponseCache(Context context) {
        responseCache.evictAll();
        if (context != null) {
            try {
                File cacheDir = new File(context.getCacheDir(), "map_tiles");
                if (cacheDir.exists() && cacheDir.isDirectory()) {
                    File[] files = cacheDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            file.delete();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static CachedResponse getCachedResponse(String url) {
        return responseCache.get(url);
    }

    public static int getResponseCacheCount() {
        return responseCache.size();
    }

    /**
     * @return The WebView control
     */
    public WebView getWebView() {
        return leaflet;
    }

    /**
     * Loads a URL into the WebView with the "Referer" header set to "https://noise-planet.org".
     *
     * @param url The URL to load
     */
    public void loadUrl(String url) {
        if(leaflet != null) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER_URL);
            leaflet.loadUrl(url, headers);
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

    public static class CachedResponse {
        private final String mimeType;
        private final String encoding;
        private final int statusCode;
        private final String reasonPhrase;
        private final Map<String, String> headers;
        private final byte[] data;

        public CachedResponse(String mimeType, String encoding, int statusCode, String reasonPhrase, Map<String, String> headers, byte[] data) {
            this.mimeType = mimeType;
            this.encoding = encoding;
            this.statusCode = statusCode;
            this.reasonPhrase = reasonPhrase;
            this.headers = headers != null ? headers : new HashMap<String, String>();
            this.data = data;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getEncoding() {
            return encoding;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getReasonPhrase() {
            return reasonPhrase;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public byte[] getData() {
            return data;
        }
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
