package org.schabi.newpipe.fragments;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.OneShotPreDrawListener;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentHomeBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class HomeFragment extends BaseFragment {
    public static final String KIOSK_WHATS_NEW = "whats_new";
    private static final String KEY_SELECTED_INDEX = "home_selected_index";
    private static final String KEY_SERVICE_ID = "home_service_id";
    private static final int INDEX_ALL = 0;
    private static final List<String> YOUTUBE_KIOSK_ORDER = List.of(
            "trending_gaming",
            "trending_music",
            "trending_movies_and_shows",
            "trending_podcasts_episodes",
            "Trending",
            "live",
            KIOSK_WHATS_NEW);

    private FragmentHomeBinding binding;
    private final List<String> kioskIds = new ArrayList<>();
    private int serviceId = -1;
    private int selectedIndex = INDEX_ALL;
    private boolean updatingChips;
    private ValueAnimator chipScrollAnimator;
    private boolean areChipsHidden = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        serviceId = ServiceHelper.getSelectedServiceId(requireContext());
        buildChips();

        final int savedServiceId = savedInstanceState == null ? serviceId
                : savedInstanceState.getInt(KEY_SERVICE_ID, serviceId);
        selectedIndex = savedInstanceState == null ? INDEX_ALL
                : savedInstanceState.getInt(KEY_SELECTED_INDEX, INDEX_ALL);
        selectedIndex = Math.max(INDEX_ALL,
                Math.min(selectedIndex, binding.homeChipGroup.getChildCount() - 1));

        // A restored child only matches the chips when the service didn't change
        final boolean canReuseContent = savedServiceId == serviceId
                && getChildFragmentManager().findFragmentById(R.id.home_content) != null;
        if (canReuseContent) {
            final Fragment child = getChildFragmentManager().findFragmentById(R.id.home_content);
            if (child instanceof BaseFragment) {
                ((BaseFragment) child).useAsFrontPage(true);
            }
            checkChip(selectedIndex);
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).ensureMainToolbarState();
            }
        } else {
            showCategory(selectedIndex);
        }

        if (savedInstanceState == null && binding.homeChipSkeleton != null) {
            binding.homeChipSkeleton.setVisibility(View.VISIBLE);
            binding.homeChipSkeleton.startShimmer();
            binding.homeChipScroll.setAlpha(0f);
            binding.homeChipScroll.postDelayed(() -> {
                if (binding == null) {
                    return;
                }
                binding.homeChipScroll.animate()
                        .alpha(1f)
                        .setDuration(250)
                        .start();
                binding.homeChipSkeleton.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction(() -> {
                            if (binding != null) {
                                binding.homeChipSkeleton.stopShimmer();
                                binding.homeChipSkeleton.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }, 750);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_INDEX, selectedIndex);
        outState.putInt(KEY_SERVICE_ID, serviceId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chipScrollAnimator != null) {
            chipScrollAnimator.cancel();
            chipScrollAnimator = null;
        }
        binding = null;
    }

    public void setChipsHidden(final boolean hide) {
        if (binding == null || binding.homeChipScroll == null) {
            return;
        }
        if (areChipsHidden == hide) {
            return;
        }
        areChipsHidden = hide;

        final int chipHeight = binding.homeChipScroll.getHeight();
        if (chipHeight <= 0) {
            return;
        }

        final ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) binding.homeChipScroll.getLayoutParams();
        final int startMargin = lp.topMargin;
        final int endMargin = hide ? -chipHeight : 0;

        if (chipScrollAnimator != null && chipScrollAnimator.isRunning()) {
            chipScrollAnimator.cancel();
        }

        chipScrollAnimator = ValueAnimator.ofInt(startMargin, endMargin);
        chipScrollAnimator.setDuration(200);
        chipScrollAnimator.setInterpolator(new DecelerateInterpolator());
        chipScrollAnimator.addUpdateListener(animation -> {
            if (binding != null && binding.homeChipScroll != null) {
                final ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) binding.homeChipScroll
                                .getLayoutParams();
                params.topMargin = (int) animation.getAnimatedValue();
                binding.homeChipScroll.setLayoutParams(params);
            }
        });
        chipScrollAnimator.start();
    }

    private void buildChips() {
        kioskIds.clear();
        kioskIds.addAll(getOrderedKioskIds());

        updatingChips = true;
        binding.homeChipGroup.removeAllViews();
        addChip(getString(R.string.home_chip_all));
        for (final String kioskId : kioskIds) {
            addChip(getChipLabel(kioskId));
        }
        updatingChips = false;

        binding.homeChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (updatingChips || checkedIds.isEmpty()) {
                return;
            }
            final int index = group.indexOfChild(group.findViewById(checkedIds.get(0)));
            if (index >= 0) {
                showCategory(index);
            }
        });
    }

    private String getChipLabel(final String kioskId) {
        switch (kioskId) {
            case "trending_gaming":
                return getString(R.string.trending_gaming);
            case "trending_music":
                return getString(R.string.trending_music);
            case "trending_movies_and_shows":
                return getString(R.string.trending_movies);
            case "trending_podcasts_episodes":
                return getString(R.string.trending_podcasts);
            case "Trending":
                return getString(R.string.trending);
            case "live":
                return getString(R.string.duration_live);
            case KIOSK_WHATS_NEW:
                return getString(R.string.fragment_feed_title);
            default:
                return KioskTranslator.getTranslatedKioskName(kioskId, requireContext());
        }
    }

    private List<String> getOrderedKioskIds() {
        final Set<String> available;
        try {
            available = NewPipe.getService(serviceId).getKioskList().getAvailableKiosks();
        } catch (final ExtractionException e) {
            ErrorUtil.showUiErrorSnackbar(this, "Loading home categories", e);
            return List.of();
        }

        final List<String> ordered = new ArrayList<>();
        if (serviceId == ServiceList.YouTube.getServiceId()) {
            for (final String kioskId : YOUTUBE_KIOSK_ORDER) {
                if (KIOSK_WHATS_NEW.equals(kioskId) || available.contains(kioskId)) {
                    ordered.add(kioskId);
                }
            }
        }
        final List<String> remaining = new ArrayList<>(available);
        remaining.removeAll(ordered);
        remaining.sort(Comparator.comparing(
                id -> KioskTranslator.getTranslatedKioskName(id, requireContext())));
        ordered.addAll(remaining);
        return ordered;
    }

    private void addChip(final String label) {
        final Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.chip_home, binding.homeChipGroup, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        binding.homeChipGroup.addView(chip);
    }

    private void checkChip(final int index) {
        if (binding == null || index < 0 || index >= binding.homeChipGroup.getChildCount()) {
            return;
        }
        updatingChips = true;
        final View child = binding.homeChipGroup.getChildAt(index);
        if (child instanceof Chip) {
            ((Chip) child).setChecked(true);
        }
        OneShotPreDrawListener.add(binding.homeChipScroll, () -> {
            if (binding != null && selectedIndex >= 0
                    && selectedIndex < binding.homeChipGroup.getChildCount()) {
                final View chip = binding.homeChipGroup.getChildAt(selectedIndex);
                if (chip != null) {
                    final int scrollX = chip.getLeft() - binding.homeChipScroll.getWidth() / 2
                            + chip.getWidth() / 2;
                    binding.homeChipScroll.smoothScrollTo(Math.max(0, scrollX), 0);
                }
            }
        });
        updatingChips = false;
    }

    private void showCategory(final int index) {
        setChipsHidden(false);
        selectedIndex = index;
        checkChip(index);

        if (activity instanceof MainActivity) {
            ((MainActivity) activity).ensureMainToolbarState();
        }

        final Fragment fragment = createFragment(index);
        if (fragment instanceof BaseFragment) {
            ((BaseFragment) fragment).useAsFrontPage(true);
        }
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.home_content, fragment)
                .commit();
    }

    private Fragment createFragment(final int index) {
        if (index == INDEX_ALL) {
            return new HomeFeedFragment();
        }
        final int kioskIndex = index - 1;
        if (kioskIndex < 0 || kioskIndex >= kioskIds.size()) {
            return new HomeFeedFragment();
        }
        final String kioskId = kioskIds.get(kioskIndex);
        if (KIOSK_WHATS_NEW.equals(kioskId)) {
            return new FeedFragment();
        }
        try {
            return KioskFragment.getInstance(serviceId, kioskId);
        } catch (final ExtractionException e) {
            return new BlankFragment(new ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading home category " + kioskId));
        }
    }
}
