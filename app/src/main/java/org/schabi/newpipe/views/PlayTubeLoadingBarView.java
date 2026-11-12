package org.schabi.newpipe.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

public class PlayTubeLoadingBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF progressRect = new RectF();

    @Nullable
    private Drawable playSymbolDrawable;
    private ValueAnimator animator;
    private float animationFraction = 0f;

    private float trackHeightPx;
    private float iconWidthPx;
    private float iconHeightPx;
    private float gapPx;

    public PlayTubeLoadingBarView(final Context context) {
        super(context);
        init(context);
    }

    public PlayTubeLoadingBarView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PlayTubeLoadingBarView(final Context context, @Nullable final AttributeSet attrs,
                                  final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(final Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        trackHeightPx = 3f * density;
        iconWidthPx = 18f * density;
        iconHeightPx = 21f * density;
        gapPx = 10f * density;

        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.playtube_loader_track));

        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setColor(ContextCompat.getColor(context, R.color.playtube_loader_progress));

        playSymbolDrawable = ContextCompat.getDrawable(
                context, R.drawable.ic_playtube_loader_symbol);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final float density = getContext().getResources().getDisplayMetrics().density;
        final int desiredWidth = (int) (180f * density);
        final int desiredHeight = (int) (24f * density);

        final int width = resolveSize(desiredWidth, widthMeasureSpec);
        final int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onVisibilityChanged(final View changedView, final int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    public void startAnimation() {
        if (animator != null && animator.isRunning()) {
            return;
        }

        final float animScale = Settings.Global.getFloat(
                getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        if (animScale == 0f) {
            animationFraction = 0.5f;
            invalidate();
            return;
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animationFraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        // Draw icon on left
        final float centerY = h / 2f;
        if (playSymbolDrawable != null) {
            final int iconLeft = 0;
            final int iconTop = (int) (centerY - iconHeightPx / 2f);
            final int iconRight = (int) (iconLeft + iconWidthPx);
            final int iconBottom = (int) (centerY + iconHeightPx / 2f);
            playSymbolDrawable.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            playSymbolDrawable.draw(canvas);
        }

        // Draw track
        final float trackLeft = iconWidthPx + gapPx;
        final float trackRight = w;
        final float trackTop = centerY - trackHeightPx / 2f;
        final float trackBottom = centerY + trackHeightPx / 2f;
        final float radius = trackHeightPx / 2f;

        trackRect.set(trackLeft, trackTop, trackRight, trackBottom);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        // Clip to track for progress
        final int save = canvas.save();
        canvas.clipRect(trackRect);

        final float totalTrackW = trackRight - trackLeft;
        final float progressSegmentW = totalTrackW * 0.35f;

        // Position animated segment
        final float startX = trackLeft - progressSegmentW;
        final float endX = trackRight;
        final float currentLeft = startX + (endX - startX) * animationFraction;
        final float currentRight = currentLeft + progressSegmentW;

        progressRect.set(currentLeft, trackTop, currentRight, trackBottom);
        canvas.drawRoundRect(progressRect, radius, radius, progressPaint);

        canvas.restoreToCount(save);
    }
}
