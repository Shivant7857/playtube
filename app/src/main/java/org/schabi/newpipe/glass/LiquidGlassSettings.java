package org.schabi.newpipe.glass;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/**
 * Reads the liquid glass sliders into {@link LiquidGlassRenderer.Params}.
 *
 * <p>The stored numbers are the ones shown in the settings screen, not the shader values:
 * intensities are thousandths, widths are in dp, and the falloff constants are derived from the
 * width over which the effect should die out, because "inverse pixels" is not something anyone can
 * dial in by eye.
 */
public final class LiquidGlassSettings {

    private LiquidGlassSettings() {
    }

    /**
     * @param context any context with the theme applied
     * @return whether the user wants the refracting bar at all
     */
    public static boolean isEnabled(final Context context) {
        return preferences(context).getBoolean(
                context.getString(R.string.liquid_glass_enabled_key), true);
    }

    /**
     * @param params the parameters to overwrite
     * @param context any context with the theme applied
     */
    public static void apply(final LiquidGlassRenderer.Params params, final Context context) {
        final SharedPreferences prefs = preferences(context);
        final float density = context.getResources().getDisplayMetrics().density;

        params.blurRadius = prefs.getInt(key(context, R.string.liquid_glass_blur_key), 12)
                * density;
        params.edgeIntensity = prefs.getInt(
                key(context, R.string.liquid_glass_edge_intensity_key), 50) / 1000f;
        params.rimIntensity = prefs.getInt(
                key(context, R.string.liquid_glass_rim_intensity_key), 80) / 1000f;
        params.cornerBoost = prefs.getInt(
                key(context, R.string.liquid_glass_corner_key), 20) / 1000f;
        params.ripple = prefs.getInt(
                key(context, R.string.liquid_glass_ripple_key), 30) / 1000f;

        params.edgeDistance = falloff(prefs.getInt(
                key(context, R.string.liquid_glass_edge_width_key), 8), density);
        params.rimDistance = falloff(prefs.getInt(
                key(context, R.string.liquid_glass_rim_width_key), 25), density);
        params.baseDistance = falloff(30, density);

        params.tintOpacity = prefs.getInt(
                key(context, R.string.liquid_glass_tint_key), 25) / 100f;
        params.warp = prefs.getBoolean(
                key(context, R.string.liquid_glass_warp_key), false);

        final int radiusDp = prefs.getInt(
                key(context, R.string.liquid_glass_radius_key), 0);
        params.radius = radiusDp == 0 ? 0 : radiusDp * density;
        params.strokeWidth = prefs.getInt(
                key(context, R.string.liquid_glass_stroke_key), 15) / 10f * density;
    }

    private static float falloff(final float widthDp, final float density) {
        // exp(-3) is 95% gone, so three widths describe how far the effect reaches inwards
        return 3f / Math.max(widthDp * density, 1f);
    }

    private static String key(final Context context, final int keyRes) {
        return context.getString(keyRes);
    }

    private static SharedPreferences preferences(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
