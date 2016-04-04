package org.noise_planet.noisecapture;


import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    // For the list view
    public ListView mDrawerList;
    public DrawerLayout mDrawerLayout;
    public String[] mMenuLeft;
    public ActionBarDrawerToggle mDrawerToggle;

    public boolean isResults = false;
    public boolean isHistory = true; // must be false but just for the testing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);

    }

    // Drawer navigation
    void initDrawer() {
        try {
            // List view
            mMenuLeft = getResources().getStringArray(R.array.dm_list_array);
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            mDrawerList = (ListView) findViewById(R.id.left_drawer);
            // Set the adapter for the list view
            mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                    R.layout.drawer_list_item, mMenuLeft));
            // Set the list's click listener
            mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
            // Display the List view into the action bar
            mDrawerToggle = new ActionBarDrawerToggle(
                    this,                  /* host Activity */
                    mDrawerLayout,         /* DrawerLayout object */
                    R.string.drawer_open,  /* "open drawer" description */
                    R.string.drawer_close  /* "close drawer" description */
            ) {
                /**
                 * Called when a drawer has settled in a completely closed state.
                 */
                public void onDrawerClosed(View view) {
                    super.onDrawerClosed(view);
                    getSupportActionBar().setTitle(getTitle());
                }

                /**
                 * Called when a drawer has settled in a completely open state.
                 */
                public void onDrawerOpened(View drawerView) {
                    super.onDrawerOpened(drawerView);
                    getSupportActionBar().setTitle(getString(R.string.title_menu));
                }
            };
            // Set the drawer toggle as the DrawerListener
            mDrawerLayout.setDrawerListener(mDrawerToggle);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            e.printStackTrace();
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            switch(position) {
                case 0:
                    // Measurement
                    Intent im = new Intent(getApplicationContext(),Measurement.class);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(im);
                    break;
                case 1:
                    // Results
                    gotoResults();
                    break;
                case 2:
                    // History
                    gotoHistory();
                    break;
                case 3:
                    // Show the map if data transfer settings is true
                    // TODO: Check also if data transfer using wifi
                    if (CheckDataTransfer()) {
                        Intent imap = new Intent(getApplicationContext(), Map.class);
                        //mDrawerLayout.closeDrawer(mDrawerList);
                        startActivity(imap);
                    }
                    else {
                        DialogBoxDataTransfer();
                    }
                    mDrawerLayout.closeDrawer(mDrawerList);
                    break;
                case 4:
                    Intent ics = new Intent(getApplicationContext(), activity_calibration_start.class);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ics);
                    break;
                case 5:
                    Intent ih = new Intent(getApplicationContext(),View_html_page.class);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    ih.putExtra(this.getClass().getPackage().getName() + ".pagetosee",
                            getString(R.string.url_help));
                    ih.putExtra(this.getClass().getPackage().getName() + ".titletosee",
                            getString(R.string.title_activity_help));
                    startActivity(ih);
                    break;
                case 6:
                    Intent ia = new Intent(getApplicationContext(),View_html_page.class);
                    ia.putExtra(this.getClass().getPackage().getName() + ".pagetosee",
                            getString(R.string.url_about));
                    ia.putExtra(this.getClass().getPackage().getName() + ".titletosee",
                            getString(R.string.title_activity_about));
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ia);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        CharSequence mTitle = title;
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setTitle(mTitle);
        }
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

        mDrawerLayout.closeDrawers();

        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent is = new Intent(getApplicationContext(),Settings.class);
                startActivity(is);
            return true;
            /*
            case R.id.action_about:
                Intent ia = new Intent(getApplicationContext(),View_html_page.class);
                pagetosee=getString(R.string.url_about);
                titletosee=getString((R.string.title_activity_about));
                startActivity(ia);
                return true;
                */
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    private boolean CheckDataTransfer() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean DataTransfer = sharedPref.getBoolean("settings_data_transfer", true);
        return DataTransfer;
    }

    // Dialog box for activating data transfer
    public boolean DialogBoxDataTransfer() {
           new AlertDialog.Builder(this)
                .setTitle(R.string.title_caution_data_transfer)
                .setMessage(R.string.text_caution_data_transfer)
                .setPositiveButton(R.string.text_OK_data_transfer, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Goto to the settings page
                        Intent is = new Intent(getApplicationContext(),Settings.class);
                        startActivity(is);
                    }
                })
                .setNegativeButton(R.string.text_CANCEL_data_transfer, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Nothing is done
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
        return true;
    }


     // Color for noise exposition representation
    public int[] NE_COLORS() {
        Resources res = getResources();
        int R1_SL_level = res.getColor(R.color.R1_SL_level);
        int R2_SL_level = res.getColor(R.color.R2_SL_level);
        int R3_SL_level = res.getColor(R.color.R3_SL_level);
        int R4_SL_level = res.getColor(R.color.R4_SL_level);
        int R5_SL_level = res.getColor(R.color.R5_SL_level);
        return new int[] {R1_SL_level,R2_SL_level,R3_SL_level,R4_SL_level,R5_SL_level};
    };
    // Choose color category in function of sound level
    public int getNEcatColors(double SL) {

        int NbNEcat;

        if (SL > 75.) {
            NbNEcat = 0;
        } else if ((SL <= 75) & (SL > 65)) {
            NbNEcat = 1;
        } else if ((SL <= 65) & (SL > 55)) {
            NbNEcat = 2;
        } else if ((SL <= 55) & (SL > 45)) {
            NbNEcat = 3;
        } else {
            NbNEcat = 4;
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

        System.arraycopy(SPECTRUM_COLORS, 0, colors, 0, stacksize);

        return colors;
    }

    public final void gotoHistory(){

        if (isHistory) {
            Intent a = new Intent(getApplicationContext(), History.class);
            startActivity(a);
            mDrawerLayout.closeDrawer(mDrawerList);
        }
        else {
            // Message for starting a record
            Toast.makeText(getApplicationContext(),
                    getString(R.string.no_history), Toast.LENGTH_LONG).show();
        }

    }

    public final void gotoResults() {
        if (isResults) {
            Intent ir = new Intent(getApplicationContext(), Results.class);
            mDrawerLayout.closeDrawer(mDrawerList);
            startActivity(ir);
        }
        else {
            // Message for starting a record
            Toast.makeText(getApplicationContext(),
                    getString(R.string.no_results), Toast.LENGTH_LONG).show();
        }
    }

    public final void checkHistoryButton() {
        // Enabled/disabled buttons
        // history disabled; cancel enabled; record button enabled
        ImageButton buttonhistory= (ImageButton) findViewById(R.id.historyBtn);
        if (isHistory){
            buttonhistory.setImageResource(R.drawable.button_history_normal);
            buttonhistory.setEnabled(true);
        }
        else {
            buttonhistory.setImageResource(R.drawable.button_history_disabled);
            buttonhistory.setEnabled(false);
        }
    }

}
