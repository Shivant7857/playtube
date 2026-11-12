package org.schabi.newpipe.fragments;

import static android.widget.RelativeLayout.ABOVE;
import static android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM;
import static android.widget.RelativeLayout.ALIGN_PARENT_TOP;
import static android.widget.RelativeLayout.BELOW;
import static com.google.android.material.tabs.TabLayout.INDICATOR_GRAVITY_BOTTOM;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.LiquidGlassTabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private FragmentMainBinding binding;
    private SelectedTabsPagerAdapter pagerAdapter;

    private final List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;

    private SharedPreferences prefs;
    private boolean youtubeRestrictedModeEnabled;
    private String youtubeRestrictedModeEnabledKey;
    private boolean mainTabsPositionBottom;
    private String mainTabsPositionKey;

    private Disposable badgeDisposable;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                setupTabs();
            } else {
                hasTabsChanged = true;
            }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        mainTabsPositionKey = getString(R.string.main_tabs_position_key);
        mainTabsPositionBottom = true;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        binding = FragmentMainBinding.bind(rootView);

        binding.mainTabLayout.setupWithViewPager(binding.pager);
        binding.mainTabLayout.addOnTabSelectedListener(this);
        binding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(final int position) {
                updateTitleForTab(position);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainTabLayout, (v, insets) -> {
            if (mainTabsPositionBottom) {
                final Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                final var params = (RelativeLayout.LayoutParams) v.getLayoutParams();
                if (params != null) {
                    final int side = getResources()
                            .getDimensionPixelSize(R.dimen.glass_bar_margin_side);
                    final int baseBottom = getResources()
                            .getDimensionPixelSize(R.dimen.glass_bar_margin_bottom);
                    params.height = getResources()
                            .getDimensionPixelSize(R.dimen.glass_bar_height);
                    params.setMargins(side, 0, side, baseBottom + sysBars.bottom);
                    v.setLayoutParams(params);
                }
            }
            return insets;
        });

        setupTabs();
        updateTabLayoutPosition();
    }

    @Override
    public void onResume() {
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateBrandingForCurrentTab();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).updateHeaderBellBadge(
                    ((MainActivity) activity).getActiveNotificationCount());
        }
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }

        final boolean newYoutubeRestrictedModeEnabled =
                prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        if (youtubeRestrictedModeEnabled != newYoutubeRestrictedModeEnabled || hasTabsChanged) {
            youtubeRestrictedModeEnabled = newYoutubeRestrictedModeEnabled;
            setupTabs();
        }

        final boolean newMainTabsPosition = true;
        if (mainTabsPositionBottom != newMainTabsPosition) {
            mainTabsPositionBottom = newMainTabsPosition;
            updateTabLayoutPosition();
        }
    }

    @Override
    public void onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences changedPreferences,
                                          final String key) {
        if (binding != null && key != null && key.startsWith("liquid_glass_")) {
            applyGlassBarStyle(binding.mainTabLayout);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
        if (badgeDisposable != null) {
            badgeDisposable.dispose();
            badgeDisposable = null;
        }
        if (binding != null) {
            binding.pager.setAdapter(null);
            binding = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (badgeDisposable != null) {
            badgeDisposable.dispose();
            badgeDisposable = null;
        }
        binding = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        inflater.inflate(R.menu.menu_main_fragment, menu);

        final MenuItem notifItem = menu.findItem(R.id.action_notifications);
        bindBellAction(notifItem);
        if (binding != null && binding.getRoot() != null) {
            binding.getRoot().post(() -> bindBellAction(notifItem));
        }

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            if (activity instanceof MainActivity && !isSettingsTabSelected()) {
                ((MainActivity) activity).ensureMainToolbarState();
            } else {
                supportActionBar.setDisplayHomeAsUpEnabled(false);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_search) {
            try {
                NavigationHelper.openSearchFragment(getFM(),
                        ServiceHelper.getSelectedServiceId(activity), "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
            return true;
        } else if (itemId == R.id.action_notifications) {
            try {
                NavigationHelper.openFeedFragment(getFM());
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening feed fragment", e);
            }
            return true;
        } else if (itemId == R.id.action_downloads) {
            try {
                NavigationHelper.openDownloads(activity);
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening downloads", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void setupTabs() {
        tabsList.clear();
        tabsList.addAll(tabsManager.getTabs());

        if (pagerAdapter == null || !pagerAdapter.sameTabs(tabsList)) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList);
        }

        binding.pager.setAdapter(null);
        binding.pager.setAdapter(pagerAdapter);

        updateTabsIconAndDescription();
        updateTitleForTab(binding.pager.getCurrentItem());

        hasTabsChanged = false;
    }

    private void updateTabsIconAndDescription() {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = binding.mainTabLayout.getTabAt(i);
            if (tabToSet != null) {
                final Tab tab = tabsList.get(i);
                tabToSet.setIcon(tab.getTabIconRes(requireContext()));
                tabToSet.setText(tab.getTabName(requireContext()));
                tabToSet.setContentDescription(tab.getTabName(requireContext()));
            }
        }
    }

    public void updateBrandingForCurrentTab() {
        if (binding != null && binding.pager != null) {
            updateTitleForTab(binding.pager.getCurrentItem());
        }
    }

    public boolean isSettingsTabSelected() {
        if (binding == null || binding.pager == null) {
            return false;
        }
        final int currentItem = binding.pager.getCurrentItem();
        if (currentItem >= 0 && currentItem < tabsList.size()) {
            return tabsList.get(currentItem).getTabId() == Tab.SettingsTab.ID;
        }
        return false;
    }

    private void updateTitleForTab(final int tabPosition) {
        if (tabPosition >= 0 && tabPosition < tabsList.size()) {
            final boolean isSettings = tabsList.get(tabPosition).getTabId() == Tab.SettingsTab.ID;
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).setSettingsTabMode(isSettings);
                if (!isSettings) {
                    ((MainActivity) activity).ensureMainToolbarState();
                }
            }
        }
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final boolean isSettings = isSettingsTabSelected();
        final MenuItem notif = menu.findItem(R.id.action_notifications);
        if (notif != null) {
            notif.setVisible(!isSettings);
            if (!isSettings) {
                bindBellAction(notif);
            }
        }
        final MenuItem dl = menu.findItem(R.id.action_downloads);
        if (dl != null) {
            dl.setVisible(!isSettings);
        }
        final MenuItem search = menu.findItem(R.id.action_search);
        if (search != null) {
            search.setVisible(!isSettings);
        }
    }

    private void bindBellAction(final MenuItem notifItem) {
        if (notifItem == null || notifItem.getActionView() == null) {
            return;
        }
        final View actionView = notifItem.getActionView();
        final View.OnClickListener clickListener = v -> {
            try {
                prefs.edit().putLong("feed_notifications_last_seen",
                        System.currentTimeMillis()).apply();
                final NotificationManager nm = (NotificationManager)
                        requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.cancelAll();
                }
                updateBadgeUi(actionView.findViewById(R.id.bell_badge), 0);
                NavigationHelper.openFeedFragment(getFM());
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening feed fragment", e);
            }
        };
        actionView.setOnClickListener(clickListener);
        final View bellIcon = actionView.findViewById(R.id.bell_icon);
        if (bellIcon != null) {
            bellIcon.setOnClickListener(clickListener);
        }
        final View bellBadge = actionView.findViewById(R.id.bell_badge);
        if (bellBadge != null) {
            bellBadge.setOnClickListener(clickListener);
        }
        setupNotificationBadge(actionView);
    }

    private int getActiveNotificationCount() {
        if (getContext() == null) {
            return 0;
        }
        try {
            final NotificationManager nm = (NotificationManager)
                    getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                final StatusBarNotification[] notifs = nm.getActiveNotifications();
                if (notifs != null) {
                    int count = 0;
                    final String streamChannelId =
                            getString(R.string.streams_notification_channel_id);
                    for (final StatusBarNotification sbn : notifs) {
                        if (streamChannelId.equals(sbn.getNotification().getChannelId())) {
                            if ((sbn.getNotification().flags
                                    & Notification.FLAG_GROUP_SUMMARY) == 0) {
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        return count;
                    }
                }
            }
        } catch (final Exception ignored) {
        }
        return 0;
    }

    private void setupNotificationBadge(final View actionView) {
        final TextView badge = actionView.findViewById(R.id.bell_badge);
        if (badge == null) {
            return;
        }

        final int activeCount = getActiveNotificationCount();
        if (activeCount > 0) {
            updateBadgeUi(badge, activeCount);
            return;
        }

        if (badgeDisposable != null && !badgeDisposable.isDisposed()) {
            badgeDisposable.dispose();
        }

        try {
            final AppDatabase db = NewPipeDatabase.getInstance(requireContext());
            final long lastSeen = prefs.getLong("feed_notifications_last_seen", 0L);
            final java.time.OffsetDateTime seenThreshold = lastSeen > 0L
                    ? java.time.OffsetDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(lastSeen), java.time.ZoneOffset.UTC)
                    : null;
            badgeDisposable = db.feedDAO().unplayedStreamsCount(seenThreshold)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(count -> {
                        final int active = getActiveNotificationCount();
                        final long total = active > 0 ? active : count;
                        updateBadgeUi(badge, total);
                    }, throwable -> {
                        if (DEBUG) {
                            Log.e(TAG, "Error querying notification count", throwable);
                        }
                        updateBadgeUi(badge, 0);
                    });
        } catch (final Exception e) {
            updateBadgeUi(badge, 0);
        }
    }

    private void updateBadgeUi(final TextView badge, final long count) {
        if (badge == null) {
            return;
        }
        if (count <= 0) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setVisibility(View.VISIBLE);
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
        }
    }

    public void commitPlaylistTabs() {
        pagerAdapter.getLocalPlaylistFragments()
                .stream()
                .forEach(LocalPlaylistFragment::saveImmediate);
    }

    private void updateTabLayoutPosition() {
        final LiquidGlassTabLayout tabLayout = binding.mainTabLayout;
        final ViewPager viewPager = binding.pager;
        final boolean bottom = mainTabsPositionBottom;

        // change layout params to make the tab layout appear either at the top or at the bottom
        final var tabParams = (RelativeLayout.LayoutParams) tabLayout.getLayoutParams();
        final var pagerParams = (RelativeLayout.LayoutParams) viewPager.getLayoutParams();

        tabParams.removeRule(bottom ? ALIGN_PARENT_TOP : ALIGN_PARENT_BOTTOM);
        tabParams.addRule(bottom ? ALIGN_PARENT_BOTTOM : ALIGN_PARENT_TOP);

        pagerParams.removeRule(BELOW);
        pagerParams.removeRule(ABOVE);
        if (bottom) {
            // the feed runs behind the floating bar, otherwise there is nothing to refract
            final int side = getResources().getDimensionPixelSize(R.dimen.glass_bar_margin_side);
            final int below =
                    getResources().getDimensionPixelSize(R.dimen.glass_bar_margin_bottom);
            final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(tabLayout);
            final int bottomInset = rootInsets != null
                    ? rootInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                    : 0;
            tabParams.height = getResources().getDimensionPixelSize(R.dimen.glass_bar_height);
            tabParams.setMargins(side, 0, side, below + bottomInset);
        } else {
            pagerParams.addRule(BELOW, R.id.main_tab_layout);
            tabParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            tabParams.setMargins(0, 0, 0, 0);
        }

        tabLayout.setLayoutParams(tabParams);
        viewPager.setLayoutParams(pagerParams);

        if (bottom) {
            applyGlassBarStyle(tabLayout);
        } else {
            applyPlainBarStyle(tabLayout);
        }
    }

    private void applyGlassBarStyle(final LiquidGlassTabLayout tabLayout) {
        tabLayout.setLiquidGlassEnabled(false);
        tabLayout.setBackground(ContextCompat.getDrawable(requireContext(),
                R.drawable.tab_bar_glass));
        tabLayout.setElevation(
                getResources().getDimensionPixelSize(R.dimen.glass_bar_elevation));

        final int selected = Color.parseColor("#FF3D33");
        final int idle = Color.parseColor("#8D8D92");

        tabLayout.setTabRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        tabLayout.setTabIconTint(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}},
                new int[]{selected, idle}));
        tabLayout.setTabTextColors(idle, selected);

        tabLayout.setSelectedTabIndicator(null);
        tabLayout.setSelectedTabIndicatorHeight(0);
    }

    private void applyPlainBarStyle(final LiquidGlassTabLayout tabLayout) {
        tabLayout.setLiquidGlassEnabled(false);
        tabLayout.setBackgroundColor(
                ThemeHelper.resolveColorFromAttr(requireContext(), R.attr.colorPrimary));
        tabLayout.setElevation(0);

        tabLayout.setTabRippleColor(ColorStateList.valueOf(Color.WHITE).withAlpha(32));
        tabLayout.setTabIconTint(ColorStateList.valueOf(Color.WHITE));
        tabLayout.setSelectedTabIndicator(new ColorDrawable(Color.WHITE));
        tabLayout.setSelectedTabIndicatorGravity(INDICATOR_GRAVITY_BOTTOM);
        tabLayout.setTabIndicatorFullWidth(true);
        tabLayout.setSelectedTabIndicatorHeight(
                getResources().getDimensionPixelSize(R.dimen.tab_indicator_height));
    }

    @Override
    public void onTabSelected(final TabLayout.Tab selectedTab) {
        if (DEBUG) {
            Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]");
        }
        updateTitleForTab(selectedTab.getPosition());
    }

    @Override
    public void onTabUnselected(final TabLayout.Tab tab) { }

    @Override
    public void onTabReselected(final TabLayout.Tab tab) {
        if (DEBUG) {
            Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]");
        }
        updateTitleForTab(tab.getPosition());
    }

    public static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;
        /**
         * Keep reference to LocalPlaylistFragments, because their data can be modified by the user
         * during runtime and changes are not committed immediately. However, in some cases,
         * the changes need to be committed immediately by calling
         * {@link LocalPlaylistFragment#saveImmediate()}.
         * The fragments are removed when {@link LocalPlaylistFragment#onDestroy()} is called.
         */
        private final List<LocalPlaylistFragment> localPlaylistFragments = new ArrayList<>();

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.internalTabsList = new ArrayList<>(tabsList);
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(position);

            final Fragment fragment;
            try {
                fragment = tab.getFragment(context);
            } catch (final Throwable t) {
                return new BlankFragment(new ErrorInfo(t, UserAction.GETTING_MAIN_SCREEN_TAB,
                        "Tab " + tab.getClass().getSimpleName() + ":" + tab.getTabName(context)));
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            if (fragment instanceof LocalPlaylistFragment) {
                localPlaylistFragments.add((LocalPlaylistFragment) fragment);
            }

            return fragment;
        }

        public List<LocalPlaylistFragment> getLocalPlaylistFragments() {
            return localPlaylistFragments;
        }

        @Override
        public int getItemPosition(@NonNull final Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return internalTabsList.size();
        }

        public boolean sameTabs(final List<Tab> tabsToCompare) {
            return internalTabsList.equals(tabsToCompare);
        }
    }
}
