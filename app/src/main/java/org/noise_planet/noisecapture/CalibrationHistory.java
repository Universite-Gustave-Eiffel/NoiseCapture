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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.noise_planet.noisecapture.util.TrafficNoiseEstimator;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalibrationHistory extends MainActivity {

    private ListView infohistory;
    private InformationHistoryAdapter historyListAdapter;
    private MeasurementManager measurementManager;
    private SparseBooleanArray mSelectedItemsIds = new SparseBooleanArray();
    private TextView textGain;
    private TextView textUncertainty;
    private Button newMeasurementButton;
    private Button applyButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.measurementManager = new MeasurementManager(getApplicationContext());
        setContentView(R.layout.activity_calibration_history);
        initDrawer();

        textGain = findViewById(R.id.spl_estimated_gain);
        textUncertainty = findViewById(R.id.spl_uncertainty);

        newMeasurementButton = findViewById(R.id.btn_new_measurement);
        applyButton = findViewById(R.id.btn_apply);


        // Fill the listview
        historyListAdapter = new InformationHistoryAdapter(measurementManager, this);
        infohistory = (ListView)findViewById(R.id.listview_calibration_history);
        infohistory.setMultiChoiceModeListener(new HistoryMultiChoiceListener(this));
        infohistory.setAdapter(historyListAdapter);
        infohistory.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        infohistory.setLongClickable(true);
        infohistory.setOnItemClickListener(new HistoryItemListener(this));
    }

    public void onApply(View v) {
        double averageGain = 0;
        for(Storage.TrafficCalibrationSession session : historyListAdapter.informationHistoryList) {
            averageGain += session.getComputedGain(historyListAdapter.trafficNoiseEstimator);
        }
        averageGain /= historyListAdapter.informationHistoryList.size();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("settings_recording_gain", String.valueOf(averageGain));
        editor.putString("settings_calibration_method", String.valueOf(Storage.Record.CALIBRATION_METHODS.Traffic.ordinal()));
        editor.apply();
        Toast.makeText(getApplicationContext(),
                getString(R.string.calibrate_done, averageGain), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        updateGain();
    }

    public void updateGain() {
        if(historyListAdapter.informationHistoryList.size() > 0) {
            double averageCount = 0;
            double averageGain = 0;
            for (Storage.TrafficCalibrationSession session : historyListAdapter.informationHistoryList) {
                averageCount += session.getTrafficCount();
                averageGain += session.getComputedGain(historyListAdapter.trafficNoiseEstimator);
            }
            averageCount /= historyListAdapter.informationHistoryList.size();
            averageGain /= historyListAdapter.informationHistoryList.size();
            double uncertaintyEstimation =
                    TrafficNoiseEstimator.getCalibrationUncertainty((int) averageCount,
                            historyListAdapter.informationHistoryList.size());
            textGain.setText(String.format(Locale.getDefault(), "%.2f", averageGain));
            textUncertainty.setText(String.format(Locale.getDefault(), "%.1f",
                    uncertaintyEstimation));
            applyButton.setEnabled(true);
        } else {
            textGain.setText(R.string.no_valid_dba_value);
            textUncertainty.setText(R.string.no_valid_dba_value);
            applyButton.setEnabled(false);
        }
    }

    public void onNewMeasurement(View v) {
        Intent ics = new Intent(getApplicationContext(), TrafficCalibrationActivity.class);
        mDrawerLayout.closeDrawer(mDrawerList);
        startActivity(ics);
        finish();
    }

    public static class InformationHistoryAdapter extends BaseAdapter {
        private List<Storage.TrafficCalibrationSession> informationHistoryList;
        private CalibrationHistory activity;
        private MeasurementManager measurementManager;
        private TrafficNoiseEstimator trafficNoiseEstimator;
        private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm z", Locale.getDefault());

        public InformationHistoryAdapter(MeasurementManager measurementManager, CalibrationHistory activity) {
            this.informationHistoryList = measurementManager.getTrafficCalibrationSessions();
            this.activity = activity;
            this.measurementManager = measurementManager;
            this.trafficNoiseEstimator = new TrafficNoiseEstimator();
            InputStream input = activity.getResources().openRawResource(R.raw.coefficients_cnossos);
            try {
                try {
                    trafficNoiseEstimator.loadConstants(input);
                } finally {
                    input.close();
                }
            } catch (IOException ex) {
                // ignore
            }
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
            informationHistoryList = measurementManager.getTrafficCalibrationSessions();
            notifyDataSetChanged();
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            activity.updateGain();
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
                    for(int idcalib : ids) {
                        activity.measurementManager.deleteTrafficCalibrationSession(idcalib);
                    }
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
            return informationHistoryList.get(position).getSessionId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(convertView == null) {
                LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context
                        .LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.calibration_history_item_layout, parent, false);
            }
            TextView description = (TextView) convertView.findViewById(R.id
                    .textView_description_item_history);
            TextView history_Date = (TextView) convertView.findViewById(R.id
                    .textView_Date_item_history);
            TextView history_SEL = (TextView) convertView.findViewById(R.id
                    .textView_SEL_item_history);
            Storage.TrafficCalibrationSession record = informationHistoryList.get(position);

            // Update history item
            Resources res = activity.getResources();
            double gain = record.getComputedGain(trafficNoiseEstimator);
            description.setText(res.getString(R.string.calibration_estimated_gain, gain));
            history_Date.setText(res.getString(R.string.traffic_history_count, record.getTrafficCount()) +
                    " " + res.getString(R.string.history_date, simpleDateFormat.format(new Date
                    (record.getUtc()))));
            history_SEL.setText(res.getString(R.string.history_sel, record.getMedianPeak()));
            return convertView;
        }

        public Storage.TrafficCalibrationSession getInformationHistory(int position)
        {
            return informationHistoryList.get(position);
        }

    }

    private static class HistoryMultiChoiceListener implements AbsListView.MultiChoiceModeListener {
        CalibrationHistory history;

        public HistoryMultiChoiceListener(CalibrationHistory history) {
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

    private static final class HistoryItemListener implements AdapterView.OnItemClickListener {
        private CalibrationHistory historyActivity;

        public HistoryItemListener(CalibrationHistory historyActivity) {
            this.historyActivity = historyActivity;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final int recordId = (int) id;
            AlertDialog.Builder builder = new AlertDialog.Builder(historyActivity);
            // Add the buttons
            builder.setPositiveButton(R.string.comment_delete_record, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // Delete record
                    historyActivity.measurementManager.deleteTrafficCalibrationSession(recordId);
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
    }
}
