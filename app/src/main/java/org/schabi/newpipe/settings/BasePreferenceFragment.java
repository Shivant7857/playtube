package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected static final boolean DEBUG = MainActivity.DEBUG;

    SharedPreferences defaultPreferences;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        super.onCreate(savedInstanceState);
    }

    protected void addPreferencesFromResourceRegistry() {
        addPreferencesFromResource(
                SettingsResourceRegistry.getInstance().getPreferencesResId(this.getClass()));
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        setDivider(null);
        updateScreenChrome();
        applyBottomBarPadding();
    }

    private void applyBottomBarPadding() {
        final View listView = getListView();
        if (listView instanceof androidx.recyclerview.widget.RecyclerView) {
            final androidx.recyclerview.widget.RecyclerView rv =
                    (androidx.recyclerview.widget.RecyclerView) listView;
            rv.setClipToPadding(false);
            final int padBottom = getResources().getDimensionPixelSize(
                    R.dimen.glass_bar_scroll_padding);
            rv.setPadding(rv.getPaddingLeft(), rv.getPaddingTop(),
                    rv.getPaddingRight(), padBottom);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateScreenChrome();
    }

    /**
     * Reports the title to the activity header and re-groups the rows. Runs on every resume
     * because a preference can hide itself while the screen is open, which changes which row
     * is the last one of its card.
     */
    private void updateScreenChrome() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (getActivity() instanceof SettingsActivity) {
            ThemeHelper.setTitleToAppCompatActivity(getActivity(), screen.getTitle());
            ((SettingsActivity) getActivity()).setSettingsHeader(
                    screen.getTitle(), showSearchField());
        }
        groupIntoCards(screen);
    }

    /**
     * @return whether this screen owns the search field below its title
     */
    protected boolean showSearchField() {
        return false;
    }

    /**
     * Turns every run of preferences that belongs together into one rounded card: children of a
     * category share a card, preferences sitting directly on the screen share one too, and a
     * category without any visible row is hidden instead of leaving an empty header behind.
     *
     * @param group the screen or category to walk
     */
    private void groupIntoCards(@NonNull final PreferenceGroup group) {
        final List<Preference> card = new ArrayList<>();
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            final Preference preference = group.getPreference(i);
            if (preference instanceof PreferenceCategory) {
                styleCard(card);
                card.clear();
                final PreferenceCategory category = (PreferenceCategory) preference;
                category.setLayoutResource(R.layout.settings_section_header);
                category.setVisible(hasVisibleRow(category));
                groupIntoCards(category);
            } else if (preference.isVisible()) {
                card.add(preference);
            }
        }
        styleCard(card);
    }

    /**
     * @param group the category to check
     * @return whether at least one of its rows is shown
     */
    private static boolean hasVisibleRow(@NonNull final PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            final Preference preference = group.getPreference(i);
            if (preference.isVisible() && !(preference instanceof PreferenceCategory)) {
                return true;
            }
            if (preference instanceof PreferenceCategory
                    && hasVisibleRow((PreferenceCategory) preference)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives each row the background of its position and a chevron when it leads somewhere else.
     *
     * @param rows the visible rows of one card, in display order
     */
    private void styleCard(@NonNull final List<Preference> rows) {
        for (int i = 0; i < rows.size(); i++) {
            final Preference preference = rows.get(i);
            preference.setLayoutResource(rowLayout(rows.size(), i));
            if (isNavigable(preference)) {
                preference.setWidgetLayoutResource(R.layout.settings_row_chevron);
            }
        }
    }

    /**
     * @param preference the row to check
     * @return whether tapping it replaces the screen or opens another one
     */
    private static boolean isNavigable(@NonNull final Preference preference) {
        return preference instanceof PreferenceScreen
                || preference.getFragment() != null
                || "pref_donate_key".equals(preference.getKey())
                || "pref_about_key".equals(preference.getKey());
    }

    /**
     * @param groupSize how many rows the card has
     * @param index the row's position inside the card
     * @return the row layout with the corners and the divider that belong to that position
     */
    private static int rowLayout(final int groupSize, final int index) {
        if (groupSize == 1) {
            return R.layout.preference_row_card_alone;
        } else if (index == 0) {
            return R.layout.preference_row_card_top;
        } else if (index == groupSize - 1) {
            return R.layout.preference_row_card_bottom;
        }
        return R.layout.preference_row_card_middle;
    }

    @NonNull
    public final <T extends Preference> T requirePreference(@StringRes final int resId) {
        final T preference = findPreference(getString(resId));
        Objects.requireNonNull(preference);
        return preference;
    }
}
