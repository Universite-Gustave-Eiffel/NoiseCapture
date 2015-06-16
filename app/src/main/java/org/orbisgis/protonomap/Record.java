package org.orbisgis.protonomap;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;


public class Record extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment().setContext(getApplicationContext()))
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
        private Context context;

        public PlaceholderFragment() {
        }

        public PlaceholderFragment setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_record, container, false);
            ToggleButton toggleButton = (ToggleButton) rootView.findViewById(R.id.toggleRecordButton);
            toggleButton.setOnCheckedChangeListener(new RecordButtonListener(rootView, recording, new File(Environment.getExternalStorageDirectory(),"record_test.ac3")));
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
        private AtomicBoolean recording;
        private final int bufferSize;
        private final int encoding;
        private final int rate;
        private final int audioChannel;
        private File recordDestination;


        private RecordButtonListener(View rootView, AtomicBoolean recording, File recordDestination) {
            this.rootView = rootView;
            this.recording = recording;
            this.recordDestination = recordDestination;
            final int[] mSampleRates = new int[] { 8000, 11025, 22050, 44100, 48000 };
            final int[] encodings = new int[] { AudioFormat.ENCODING_PCM_16BIT , AudioFormat.ENCODING_PCM_8BIT };
            final short[] audioChannels = new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO };
            for (int tryRate : mSampleRates) {
                for (int tryEncoding : encodings) {
                    for(short tryAudioChannel : audioChannels) {
                        int tryBufferSize = AudioRecord.getMinBufferSize(tryRate,
                                tryAudioChannel, tryEncoding);
                        if (tryBufferSize != AudioRecord.ERROR_BAD_VALUE) {
                            bufferSize = tryBufferSize;
                            encoding = tryEncoding;
                            audioChannel = tryAudioChannel;
                            rate = tryRate;
                            return;
                        }
                    }
                }
            }
            bufferSize = AudioRecord.ERROR_BAD_VALUE;
            encoding = -1;
            audioChannel = -1;
            rate = -1;
        }

        private AudioRecord createAudioRecord() {
            if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                return new AudioRecord(MediaRecorder.AudioSource.MIC,
                        rate, audioChannel,
                        encoding, bufferSize);
            } else {
                return null;
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            TextView textView = (TextView)rootView.findViewById(R.id.recordInfo);
            textView.setText("Record "+(isChecked ? "starting" : "stop"));
            if(isChecked) {
                recording.set(true);
                AudioRecord audioRecord = createAudioRecord();
                if(audioRecord != null) {
                    AudioProcess audioProcess = new AudioProcess(recording, audioRecord, bufferSize, recordDestination);
                    new Thread(audioProcess).start();
                }
            } else {
                recording.set(false);
            }
        }
    }

    private static class AudioProcess implements Runnable {

        private AtomicBoolean recording;
        private AudioRecord audioRecord;
        private int bufferSize;
        private File recordDestination;

        private AudioProcess(AtomicBoolean recording, AudioRecord audioRecord, int bufferSize, File recordDestination) {
            this.recording = recording;
            this.audioRecord = audioRecord;
            this.bufferSize = bufferSize;
            this.recordDestination = recordDestination;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[bufferSize];
            if(recording.get()) {
                try {
                    FileOutputStream outputStream = new FileOutputStream(recordDestination);
                    try {
                        try {
                            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                        } catch (IllegalArgumentException | SecurityException ex) {
                            // Ignore
                        }
                        audioRecord.startRecording();
                        while (recording.get()) {
                            audioRecord.read(buffer, 0, buffer.length);
                            outputStream.write(buffer);
                        }
                    } finally {
                        outputStream.close();
                    }
                } catch (Exception ex) {
                    Log.e("tag_record","Error while recording", ex);
                } finally {
                    audioRecord.stop();
                    audioRecord.release();
                }
            }
        }
    }
}
