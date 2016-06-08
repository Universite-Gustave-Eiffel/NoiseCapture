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

package org.noise_planet.noisecapture.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;

/**
 * A utility class that helps with showing and hiding system UI such as the
 * status bar and navigation/system bar. This class uses backward-compatibility
 * techniques described in <a href=
 * "http://developer.android.com/training/backward-compatible-ui/index.html">
 * Creating Backward-Compatible UIs</a> to ensure that devices running any
 * version of ndroid OS are supported. More specifically, there are separate
 * implementations of this abstract class: for newer devices,
 * {@link #getInstance} will return a {@link SystemUiHiderHoneycomb} instance,
 * while on older devices {@link #getInstance} will return a
 * {@link SystemUiHiderBase} instance.
 * <p/>
 * For more on system bars, see <a href=
 * "http://developer.android.com/design/get-started/ui-overview.html#system-bars"
 * > System Bars</a>.
 *
 * @see android.view.View#setSystemUiVisibility(int)
 * @see android.view.WindowManager.LayoutParams#FLAG_FULLSCREEN
 */
public abstract class SystemUiHider {
    /**
     * When this flag is set, the
     * {@link android.view.WindowManager.LayoutParams#FLAG_LAYOUT_IN_SCREEN}
     * flag will be set on older devices, making the status bar "float" on top
     * of the activity layout. This is most useful when there are no controls at
     * the top of the activity layout.
     * <p/>
     * This flag isn't used on newer devices because the <a
     * href="http://developer.android.com/design/patterns/actionbar.html">action
     * bar</a>, the most important structural element of an Android app, should
     * be visible and not obscured by the system UI.
     */
    public static final int FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES = 0x1;

    /**
     * When this flag is set, {@link #show()} and {@link #hide()} will toggle
     * the visibility of the status bar. If there is a navigation bar, show and
     * hide will toggle low profile mode.
     */
    public static final int FLAG_FULLSCREEN = 0x2;

    /**
     * When this flag is set, {@link #show()} and {@link #hide()} will toggle
     * the visibility of the navigation bar, if it's present on the device and
     * the device allows hiding it. In cases where the navigation bar is present
     * but cannot be hidden, show and hide will toggle low profile mode.
     */
    public static final int FLAG_HIDE_NAVIGATION = FLAG_FULLSCREEN | 0x4;

    /**
     * The activity associated with this UI hider object.
     */
    protected Activity mActivity;

    /**
     * The view on which {@link View#setSystemUiVisibility(int)} will be called.
     */
    protected View mAnchorView;

    /**
     * The current UI hider flags.
     *
     * @see #FLAG_FULLSCREEN
     * @see #FLAG_HIDE_NAVIGATION
     * @see #FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES
     */
    protected int mFlags;

    /**
     * The current visibility callback.
     */
    protected OnVisibilityChangeListener mOnVisibilityChangeListener = sDummyListener;

    /**
     * Creates and returns an instance of {@link SystemUiHider} that is
     * appropriate for this device. The object will be either a
     * {@link SystemUiHiderBase} or {@link SystemUiHiderHoneycomb} depending on
     * the device.
     *
     * @param activity   The activity whose window's system UI should be
     *                   controlled by this class.
     * @param anchorView The view on which
     *                   {@link View#setSystemUiVisibility(int)} will be called.
     * @param flags      Either 0 or any combination of {@link #FLAG_FULLSCREEN},
     *                   {@link #FLAG_HIDE_NAVIGATION}, and
     *                   {@link #FLAG_LAYOUT_IN_SCREEN_OLDER_DEVICES}.
     */
    public static SystemUiHider getInstance(Activity activity, View anchorView, int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return new SystemUiHiderHoneycomb(activity, anchorView, flags);
        } else {
            return new SystemUiHiderBase(activity, anchorView, flags);
        }
    }

    protected SystemUiHider(Activity activity, View anchorView, int flags) {
        mActivity = activity;
        mAnchorView = anchorView;
        mFlags = flags;
    }

    /**
     * Sets up the system UI hider. Should be called from
     * {@link Activity#onCreate}.
     */
    public abstract void setup();

    /**
     * Returns whether or not the system UI is visible.
     */
    public abstract boolean isVisible();

    /**
     * Hide the system UI.
     */
    public abstract void hide();

    /**
     * Show the system UI.
     */
    public abstract void show();

    /**
     * Toggle the visibility of the system UI.
     */
    public void toggle() {
        if (isVisible()) {
            hide();
        } else {
            show();
        }
    }

    /**
     * Registers a callback, to be triggered when the system UI visibility
     * changes.
     */
    public void setOnVisibilityChangeListener(OnVisibilityChangeListener listener) {
        if (listener == null) {
            listener = sDummyListener;
        }

        mOnVisibilityChangeListener = listener;
    }

    /**
     * A dummy no-op callback for use when there is no other listener set.
     */
    private static OnVisibilityChangeListener sDummyListener = new OnVisibilityChangeListener() {
        @Override
        public void onVisibilityChange(boolean visible) {
        }
    };

    /**
     * A callback interface used to listen for system UI visibility changes.
     */
    public interface OnVisibilityChangeListener {
        /**
         * Called when the system UI visibility has changed.
         *
         * @param visible True if the system UI is visible.
         */
        public void onVisibilityChange(boolean visible);
    }
}
