/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

package org.noise_planet.noisecapture;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.content.FileProvider;
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
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;


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
    private static final Logger LOGGER = LoggerFactory.getLogger(History.class);
    private ProgressDialog progress;

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
            // Calls getSelectedIds method from ListViewAdapter Class
            SparseBooleanArray selected = history.historyListAdapter
                    .getSelectedIds();
            // Captures all selected ids with a loop
            List<Integer> selectedRecordIds = new ArrayList<Integer>();
            for (int i = (selected.size() - 1); i >= 0; i--) {
                if (selected.valueAt(i)) {
                    selectedRecordIds.add((int)history.historyListAdapter.getItemId(selected.keyAt(i)));
                }
            }
            switch (item.getItemId()) {
                case R.id.delete:
                    if(!selectedRecordIds.isEmpty()) {
                        // Remove selected items following the ids
                        history.historyListAdapter.remove(selectedRecordIds);
                    }
                    // Close CAB
                    mode.finish();
                    return true;
                case R.id.publish:
                    boolean deleted = false;
                    if(!selectedRecordIds.isEmpty()) {
                        for(Integer recordId : new ArrayList<Integer>(selectedRecordIds)) {
                            Storage.Record record = history.measurementManager.getRecord(recordId);
                            if (!record.getUploadId().isEmpty()) {
                                selectedRecordIds.remove(recordId);
                                deleted = true;
                            }
                        }
                        if(deleted) {
                            Toast.makeText(history, history.getString(R.string.history_already_uploaded), Toast.LENGTH_LONG).show();
                        }
                        // publish selected items following the ids
                        history.doTransferRecords(selectedRecordIds);
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

    @Override
    protected void onTransferRecord() {
        historyListAdapter.reload();
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
            String[] menuEntries = historyActivity.getResources().getStringArray(R
                    .array.choice_user_history);
            builder.setItems(menuEntries, new ItemActionOnClickListener(historyActivity, (int)
                    id));
            builder.show();
        }
    }

    public static void doBuildZip(File file, Context context,int recordId) throws IOException {
        // Create parent dirs if necessary
        file.getParentFile().mkdirs();
        FileOutputStream fop = new FileOutputStream(file);
        try {
            MeasurementExport measurementExport = new MeasurementExport(context);
            measurementExport.exportRecord(recordId, fop, true);
        } finally {
            fop.close();
        }
    }

    public static class ItemActionOnClickListener implements DialogInterface.OnClickListener {
        private History historyActivity;
        private int recordId;

        public ItemActionOnClickListener(History historyActivity, int recordId) {
            this.historyActivity = historyActivity;
            this.recordId = recordId;
        }

        private void launchComment() {
            Intent ir = new Intent(historyActivity.getApplicationContext(), CommentActivity.class);
            ir.putExtra(CommentActivity.COMMENT_RECORD_ID, recordId);
            historyActivity.finish();
            historyActivity.startActivity(ir);
        }

        private void launchUpload() {
            Storage.Record record = historyActivity.measurementManager.getRecord(recordId);
            if(record.getUploadId().isEmpty()) {
                historyActivity.progress = ProgressDialog.show(historyActivity, historyActivity.getText(R.string.upload_progress_title), historyActivity.getText(R.string.upload_progress_message), true);
                new Thread(new SendZipToServer(historyActivity, recordId, historyActivity.progress, new RefreshListener(historyActivity.historyListAdapter))).start();
            } else {
                Toast.makeText(historyActivity,
                        historyActivity.getString(R.string.history_already_uploaded), Toast.LENGTH_LONG).show();

            }
        }

        private File getSharedFile() {
            return new File(historyActivity.getCacheDir().getAbsolutePath() ,MeasurementExport.ZIP_FILENAME);
        }

        private void buildZipFile() {
            // Write file
            try {
                doBuildZip(getSharedFile(), historyActivity, recordId);
            } catch (IOException ex) {
                Toast.makeText(historyActivity,
                        historyActivity.getString(R.string.fail_share), Toast.LENGTH_LONG).show();
                LOGGER.error(ex.getLocalizedMessage(), ex);
            }
        }


        private void launchExport() {

            File requestFile = getSharedFile();
            Uri fileUri = FileProvider.getUriForFile(
                    historyActivity,
                    "org.noise_planet.noisecapture.fileprovider",
                    requestFile);


            Intent mResultIntent = new Intent(Intent.ACTION_SEND);

            mResultIntent.setDataAndType(fileUri, "application/zip");
            mResultIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            mResultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            buildZipFile();
            historyActivity.startActivity(Intent.createChooser(mResultIntent,
                    historyActivity.getText(R.string.result_share)));
        }

        private void launchResult() {
            Intent ir = new Intent(historyActivity.getApplicationContext(), Results.class);
            ir.putExtra(RESULTS_RECORD_ID, recordId);
            historyActivity.finish();
            historyActivity.startActivity(ir);
        }

        private void launchMap() {
            Intent ir = new Intent(historyActivity.getApplicationContext(), MapActivity.class);
            ir.putExtra(RESULTS_RECORD_ID, recordId);
            historyActivity.finish();
            historyActivity.startActivity(ir);
        }

        private void delete() {
            AlertDialog.Builder builder = new AlertDialog.Builder(historyActivity);
            // Add the buttons
            builder.setPositiveButton(R.string.comment_delete_record, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // Delete record
                    historyActivity.measurementManager.deleteRecord(recordId);
                    historyActivity.historyListAdapter.reload();
                }
            });
            builder.setNegativeButton(R.string.comment_cancel_change, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
            // Create the AlertDialog
            AlertDialog dialog = builder.create();
            dialog.setTitle(R.string.comment_title_delete);
            dialog.show();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case 0:
                    // Upload
                    launchUpload();
                    break;
                case 1:
                    launchExport();
                    break;
                case 2:
                    // Comment
                    launchComment();
                    break;
                case 3:
                    // Result
                    launchResult();
                    break;
                case 4:
                    // Map
                    launchMap();
                    break;
                case 5:
                    delete();
                    break;
            }
        }
    }


    private static final class RefreshListener implements OnUploadedListener {

        private InformationHistoryAdapter historyListAdapter;

        public RefreshListener(InformationHistoryAdapter historyListAdapter) {
            this.historyListAdapter = historyListAdapter;
        }

        @Override
        public void onMeasurementUploaded() {
            historyListAdapter.reload();
        }
    }
    public static class InformationHistoryAdapter extends BaseAdapter {
        private List<Storage.Record> informationHistoryList;
        private History activity;
        private MeasurementManager measurementManager;
        private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm z", Locale.getDefault());

        public InformationHistoryAdapter(MeasurementManager measurementManager, History activity) {
            this.informationHistoryList = measurementManager.getRecords();
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
            informationHistoryList = measurementManager.getRecords();
            notifyDataSetChanged();
        }

        @Override
        public Object getItem(int position) {
            return informationHistoryList.get(position);
        }

        public void remove(final Collection<Integer> ids) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            // Add the buttons
            builder.setPositiveButton(R.string.comment_delete_record, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // Delete records
                    activity.measurementManager.deleteRecords(ids);
                    reload();
                }
            });
            builder.setNegativeButton(R.string.comment_cancel_change, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
            // Create the AlertDialog
            AlertDialog dialog = builder.create();
            dialog.setTitle(R.string.history_title_delete);
            dialog.show();
        }

        @Override
        public long getItemId(int position) {
            return informationHistoryList.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView == null) {
                LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context
                        .LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.history_item_layout, parent, false);
            }
            TextView description = (TextView) convertView.findViewById(R.id
                    .textView_description_item_history);
            TextView history_Date = (TextView) convertView.findViewById(R.id
                    .textView_Date_item_history);
            TextView history_SEL = (TextView) convertView.findViewById(R.id
                    .textView_SEL_item_history);
            TextView history_SEL_bar = (TextView) convertView.findViewById(R.id
                    .textView_SEL_bar_item_history);
            Storage.Record record = informationHistoryList.get(position);

            // Update history item
            Resources res = activity.getResources();
            description.setText(record.getDescription());
            history_Date.setText(res.getString(R.string.history_length, record.getTimeLength()) +
                    " " + res.getString(R.string.history_date, simpleDateFormat.format(new Date
                    (record.getUtc()))));
            history_SEL.setText(res.getString(R.string.history_sel, record.getLeqMean()));
            int nc = getNEcatColors(record.getLeqMean());
            history_SEL.setTextColor(activity.NE_COLORS[nc]);
            history_SEL_bar.setBackgroundColor(activity.NE_COLORS[nc]);

            ImageView imageView = (ImageView) convertView.findViewById(R.id.history_uploaded);
            if (record.getUploadId().isEmpty()) {
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

