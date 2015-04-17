package org.orbisgis.protonomap;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.utils.PercentFormatter;


/* Source for example: http://www.codelearn.org/android-tutorial/android-listview
 * @author Pranay Airan
 *
 */
public class History extends MainActivity {

    public class informationHistory {
        String Id;
        String Date;
        Float SEL;
    }

    InformationHistoryAdapter historyListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        initDrawer();


        historyListAdapter = new InformationHistoryAdapter();

        ListView infohistory = (ListView)findViewById(R.id.listiew_history);
        infohistory.setAdapter(historyListAdapter);

        infohistory.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {

                informationHistory chapter = historyListAdapter.getInformationHistory(arg2);

                Toast.makeText(History.this, chapter.Id,Toast.LENGTH_LONG).show();

            }
        });
    }


    public class InformationHistoryAdapter extends BaseAdapter {

        List<informationHistory> informationHistoryList = getDataForListView();
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return informationHistoryList.size();
        }

        @Override
        public informationHistory getItem(int arg0) {
            // TODO Auto-generated method stub
            return informationHistoryList.get(arg0);
        }

        @Override
        public long getItemId(int arg0) {
            // TODO Auto-generated method stub
            return arg0;
        }

        @Override
        public View getView(int arg0, View arg1, ViewGroup arg2) {

            int[] color_rep=NE_COLORS();

            if(arg1==null)
            {
                LayoutInflater inflater = (LayoutInflater) History.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                arg1 = inflater.inflate(R.layout.history_item_layout, arg2,false);
            }

            TextView history_Id = (TextView)arg1.findViewById(R.id.textView_Id_item_history);
            TextView history_Date = (TextView)arg1.findViewById(R.id.textView_Date_item_history);
            TextView history_SEL = (TextView)arg1.findViewById(R.id.textView_SEL_item_history);
            informationHistory informationHistory = informationHistoryList.get(arg0);

            // Update history item
            history_Id.setText(informationHistory.Id);
            history_Date.setText(informationHistory.Date);
            history_SEL.setText(Float.toString(informationHistory.SEL).concat(" dB(A)"));
            int nc=getNEcatColors(informationHistory.SEL);
            history_SEL.setBackgroundColor(color_rep[nc]);

            return arg1;
        }

        public informationHistory getInformationHistory(int position)
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

    public List<informationHistory> getDataForListView()
    {
        List<informationHistory> informationHistoryList = new ArrayList<informationHistory>();

        for(int i=0;i<5;i++)
        {

            informationHistory history = new informationHistory();
            history.Id = "Id: "+i;
            history.Date = "Date: "+i;
            history.SEL = 50f+10*i;
            informationHistoryList.add(history);
        }

        return informationHistoryList;

    }
}

