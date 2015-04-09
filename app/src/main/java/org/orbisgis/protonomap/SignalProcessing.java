package org.orbisgis.protonomap;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;


public class SignalProcessing extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signal_processing);
        if (savedInstanceState == null) {
            PlaceholderFragment frag = new PlaceholderFragment();
            frag.setContext(getApplicationContext());
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, frag)
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_signal_processing, menu);
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
        private Context context;

        public void setContext(Context context) {
            this.context = context;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_signal_processing, container, false);
            Button button = (Button) rootView.findViewById(R.id.doprocess);
            button.setOnClickListener(new DoProcessing((TextView)rootView.findViewById(R.id.textView), context, getActivity()));
            return rootView;
        }
    }

    private static class DoProcessing implements CompoundButton.OnClickListener {
        private TextView textView;
        private Context context;
        private Activity activity;


        private DoProcessing(TextView textView, Context context, Activity activity) {
            this.textView = textView;
            this.context = context;
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            new Thread(new ProcessThread(textView, context, activity)).start();
        }
    }

    private static class ProcessThread implements Runnable {
        private TextView textView;
        private Context context;
        private Activity activity;

        private ProcessThread(TextView textView, Context context, Activity activity) {
            this.textView = textView;
            this.context = context;
            this.activity = activity;
        }

        @Override
        public void run() {
            InputStream inputStream = context.getResources().openRawResource(R.raw.record_test);
            try {
                final int rate = 16000;
                CoreSignalProcessing signalProcessing = new CoreSignalProcessing(rate);
                signalProcessing.addPropertyChangeListener(CoreSignalProcessing.PROP_SPECTRUM ,new RenderSpectrum(textView, signalProcessing, activity));
                signalProcessing.processAudio(16, rate, inputStream);
            } catch (IOException ex) {
                Log.e(ex.getLocalizedMessage(),"Processing failed", ex);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    Log.e(ex.getLocalizedMessage(),"Close file failed", ex);
                }
            }
        }
    }

    private static class RenderSpectrum implements PropertyChangeListener {
        private TextView textView;
        private CoreSignalProcessing signalProcessing;
        private Activity activity;

        private RenderSpectrum(TextView textView, CoreSignalProcessing signalProcessing, Activity activity) {
            this.textView = textView;
            this.signalProcessing = signalProcessing;
            this.activity = activity;
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            StringBuilder stringBuffer = new StringBuilder();
            for(double level : signalProcessing.getSpectrum()) {
                if(stringBuffer.length() != 0) {
                    stringBuffer.append("\n");
                }
                stringBuffer.append(level);
            }
            activity.runOnUiThread(new UpdateText(textView, stringBuffer.toString()));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                // Ignore
            }
        }
    }

    private static class UpdateText implements Runnable {
        private TextView textView;
        private String text;

        private UpdateText(TextView textView, String text) {
            this.textView = textView;
            this.text = text;
        }

        @Override
        public void run() {
            textView.setText(text);
        }
    }
}
