package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import org.schabi.newpipe.glass.WaterDropletRenderer;

/**
 * A crystal-clear water-droplet bottom navigation bar.
 *
 * <p>This view floats over its parent content and refracts whatever is behind it through a
 * physically-based water-droplet lens shader — no blur, no frost, no shadow. The backdrop
 * appears perfectly sharp but distorted at the edges, exactly like looking through a bead
 * of water sitting on a phone screen.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>The bar captures whatever is behind it into a {@link RenderNode} (hardware-accelerated,
 *       no bitmap allocation).</li>
 *   <li>The {@link WaterDropletRenderer} runs an AGSL shader on the node that bends the light
 *       at the edges, adds chromatic dispersion (prismatic rainbow) and caustic highlights.</li>
 *   <li>The result is clipped to a rounded-rect and drawn, then the child views (icons + labels)
 *       are drawn on top.</li>
 * </ol>
 *
 * <h3>Usage (XML)</h3>
 * <pre>{@code
 * <org.schabi.newpipe.views.LiquidGlassBottomNavBar
 *     android:id="@+id/bottom_nav"
 *     android:layout_width="match_parent"
 *     android:layout_height="56dp"
 *     android:layout_alignParentBottom="true"
 *     android:layout_marginStart="16dp"
 *     android:layout_marginEnd="16dp"
 *     android:layout_marginBottom="12dp"
 *     android:orientation="horizontal" />
 * }</pre>
 *
 * <h3>Usage (Java)</h3>
 * <pre>{@code
 * LiquidGlassBottomNavBar bar = findViewById(R.id.bottom_nav);
 * bar.setWaterDropletEnabled(true);
 *
 * // Optional: tweak the physics
 * WaterDropletRenderer.Params p = bar.getDropletParams();
 * p.edgeRefraction = 0.09f;
 * p.dispersion = 0.015f;
 * bar.invalidateDroplet();
 * }</pre>
 *
 * <p>Requires API 33+ (Tiramisu) for the AGSL shader. On older devices, call
 * {@link #setFallbackBackground(int)} to set a semi-transparent flat colour.
 */
public class LiquidGlassBottomNavBar extends LinearLayout {

    // ── Constants ───────────────────────────────────────────────────────────
    /** Minimum time between backdrop re-captures. */
    private static final long CAPTURE_INTERVAL_NANOS = 25_000_000L;   // ~40 fps
    /** Keep refreshing for this long after the last scroll event. */
    private static final long SCROLL_DECAY_NANOS = 500_000_000L;      // 500 ms

    // ── Renderer ────────────────────────────────────────────────────────────
    @Nullable
    private WaterDropletRenderer renderer;
    private final WaterDropletRenderer.Params params = new WaterDropletRenderer.Params();

    // ── Clip / draw state ───────────────────────────────────────────────────
    private final Path clipPath = new Path();
    private final RectF barRect = new RectF();
    private final int[] barLocation = new int[2];
    private final int[] parentLocation = new int[2];
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Nullable
    private RenderNode layer;

    private boolean enabled;
    private boolean effectDirty = true;
    private int layerWidth;
    private int layerHeight;
    private int lastPad;
    private long lastCaptureNanos;
    private long lastScrollNanos;
    @ColorInt
    private int fallbackColor = 0x40000000;

    // ── Scroll listener ─────────────────────────────────────────────────────
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = () -> {
        lastScrollNanos = System.nanoTime();
        postInvalidateOnAnimation();
    };

    // ── Constructors ────────────────────────────────────────────────────────
    public LiquidGlassBottomNavBar(final Context context) {
        super(context);
        init();
    }

    public LiquidGlassBottomNavBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiquidGlassBottomNavBar(final Context context, final AttributeSet attrs,
                                   final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);              // we override dispatchDraw
        setOrientation(HORIZONTAL);         // icons side by side
        setGravity(android.view.Gravity.CENTER);
        fallbackPaint.setColor(fallbackColor);
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * @return whether this device can run the water droplet shader
     */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /**
     * @return the parameters the droplet is drawn with; call {@link #invalidateDroplet()} after
     *         changing them
     */
    public WaterDropletRenderer.Params getDropletParams() {
        return params;
    }

    /**
     * Enable or disable the water droplet refraction.
     * On unsupported devices this is a no-op if {@code enable} is true.
     *
     * @param enable whether the droplet effect is active
     */
    public void setWaterDropletEnabled(final boolean enable) {
        if (enabled == enable) {
            return;
        }
        enabled = enable;
        if (enable && isSupported() && renderer == null) {
            renderer = new WaterDropletRenderer();
        }
        invalidateDroplet();
    }

    /**
     * Set the background colour for devices that can't run AGSL (API &lt; 33).
     *
     * @param color the fallback background colour
     */
    public void setFallbackBackground(@ColorInt final int color) {
        fallbackColor = color;
        fallbackPaint.setColor(color);
        invalidate();
    }

    /** Forces the next frame to rebuild the effect and re-capture the backdrop. */
    public void invalidateDroplet() {
        effectDirty = true;
        lastCaptureNanos = 0;
        invalidate();
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

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
        invalidateDroplet();
    }

    // ── Drawing ─────────────────────────────────────────────────────────────

    @Override
    protected void dispatchDraw(final Canvas canvas) {
        if (isDropletActive()) {
            drawWaterDroplet(canvas);
        } else if (enabled) {
            // fallback: draw a rounded semi-transparent background
            drawFallback(canvas);
        }
        super.dispatchDraw(canvas);
    }

    private boolean isDropletActive() {
        return enabled
                && renderer != null
                && getWidth() > 0
                && getHeight() > 0;
    }

    private void drawFallback(final Canvas canvas) {
        canvas.save();
        clipPath.reset();
        barRect.set(0, 0, getWidth(), getHeight());
        clipPath.addRoundRect(barRect, cornerRadius(), cornerRadius(), Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.drawPaint(fallbackPaint);
        canvas.restore();
    }

    private void drawWaterDroplet(final Canvas canvas) {
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
        barRect.set(pad, pad, pad + getWidth(), pad + getHeight());
        clipPath.addRoundRect(barRect, cornerRadius(), cornerRadius(), Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.drawRenderNode(layer);
        canvas.restore();

        if (System.nanoTime() - lastScrollNanos < SCROLL_DECAY_NANOS) {
            postInvalidateOnAnimation();
        }
    }

    // ── Internal geometry ───────────────────────────────────────────────────

    /**
     * @return the corner radius; 0 in params means full capsule
     */
    private float cornerRadius() {
        return params.radius > 0
                ? Math.min(params.radius, Math.min(getWidth(), getHeight()) / 2f)
                : Math.min(getWidth(), getHeight()) / 2f;
    }

    /**
     * @return padding around the bar so refraction has real pixels to pull from
     */
    private int borderPad() {
        final float shortSide = Math.min(getWidth(), getHeight());
        final float refraction = (params.edgeRefraction + params.rimRefraction
                + params.cornerLensing + params.ripple + params.dispersion) * shortSide;
        return Math.max(8, (int) (refraction + 16));
    }

    private void ensureLayer(final int width, final int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (layer == null) {
            layer = new RenderNode("water-droplet-nav");
        }
        if (layerWidth != width || layerHeight != height) {
            layerWidth = width;
            layerHeight = height;
            layer.setPosition(0, 0, width, height);
            lastCaptureNanos = 0;
        }
    }

    /**
     * Records everything behind this bar into the layer.
     * The bar itself is skipped to avoid self-refraction.
     *
     * @param pad the extra border captured around the bar
     */
    private void captureBackdrop(final int pad) {
        final ViewGroup parent =
                getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
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
            canvas.translate(child.getLeft(), child.getTop());
            child.draw(canvas);
            canvas.restoreToCount(savedCanvasState);
        }
        layer.endRecording();

        lastCaptureNanos = System.nanoTime();
    }
}
