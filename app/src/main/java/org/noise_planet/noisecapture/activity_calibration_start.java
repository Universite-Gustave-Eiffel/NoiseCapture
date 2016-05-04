package org.noise_planet.noisecapture;

import android.os.Handler;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;


public class activity_calibration_start extends MainActivity {

    private int progressStatus = 0;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_calibration_start);
        initDrawer();

        // Fill the spinner for the calibration mode selection
        Spinner spinner = (Spinner) findViewById(R.id.spinner_calibration_mode);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.choice_user_calibration_mode, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Make START and CANCEL buttons visible/invisible
        Button button_start = (Button) findViewById(R.id.button_start_calibration);
        button_start.setEnabled(true);
        button_start.setVisibility(View.VISIBLE);
        Button button_cancel = (Button) findViewById(R.id.button_cancel_calibration);
        button_cancel.setEnabled(false);
        button_cancel.setVisibility(View.INVISIBLE);
        final ProgressBar progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
        progressBar_wait_calibration_recording.setProgress(0);
        final ProgressBar progressBar_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_calibration_recording);
        progressBar_calibration_recording.setProgress(0);

        // Action on START button:
        // Hide START button - Display CANCEL button
        // Start "Prepare..." progressbar
        // Start "Recording..." progressbar and start audiorecording...
        button_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Make START and CANCEL buttons invisible/visible
                // Set progressbar to 0
                Button button_start = (Button) findViewById(R.id.button_start_calibration);
                button_start.setEnabled(false);
                button_start.setVisibility(View.INVISIBLE);
                Button button_cancel = (Button) findViewById(R.id.button_cancel_calibration);
                button_cancel.setEnabled(true);
                button_cancel.setVisibility(View.VISIBLE);
                final ProgressBar progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
                progressBar_wait_calibration_recording.setProgress(0);
                final ProgressBar progressBar_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_calibration_recording);
                progressBar_calibration_recording.setProgress(0);

                // Start Progressbar for waiting record
                // Source: http://www.compiletimeerror.com/2013/09/android-progress-bar-example.html#.VZZNEEa7_m4
                // Start long running operation in a background thread
                EditText delay = (EditText)findViewById(R.id.editText_calibration_delay);
                final int value_delay=Integer.parseInt(delay.getText().toString());
                progressStatus=0;
                new Thread(new Runnable() {
                    public void run() {
                        while (progressStatus < 100) {
                            progressStatus += 1;
                            // Update the progress bar and display the

                            //current value in the text view
                            handler.post(new Runnable() {
                                public void run() {
                                    progressBar_wait_calibration_recording.setProgress(progressStatus);
                                    //textView.setText(progressStatus+"/"+progressBar.getMax());
                                }
                            });
                            try {
                                //Display the progressbar with a time increment corresponding to a total duration of the delay
                                Thread.sleep(1000*value_delay/100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();

                //TODO : start recording with progressbar progression

            }
        });

        // Action on CANCEL button:
        // Stop recording
        // Hide CANCEL button - Display CANCEL button
        button_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Make START and CANCEL buttons invisible/visible
                Button button_start = (Button) findViewById(R.id.button_start_calibration);
                button_start.setEnabled(true);
                button_start.setVisibility(View.VISIBLE);
                Button button_cancel = (Button) findViewById(R.id.button_cancel_calibration);
                button_cancel.setEnabled(false);
                button_cancel.setVisibility(View.INVISIBLE);
                final ProgressBar progressBar_wait_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_wait_calibration_recording);
                progressBar_wait_calibration_recording.setProgress(0);
                final ProgressBar progressBar_calibration_recording = (ProgressBar) findViewById(R.id.progressBar_calibration_recording);
                progressBar_calibration_recording.setProgress(0);

            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
