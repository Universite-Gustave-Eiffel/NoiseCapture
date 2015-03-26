package org.orbisgis.protonomap;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.concurrent.atomic.AtomicBoolean;


public class Record extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_record, menu);
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
    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        private AtomicBoolean recording = new AtomicBoolean(false);

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_record, container, false);
            ToggleButton toggleButton = (ToggleButton) rootView.findViewById(R.id.toggleRecordButton);
            toggleButton.setOnCheckedChangeListener(new RecordButtonListener(rootView, recording));
            return rootView;
        }

        @Override
        public void onStop() {
            super.onStop();
            recording.set(false);
        }
    }

    private static class RecordButtonListener implements CompoundButton.OnCheckedChangeListener {
        private View rootView;
        private static final int RECORDER_SAMPLE_RATE = 8000;
        private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
        private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        private AtomicBoolean recording;
        private AudioRecord audioRecord;
        private final int bufferSize;

        private RecordButtonListener(View rootView, AtomicBoolean recording) {
            this.rootView = rootView;
            this.recording = recording;
            bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLE_RATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, bufferSize);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            TextView textView = (TextView)rootView.findViewById(R.id.recordInfo);
            textView.setText("Record "+(isChecked ? "starting" : "stop"));
            if(isChecked) {
                audioRecord.startRecording();
                recording.set(true);
                AudioProcess audioProcess = new AudioProcess(recording, audioRecord,bufferSize);
                new Thread(audioProcess).run();
            } else {
                recording.set(false);
            }
        }
    }

    private static class AudioProcess implements Runnable {

        private AtomicBoolean recording;
        private AudioRecord audioRecord;
        private int bufferSize;

        private AudioProcess(AtomicBoolean recording, AudioRecord audioRecord, int bufferSize) {
            this.recording = recording;
            this.audioRecord = audioRecord;
            this.bufferSize = bufferSize;
        }

        @Override
        public void run() {
            short[] buffer = new short[bufferSize];
            while (recording.get()) {
                audioRecord.read(buffer, 0, buffer.length);

            }
            audioRecord.stop();
            audioRecord.release();
        }
    }
}
