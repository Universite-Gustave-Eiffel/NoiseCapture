package org.noise_planet.noisecapture;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ToggleButton;
import android.widget.GridLayout.LayoutParams;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommentActivity extends MainActivity {
    private AtomicBoolean userInputSeekBar = new AtomicBoolean(false);

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
            addTag(tags[idTag], tagColumn);
        }
        tagColumn = (LinearLayout) findViewById(R.id.tags_grid_col2);
        for(int idTag = 1; idTag < tags.length; idTag += 3) {
            addTag(tags[idTag], tagColumn);
        }
        tagColumn = (LinearLayout) findViewById(R.id.tags_grid_col3);
        for(int idTag = 2; idTag < tags.length; idTag += 3) {
            addTag(tags[idTag], tagColumn);
        }
    }

    private void addTag(String tagName, LinearLayout column) {
        ToggleButton tagButton = new ToggleButton(this);
        column.addView(tagButton);
        tagButton.setTextOff(tagName);
        tagButton.setTextOn(tagName);
        tagButton.setChecked(false);
        tagButton.setMinHeight(0);
        tagButton.setMinWidth(0);
        tagButton.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tagButton.invalidate();
    }

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
                seekBar.setThumb(seekBar.getResources().getDrawable(R.drawable.seekguess_scrubber_control_selector_holo_dark));
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }
}
