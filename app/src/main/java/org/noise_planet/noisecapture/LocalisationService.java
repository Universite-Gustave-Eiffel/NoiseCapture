package org.noise_planet.noisecapture;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Fetch the most precise location from different location services.
 */
public class LocalisationService extends Service {

    private enum LISTENER {GPS, NETWORK, PASSIVE};
    private LocationManager gpsLocationManager;
    private LocationManager passiveLocationManager;
    private LocationManager networkLocationManager;
    private CommonLocationListener gpsLocationListener;
    private CommonLocationListener networkLocationListener;
    private CommonLocationListener passiveLocationListener;
    private long minTimeDelay = 1000;

    private TreeMap<Long, Location> timeLocation = new TreeMap<Long, Location>();

    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_service_started;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        LocalisationService getService() {
            return LocalisationService.this;
        }
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        initService();

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, "gps service stop", Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.local_service_started);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, Measurement.class), 0);

        // Set the info for the views that show in the notification panel.
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)  // the status icon
                .setTicker(text)  // the status text
                .setWhen(System.currentTimeMillis())  // the time stamp
                .setContentTitle("NoiseCapture")  // the label of the entry
                .setContentText(text)  // the contents of the entry
                .setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                .getNotification();

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

    private void initService() {
        initPassive();
        initGPS();
        initNetworkLocation();
    }

    private void initPassive() {
        if (passiveLocationListener == null) {
            passiveLocationListener = new CommonLocationListener(this, LISTENER.PASSIVE);
        }
        passiveLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && passiveLocationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            passiveLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                    minTimeDelay, 0, passiveLocationListener);
        }
    }

    private void initNetworkLocation() {
        if (networkLocationListener == null) {
            networkLocationListener = new CommonLocationListener(this, LISTENER.NETWORK);
        }
        networkLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && networkLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            networkLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    minTimeDelay, 0, networkLocationListener);
        }
    }

    private void initGPS() {
        if (gpsLocationListener == null) {
            gpsLocationListener = new CommonLocationListener(this, LISTENER.GPS);
        }
        gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && passiveLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            gpsLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeDelay, 0,
                    gpsLocationListener);
        }
    }

    public void addLocation(Location location) {
        timeLocation.put(location.getTime(), location);
    }

    /**
     * Fetch the nearest location acquired during the provided utc time.
     * @param utcTime UTC time
     * @return Location or null if not found.
     */
    public Location fetchLocation(Long utcTime) {
        Map.Entry<Long, Location> low = timeLocation.floorEntry(utcTime);
        Map.Entry<Long, Location> high = timeLocation.ceilingEntry(utcTime);
        Location res = null;
        if (low != null && high != null) {
            // Got two results, find nearest
            res = Math.abs(utcTime-low.getKey()) < Math.abs(utcTime-high.getKey())
                    ?   low.getValue()
                    :   high.getValue();
        } else if (low != null || high != null) {
            // Just one range bound, search the good one
            res = low != null ? low.getValue() : high.getValue();
        }
        return res;
    }


    private static class CommonLocationListener implements LocationListener, GpsStatus.Listener, GpsStatus.NmeaListener {
        private LocalisationService localisationService;
        private LISTENER listenerId;

        public CommonLocationListener(LocalisationService localisationService, LISTENER listenerId) {
            this.localisationService = localisationService;
            this.listenerId = listenerId;
        }

        @Override
        public void onGpsStatusChanged(int event) {

        }

        @Override
        public void onLocationChanged(Location location) {
            System.out.println(location.toString());
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }


        private int nmeaChecksum(String s) {
            int c = 0;
            for(char ch : s.toCharArray()) {
                c ^= ch;
            }
            return c;
        }

        @Override
        public void onNmeaReceived(long timestamp, String nmea) {

            if(nmea == null || !nmea.startsWith("$")){
                return;
            }
            StringTokenizer stringTokenizer = new StringTokenizer(nmea, ",");


        }
    }


}