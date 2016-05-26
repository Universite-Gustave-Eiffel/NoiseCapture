package org.noise_planet.noisecapture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;


/* Source for example:
 * http://www.codelearn.org/android-tutorial/android-listview
 * http://www.androidbegin.com/tutorial/android-delete-multiple-selected-items-listview-tutorial/
 * @author Pranay Airan
 * @author Nicolas Fortin
 */
public class History extends MainActivity {
    private MeasurementManager measurementManager;
    private ListView infohistory;
    private SparseBooleanArray mSelectedItemsIds = new SparseBooleanArray();

    InformationHistoryAdapter historyListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.measurementManager = new MeasurementManager(getApplicationContext());
        setContentView(R.layout.activity_history);
        initDrawer();

        // Fill the listview
        historyListAdapter = new InformationHistoryAdapter(measurementManager, this);
        infohistory = (ListView)findViewById(R.id.listiew_history);
        infohistory.setMultiChoiceModeListener(new HistoryMultiChoiceListener(this));
        infohistory.setAdapter(historyListAdapter);
        infohistory.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        infohistory.setLongClickable(true);
        infohistory.setOnItemClickListener(new HistoryItemListener(this));
    }


    private static class HistoryMultiChoiceListener implements AbsListView.MultiChoiceModeListener {
        History history;

        public HistoryMultiChoiceListener(History history) {
            this.history = history;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                                              int position, long id, boolean checked) {
            // Capture total checked items
            final int checkedCount = history.infohistory.getCheckedItemCount();
            // Set the CAB title according to total checked items
            mode.setTitle(checkedCount + " Selected");
            // Calls toggleSelection method from ListViewAdapter Class
            history.historyListAdapter.toggleSelection(position);
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete:
                    // Calls getSelectedIds method from ListViewAdapter Class
                    SparseBooleanArray selected = history.historyListAdapter
                            .getSelectedIds();
                    // Captures all selected ids with a loop
                    List<Integer> recordIdToDelete = new ArrayList<Integer>();
                    for (int i = (selected.size() - 1); i >= 0; i--) {
                        if (selected.valueAt(i)) {
                            recordIdToDelete.add((int)history.historyListAdapter.getItemId(selected.keyAt(i)));

                        }
                    }
                    if(!recordIdToDelete.isEmpty()) {
                        // Remove selected items following the ids
                        history.historyListAdapter.remove(recordIdToDelete);
                    }
                    // Close CAB
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_delete, menu);
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            history.historyListAdapter.removeSelection();
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            return false;
        }
    }

    private static final class HistoryItemSelectionListener implements View.OnLongClickListener {
        private History history;

        public HistoryItemSelectionListener(History history) {
            this.history = history;
        }

        @Override
        public boolean onLongClick(View v) {
            return false;
        }
    }

    private static final class HistoryItemListener implements OnItemClickListener {
        private History historyActivity;

        public HistoryItemListener(History historyActivity) {
            this.historyActivity = historyActivity;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // Show
            AlertDialog.Builder builder = new AlertDialog.Builder(historyActivity);
            builder.setTitle(String.format(historyActivity.getText(R.string.history_item_choice_title).toString(),
                    historyActivity.historyListAdapter.getInformationHistory(position).getUtcDate()));
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(historyActivity,
                    R.array.choice_user_history, android.R.layout.simple_selectable_list_item);
            builder.setAdapter(adapter,
                    new ItemActionOnClickListener(historyActivity, (int) id));
            builder.show();
        }
    }

    private static class ItemActionOnClickListener implements DialogInterface.OnClickListener {
        private History historyActivity;
        private int recordId;

        public ItemActionOnClickListener(History historyActivity, int recordId) {
            this.historyActivity = historyActivity;
            this.recordId = recordId;
        }

        private void launchResult() {
            Intent ir = new Intent(historyActivity.getApplicationContext(), Results.class);
            ir.putExtra(Results.RESULTS_RECORD_ID, recordId);
            historyActivity.startActivity(ir);
        }

        private void launchMap() {
            Intent ir = new Intent(historyActivity.getApplicationContext(), MapActivity.class);
            ir.putExtra(MapActivity.RESULTS_RECORD_ID, recordId);
            historyActivity.startActivity(ir);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:
                    // Result
                    launchResult();
                    break;
                case 1:
                    // Map
                    launchMap();
                    break;
                case 2:
                    // Delete record
                    historyActivity.measurementManager.deleteRecord(recordId);
                    historyActivity.historyListAdapter.reload();
                    break;
                case 3:
                    // Upload
                    // TODO upload action
                    break;
                case 4:
                    // TODO Share action
                    break;
            }
        }
    }

    public static class InformationHistoryAdapter extends BaseAdapter {
        private List<Storage.Record> informationHistoryList;
        private History activity;
        private MeasurementManager measurementManager;

        public InformationHistoryAdapter(MeasurementManager measurementManager, History activity) {
            this.informationHistoryList = measurementManager.getRecords(false);
            this.activity = activity;
            this.measurementManager = measurementManager;
        }

        public SparseBooleanArray getSelectedIds() {
            return activity.mSelectedItemsIds;
        }

        public void toggleSelection(int position) {
            selectView(position, !activity.mSelectedItemsIds.get(position));
        }

        public void removeSelection() {
            activity.mSelectedItemsIds.clear();
            activity.historyListAdapter.notifyDataSetChanged();
        }

        public void selectView(int position, boolean value) {
            if (value) {
                activity.mSelectedItemsIds.put(position, true);
            } else {
                activity.mSelectedItemsIds.delete(position);
            }
            activity.historyListAdapter.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return informationHistoryList.size();
        }

        public void reload() {
            informationHistoryList = measurementManager.getRecords(false);
            notifyDataSetChanged();
        }

        @Override
        public Object getItem(int position) {
            return informationHistoryList.get(position);
        }

        public void remove(Collection<Integer> ids) {
            activity.measurementManager.deleteRecords(ids);
            reload();
        }

        @Override
        public long getItemId(int position) {
            return informationHistoryList.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView==null)
            {
                LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.history_item_layout, parent,false);
            }
            TextView history_length = (TextView)convertView.findViewById(R.id.textView_length_item_history);
            TextView history_Date = (TextView)convertView.findViewById(R.id.textView_Date_item_history);
            TextView history_SEL = (TextView)convertView.findViewById(R.id.textView_SEL_item_history);
            TextView history_SEL_bar = (TextView)convertView.findViewById(R.id.textView_SEL_bar_item_history);
            Storage.Record record = informationHistoryList.get(position);

            // Update history item
            Resources res = activity.getResources();
            history_length.setText(res.getString(R.string.history_length, record.getTimeLength()));
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

