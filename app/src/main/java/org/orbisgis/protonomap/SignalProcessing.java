package org.orbisgis.protonomap;

import android.content.Context;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;


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
            button.setOnClickListener(new DoProcessing((TextView)rootView.findViewById(R.id.textView), context));
            return rootView;
        }
    }

    private static class DoProcessing implements CompoundButton.OnClickListener {
        private TextView textView;
        private Context context;

        private DoProcessing(TextView textView, Context context) {
            this.textView = textView;
            this.context = context;
        }

        @Override
        public void onClick(View v) {
            InputStream inputStream = context.getResources().openRawResource(R.raw.record_test);
            try {
                final int rate = 16000;
                double[] secondSample = new double[rate];
                int secondCursor = 0;
                byte[] buffer = new byte[4096];
                int read = 0;
                CoreSignalProcessing signalProcessing = new CoreSignalProcessing(rate);
                while ((read = inputStream.read(buffer)) != -1) {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                    double[] samples = byteBuffer.asDoubleBuffer().array();
                    System.arraycopy(samples, 0, secondSample, secondCursor, Math.min(samples.length,rate - secondCursor));
                }
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
}
