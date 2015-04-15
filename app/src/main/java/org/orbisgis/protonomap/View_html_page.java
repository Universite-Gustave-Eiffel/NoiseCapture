package org.orbisgis.protonomap;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;


public class View_html_page extends MainActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_html_page);
        initDrawer();

        WebView myWebView = (WebView) findViewById(R.id.webview);
        myWebView.loadUrl(MainActivity.pagetosee);
        getSupportActionBar().setTitle(MainActivity.titletosee);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
}
