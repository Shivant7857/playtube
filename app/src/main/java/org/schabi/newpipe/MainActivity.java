/*
 * Created by Christian Schabesberger on 02.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DownloadActivity.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.schabi.newpipe.util.VideoTransitionManager;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.schabi.newpipe.databinding.ActivityMainBinding;
import org.schabi.newpipe.databinding.ToolbarLayoutBinding;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment;
import org.schabi.newpipe.fragments.list.search.SearchFragment;
import org.schabi.newpipe.local.feed.notifications.NotificationWorker;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.event.OnKeyDownListener;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.settings.UpdateSettingsFragment;
import org.schabi.newpipe.settings.migration.MigrationManager;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ReleaseVersionUtil;
import org.schabi.newpipe.util.SerializedCache;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.FocusOverlayView;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private static final String TAG = "MainActivity";
    @SuppressWarnings("ConstantConditions")
    public static final boolean DEBUG = !BuildConfig.BUILD_TYPE.equals("release");

    private ActivityMainBinding mainBinding;
    private ToolbarLayoutBinding toolbarLayoutBinding;

    private BroadcastReceiver broadcastReceiver;

    public static final String KEY_IS_IN_BACKGROUND = "is_in_background";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor sharedPrefEditor;
    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }

        Localization.migrateAppLanguageSettingIfNecessary(getApplicationContext());
        ThemeHelper.setDayNightMode(this);
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this));

        // Fixes text color turning black in dark/black mode:
        // https://github.com/TeamNewPipe/NewPipe/issues/12016
        // For further reference see: https://issuetracker.google.com/issues/37124582
        if (DeviceUtils.supportsWebView()) {
            try {
                new WebView(this);
            } catch (final Throwable e) {
                if (DEBUG) {
                    Log.e(TAG, "Failed to create WebView", e);
                }
            }
        }

        super.onCreate(savedInstanceState);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefEditor = sharedPreferences.edit();

        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        toolbarLayoutBinding = mainBinding.toolbarLayout;
        setContentView(mainBinding.getRoot());
        startOpeningSplashSequence(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        final boolean isLight = ThemeHelper.isLightThemeSelected(this);
        final WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(isLight);
        insetsController.setAppearanceLightNavigationBars(isLight);

        ViewCompat.setOnApplyWindowInsetsListener(toolbarLayoutBinding.getRoot(),
                (v, windowInsets) -> {
                    final androidx.core.graphics.Insets insets =
                            windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
                    statusBarTopInset = insets.top;
                    v.setPadding(0, insets.top, 0, 0);
                    updateFragmentHolderMargin();
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(toolbarLayoutBinding.getRoot());

        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            initFragments();
        }

        setSupportActionBar(toolbarLayoutBinding.toolbar);
        setupHeaderActionButtons();
        setupPlayerBottomSheetCallback();
        ensureMainToolbarState();
        getSupportFragmentManager().addOnBackStackChangedListener(this::updateNavigationState);
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentResumed(@NonNull final FragmentManager fm,
                                                  @NonNull final Fragment f) {
                        super.onFragmentResumed(fm, f);
                        if (isMainFragmentActive() && !isSettingsTabActive()) {
                            ensureMainToolbarState();
                        }
                    }
                }, true);
        updateNavigationState();

        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this);
        }
        openMiniPlayerUponPlayerStarted();

        if (PermissionHelper.checkPostNotificationsPermission(this,
                PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE)) {
            // Schedule worker for checking for new streams and creating corresponding notifications
            // if this is enabled by the user.
            NotificationWorker.initialize(this);
        }
        if (!UpdateSettingsFragment.wasUserAskedForConsent(this)
                && !App.getInstance().isFirstRun()
                && ReleaseVersionUtil.INSTANCE.isReleaseApk()) {
            UpdateSettingsFragment.askForConsentToUpdateChecks(this);
        }

        // ReleaseVersionUtil.INSTANCE.isReleaseApk() will be true only for main official build
        // We want every release build (nightly, nightly-refactor) to show the popup
        if (!DEBUG) {
            showKeepAndroidDialog();
            showApi23RequirementDialog();
        }

        MigrationManager.showUserInfoIfPresent(this);
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        final App app = App.getInstance();

        if (sharedPreferences.getBoolean(app.getString(R.string.update_app_key), false)
                && sharedPreferences
                .getBoolean(app.getString(R.string.update_check_consent_key), false)) {
            // Start the worker which is checking all conditions
            // and eventually searching for a new version.
            NewVersionWorker.enqueueNewVersionCheckingWork(app, false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        sharedPrefEditor.putBoolean(KEY_IS_IN_BACKGROUND, false).apply();
        Log.d(TAG, "App moved to foreground");
    }

    @Override
    protected void onStop() {
        super.onStop();
        sharedPrefEditor.putBoolean(KEY_IS_IN_BACKGROUND, true).apply();
        Log.d(TAG, "App moved to background");
    }
    @Override
    public boolean onPreferenceStartFragment(
            @NonNull final PreferenceFragmentCompat caller,
            @NonNull final Preference pref) {
        final Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, pref.getFragment());
        intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, pref.getExtras());
        startActivity(intent);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            StateSaver.clearStateFiles();
        }
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    @Override
    protected void onResume() {
        // Change the date format to match the selected language on resume
        Localization.initPrettyTime(Localization.resolvePrettyTime());
        super.onResume();
        updateHeaderBellBadge(getActiveNotificationCount());

        if (ServiceHelper.getSelectedServiceId(this) != 0) {
            ServiceHelper.setSelectedServiceId(this, 0);
        }

        if (sharedPreferences.getBoolean(Constants.KEY_THEME_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "Theme has changed, recreating activity...");
            }
            sharedPrefEditor.putBoolean(Constants.KEY_THEME_CHANGE, false).apply();
            ActivityCompat.recreate(this);
        }

        if (sharedPreferences.getBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "main page has changed, recreating main fragment...");
            }
            sharedPrefEditor.putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false).apply();
            NavigationHelper.openMainActivity(this);
        }

        updateNavigationState();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        }
        if (intent != null) {
            // Return if launched from a launcher (e.g. Nova Launcher, Pixel Launcher ...)
            // to not destroy the already created backstack
            final String action = intent.getAction();
            if ((action != null && action.equals(Intent.ACTION_MAIN))
                    && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                return;
            }
        }

        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        final Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder);
        if (fragment instanceof OnKeyDownListener
                && !bottomSheetHiddenOrCollapsed()) {
            // Provide keyDown event to fragment which then sends this event
            // to the main player service
            return ((OnKeyDownListener) fragment).onKeyDown(keyCode)
                    || super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called");
        }

        // In case bottomSheet is not visible on the screen or collapsed we can assume that the user
        // interacts with a fragment inside fragment_holder so all back presses should be
        // handled by it
        if (bottomSheetHiddenOrCollapsed()) {
            final FragmentManager fm = getSupportFragmentManager();
            final Fragment fragment = fm.findFragmentById(R.id.fragment_holder);
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragment instanceof BackPressable) {
                if (((BackPressable) fragment).onBackPressed()) {
                    return;
                }
            } else if (fragment instanceof CommentRepliesFragment) {
                // expand DetailsFragment if CommentRepliesFragment was opened
                // to show the top level comments again
                // Expand DetailsFragment if CommentRepliesFragment was opened
                // and no other CommentRepliesFragments are on top of the back stack
                // to show the top level comments again.
                openDetailFragmentFromCommentReplies(fm, false);
            }

        } else {
            final Fragment fragmentPlayer = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_player_holder);
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragmentPlayer instanceof BackPressable) {
                if (!((BackPressable) fragmentPlayer).onBackPressed()) {
                    if (runVideoCloseTransition(() ->
                            BottomSheetBehavior.from(mainBinding.fragmentPlayerHolder)
                                    .setState(BottomSheetBehavior.STATE_COLLAPSED))) {
                        return;
                    }
                    BottomSheetBehavior.from(mainBinding.fragmentPlayerHolder)
                            .setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
                return;
            }
        }

        if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (final int i : grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                return;
            }
        }
        switch (requestCode) {
            case PermissionHelper.DOWNLOADS_REQUEST_CODE:
                NavigationHelper.openDownloads(this);
                break;
            case PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE:
                final Fragment fragment = getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_player_holder);
                if (fragment instanceof VideoDetailFragment) {
                    ((VideoDetailFragment) fragment).openDownloadDialog();
                }
                break;
            case PermissionHelper.POST_NOTIFICATIONS_REQUEST_CODE:
                NotificationWorker.initialize(this);
                break;
        }
    }

    /**
     * Implement the following diagram behavior for the up button:
     * <pre>
     *              +---------------+
     *              |  Main Screen  +----+
     *              +-------+-------+    |
     *                      |            |
     *                      ▲ Up         | Search Button
     *                      |            |
     *                 +----+-----+      |
     *    +------------+  Search  |◄-----+
     *    |            +----+-----+
     *    |   Open          |
     *    |  something      ▲ Up
     *    |                 |
     *    |    +------------+-------------+
     *    |    |                          |
     *    |    |  Video    <->  Channel   |
     *    +---►|  Channel  <->  Playlist  |
     *         |  Video    <->  ....      |
     *         |                          |
     *         +--------------------------+
     * </pre>
     */
    private void onHomeButtonPressed() {
        final FragmentManager fm = getSupportFragmentManager();
        final Fragment fragment = fm.findFragmentById(R.id.fragment_holder);

        if (fragment instanceof CommentRepliesFragment) {
            // Expand DetailsFragment if CommentRepliesFragment was opened
            // and no other CommentRepliesFragments are on top of the back stack
            // to show the top level comments again.
            openDetailFragmentFromCommentReplies(fm, true);
        } else if (!NavigationHelper.tryGotoSearchFragment(fm)) {
            // If search fragment wasn't found in the backstack go to the main fragment
            NavigationHelper.gotoMainFragment(fm);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "]");
        }
        super.onCreateOptionsMenu(menu);

        final Fragment fragment =
                getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (!(fragment instanceof SearchFragment)) {
            toolbarLayoutBinding.toolbarSearchContainer.getRoot().setVisibility(View.GONE);
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        updateNavigationState();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (DEBUG) {
            Log.d(TAG, "onOptionsItemSelected() called with: item = [" + item + "]");
        }

        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onHomeButtonPressed();
            return true;
        } else if (itemId == R.id.action_search) {
            try {
                NavigationHelper.openSearchFragment(getSupportFragmentManager(),
                        ServiceHelper.getSelectedServiceId(this), "");
            } catch (final Exception e) {
                // Ignore
            }
            return true;
        } else if (itemId == R.id.action_downloads) {
            try {
                NavigationHelper.openDownloads(this);
            } catch (final Exception e) {
                // Ignore
            }
            return true;
        } else if (itemId == R.id.action_notifications) {
            try {
                NavigationHelper.openFeedFragment(getSupportFragmentManager());
            } catch (final Exception e) {
                // Ignore
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Splash Opening Sequence (Stage 1 & Stage 2)
    //////////////////////////////////////////////////////////////////////////*/

    private void startOpeningSplashSequence(@Nullable final Bundle savedInstanceState) {
        if (savedInstanceState != null || mainBinding.splashOverlay == null) {
            if (mainBinding.splashOverlay != null) {
                mainBinding.splashOverlay.setVisibility(View.GONE);
            }
            return;
        }

        final View overlay = mainBinding.splashOverlay;
        final View logo = mainBinding.splashLogo;
        final org.schabi.newpipe.views.PlayTubeLoadingBarView loader = mainBinding.splashLoader;

        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(1f);
        logo.setVisibility(View.VISIBLE);
        logo.setAlpha(0f);
        logo.setScaleX(0.92f);
        logo.setScaleY(0.92f);
        loader.setVisibility(View.GONE);

        // Stage 1: Centered App Logo Splash (fade-in + subtle scale-in)
        logo.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(380)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    logo.postDelayed(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        // Transition to Stage 2: Minimal Horizontal PlayTube Loader
                        logo.animate()
                                .alpha(0f)
                                .scaleX(0.92f)
                                .scaleY(0.92f)
                                .setDuration(220)
                                .withEndAction(() -> {
                                    logo.setVisibility(View.GONE);
                                    loader.setVisibility(View.VISIBLE);
                                    loader.setAlpha(0f);
                                    loader.setScaleX(0.92f);
                                    loader.setScaleY(0.92f);
                                    loader.startAnimation();

                                    loader.animate()
                                            .alpha(1f)
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(220)
                                            .withEndAction(() -> {
                                                loader.postDelayed(() -> {
                                                    if (isFinishing() || isDestroyed()) {
                                                        return;
                                                    }
                                                    // Stage 3: Home Screen Skeleton Loading
                                                    overlay.setClickable(false);
                                                    overlay.setFocusable(false);
                                                    overlay.animate()
                                                            .alpha(0f)
                                                            .setDuration(260)
                                                            .withEndAction(() -> {
                                                                loader.stopAnimation();
                                                                overlay.setVisibility(View.GONE);
                                                            })
                                                            .start();
                                                }, 520);
                                            })
                                            .start();
                                })
                                .start();
                    }, 350);
                })
                .start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initFragments() {
        if (DEBUG) {
            Log.d(TAG, "initFragments() called");
        }
        StateSaver.clearStateFiles();
        if (getIntent() != null && getIntent().hasExtra(Constants.KEY_LINK_TYPE)) {
            // When user watch a video inside popup and then tries to open the video in main player
            // while the app is closed he will see a blank fragment on place of kiosk.
            // Let's open it first
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                NavigationHelper.openMainFragment(getSupportFragmentManager());
            }

            handleIntent(getIntent());
        } else {
            NavigationHelper.gotoMainFragment(getSupportFragmentManager());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final CharSequence title) {
        if (isMainFragmentActive() && !isSettingsTabActive()) {
            super.setTitle("");
            ensureMainToolbarState();
            return;
        }
        super.setTitle(title);
    }

    @Override
    protected void onTitleChanged(final CharSequence title, final int color) {
        super.onTitleChanged(title, color);
        if (isMainFragmentActive() && !isSettingsTabActive()) {
            ensureMainToolbarState();
        }
    }

    private int statusBarTopInset = 0;

    public void setSettingsTabMode(final boolean inSettingsTab) {
        if (toolbarLayoutBinding != null) {
            toolbarLayoutBinding.getRoot().setVisibility(inSettingsTab ? View.GONE : View.VISIBLE);
        }
        updateFragmentHolderMargin();
        if (inSettingsTab) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        } else {
            ensureMainToolbarState();
        }
        supportInvalidateOptionsMenu();
    }

    private void updateFragmentHolderMargin() {
        if (mainBinding == null || mainBinding.fragmentHolder == null) {
            return;
        }
        final ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) mainBinding.fragmentHolder.getLayoutParams();
        if (lp != null) {
            final boolean inSettings = isSettingsTabActive();
            lp.topMargin = inSettings ? 0 : statusBarTopInset + getActionBarHeight();
            mainBinding.fragmentHolder.setLayoutParams(lp);
        }
    }

    public boolean isMainFragmentActive() {
        final Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_holder);
        return fragment instanceof MainFragment;
    }

    public boolean isSettingsTabActive() {
        final Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_holder);
        if (fragment instanceof MainFragment) {
            return ((MainFragment) fragment).isSettingsTabSelected();
        }
        return false;
    }

    public void ensureMainToolbarState() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setTitle("");
            getSupportActionBar().setSubtitle(null);
        }
        if (toolbarLayoutBinding != null) {
            toolbarLayoutBinding.toolbar.setNavigationIcon(null);
            toolbarLayoutBinding.toolbar.setNavigationOnClickListener(null);
            toolbarLayoutBinding.toolbar.setTitle("");
            toolbarLayoutBinding.toolbar.setSubtitle("");
            if (toolbarLayoutBinding.toolbarBrandingContainer != null) {
                toolbarLayoutBinding.toolbarBrandingContainer.setVisibility(View.VISIBLE);
            }
            if (toolbarLayoutBinding.toolbarActionsContainer != null) {
                toolbarLayoutBinding.toolbarActionsContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupHeaderActionButtons() {
        if (toolbarLayoutBinding == null) {
            return;
        }

        toolbarLayoutBinding.headerActionBell.setOnClickListener(v -> {
            try {
                sharedPreferences.edit()
                        .putLong("feed_notifications_last_seen", System.currentTimeMillis())
                        .apply();
                final NotificationManager nm = (NotificationManager)
                        getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.cancelAll();
                }
                updateHeaderBellBadge(0);
                NavigationHelper.openFeedFragment(getSupportFragmentManager());
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening feed fragment", e);
            }
        });

        toolbarLayoutBinding.headerActionDownload.setOnClickListener(v -> {
            try {
                NavigationHelper.openDownloads(this);
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening downloads", e);
            }
        });

        toolbarLayoutBinding.headerActionSearch.setOnClickListener(v -> {
            try {
                NavigationHelper.openSearchFragment(getSupportFragmentManager(),
                        ServiceHelper.getSelectedServiceId(this), "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
        });

        updateHeaderBellBadge(getActiveNotificationCount());
    }

    private void setupPlayerBottomSheetCallback() {
        if (mainBinding == null || mainBinding.fragmentPlayerHolder == null) {
            return;
        }
        final BottomSheetBehavior<FrameLayout> behavior =
                BottomSheetBehavior.from(mainBinding.fragmentPlayerHolder);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    if (toolbarLayoutBinding != null) {
                        toolbarLayoutBinding.getRoot().setVisibility(View.GONE);
                    }
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED
                        || newState == BottomSheetBehavior.STATE_HIDDEN) {
                    if (toolbarLayoutBinding != null && !isSettingsTabActive()) {
                        toolbarLayoutBinding.getRoot().setVisibility(View.VISIBLE);
                        toolbarLayoutBinding.getRoot().setAlpha(1f);
                    }
                }
            }

            @Override
            public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
                if (toolbarLayoutBinding == null || isSettingsTabActive()) {
                    return;
                }
                if (slideOffset > 0.05f) {
                    final float alpha = Math.max(0f, 1f - slideOffset * 2.5f);
                    toolbarLayoutBinding.getRoot().setAlpha(alpha);
                    if (slideOffset >= 0.9f) {
                        toolbarLayoutBinding.getRoot().setVisibility(View.GONE);
                    } else {
                        toolbarLayoutBinding.getRoot().setVisibility(View.VISIBLE);
                    }
                } else {
                    toolbarLayoutBinding.getRoot().setAlpha(1f);
                    toolbarLayoutBinding.getRoot().setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void updateHeaderBellBadge(final int count) {
        if (toolbarLayoutBinding == null
                || toolbarLayoutBinding.headerBellBadge == null) {
            return;
        }
        if (count > 0) {
            toolbarLayoutBinding.headerBellBadge.setText(
                    count > 9 ? "9+" : String.valueOf(count));
            toolbarLayoutBinding.headerBellBadge.setVisibility(View.VISIBLE);
        } else {
            toolbarLayoutBinding.headerBellBadge.setVisibility(View.GONE);
        }
    }

    public int getActiveNotificationCount() {
        try {
            final NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
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

    private void updateNavigationState() {
        if (getSupportActionBar() == null) {
            return;
        }

        final Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_holder);
        if (fragment instanceof MainFragment) {
            ensureMainToolbarState();
            ((MainFragment) fragment).updateBrandingForCurrentTab();
        } else {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbarLayoutBinding.toolbar.setNavigationOnClickListener(v -> onHomeButtonPressed());
            setToolbarBrandingVisible(false);
        }
    }

    public void setToolbarBrandingVisible(final boolean visible) {
        if (toolbarLayoutBinding != null) {
            if (toolbarLayoutBinding.toolbarBrandingContainer != null) {
                toolbarLayoutBinding.toolbarBrandingContainer.setVisibility(
                        visible ? View.VISIBLE : View.GONE);
            }
            if (toolbarLayoutBinding.toolbarActionsContainer != null) {
                toolbarLayoutBinding.toolbarActionsContainer.setVisibility(
                        visible ? View.VISIBLE : View.GONE);
            }
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(!visible);
            if (visible) {
                ensureMainToolbarState();
            }
        }
    }

    private int getActionBarHeight() {
        final TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            return TypedValue.complexToDimensionPixelSize(tv.data,
                    getResources().getDisplayMetrics());
        }
        return (int) (56 * getResources().getDisplayMetrics().density);
    }

    private void handleIntent(final Intent intent) {
        try {
            if (DEBUG) {
                Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
            }

            if (intent.hasExtra(Constants.KEY_LINK_TYPE)) {
                final String url = intent.getStringExtra(Constants.KEY_URL);
                final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
                String title = intent.getStringExtra(Constants.KEY_TITLE);
                if (title == null) {
                    title = "";
                }

                final StreamingService.LinkType linkType = ((StreamingService.LinkType) intent
                        .getSerializableExtra(Constants.KEY_LINK_TYPE));
                assert linkType != null;
                switch (linkType) {
                    case STREAM:
                        final String intentCacheKey = intent.getStringExtra(
                                Player.PLAY_QUEUE_KEY);
                        final PlayQueue playQueue = intentCacheKey != null
                                ? SerializedCache.getInstance()
                                .take(intentCacheKey, PlayQueue.class)
                                : null;

                        final boolean switchingPlayers = intent.getBooleanExtra(
                                VideoDetailFragment.KEY_SWITCHING_PLAYERS, false);
                        NavigationHelper.openVideoDetailFragment(
                                getApplicationContext(), getSupportFragmentManager(),
                                serviceId, url, title, playQueue, switchingPlayers);
                        break;
                    case CHANNEL:
                        NavigationHelper.openChannelFragment(getSupportFragmentManager(),
                                serviceId, url, title);
                        break;
                    case PLAYLIST:
                        NavigationHelper.openPlaylistFragment(getSupportFragmentManager(),
                                serviceId, url, title);
                        break;
                }
            } else if (intent.hasExtra(Constants.KEY_OPEN_SEARCH)) {
                String searchString = intent.getStringExtra(Constants.KEY_SEARCH_STRING);
                if (searchString == null) {
                    searchString = "";
                }
                final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
                NavigationHelper.openSearchFragment(
                        getSupportFragmentManager(),
                        serviceId,
                        searchString);

            } else {
                NavigationHelper.gotoMainFragment(getSupportFragmentManager());
            }
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Handling intent", e);
        }
    }

    private void openMiniPlayerIfMissing() {
        final Fragment fragmentPlayer = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder);
        if (fragmentPlayer == null) {
            // We still don't have a fragment attached to the activity. It can happen when a user
            // started popup or background players without opening a stream inside the fragment.
            // Adding it in a collapsed state (only mini player will be visible).
            NavigationHelper.showMiniPlayer(getSupportFragmentManager());
        }
    }

    private void openMiniPlayerUponPlayerStarted() {
        if (getIntent().getSerializableExtra(Constants.KEY_LINK_TYPE)
                == StreamingService.LinkType.STREAM) {
            // handleIntent() already takes care of opening video detail fragment
            // due to an intent containing a STREAM link
            return;
        }

        if (PlayerHolder.getInstance().isPlayerOpen()) {
            // if the player is already open, no need for a broadcast receiver
            openMiniPlayerIfMissing();
        } else {
            // listen for player start intent being sent around
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    if (Objects.equals(intent.getAction(),
                            VideoDetailFragment.ACTION_PLAYER_STARTED)
                            && PlayerHolder.getInstance().isPlayerOpen()) {
                        openMiniPlayerIfMissing();
                        // At this point the player is added 100%, we can unregister. Other actions
                        // are useless since the fragment will not be removed after that.
                        unregisterReceiver(broadcastReceiver);
                        broadcastReceiver = null;
                    }
                }
            };
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(VideoDetailFragment.ACTION_PLAYER_STARTED);
            ContextCompat.registerReceiver(this, broadcastReceiver, intentFilter,
                    ContextCompat.RECEIVER_EXPORTED);

            // If the PlayerHolder is not bound yet, but the service is running, try to bind to it.
            // Once the connection is established, the ACTION_PLAYER_STARTED will be sent.
            PlayerHolder.getInstance().tryBindIfNeeded(this);
        }
    }

    private void openDetailFragmentFromCommentReplies(
            @NonNull final FragmentManager fm,
            final boolean popBackStack
    ) {
        // obtain the name of the fragment under the replies fragment that's going to be popped
        @Nullable final String fragmentUnderEntryName;
        if (fm.getBackStackEntryCount() < 2) {
            fragmentUnderEntryName = null;
        } else {
            fragmentUnderEntryName = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 2)
                    .getName();
        }

        // the root comment is the comment for which the user opened the replies page
        @Nullable final CommentRepliesFragment repliesFragment =
                (CommentRepliesFragment) fm.findFragmentByTag(CommentRepliesFragment.TAG);
        @Nullable final CommentsInfoItem rootComment =
                repliesFragment == null ? null : repliesFragment.getCommentsInfoItem();

        // sometimes this function pops the backstack, other times it's handled by the system
        if (popBackStack) {
            fm.popBackStackImmediate();
        }

        // only expand the bottom sheet back if there are no more nested comment replies fragments
        // stacked under the one that is currently being popped
        if (CommentRepliesFragment.TAG.equals(fragmentUnderEntryName)) {
            return;
        }

        final BottomSheetBehavior<FragmentContainerView> behavior = BottomSheetBehavior
                .from(mainBinding.fragmentPlayerHolder);
        // do not return to the comment if the details fragment was closed
        if (behavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            return;
        }

        // scroll to the root comment once the bottom sheet expansion animation is finished
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull final View bottomSheet,
                                       final int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    final Fragment detailFragment = fm.findFragmentById(
                            R.id.fragment_player_holder);
                    if (detailFragment instanceof VideoDetailFragment && rootComment != null) {
                        // should always be the case
                        ((VideoDetailFragment) detailFragment).scrollToComment(rootComment);
                    }
                    behavior.removeBottomSheetCallback(this);
                }
            }

            @Override
            public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
                // not needed, listener is removed once the sheet is expanded
            }
        });

        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private boolean bottomSheetHiddenOrCollapsed() {
        final BottomSheetBehavior<FrameLayout> bottomSheetBehavior =
                BottomSheetBehavior.from(mainBinding.fragmentPlayerHolder);

        final int sheetState = bottomSheetBehavior.getState();
        return sheetState == BottomSheetBehavior.STATE_HIDDEN
                || sheetState == BottomSheetBehavior.STATE_COLLAPSED;
    }

    private void showKeepAndroidDialog() {
        final var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final var lastCheckKey = getString(R.string.kao_last_checked_key);
        final var lastCheck = Instant.ofEpochMilli(prefs.getLong(lastCheckKey, 0));
        final var now = Instant.now();

        if (lastCheck.plus(30, ChronoUnit.DAYS).isBefore(now)) {
            final String detailsUrl = getKeepAndroidOpenDetailsUrl();

            final var dialog = new AlertDialog.Builder(this)
                    .setTitle("Keep Android Open")
                    .setCancelable(false)
                    .setMessage(R.string.kao_dialog_warning)
                    .setPositiveButton(android.R.string.ok, (d, w) -> prefs.edit()
                            .putLong(lastCheckKey, now.toEpochMilli())
                            .apply())
                    .setNeutralButton(R.string.kao_dialog_more_info, null)
                    .show();

            // If we use setNeutralButton/setNegativeButton, dialog will close after pressing the
            // buttons, but we want it to close only when positive button is pressed
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(v -> ShareUtils.openUrlInBrowser(this, detailsUrl));
        }
    }

    @NonNull
    private static String getKeepAndroidOpenDetailsUrl() {
        final var supportedLanguages = List.of("fr", "de", "ca", "es", "id", "it", "pl",
                "pt", "cs", "sk", "fa", "ar", "tr", "el", "th", "ru", "uk", "ko", "zh", "ja");
        final String kaoBaseUrl = "https://keepandroidopen.org/";
        final var locale = Localization.getAppLocale();
        if (supportedLanguages.contains(locale.getLanguage())) {
            if ("zh".equals(locale.getLanguage())) {
                return kaoBaseUrl + ("TW".equals(locale.getCountry()) ? "zh-TW" : "zh-CN");
            } else {
                return kaoBaseUrl + locale.getLanguage();
            }
        } else {
            return kaoBaseUrl;
        }
    }

    private void showApi23RequirementDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return; // only show dialog on the devices that will stop being supported
        }

        final var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final var shownKey = getString(R.string.api23_requirement_dialog_shown_key);
        if (prefs.getBoolean(shownKey, false)) {
            return; // dialog was already shown in the past, no need to show it again
        }

        final var dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.api23_requirement_dialog_title)
                .setCancelable(false)
                .setMessage(R.string.api23_requirement_dialog_message)
                .setPositiveButton(android.R.string.ok, (d, w) -> prefs.edit()
                        .putBoolean(shownKey, true)
                        .apply())
                .setNegativeButton(R.string.api23_requirement_dialog_blogpost, null)
                .show();

        // If we use setNegativeButton, dialog will close after pressing the button,
        // but we want it to close only when positive button is pressed
        final var blogpostUrl = "https://newpipe.net/blog/pinned/announcement/drop-android-5/";
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setOnClickListener(v -> ShareUtils.openUrlInBrowser(this, blogpostUrl));
    }

    private int getStatusBarHeight() {
        final int resourceId = getResources().getIdentifier("status_bar_height",
                "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public void runVideoOpenTransition() {
        if (!VideoTransitionManager.hasSource()) {
            return;
        }
        final Rect source = VideoTransitionManager.getSourceRect();
        final Drawable drawable = VideoTransitionManager.getThumbnailDrawable();
        if (source == null) {
            return;
        }

        final View overlay = mainBinding.videoTransitionOverlay;
        final View bg = mainBinding.videoTransitionBg;
        final var card = mainBinding.videoTransitionCard;
        final ImageView thumb = mainBinding.videoTransitionThumbnail;
        final View skeleton = mainBinding.videoTransitionSkeleton;

        if (drawable != null) {
            thumb.setImageDrawable(drawable);
        }

        final int screenWidth = getResources().getDisplayMetrics().widthPixels;
        final int targetPlayerHeight = (int) (screenWidth * 9f / 16f);
        final int statusBarHeight = getStatusBarHeight();

        final int startWidth = source.width();
        final int startHeight = source.height();
        final float startRadius = 12 * getResources().getDisplayMetrics().density;

        card.getLayoutParams().width = startWidth;
        card.getLayoutParams().height = startHeight;
        card.requestLayout();
        card.setTranslationX(source.left);
        card.setTranslationY(source.top);
        card.setRadius(startRadius);

        bg.setAlpha(0f);
        skeleton.setAlpha(0f);
        skeleton.setVisibility(View.VISIBLE);
        overlay.setAlpha(1f);
        overlay.setVisibility(View.VISIBLE);

        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(280);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(animation -> {
            final float fraction = animation.getAnimatedFraction();
            final int curW = (int) (startWidth + (screenWidth - startWidth) * fraction);
            final int curH = (int) (startHeight + (targetPlayerHeight - startHeight) * fraction);
            final float curX = source.left * (1f - fraction);
            final float curY = source.top + (statusBarHeight - source.top) * fraction;
            final float curR = startRadius * (1f - fraction);

            card.getLayoutParams().width = curW;
            card.getLayoutParams().height = curH;
            card.requestLayout();
            card.setTranslationX(curX);
            card.setTranslationY(curY);
            card.setRadius(curR);

            bg.setAlpha(fraction);
            if (fraction > 0.3f) {
                skeleton.setAlpha((fraction - 0.3f) / 0.7f);
            }
            skeleton.setTranslationY(curY + curH
                    + (12 * getResources().getDisplayMetrics().density));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                overlay.animate()
                        .alpha(0f)
                        .setDuration(80)
                        .withEndAction(() -> {
                            overlay.setVisibility(View.GONE);
                            overlay.setAlpha(1f);
                        })
                        .start();
            }
        });
        animator.start();
    }

    public boolean runVideoCloseTransition(@Nullable final Runnable onEnd) {
        if (!VideoTransitionManager.hasSource()) {
            return false;
        }
        final Rect target = VideoTransitionManager.getSourceRect();
        final Drawable drawable = VideoTransitionManager.getThumbnailDrawable();
        if (target == null) {
            return false;
        }

        final View overlay = mainBinding.videoTransitionOverlay;
        final View bg = mainBinding.videoTransitionBg;
        final var card = mainBinding.videoTransitionCard;
        final ImageView thumb = mainBinding.videoTransitionThumbnail;
        final View skeleton = mainBinding.videoTransitionSkeleton;

        if (drawable != null) {
            thumb.setImageDrawable(drawable);
        }

        final int screenWidth = getResources().getDisplayMetrics().widthPixels;
        final int playerHeight = (int) (screenWidth * 9f / 16f);
        final int statusBarHeight = getStatusBarHeight();
        final float targetRadius = 12 * getResources().getDisplayMetrics().density;

        skeleton.setVisibility(View.GONE);
        bg.setAlpha(1f);
        overlay.setAlpha(1f);
        overlay.setVisibility(View.VISIBLE);

        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(240);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(animation -> {
            final float fraction = animation.getAnimatedFraction();
            final int curW = (int) (screenWidth + (target.width() - screenWidth) * fraction);
            final int curH = (int) (playerHeight + (target.height() - playerHeight) * fraction);
            final float curX = target.left * fraction;
            final float curY = statusBarHeight + (target.top - statusBarHeight) * fraction;
            final float curR = targetRadius * fraction;

            card.getLayoutParams().width = curW;
            card.getLayoutParams().height = curH;
            card.requestLayout();
            card.setTranslationX(curX);
            card.setTranslationY(curY);
            card.setRadius(curR);

            bg.setAlpha(1f - fraction);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                overlay.setVisibility(View.GONE);
                VideoTransitionManager.clearSource();
                if (onEnd != null) {
                    onEnd.run();
                }
            }
        });
        animator.start();
        return true;
    }
}
