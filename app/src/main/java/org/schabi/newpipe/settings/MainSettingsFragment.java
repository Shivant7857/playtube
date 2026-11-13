package org.schabi.newpipe.settings;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.update.UpdateInfo;
import org.schabi.newpipe.update.UpdateManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ReleaseVersionUtil;
import org.schabi.newpipe.util.external_communication.ShareUtils;

public class MainSettingsFragment extends BasePreferenceFragment {
    public static final boolean DEBUG = MainActivity.DEBUG;

    private SettingsActivity settingsActivity;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResourceRegistry();

        setHasOptionsMenu(true); // Otherwise onCreateOptionsMenu is not called

        // Check if the app is updatable
        if (!ReleaseVersionUtil.INSTANCE.isReleaseApk()) {
            getPreferenceScreen().removePreferenceRecursively(
                    requirePreference(R.string.update_pref_screen_key).getKey());

            defaultPreferences.edit().putBoolean(getString(R.string.update_app_key), false).apply();
        }

        // Hide debug preferences in RELEASE build variant
        if (!DEBUG) {
            getPreferenceScreen().removePreferenceRecursively(
                    requirePreference(R.string.debug_pref_screen_key).getKey());
        }

        final Preference appInfoPref = findPreference("pref_app_info_key");
        if (appInfoPref != null) {
            appInfoPref.setOnPreferenceClickListener(pref -> {
                NavigationHelper.openAbout(requireContext());
                return true;
            });
        }

        final Preference donatePref = findPreference(getString(R.string.pref_donate_key));
        if (donatePref != null) {
            donatePref.setOnPreferenceClickListener(pref -> {
                ShareUtils.openUrlInBrowser(requireContext(), getString(R.string.donation_url));
                return true;
            });
        }

        final Preference aboutPref = findPreference(getString(R.string.pref_about_key));
        if (aboutPref != null) {
            aboutPref.setOnPreferenceClickListener(pref -> {
                NavigationHelper.openAbout(requireContext());
                return true;
            });
        }

        updateSoftwareUpdateSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSoftwareUpdateSummary();
    }

    private void updateSoftwareUpdateSummary() {
        final Preference updatePref =
                findPreference(getString(R.string.update_pref_screen_key));
        if (updatePref != null) {
            final UpdateInfo cached = UpdateManager.INSTANCE.getCachedUpdate();
            if (cached != null) {
                final String vName = cached.getVersionName().replaceFirst("^[vV]", "");
                updatePref.setSummary("Update available: v" + vName);
            } else {
                updatePref.setSummary(R.string.software_update_summary);
            }
        }
    }

    @Override
    protected boolean showSearchField() {
        return true;
    }

    @Override
    public void onCreateOptionsMenu(
            @NonNull final Menu menu,
            @NonNull final MenuInflater inflater
    ) {
        super.onCreateOptionsMenu(menu, inflater);

        // -- Link settings activity and register menu --
        if (getActivity() instanceof SettingsActivity) {
            settingsActivity = (SettingsActivity) getActivity();
            inflater.inflate(R.menu.menu_settings_main_fragment, menu);
            final MenuItem menuSearchItem = menu.getItem(0);
            settingsActivity.setMenuSearchItem(menuSearchItem);
            menuSearchItem.setOnMenuItemClickListener(ev -> {
                settingsActivity.setSearchActive(true);
                return true;
            });
        }
    }

    @Override
    public void onDestroy() {
        // Unlink activity so that we don't get memory problems
        if (settingsActivity != null) {
            settingsActivity.setMenuSearchItem(null);
            settingsActivity = null;
        }
        super.onDestroy();
    }
}
