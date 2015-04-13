package org.orbisgis.protonomap;

import android.content.res.Configuration;
import android.graphics.Color;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;


public class MainActivity extends ActionBarActivity {

    public ActionBarDrawerToggle mDrawerToggle;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_measurement, menu);
        return true;
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

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

    // Color for noise exposition representation
    public static final int[] NE_COLORS = {
            Color.rgb(255, 0, 0), Color.rgb(255, 128, 0), Color.rgb(255, 255, 0), Color.rgb(128, 255, 0), Color.rgb(0, 255, 0)
    };
    // Choose color category in function of sound level
    public int getNEcatColors(float SL) {

        int NbNEcat;

        if(SL > 75.)
        {
            NbNEcat=0;
        }
        else if( (SL<=75) & (SL>65))
        {
            NbNEcat=1;
        }
        else if( (SL<=65) & (SL>55))
        {
            NbNEcat=2;
        }
        else if( (SL<=55) & (SL>45))
        {
            NbNEcat=3;
        }
        else
        {
            NbNEcat=4;
        }
        return NbNEcat;
    }

    // Color for spectrum representation (min, iSL, max)
    public static final int[] SPECTRUM_COLORS = {
            Color.rgb(0, 128, 255), Color.rgb(102, 178, 255), Color.rgb(204, 229, 255),
    };

    public int[] getColors() {

        int stacksize = 3;

        // have as many colors as stack-values per entry
        int []colors = new int[stacksize];

        for(int i = 0; i < stacksize; i++) {
            colors[i] = SPECTRUM_COLORS[i];
        }

        return colors;
    }

}
