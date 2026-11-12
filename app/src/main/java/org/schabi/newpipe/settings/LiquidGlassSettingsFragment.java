package org.schabi.newpipe.settings;

import android.os.Bundle;

/**
 * Sliders for the liquid glass bottom bar. Changing one is applied to the bar immediately, so the
 * effect can be tuned while the feed is visible behind it.
 */
public class LiquidGlassSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();
    }
}
