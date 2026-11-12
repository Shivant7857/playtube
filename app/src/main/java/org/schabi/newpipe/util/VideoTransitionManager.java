/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * Manages shared-element container transform animation parameters between
 * video feed thumbnails and the main watch page player.
 */
public final class VideoTransitionManager {
    @Nullable
    private static Rect sourceRect = null;
    @Nullable
    private static Drawable thumbnailDrawable = null;

    private VideoTransitionManager() {
    }

    /**
     * Stores source thumbnail view bounds and drawable for the opening transition.
     *
     * @param view The clicked thumbnail view
     */
    public static void setSourceView(@Nullable final View view) {
        if (view != null && view.getWidth() > 0 && view.getHeight() > 0) {
            final int[] loc = new int[2];
            view.getLocationOnScreen(loc);
            sourceRect = new Rect(loc[0], loc[1],
                    loc[0] + view.getWidth(), loc[1] + view.getHeight());
            if (view instanceof ImageView) {
                thumbnailDrawable = ((ImageView) view).getDrawable();
            } else {
                thumbnailDrawable = null;
            }
        } else {
            clearSource();
        }
    }

    /**
     * Clears cached transition parameters.
     */
    public static void clearSource() {
        sourceRect = null;
        thumbnailDrawable = null;
    }

    /**
     * Checks if transition parameters are available.
     *
     * @return true if source bounds are cached
     */
    public static boolean hasSource() {
        return sourceRect != null;
    }

    @Nullable
    public static Rect getSourceRect() {
        return sourceRect;
    }

    @Nullable
    public static Drawable getThumbnailDrawable() {
        return thumbnailDrawable;
    }
}
