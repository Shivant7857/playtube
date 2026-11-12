package org.schabi.newpipe.util.image;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Utility to generate YouTube-style circular letter avatars when real avatar is not available.
 */
public final class LetterAvatarGenerator {
    private static final int[] COLORS = {
        0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
        0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
        0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFF4511E,
        0xFF6D4C41, 0xFF546E7A
    };

    private LetterAvatarGenerator() {
    }

    /**
     * Generate a circular avatar with the channel's initial letter and deterministic color.
     *
     * @param resources Android resources to construct BitmapDrawable
     * @param name      Channel name
     * @param sizePx    Size in pixels
     * @return Circular letter avatar drawable
     */
    @NonNull
    public static Drawable getLetterAvatar(@NonNull final Resources resources,
                                          @Nullable final String name,
                                          final int sizePx) {
        final int targetSize = Math.max(sizePx, 48);
        final Bitmap bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        final int colorIndex = Math.abs(name != null ? name.hashCode() : 0) % COLORS.length;
        paint.setColor(COLORS[colorIndex]);
        canvas.drawCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, paint);

        final String letter = (name != null && !name.trim().isEmpty())
                ? name.trim().substring(0, 1).toUpperCase(Locale.ROOT) : "?";

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(targetSize * 0.48f);
        paint.setTextAlign(Paint.Align.CENTER);

        final Paint.FontMetrics metrics = paint.getFontMetrics();
        final float y = (targetSize / 2f) - ((metrics.ascent + metrics.descent) / 2f);
        canvas.drawText(letter, targetSize / 2f, y, paint);

        return new BitmapDrawable(resources, bitmap);
    }
}
