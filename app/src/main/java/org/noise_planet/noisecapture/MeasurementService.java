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
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetch the most precise location from different location services.
 */
public class MeasurementService extends Service {

    private enum LISTENER {GPS, NETWORK, PASSIVE};
    private LocationManager gpsLocationManager;
    private LocationManager passiveLocationManager;
    private LocationManager networkLocationManager;
    private CommonLocationListener gpsLocationListener;
    private CommonLocationListener networkLocationListener;
    private CommonLocationListener passiveLocationListener;
    private long minTimeDelay = 1000;
    private static final long MAXIMUM_LOCATION_HISTORY = 50;
    private AudioProcess audioProcess;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private AtomicBoolean canceled = new AtomicBoolean(false);
    private AtomicInteger leqAdded = new AtomicInteger(0);
    private MeasurementManager measurementManager;
    private long beginMeasure = 0;
    private DoProcessing doProcessing = new DoProcessing(this);
    // This measurement identifier in the long term storage
    private int recordId = -1;
    private PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementService.class);

    private NavigableMap<Long, Location> timeLocation = new TreeMap<Long, Location>();

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
        MeasurementService getService() {
            return MeasurementService.this;
        }
    }

    public int getRecordId() {
        return recordId;
    }

    public void cancel() {
        canceled.set(true);
        isRecording.set(false);
        stopLocalisationServices();
    }

    public boolean isCanceled() {
        return canceled.get();
    }

    public int getLeqAdded() {
        return leqAdded.get();
    }

    public AudioProcess getAudioProcess() {
        return audioProcess;
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        this.measurementManager = new MeasurementManager(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Stop record
        if(isRecording()) {
            cancel();
            // Tell the user we canceled.
            Toast.makeText(this, R.string.measurement_service_canceled, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void startRecording() {
        canceled.set(false);
        initLocalisationServices();
        isRecording.set(true);
        this.audioProcess = new AudioProcess(isRecording, canceled);
        audioProcess.getListeners().addPropertyChangeListener(doProcessing);

        // Start measurement
        recordId = measurementManager.addRecord();
        leqAdded.set(0);
        new Thread(audioProcess).start();

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }

    public void stopRecording() {
        isRecording.set(false);
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // Text for the ticker
        CharSequence text = getText(R.string.title_service_measurement);

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

    private void initLocalisationServices() {
        initPassive();
        initGPS();
        initNetworkLocation();
    }

    private void stopLocalisationServices() {
        stopPassive();
        stopGPS();
        stopNetworkLocation();
    }

    private void restartLocalisationServices() {
        LOGGER.info("Restart localisation services");
        stopLocalisationServices();
        initLocalisationServices();
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

    private void stopGPS() {
        if (gpsLocationListener == null || gpsLocationManager == null) {
            return;
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && passiveLocationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            gpsLocationManager.removeUpdates(gpsLocationListener);
        }
    }


    private void stopPassive() {
        if (passiveLocationListener == null || passiveLocationManager == null) {
            return;
        }
        passiveLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && passiveLocationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            passiveLocationManager.removeUpdates(passiveLocationListener);
        }
    }

    private void stopNetworkLocation() {
        if (networkLocationListener == null || networkLocationManager == null) {
            return;
        }
        networkLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && networkLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            networkLocationManager.removeUpdates(networkLocationListener);
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

    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.addPropertyChangeListener(propertyChangeListener);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.removePropertyChangeListener(propertyChangeListener);
    }


    public void addLocation(Location location) {
        timeLocation.put(location.getTime(), location);
        if(timeLocation.size() > MAXIMUM_LOCATION_HISTORY) {
            // Clean old entry
            timeLocation.remove(timeLocation.firstKey());
        }
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
        private MeasurementService measurementService;
        private LISTENER listenerId;

        public CommonLocationListener(MeasurementService measurementService, LISTENER listenerId) {
            this.measurementService = measurementService;
            this.listenerId = listenerId;
        }

        @Override
        public void onGpsStatusChanged(int event) {

        }

        @Override
        public void onLocationChanged(Location location) {
            measurementService.addLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {
            measurementService.restartLocalisationServices();
        }

        @Override
        public void onProviderDisabled(String provider) {
            measurementService.restartLocalisationServices();
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

    /**
     * @return Start time of measurement
     */
    public long getBeginMeasure() {
        return beginMeasure;
    }

    private static class DoProcessing implements  PropertyChangeListener {
        private MeasurementService measurementService;

        public DoProcessing(MeasurementService measurementService) {
            this.measurementService = measurementService;
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if(measurementService.beginMeasure == 0) {
                measurementService.beginMeasure = SystemClock.elapsedRealtime();
            }
            if (AudioProcess.PROP_DELAYED_STANDART_PROCESSING.equals(event.getPropertyName
                    ())) {
                // Delayed audio processing
                AudioProcess.DelayedStandardAudioMeasure measure =
                        (AudioProcess.DelayedStandardAudioMeasure) event.getNewValue();
                Location location = measurementService.fetchLocation(measure.getBeginRecordTime());
                Storage.Leq leq;
                if(location == null) {
                    leq = new Storage.Leq(measurementService.recordId, -1, measure
                            .getBeginRecordTime(), 0, 0, 0, 0, 0);
                }else {
                    leq = new Storage.Leq(measurementService.recordId, -1, measure
                            .getBeginRecordTime(), location.getLatitude(), location.getLongitude(),
                            location.getAltitude(), location.getAccuracy(), location.getTime());
                }
                double[] freqValues = measurementService.audioProcess.getDelayedCenterFrequency();
                final float[] leqs = measure.getLeqs();
                List<Storage.LeqValue> leqValueList = new ArrayList<>(leqs.length);
                for (int idFreq = 0; idFreq < leqs.length; idFreq++) {
                    leqValueList
                            .add(new Storage.LeqValue(-1, (int) freqValues[idFreq], leqs[idFreq]));
                }
                measurementService.measurementManager
                        .addLeqBatch(new MeasurementManager.LeqBatch(leq, leqValueList));
                measurementService.leqAdded.addAndGet(1);
            } else if(AudioProcess.PROP_STATE_CHANGED.equals(event.getPropertyName())) {
                if(AudioProcess.STATE.CLOSED.equals(event.getNewValue())) {
                    // Recording and processing of audio has been closed
                    // Cancel the persistent notification.
                    measurementService.mNM.cancel(measurementService.NOTIFICATION);
                    if (measurementService.canceled.get() || measurementService.leqAdded.get() == 0)
                    {
                        // Canceled
                        // Destroy record
                        measurementService.measurementManager
                                .deleteRecord(measurementService.recordId);
                    } else {
                        // Update record
                        measurementService.measurementManager
                                .updateRecordFinal(measurementService.recordId,
                                        (float) measurementService.audioProcess
                                                .getStandartLeqStats().getLeqMean(),
                                        (int)(SystemClock.elapsedRealtime() - measurementService.beginMeasure) / 1000);

                    }
                    measurementService.beginMeasure = 0;
                    measurementService.stopLocalisationServices();
                }
            }
            measurementService.listeners.firePropertyChange(event);
        }
    }

    public boolean isRecording() {
        return isRecording.get();
    }

}