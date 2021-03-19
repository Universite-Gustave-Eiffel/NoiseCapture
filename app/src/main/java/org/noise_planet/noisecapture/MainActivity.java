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


import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }
    // Color for noise exposition representation
    public int[] NE_COLORS;
    public static final String RESULTS_RECORD_ID = "RESULTS_RECORD_ID";
    protected static final Logger MAINLOGGER = LoggerFactory.getLogger(MainActivity.class);
    private static final int NOTIFICATION_MAP = R.string.notification_goto_community_map_title;

    // For the list view
    public ListView mDrawerList;
    public DrawerLayout mDrawerLayout;
    public String[] mMenuLeft;
    public ActionBarDrawerToggle mDrawerToggle;
    private ProgressDialog progress;

    public static final int PERMISSION_RECORD_AUDIO_AND_GPS = 1;
    public static final int PERMISSION_WIFI_STATE = 2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        NE_COLORS = new int[]{res.getColor(R.color.R1_SL_level),
                res.getColor(R.color.R2_SL_level),
                res.getColor(R.color.R3_SL_level),
                res.getColor(R.color.R4_SL_level),
                res.getColor(R.color.R5_SL_level)};
    }


    /**
     * If necessary request user to acquire permisions for critical ressources (gps and microphone)
     * @return True if service can be bind immediately. Otherwise the bind should be done using the
     * @see #onRequestPermissionsResult
     */
    protected boolean checkAndAskPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                // After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(this,
                        R.string.permission_explain_audio_record, Toast.LENGTH_LONG).show();
            }
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(this,
                        R.string.permission_explain_gps, Toast.LENGTH_LONG).show();
            }
            // Request the permission.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.FOREGROUND_SERVICE,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        PERMISSION_RECORD_AUDIO_AND_GPS);
            } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.FOREGROUND_SERVICE},
                        PERMISSION_RECORD_AUDIO_AND_GPS);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_RECORD_AUDIO_AND_GPS);
            }
            return false;
        }
        return true;
    }


    @Override
    protected void onPause() {
        if(progress != null && progress.isShowing()) {
            try {
                progress.dismiss();
            } catch (IllegalArgumentException ex) {
                //Ignore
            }
        }
        super.onPause();
    }

    /**
     * @return Version information on this application
     * @throws PackageManager.NameNotFoundException
     */
    public static String getVersionString(Activity activity) throws PackageManager.NameNotFoundException {
        String versionName = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        Date buildDate = new Date(BuildConfig.TIMESTAMP);
        String gitHash = BuildConfig.GITHASH;
        if(gitHash == null || gitHash.isEmpty()) {
            gitHash = "";
        } else {
            gitHash = gitHash.substring(0, 7);
        }
        return activity.getString(R.string.title_appversion, versionName,
                DateFormat.getDateInstance().format(buildDate), gitHash);
    }

    /**
     * If necessary request user to acquire permissions for critical resources (gps and microphone)
     * @return True if service can be bind immediately. Otherwise the bind should be done using the
     * @see #onRequestPermissionsResult
     */
    protected boolean checkAndAskWifiStatePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_WIFI_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.ACCESS_WIFI_STATE)) {
                // After the user
                // sees the explanation, try again to request the permission.
                Toast.makeText(this,
                        R.string.permission_explain_access_wifi_state, Toast.LENGTH_LONG).show();
            }
            // Request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_WIFI_STATE},
                    PERMISSION_WIFI_STATE);
            return false;
        }
        return true;
    }

    void initDrawer(Integer recordId) {
        try {
            // List view
            mMenuLeft = getResources().getStringArray(R.array.dm_list_array);
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            mDrawerList = (ListView) findViewById(R.id.left_drawer);
            // Set the adapter for the list view
            mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                    R.layout.drawer_list_item, mMenuLeft));
            // Set the list's click listener
            mDrawerList.setOnItemClickListener(new DrawerItemClickListener(recordId));
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
            MAINLOGGER.error(e.getLocalizedMessage(), e);
        }
    }
    // Drawer navigation
    void initDrawer() {
        initDrawer(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onBackPressed() {
        if(!(this instanceof MeasurementActivity)) {
            if(mDrawerLayout != null) {
                mDrawerLayout.closeDrawer(mDrawerList);
            }
            super.onBackPressed();
        } else {
            finish();
            // Show home
            Intent im = new Intent(Intent.ACTION_MAIN);
            im.addCategory(Intent.CATEGORY_HOME);
            im.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(im);
        }
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {

        Integer recordId;

        public DrawerItemClickListener(Integer recordId) {
            this.recordId = recordId;
        }

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            switch(position) {
                case 0:
                    // Measurement
                    Intent im = new Intent(getApplicationContext(),MeasurementActivity.class);
                    im.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(im);
                    finish();
                    break;
                case 1:
                    // Comment
                    Intent ir = new Intent(getApplicationContext(), CommentActivity.class);
                    if(recordId != null && recordId >= 0) {
                        ir.putExtra(CommentActivity.COMMENT_RECORD_ID, recordId);
                    }
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ir);
                    finish();
                    break;
                case 2:
                    // Results
                    ir = new Intent(getApplicationContext(), Results.class);
                    if(recordId != null && recordId >= 0) {
                        ir.putExtra(RESULTS_RECORD_ID, recordId);
                    }
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ir);
                    finish();
                    break;
                case 3:
                    // History
                    Intent a = new Intent(getApplicationContext(), History.class);
                    startActivity(a);
                    finish();
                    mDrawerLayout.closeDrawer(mDrawerList);
                    break;
                case 4:
                    // Show the map
                    Intent imap = new Intent(getApplicationContext(), MapActivity.class);
                    if(recordId != null && recordId >= 0) {
                        imap.putExtra(Results.RESULTS_RECORD_ID, recordId);
                    }
                    startActivity(imap);
                    finish();
                    mDrawerLayout.closeDrawer(mDrawerList);
                    break;
                case 5:
                    Intent ics = new Intent(getApplicationContext(), CalibrationMenu.class);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ics);
                    finish();
                    break;
                case 6:
                    Intent ih = new Intent(getApplicationContext(),ViewHtmlPage.class);
                    mDrawerLayout.closeDrawer(mDrawerList);
                    ih.putExtra("pagetosee",
                            getString(R.string.url_help));
                    ih.putExtra("titletosee",
                            getString(R.string.title_activity_help));
                    startActivity(ih);
                    finish();
                    break;
                case 7:
                    Intent ia = new Intent(getApplicationContext(),ViewHtmlPage.class);
                    ia.putExtra("pagetosee",
                            getString(R.string.url_about));
                    ia.putExtra("titletosee",
                            getString(R.string.title_activity_about));
                    mDrawerLayout.closeDrawer(mDrawerList);
                    startActivity(ia);
                    finish();
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
        if(mDrawerToggle != null) {
            mDrawerToggle.syncState();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(mDrawerToggle != null) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if(mDrawerLayout != null) {
            mDrawerLayout.closeDrawers();
        }

        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent is = new Intent(getApplicationContext(),SettingsActivity.class);
                startActivity(is);
            return true;
            /*
            case R.id.action_about:
                Intent ia = new Intent(getApplicationContext(),ViewHtmlPage.class);
                pagetosee=getString(R.string.url_about);
                titletosee=getString((R.string.title_activity_about));
                startActivity(ia);
                return true;
                */
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    protected boolean isManualTransferOnly() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        return !sharedPref.getBoolean("settings_data_transfer", true);
    }

    protected boolean isWifiTransferOnly() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPref.getBoolean("settings_data_transfer_wifi_only", false);
    }

    /**
     * Check the non-uploaded results and the connection states
     * Upload results if necessary
     */
    protected void checkTransferResults() {
        if (!isManualTransferOnly()) {
            MeasurementManager measurementManager = new MeasurementManager(this);
            if (!measurementManager.hasNotUploadedRecords()) {
                return;
            }
            if (isWifiTransferOnly()) {
                if (checkAndAskWifiStatePermission()) {
                    if (!checkWifiState()) {
                        return;
                    }
                } else {
                    // Transfer will begin when user validate check wifi rights
                    return;
                }
            }
            new Thread(new DoSendZipToServer(this)).start();
        }
    }


    /**
     * Ping largest internet dns access to check if internet is available
     * @return True if Internet is available
     */
    public boolean isOnline() {
        try {
            URL url = new URL(MeasurementUploadWPS.CHECK_UPLOAD_AVAILABILITY);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            int code = urlConnection.getResponseCode();
            return code == 200 || code == 301 || code == 302;
        } catch (IOException e) {
            MAINLOGGER.error(e.getLocalizedMessage(), e);
        }
        return false;
    }

    /**
     * Transfer provided records
     */
    protected void doTransferRecords(List<Integer> selectedRecordIds) {
        runOnUiThread(new SendResults(this, selectedRecordIds));
    }

    /**
     * Transfer records without checking user preferences
     */
    protected void doTransferRecords() {
        if(!isOnline()) {
            MAINLOGGER.info("Not online, skip send of record");
            return;
        }
        MeasurementManager measurementManager = new MeasurementManager(this);
        List<Storage.Record> records = measurementManager.getRecords();
        final List<Integer> recordsToTransfer = new ArrayList<>();
        for(Storage.Record record : records) {
            // Auto send records only if the record is not in progress and if the user have
            // validated the Description activity
            if(record.getUploadId().isEmpty() && record.getTimeLength() > 0 && record
                    .getNoisePartyTag() != null) {
                recordsToTransfer.add(record.getId());
            }
        }
        if(!recordsToTransfer.isEmpty()) {
            doTransferRecords(recordsToTransfer);
        }
    }

    protected static final class SendResults implements Runnable {
        private MainActivity mainActivity;
        private List<Integer> recordsToTransfer;

        public SendResults(MainActivity mainActivity, List<Integer> recordsToTransfer) {
            this.mainActivity = mainActivity;
            this.recordsToTransfer = recordsToTransfer;
        }

        public SendResults(MainActivity mainActivity, Integer... recordsToTransfer) {
            this.mainActivity = mainActivity;
            this.recordsToTransfer = Arrays.asList(recordsToTransfer);
        }

        @Override
        public void run() {
            // Export
            try {
                mainActivity.progress = ProgressDialog.show(mainActivity, mainActivity
                        .getText(R.string
                        .upload_progress_title),
                        mainActivity.getText(R.string.upload_progress_message), true);
            } catch (RuntimeException ex) {
                // This error may arise on some system
                // The display of progression are not vital so cancel the crash by handling the
                // error
                MAINLOGGER.error(ex.getLocalizedMessage(), ex);
            }
            new Thread(new SendZipToServer(mainActivity, recordsToTransfer, mainActivity
                    .progress, new
                    OnUploadedListener() {
                @Override
                public void onMeasurementUploaded() {
                    mainActivity.onTransferRecord();
                }
            })).start();
        }
    }

    protected void onTransferRecord() {
        // Nothing to do
    }


    /***
     * Checks that application runs first time and write flags at SharedPreferences
     * Need further codes for enhancing conditions
     * @return true if 1st time
     * see : http://stackoverflow.com/questions/9806791/showing-a-message-dialog-only-once-when-application-is-launched-for-the-first
     * see also for checking version (later) : http://stackoverflow.com/questions/7562786/android-first-run-popup-dialog
     * Can be used for checking new version
     */
    protected boolean CheckNbRun(String preferenceName, int maxCount) {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        Integer NbRun = preferences.getInt(preferenceName, 1);
        if (NbRun > maxCount) {
            NbRun=1;
        }
        editor.putInt(preferenceName, NbRun+1);
        editor.apply();
        return (NbRun==1);
    }

    protected void displayCommunityMapNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(MeasurementService.getNotificationIcon())
                .setContentTitle(getString(R.string.notification_goto_community_map_title))
                .setContentText(getString(R.string.notification_goto_community_map))
                .setAutoCancel(true);
        NotificationCompat.BigTextStyle bigTextStyle =
                new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(getString(R.string.notification_goto_community_map_title));
        bigTextStyle.bigText(getString(R.string.notification_goto_community_map));
        builder.setStyle(bigTextStyle);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://noise-planet.org/map_noisecapture"));
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(this);
        stackBuilder.addNextIntent(intent);
        builder.setContentIntent(stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
        NotificationManager mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mNM.notify(NOTIFICATION_MAP, builder.build());
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_WIFI_STATE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkTransferResults();
                }
            }
        }
    }

    private boolean checkWifiState() {

        // Check connection state
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiMgr.isWifiEnabled()) { // WiFi adapter is ON
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            if (wifiInfo.getNetworkId() == -1) {
                return false; // Not connected to an access-Point
            }
            // Connected to an Access Point
            return true;
        } else {
            return false; // WiFi adapter is OFF
        }
    }

    public static final class DoSendZipToServer implements Runnable {
        MainActivity mainActivity;

        public DoSendZipToServer(MainActivity mainActivity) {
            this.mainActivity = mainActivity;
        }

        @Override
        public void run() {
            mainActivity.doTransferRecords();
        }
    }

    public static final class SendZipToServer implements Runnable {
        private Activity activity;
        private List<Integer> recordsId = new ArrayList<>();
        private ProgressDialog progress;
        private final OnUploadedListener listener;

        public SendZipToServer(Activity activity, int recordId, ProgressDialog progress, OnUploadedListener listener) {
            this.activity = activity;
            this.recordsId.add(recordId);
            this.progress = progress;
            this.listener = listener;
        }

        public SendZipToServer(Activity activity, Collection<Integer> records, ProgressDialog progress, OnUploadedListener listener) {
            this.activity = activity;
            this.recordsId.addAll(records);
            this.progress = progress;
            this.listener = listener;
        }

        @Override
        public void run() {
            MeasurementUploadWPS measurementUploadWPS = new MeasurementUploadWPS(activity);
            MeasurementManager measurementManager = new MeasurementManager(activity);
            try {
                for(Integer recordId : recordsId) {
                    Storage.Record record = measurementManager.getRecord(recordId);
                    if(record.getUploadId().isEmpty()) {
                        measurementUploadWPS.uploadRecord(recordId);
                    }
                }
                if(listener != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onMeasurementUploaded();
                        }
                    });
                }
            } catch (final IOException ex) {
                MAINLOGGER.error(ex.getLocalizedMessage(), ex);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity,
                                ex.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                if(progress != null && progress.isShowing()) {
                    try {
                        progress.dismiss();
                    } catch (IllegalArgumentException ex) {
                        //Ignore
                    }
                }
            }
        }
    }

    public interface OnUploadedListener {
        void onMeasurementUploaded();
    }
    // Choose color category in function of sound level
    public static int getNEcatColors(double SL) {

        int NbNEcat;

        if (SL > 75.) {
            NbNEcat = 0;
        } else if (SL > 65) {
            NbNEcat = 1;
        } else if (SL > 55) {
            NbNEcat = 2;
        } else if (SL > 45) {
            NbNEcat = 3;
        } else {
            NbNEcat = 4;
        }
        return NbNEcat;
    }


    public static double getDouble(SharedPreferences sharedPref, String key, double defaultValue) {
        try {
            return Double.valueOf(sharedPref.getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static int getInteger(SharedPreferences sharedPref, String key, int defaultValue) {
        try {
            return Integer.valueOf(sharedPref.getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
