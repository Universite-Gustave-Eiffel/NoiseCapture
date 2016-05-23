package org.noise_planet.noisecapture;

import android.os.Bundle;

public class CommentActivity extends MainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);
        initDrawer();
    }

}
