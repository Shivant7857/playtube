package org.schabi.newpipe.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

/**
 * Convenience helper to populate a {@link LiquidGlassBottomNavBar} with icon-only
 * or icon+label navigation items. Each item is a vertical LinearLayout holding an
 * ImageView and an optional TextView.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * LiquidGlassBottomNavBar bar = findViewById(R.id.bottom_nav);
 * NavBarHelper helper = new NavBarHelper(bar);
 * helper.addItem(R.drawable.ic_home, "Home");
 * helper.addItem(R.drawable.ic_subscriptions, "Subs");
 * helper.addItem(R.drawable.ic_trending, "Hot");
 * helper.addItem(R.drawable.ic_bookmark, "Library");
 * helper.setOnItemSelected(index -> { /* switch fragment *​/ });
 * helper.select(0);
 * }</pre>
 */
public final class NavBarHelper {

    /** Callback when a tab is tapped. */
    public interface OnItemSelectedListener {
        void onItemSelected(int index);
    }

    private final LiquidGlassBottomNavBar bar;
    private final Context context;

    @ColorInt
    private int selectedColor = 0xFF1E88E5;     // Material Blue 600
    @ColorInt
    private int idleColor = 0x99FFFFFF;          // white 60%

    private int iconSizeDp = 24;
    private int labelSizeSp = 10;
    private boolean showLabels = true;
    private int currentSelection = -1;

    @Nullable
    private OnItemSelectedListener listener;

    public NavBarHelper(final LiquidGlassBottomNavBar bar) {
        this.bar = bar;
        this.context = bar.getContext();
    }

    // ── Configuration ───────────────────────────────────────────────────────

    public NavBarHelper setSelectedColor(@ColorInt final int color) {
        selectedColor = color;
        return this;
    }

    public NavBarHelper setIdleColor(@ColorInt final int color) {
        idleColor = color;
        return this;
    }

    public NavBarHelper setIconSizeDp(final int dp) {
        iconSizeDp = dp;
        return this;
    }

    public NavBarHelper setShowLabels(final boolean show) {
        showLabels = show;
        return this;
    }

    public NavBarHelper setOnItemSelected(final OnItemSelectedListener l) {
        listener = l;
        return this;
    }

    // ── Adding items ────────────────────────────────────────────────────────

    /**
     * Adds a navigation item with an icon and optional label.
     *
     * @param iconRes drawable resource for the icon
     * @param label   label text (shown only when {@link #setShowLabels} is true)
     * @return this helper, for chaining
     */
    public NavBarHelper addItem(@DrawableRes final int iconRes, final String label) {
        final float density = context.getResources().getDisplayMetrics().density;
        final int iconPx = (int) (iconSizeDp * density);

        // outer container for this item
        final LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);

        // ripple feedback on tap
        final TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        item.setBackgroundResource(ripple.resourceId);

        // icon
        final ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(idleColor));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(iconPx, iconPx);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        item.addView(icon, iconParams);

        // label
        if (showLabels) {
            final TextView tv = new TextView(context);
            tv.setText(label);
            tv.setTextColor(idleColor);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSizeSp);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, (int) (2 * density), 0, 0);
            final LinearLayout.LayoutParams tvParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            tvParams.gravity = Gravity.CENTER_HORIZONTAL;
            item.addView(tv, tvParams);
        }

        // item tag = its index
        final int index = bar.getChildCount();
        item.setTag(index);

        // click handler
        item.setOnClickListener(v -> select((int) v.getTag()));

        // add to bar with equal weight
        final LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        bar.addView(item, itemParams);

        return this;
    }

    // ── Selection ───────────────────────────────────────────────────────────

    /**
     * Selects the item at the given index, tinting it with the selected colour
     * and resetting the others.
     *
     * @param index the item to select
     */
    public void select(final int index) {
        if (index == currentSelection) {
            return;
        }
        currentSelection = index;

        for (int i = 0; i < bar.getChildCount(); i++) {
            final View child = bar.getChildAt(i);
            if (!(child instanceof LinearLayout)) {
                continue;
            }
            final LinearLayout item = (LinearLayout) child;
            final boolean selected = (i == index);
            @ColorInt final int tint = selected ? selectedColor : idleColor;

            for (int j = 0; j < item.getChildCount(); j++) {
                final View v = item.getChildAt(j);
                if (v instanceof ImageView) {
                    ((ImageView) v).setImageTintList(ColorStateList.valueOf(tint));
                    // selected icon scales up slightly
                    v.setScaleX(selected ? 1.15f : 1f);
                    v.setScaleY(selected ? 1.15f : 1f);
                } else if (v instanceof TextView) {
                    ((TextView) v).setTextColor(tint);
                }
            }
        }

        if (listener != null) {
            listener.onItemSelected(index);
        }
    }

    /**
     * @return the currently selected index, or -1 if nothing is selected
     */
    public int getSelectedIndex() {
        return currentSelection;
    }
}
