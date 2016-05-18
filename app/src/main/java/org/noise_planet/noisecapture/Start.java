package org.noise_planet.noisecapture;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;


public class Start extends Activity {
    private static final Logger LOGGER = LoggerFactory.getLogger(Start.class);
    private Thread thread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        // If first start then create a unique identifier for this install
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if(!sharedPref.contains(MeasurementExport.PROP_UUID)) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(MeasurementExport.PROP_UUID, UUID.randomUUID().toString());
            editor.apply();
        }
    // read app version name
    try {
        String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        Date buildDate = new Date(BuildConfig.TIMESTAMP);
        TextView versionText = (TextView) findViewById(R.id.textView_appversion);
        versionText.setText(getString(R.string.title_appversion, versionName,
                DateFormat.getDateInstance().format(buildDate)));
    } catch (PackageManager.NameNotFoundException ex) {
        LOGGER.error(ex.getLocalizedMessage(), ex);
    }

    final Start myActivity = this;

    thread=  new Thread(){
        @Override
        public void run(){
            try {
                synchronized(this){
                    wait(3000);
                }
            }
            catch(InterruptedException ex){
            }

            Intent i = new Intent(getApplicationContext(),Measurement.class);
            startActivity(i);
            finish();
        }
    };

    thread.start();
}

    @Override
    public boolean onTouchEvent(MotionEvent evt)
    {
        if(evt.getAction() == MotionEvent.ACTION_DOWN)
        {
            synchronized(thread){
                thread.notifyAll();
            }
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_start, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
