package org.noise_planet.noisecapture;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ToggleButton;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommentActivity extends MainActivity {
    private AtomicBoolean userInputSeekBar = new AtomicBoolean(false);
    private Set<Integer> checkedTags = new TreeSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);
        initDrawer();
        SeekBar seekBar = (SeekBar) findViewById(R.id.pleasantness_slider);
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

        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Save content
    }

    private void addTag(String tagName, int id, LinearLayout column) {
        ToggleButton tagButton = new ToggleButton(this);
        column.addView(tagButton);
        tagButton.setTextOff(tagName);
        tagButton.setTextOn(tagName);
        tagButton.setChecked(false);
        tagButton.setOnCheckedChangeListener(new TagStateListener(id, checkedTags));
        tagButton.setMinHeight(0);
        tagButton.setMinWidth(0);
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
