package org.noise_planet.noisecapture;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommentActivity extends MainActivity {
    private AtomicBoolean userInputSeekBar = new AtomicBoolean(false);
    public static final String COMMENT_RECORD_ID = "COMMENT_RECORD_ID";
    private Set<Integer> checkedTags = new TreeSet<>();
    private MeasurementManager measurementManager;
    private Storage.Record record;
    private Bitmap thumbnail;
    private Uri photo_uri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);
        // Read record activity parameter
        // Use last record of no parameter provided
        this.measurementManager = new MeasurementManager(this);
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra(COMMENT_RECORD_ID)) {
            record = measurementManager.getRecord(intent.getIntExtra(COMMENT_RECORD_ID, -1), false);
        } else {
            // Read the last stored record
            List<Storage.Record> recordList = measurementManager.getRecords(false);
            if(!recordList.isEmpty()) {
                record = recordList.get(recordList.size() - 1);
            } else {
                // Message for starting a record
                Toast.makeText(getApplicationContext(),
                        getString(R.string.no_results), Toast.LENGTH_LONG).show();
                return;
            }
        }
        initDrawer(record != null ? record.getId() : null);
        SeekBar seekBar = (SeekBar) findViewById(R.id.pleasantness_slider);

        // Load stored user comment
        // Pleasantness and tags are read only if the record has been uploaded
        Map<String, Integer> tagToIndex = new HashMap<>(Storage.TAGS.length);
        int i = 0;
        for(String sysTag : Storage.TAGS) {
            tagToIndex.put(sysTag, i++);
        }
        if(record != null) {
            // Load selected tags
            for (String sysTag : measurementManager.getTags(record.getId())) {
                Integer tagIndex = tagToIndex.get(sysTag);
                if (tagIndex != null) {
                    checkedTags.add(tagIndex);
                }
            }
            // Load description
            if (record.getDescription() != null) {
                EditText description = (EditText) findViewById(R.id.edit_description);
                description.setText(record.getDescription());
            }
            Integer pleasantness = record.getPleasantness();
            if(pleasantness != null) {
                seekBar.setProgress(pleasantness);
                seekBar.setThumb(seekBar.getResources().getDrawable(
                        R.drawable.seekguess_scrubber_control_selector_holo_dark));
            }

            // User can only update not uploaded data
            seekBar.setEnabled(record.getUploadId().isEmpty());
        } else {
            // Message for starting a record
            Toast.makeText(getApplicationContext(),
                    getString(R.string.no_results), Toast.LENGTH_LONG).show();
        }

        seekBar.setOnSeekBarChangeListener(new OnSeekBarUserInput(userInputSeekBar));
        // Fill tags grid
        String[] tags = getResources().getStringArray(R.array.tags);
        // Append tags items
        LinearLayout tagColumn = (LinearLayout) findViewById(R.id.tags_grid_col1);
        for(int idTag = 0; idTag < tags.length; idTag += 3) {
            addTag(tags[idTag], idTag, tagColumn);
        }
        tagColumn = (LinearLayout) findViewById(R.id.tags_grid_col2);
        for(int idTag = 1; idTag < tags.length; idTag += 3) {
            addTag(tags[idTag], idTag, tagColumn);
        }
        tagColumn = (LinearLayout) findViewById(R.id.tags_grid_col3);
        for(int idTag = 2; idTag < tags.length; idTag += 3) {
            addTag(tags[idTag], idTag, tagColumn);
        }
    }

    @Override
    public void onBackPressed() {
        // Ask user if he want to keep modified data
        validateCancel();
    }
    private void validateCancel() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Add the buttons
        builder.setPositiveButton(R.string.comment_save_change, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                CommentActivity.super.onBackPressed();
            }
        });
        builder.setNegativeButton(R.string.comment_cancel_change, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                CommentActivity.this.record = null;
                CommentActivity.super.onBackPressed();
            }
        });
        // Create the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.setTitle(R.string.comment_title_save_change);
        dialog.show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Save content
        saveChanges();
    }

    private void saveChanges() {
        if(record != null) {
            EditText description = (EditText) findViewById(R.id.edit_description);
            SeekBar seekBar = (SeekBar) findViewById(R.id.pleasantness_slider);
            String[] tags = new String[checkedTags.size()];
            int tagCounter = 0;
            for(int tagId : checkedTags) {
                tags[tagCounter++] = Storage.TAGS[tagId];
            }
            measurementManager.updateRecordUserInput(record.getId(), description.getText().toString(),
                    (short)seekBar.getProgress(), tags, thumbnail, photo_uri);
        }
    }

    private void addTag(String tagName, int id, LinearLayout column) {
        ToggleButton tagButton = new ToggleButton(this);
        column.addView(tagButton);
        tagButton.setTextOff(tagName);
        tagButton.setTextOn(tagName);
        tagButton.setChecked(checkedTags.contains(id));
        tagButton.setOnCheckedChangeListener(new TagStateListener(id, checkedTags));
        tagButton.setMinHeight(0);
        tagButton.setMinWidth(0);
        tagButton.setEnabled(record == null || record.getUploadId().isEmpty());
        tagButton.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tagButton.invalidate();
    }

    private static final class TagStateListener implements CompoundButton.OnCheckedChangeListener {
        private int id;
        private Set<Integer> checkList;

        public TagStateListener(int id, Set<Integer> checkList) {
            this.id = id;
            this.checkList = checkList;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(isChecked) {
                checkList.add(id);
            } else {
                checkList.remove(id);
            }
        }
    }

    /**
     * Remove ? in the seekbar on first user input
     */
    private static class OnSeekBarUserInput implements SeekBar.OnSeekBarChangeListener {

        private AtomicBoolean userInputSeekBar;

        public OnSeekBarUserInput(AtomicBoolean userInputSeekBar) {
            this.userInputSeekBar = userInputSeekBar;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if(!userInputSeekBar.getAndSet(true)) {
                seekBar.setThumb(seekBar.getResources().getDrawable(
                        R.drawable.seekguess_scrubber_control_selector_holo_dark));
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }
}
