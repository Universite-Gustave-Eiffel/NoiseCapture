package org.noise_planet.noisecapture;

import android.os.Bundle;
import android.view.Menu;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;


public class MapActivity extends MainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        initDrawer();

        // Fill the spinner_map
        Spinner spinner = (Spinner) findViewById(R.id.spinner_map);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.choice_user_map, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Display the map (test)
        WebView leaflet  = (WebView) findViewById(R.id.webview_map);
        WebSettings webSettings = leaflet.getSettings();
        webSettings.setJavaScriptEnabled(true);
        leaflet.clearCache(true);
        leaflet.setInitialScale(200);
        leaflet.loadUrl("http://webcarto.orbisgis.org/noisemap.html");

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
}
