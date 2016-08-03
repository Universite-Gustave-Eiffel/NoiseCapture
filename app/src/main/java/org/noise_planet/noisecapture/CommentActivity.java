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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommentActivity extends MainActivity {
    private AtomicBoolean userInputSeekBar = new AtomicBoolean(false);
    public static final String COMMENT_RECORD_ID = "COMMENT_RECORD_ID";
    private Set<Integer> checkedTags = new TreeSet<>();
    private MeasurementManager measurementManager;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private Storage.Record record;
    private Bitmap thumbnailBitmap;
    private Uri photo_uri;
    private static final Logger LOGGER = LoggerFactory.getLogger(CommentActivity.class);
    private OnThumbnailImageLayoutDoneObserver thumbnailImageLayoutDoneObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);

        View mainView = findViewById(R.id.mainLayout);
        if(mainView != null) {
            mainView.setOnTouchListener(new MainOnTouchListener(this));
        }

        // Read record activity parameter
        // Use last record of no parameter provided
        this.measurementManager = new MeasurementManager(this);
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra(COMMENT_RECORD_ID)) {
            record = measurementManager.getRecord(intent.getIntExtra(COMMENT_RECORD_ID, -1));
        } else {
            // Read the last stored record
            List<Storage.Record> recordList = measurementManager.getRecords();
            if(!recordList.isEmpty()) {
                record = recordList.get(0);
            } else {
                // Message for starting a record
                Toast.makeText(getApplicationContext(),
                        getString(R.string.no_results), Toast.LENGTH_LONG).show();
                return;
            }
        }
        if(record != null) {
            ImageButton addPhoto = (ImageButton) findViewById(R.id.btn_add_photo);
            addPhoto.setOnClickListener(new OnAddPhotoClickListener(this));
            Button resultsBtn = (Button) findViewById(R.id.resultsBtn);
            resultsBtn.setOnClickListener(new OnGoToResultPage(this));
        }
        Button measureButton = (Button) findViewById(R.id.measureBtn);
        measureButton.setOnClickListener(new OnGoToMeasurePage(this));
        initDrawer(record != null ? record.getId() : null);
        SeekBar seekBar = (SeekBar) findViewById(R.id.pleasantness_slider);

        // Load stored user comment
        // Pleasantness and tags are read only if the record has been uploaded
        Map<String, Integer> tagToIndex = new HashMap<>(Storage.TAGS.length);
        int i = 0;
        for(String sysTag : Storage.TAGS) {
            tagToIndex.put(sysTag, i++);
        }

        ImageView thumbnail = (ImageView) findViewById(R.id.image_thumbnail);
        thumbnail.setOnClickListener(new OnImageClickListener(this));
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
                userInputSeekBar.set(true);
            }
            photo_uri = record.getPhotoUri();
            // User can only update not uploaded data
            seekBar.setEnabled(record.getUploadId().isEmpty());
        } else {
            // Message for starting a record
            Toast.makeText(getApplicationContext(),
                    getString(R.string.no_results), Toast.LENGTH_LONG).show();
        }
        thumbnailImageLayoutDoneObserver = new OnThumbnailImageLayoutDoneObserver(this);
        thumbnail.getViewTreeObserver().addOnGlobalLayoutListener(thumbnailImageLayoutDoneObserver);

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
        ImageView thumbnail = (ImageView) findViewById(R.id.image_thumbnail);
        if(thumbnail != null && thumbnailBitmap != null) {
            thumbnail.setImageResource(android.R.color.transparent);
            thumbnailBitmap.recycle();
        }
        // Save content
        saveChanges();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(photo_uri != null) {
            // Load bitmap only when the layout is done (required width height are known)
            thumbnailImageLayoutDoneObserver.activated.set(true);
        }
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
                  userInputSeekBar.get() ? (short)seekBar.getProgress() : null, tags, photo_uri);
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
                buttonView.setTextColor(Color.GREEN);
            } else {
                checkList.remove(id);
                buttonView.setTextColor(Color.WHITE);
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
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            thumbnailImageLayoutDoneObserver.activated.set(true);
        }
    }
    private static final class OnAddPhotoClickListener implements View.OnClickListener {
        private CommentActivity activity;

        public OnAddPhotoClickListener(CommentActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "MEASURE_" + activity.record.getId() + "_" + timeStamp + ".jpg";
            File storageDirs = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);

            File image =new File(storageDirs , imageFileName);

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            activity.photo_uri = Uri.fromFile(image);
            LOGGER.info("Write photo to "+activity.photo_uri);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, activity.photo_uri);
            if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    public void startActivity(Intent intent) {
        // Save content
        saveChanges();
        super.startActivity(intent);
    }

    private static final class OnGoToResultPage implements View.OnClickListener {
        private CommentActivity activity;

        public OnGoToResultPage(CommentActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            //Open result page
            Intent ir = new Intent(activity, Results.class);
            ir.putExtra(Results.RESULTS_RECORD_ID, activity.record.getId());
            activity.startActivity(ir);
        }
    }

    private static final class OnGoToMeasurePage implements View.OnClickListener {
        private CommentActivity activity;

        public OnGoToMeasurePage(CommentActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            //Open result page
            Intent ir = new Intent(activity, MeasurementActivity.class);
            activity.startActivity(ir);
        }
    }

    private static final class LoadThumbnail extends AsyncTask<Uri, Integer, Bitmap> {
        private int reqWidth;
        private int reqHeight;
        private ImageView thumbnail;
        private CommentActivity activity;

        public LoadThumbnail(CommentActivity activity) {
            this.activity = activity;
            thumbnail = (ImageView) activity.findViewById(R.id.image_thumbnail);
        }

        public static int calculateInSampleSize(
                BitmapFactory.Options options, int reqWidth, int reqHeight) {
            // Raw height and width of image
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {

                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while ((halfHeight / inSampleSize) > reqHeight
                        && (halfWidth / inSampleSize) > reqWidth) {
                    inSampleSize *= 2;
                }
            }

            return inSampleSize;
        }

        @Override
        protected void onPreExecute() {
            reqWidth = thumbnail.getMeasuredHeight();
            reqHeight = thumbnail.getMeasuredWidth();

        }

        @Override
        protected Bitmap doInBackground(Uri... uri) {
            String imPath = uri[0].getPath();
            // First decode with inJustDecodeBounds=true to check dimensions
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imPath, options);

            // Calculate inSampleSize
            LOGGER.info("Loading jpg with required size "+reqWidth+"x"+reqHeight);
            options.inSampleSize = calculateInSampleSize(options, reqWidth,
                    reqHeight);
            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(imPath, options);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {

        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if(activity.thumbnailBitmap != null) {
                thumbnail.setImageResource(android.R.color.transparent);
                activity.thumbnailBitmap.recycle();
            }
            activity.thumbnailBitmap = result;
            thumbnail.setBackgroundResource(android.R.color.transparent);
            thumbnail.setImageBitmap(result);
            thumbnail.invalidate();
        }
    }

    private static final class OnImageClickListener implements View.OnClickListener {
        CommentActivity activity;

        public OnImageClickListener(CommentActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            if(activity.photo_uri != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(activity.photo_uri,"image/*");
                activity.startActivity(intent);
            }
        }
    }

    public static final class OnThumbnailImageLayoutDoneObserver implements
            ViewTreeObserver.OnGlobalLayoutListener {
        private CommentActivity activity;
        private AtomicBoolean activated = new AtomicBoolean(true);

        public OnThumbnailImageLayoutDoneObserver(CommentActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onGlobalLayout() {
            if(activated.getAndSet(false)) {
                if(activity.photo_uri != null) {
                    new LoadThumbnail(activity).execute(activity.photo_uri);
                }
            }
        }
    }

    private static class MainOnTouchListener implements View.OnTouchListener {
        CommentActivity activity;

        public MainOnTouchListener(CommentActivity activity) {
            this.activity = activity;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(INPUT_METHOD_SERVICE);
            if(activity.getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
            }
            return false;
        }
    }
}
