package org.schabi.newpipe.views.skeleton;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

public class PlayTubeShimmerContainer extends FrameLayout {

    private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private LinearGradient shimmerGradient;
    private ValueAnimator shimmerAnimator;

    private int highlightColor;
    private float animationFraction = 0f;
    private boolean isShimmerEnabled = true;

    public PlayTubeShimmerContainer(@NonNull final Context context) {
        super(context);
        init(context);
    }

    public PlayTubeShimmerContainer(@NonNull final Context context,
                                    @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PlayTubeShimmerContainer(@NonNull final Context context,
                                    @Nullable final AttributeSet attrs,
                                    final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(final Context context) {
        setWillNotDraw(false);
        highlightColor = ContextCompat.getColor(context, R.color.playtube_skeleton_shimmer);

        shimmerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader(w);
    }

    private void updateShader(final int width) {
        if (width <= 0) {
            return;
        }

        final float shimmerWidth = Math.max(width * 0.45f, 200f);
        final int edgeColor = 0x00FFFFFF & highlightColor;
        final int centerColor = (0x99FFFFFF & highlightColor) | 0x66000000;

        shimmerGradient = new LinearGradient(
                0, 0, shimmerWidth, 0,
                new int[]{edgeColor, highlightColor, edgeColor},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        shimmerPaint.setShader(shimmerGradient);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            startShimmer();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopShimmer();
    }

    @Override
    protected void onVisibilityChanged(@NonNull final View changedView, final int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startShimmer();
        } else {
            stopShimmer();
        }
    }

    public void setShimmerEnabled(final boolean enabled) {
        this.isShimmerEnabled = enabled;
        if (enabled) {
            startShimmer();
        } else {
            stopShimmer();
        }
        invalidate();
    }

    public void startShimmer() {
        if (!isShimmerEnabled || (shimmerAnimator != null && shimmerAnimator.isRunning())) {
            return;
        }

        final float animScale = Settings.Global.getFloat(
                getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
        if (animScale == 0f) {
            return;
        }

        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f);
        shimmerAnimator.setDuration(1300);
        shimmerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shimmerAnimator.setInterpolator(new LinearInterpolator());
        shimmerAnimator.addUpdateListener(anim -> {
            animationFraction = (float) anim.getAnimatedValue();
            invalidate();
        });
        shimmerAnimator.start();
    }

    public void stopShimmer() {
        if (shimmerAnimator != null) {
            shimmerAnimator.cancel();
            shimmerAnimator = null;
        }
    }

    @Override
    protected void dispatchDraw(final Canvas canvas) {
        if (!isShimmerEnabled || getWidth() <= 0 || getHeight() <= 0 || shimmerGradient == null) {
            super.dispatchDraw(canvas);
            return;
        }

        final int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.dispatchDraw(canvas);

        final float shimmerWidth = Math.max(getWidth() * 0.45f, 200f);
        final float totalDistance = getWidth() + shimmerWidth * 2f;
        final float translateX = -shimmerWidth + totalDistance * animationFraction;

        shaderMatrix.reset();
        shaderMatrix.setTranslate(translateX, 0);
        shimmerGradient.setLocalMatrix(shaderMatrix);

        canvas.drawRect(0, 0, getWidth(), getHeight(), shimmerPaint);
        canvas.restoreToCount(saveCount);
    }
}
