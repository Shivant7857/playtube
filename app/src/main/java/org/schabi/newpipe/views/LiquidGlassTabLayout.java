package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

import org.schabi.newpipe.glass.LiquidGlassRenderer;

/**
 * A {@link ScrollableTabLayout} that draws itself as a real liquid glass bar.
 *
 * <p>Android has no view level API for blurring whatever happens to be behind a view, so the
 * backdrop is grabbed by recording the views sitting behind this one into a {@link RenderNode} that
 * covers the bar plus a margin, and running that node through {@link LiquidGlassRenderer}.
 * Recording instead of drawing into a bitmap keeps the feed's GPU resident thumbnails usable,
 * which software rendering refuses. The bar is drawn on top, so the feed has to extend behind it
 * to refract anything.
 */
public class LiquidGlassTabLayout extends ScrollableTabLayout {

    /** Below this the layer is not worth re-recording the feed for. */
    private static final long CAPTURE_INTERVAL_NANOS = 30_000_000L;
    /** Keep refreshing for a while after the last scroll, then go idle. */
    private static final long SCROLL_DECAY_NANOS = 400_000_000L;

    @Nullable
    private LiquidGlassRenderer renderer;
    private final LiquidGlassRenderer.Params params = new LiquidGlassRenderer.Params();

    private final Path clipPath = new Path();
    private final RectF barRect = new RectF();
    private final int[] barLocation = new int[2];
    private final int[] parentLocation = new int[2];

    @Nullable
    private RenderNode layer;

    private boolean enabled;
    private boolean effectDirty = true;
    private int layerWidth;
    private int layerHeight;
    private int lastPad;
    private long lastCaptureNanos;
    private long lastScrollNanos;

    private final ViewTreeObserver.OnScrollChangedListener scrollListener = () -> {
        lastScrollNanos = System.nanoTime();
        postInvalidateOnAnimation();
    };

    public LiquidGlassTabLayout(final Context context) {
        super(context);
    }

    public LiquidGlassTabLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public LiquidGlassTabLayout(final Context context, final AttributeSet attrs,
                                final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * @return whether this device can run the shader based glass at all
     */
    public static boolean isLiquidGlassSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /**
     * @return the parameters the glass is drawn with; call {@link #invalidateGlass()} after
     *         changing them
     */
    public LiquidGlassRenderer.Params getGlassParams() {
        return params;
    }

    /**
     * @param useLiquidGlass whether to replace the flat background with the glass layer
     */
    public void setLiquidGlassEnabled(final boolean useLiquidGlass) {
        if (enabled == useLiquidGlass) {
            return;
        }
        enabled = useLiquidGlass;
        if (useLiquidGlass && isLiquidGlassSupported() && renderer == null) {
            renderer = new LiquidGlassRenderer();
        }
        invalidateGlass();
    }

    /** Forces the next frame to rebuild the effect and re-grab the backdrop. */
    public void invalidateGlass() {
        effectDirty = true;
        lastCaptureNanos = 0;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getViewTreeObserver().isAlive()) {
            getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (getViewTreeObserver().isAlive()) {
            getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        invalidateGlass();
    }

    @Override
    protected void dispatchDraw(final Canvas canvas) {
        if (isGlassActive()) {
            drawLiquidGlass(canvas);
        }
        super.dispatchDraw(canvas);
    }

    private boolean isGlassActive() {
        return enabled
                && renderer != null
                && getWidth() > 0
                && getHeight() > 0;
    }

    private void drawLiquidGlass(final Canvas canvas) {
        final int pad = borderPad();
        if (pad != lastPad) {
            lastPad = pad;
            effectDirty = true;
        }

        ensureLayer(getWidth() + pad * 2, getHeight() + pad * 2);
        if (layer == null) {
            return;
        }

        if (System.nanoTime() - lastCaptureNanos > CAPTURE_INTERVAL_NANOS) {
            captureBackdrop(pad);
        }

        if (effectDirty) {
            layer.setRenderEffect(renderer.buildEffect(params,
                    getWidth(), getHeight(), layerWidth, layerHeight, pad, pad));
            effectDirty = false;
        }

        canvas.save();
        canvas.translate(-pad, -pad);
        clipPath.reset();
        // the canvas is now in layer space, where the bar starts at (pad, pad)
        barRect.set(pad, pad, pad + getWidth(), pad + getHeight());
        clipPath.addRoundRect(barRect, cornerRadius(), cornerRadius(), Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.drawRenderNode(layer);
        canvas.restore();

        if (System.nanoTime() - lastScrollNanos < SCROLL_DECAY_NANOS) {
            postInvalidateOnAnimation();
        }
    }

    /**
     * @return the corner radius to cut the bar into, matching the tab_bar_glass radius
     */
    private float cornerRadius() {
        final float targetRadius = params.radius > 0
                ? params.radius
                : 28f * getResources().getDisplayMetrics().density;
        return Math.min(targetRadius, Math.min(getWidth(), getHeight()) / 2f);
    }

    /**
     * @return how far outside the bar the backdrop has to reach, so refraction and blur have real
     *         pixels to pull from instead of smearing the clip edge into the bar
     */
    private int borderPad() {
        final float shortSide = Math.min(getWidth(), getHeight());
        final float refraction = (params.edgeIntensity + params.rimIntensity
                + params.cornerBoost + params.ripple) * shortSide;
        return Math.max(4, (int) (params.blurRadius * 2f + refraction + 8));
    }

    private void ensureLayer(final int width, final int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (layer == null) {
            layer = new RenderNode("newpipe-glass");
        }
        if (layerWidth != width || layerHeight != height) {
            layerWidth = width;
            layerHeight = height;
            layer.setPosition(0, 0, width, height);
            lastCaptureNanos = 0;
        }
    }

    /**
     * Records everything this bar is floating on into the layer. The bar itself is skipped, because
     * capturing it would make the glass refract its own icons.
     *
     * @param pad how far outside the bar the recorded region reaches
     */
    private void captureBackdrop(final int pad) {
        final ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent == null || layer == null) {
            return;
        }

        getLocationInWindow(barLocation);
        parent.getLocationInWindow(parentLocation);
        final int left = barLocation[0] - parentLocation[0] - pad;
        final int top = barLocation[1] - parentLocation[1] - pad;

        final Canvas canvas = layer.beginRecording();
        canvas.translate(-left, -top);
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            if (child == this || child.getVisibility() == View.GONE) {
                continue;
            }
            final int savedCanvasState = canvas.save();
            // draw(Canvas) paints in the view's own coordinates, unlike the drawChild() path
            canvas.translate(child.getLeft(), child.getTop());
            child.draw(canvas);
            canvas.restoreToCount(savedCanvasState);
        }
        layer.endRecording();

        lastCaptureNanos = System.nanoTime();
    }
}
