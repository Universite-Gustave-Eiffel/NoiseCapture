package org.noise_planet.noisecapture;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;


/* Source for example: http://www.codelearn.org/android-tutorial/android-listview
 * @author Pranay Airan
 *
 */
public class History extends MainActivity {
    private MeasurementManager measurementManager;

    InformationHistoryAdapter historyListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.measurementManager = new MeasurementManager(getApplicationContext());
        setContentView(R.layout.activity_history);
        initDrawer();

        // Fill the spinner_history
        Spinner spinner = (Spinner) findViewById(R.id.spinner_history);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.choice_user_history, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Fill the listview
        historyListAdapter = new InformationHistoryAdapter(measurementManager, this);
        ListView infohistory = (ListView)findViewById(R.id.listiew_history);
        infohistory.setAdapter(historyListAdapter);
        infohistory.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        infohistory.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {

                Storage.Record history = historyListAdapter.getInformationHistory(arg2);
                //Toast.makeText(History.this, history.Id,Toast.LENGTH_LONG).show();

            }
        });
    }


    public static class InformationHistoryAdapter extends BaseAdapter {
        private MeasurementManager measurementManager;
        private List<Storage.Record> informationHistoryList;
        private MainActivity activity;

        public InformationHistoryAdapter(MeasurementManager measurementManager, MainActivity activity) {
            this.measurementManager = measurementManager;
            this.informationHistoryList = measurementManager.getRecords();
            this.activity = activity;
        }

        @Override
        public int getCount() {
            return informationHistoryList.size();
        }


        @Override
        public Object getItem(int position) {
            return informationHistoryList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView==null)
            {
                LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.history_item_layout, parent,false);
            }

            TextView history_Id = (TextView)convertView.findViewById(R.id.textView_Id_item_history);
            TextView history_Date = (TextView)convertView.findViewById(R.id.textView_Date_item_history);
            TextView history_SEL = (TextView)convertView.findViewById(R.id.textView_SEL_item_history);
            TextView history_SEL_bar = (TextView)convertView.findViewById(R.id.textView_SEL_bar_item_history);
            Storage.Record record = informationHistoryList.get(position);

            // Update history item
            Resources res = activity.getResources();
            history_Id.setText(res.getString(R.string.history_id, record.getId()));
            history_Date.setText(res.getString(R.string.history_date, record.getUtcDate()));
            history_SEL.setText(res.getString(R.string.history_sel, record.getLeqMean()));
            int nc= getNEcatColors(record.getLeqMean());
            history_SEL.setTextColor(activity.NE_COLORS[nc]);
            history_SEL_bar.setBackgroundColor(activity.NE_COLORS[nc]);

            ImageView imageView = (ImageView)convertView.findViewById(R.id.history_uploaded);
            if(record.getUploadId().isEmpty()) {
                imageView.setImageResource(R.drawable.localonly);
            } else {
                imageView.setImageResource(R.drawable.uploaded);
            }

            return convertView;
        }

        public Storage.Record getInformationHistory(int position)
        {
            return informationHistoryList.get(position);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
}

