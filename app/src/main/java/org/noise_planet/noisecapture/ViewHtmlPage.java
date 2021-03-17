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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;
import android.util.TypedValue;
import android.view.Menu;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;


public class ViewHtmlPage extends MainActivity {
    private AtomicBoolean refreshed = new AtomicBoolean(false);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.activity_view_html_page);
        initDrawer();
        final WebView webview = (WebView) findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        String url = "";
        if(intent.hasExtra("pagetosee")) {
            url = intent.getStringExtra("pagetosee");
        } else {
            // Convert
            // org.noise_planet.noisecapture://android_asset/html/help.html#smartphone_calibration
            // into file:///android_asset/html/help.html#smartphone_calibration
            try {
                URL internalUrl = new URL("file", intent.getData().getHost(), intent.getData().getPath() + "#" + intent.getData().getFragment());
                url = internalUrl.toString();
            }catch (MalformedURLException ex) {
                onBackPressed();
            }
        }
        webview.loadUrl(url);
        runJs();
        webview.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if(refreshed.compareAndSet(false, true) && url.contains("#")) {
                    webview.loadUrl(url);
                } else {
                    runJs();
                }
            }
        });
        // Get background color
        TypedValue a = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);
        if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            webview.setBackgroundColor(a.data);
        }
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setTitle(intent.getStringExtra("titletosee"));
        }

    }

    private void runJs() {
        final WebView webView = (WebView) findViewById(R.id.webview);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        // Load extra parameters
        String versionInfo = "";
        try {
            versionInfo = getVersionString(ViewHtmlPage.this);
        } catch (PackageManager.NameNotFoundException ex){
            MAINLOGGER.error(ex.getLocalizedMessage(), ex);
        }
        String uuid = sharedPref.getString(MeasurementExport.PROP_UUID, "");
        String content = "<h1>"+getText(R.string.app_name)+"</h1> "+versionInfo+"<br/>"+getText(R.string.user_id_activity_about)+": "+uuid;
        String js = "document.getElementById(\"about_title\").innerHTML = \""+content+"\"";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.evaluateJavascript(js, null);
        } else {
            webView.loadUrl("javascript:"+js);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
}
