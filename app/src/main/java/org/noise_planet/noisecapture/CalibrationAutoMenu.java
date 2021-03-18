package org.noise_planet.noisecapture;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class CalibrationAutoMenu extends MainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration_auto_menu);
        initDrawer();
    }


    public void onAutoGuestCalibration(View view) {
        Intent ics = new Intent(getApplicationContext(), CalibrationActivityGuest.class);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(ics);
    }

    public void onAutoHostCalibration(View view) {
        Intent ics = new Intent(getApplicationContext(), CalibrationActivityHost.class);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(ics);
    }
}
