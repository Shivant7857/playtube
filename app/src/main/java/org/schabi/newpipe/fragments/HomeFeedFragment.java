package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.stream.StreamWithState;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import io.reactivex.rxjava3.core.Single;

public class HomeFeedFragment extends BaseListInfoFragment<StreamInfoItem, KioskInfo> {

    private static final String CAT_WHATS_NEW = "whats_new";
    private static final String CAT_MUSIC = "trending_music";
    private static final String CAT_GAMING = "trending_gaming";
    private static final String CAT_MOVIES = "trending_movies_and_shows";
    private static final String CAT_LIVE = "live";
    private static final String CAT_PODCASTS = "trending_podcasts_episodes";

    private static final int BLOCK_SIZE = 20;
    private static final int TARGET_WHATS_NEW = 12; // 60%
    private static final int TARGET_MOVIES = 3;    // 15%
    private static final int TARGET_MUSIC = 2;     // 10%
    private static final int TARGET_GAMING = 1;    // 5%
    private static final int TARGET_LIVE = 1;      // 5%
    private static final int TARGET_PODCASTS = 1;  // 5%

    private final Map<String, List<StreamInfoItem>> categoryPools = new HashMap<>();
    private final Map<String, Page> categoryNextPages = new HashMap<>();
    private final Map<String, String> categoryUrls = new HashMap<>();
    private final Set<String> seenUrls = new HashSet<>();
    private final Random random = new Random();

    private int pageCounter = 1;
    private String previousCategory = null;
    private String previousUploader = null;

    public HomeFeedFragment() {
        super(UserAction.REQUESTED_KIOSK);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        name = getString(R.string.home_chip_all);
        serviceId = ServiceHelper.getSelectedServiceId(requireContext());
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).ensureMainToolbarState();
        }
    }

    @Override
    public void setTitle(final String title) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).ensureMainToolbarState();
        }
    }

    @Override
    public void handleResult(@NonNull final KioskInfo result) {
        super.handleResult(result);
        name = getString(R.string.home_chip_all);
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).ensureMainToolbarState();
        }
    }

    @Override
    protected Single<KioskInfo> loadResult(final boolean forceReload) {
        return Single.fromCallable(() -> {
            serviceId = ServiceHelper.getSelectedServiceId(requireContext());
            final StreamingService service = NewPipe.getService(serviceId);
            final Set<String> available = service.getKioskList().getAvailableKiosks();

            categoryPools.clear();
            categoryNextPages.clear();
            categoryUrls.clear();
            seenUrls.clear();
            previousCategory = null;
            previousUploader = null;
            pageCounter = 1;

            for (final String cat : List.of(CAT_WHATS_NEW, CAT_MUSIC, CAT_GAMING,
                    CAT_MOVIES, CAT_LIVE, CAT_PODCASTS)) {
                categoryPools.put(cat, new ArrayList<>());
            }

            // 1. Fetch What's New from database (subscribed channels)
            try {
                final FeedDatabaseManager dbManager =
                        new FeedDatabaseManager(requireContext());
                final List<StreamWithState> dbStreams = dbManager
                        .getStreams(FeedGroupEntity.GROUP_ALL_ID, true, true, false)
                        .blockingGet(Collections.emptyList());
                if (dbStreams != null) {
                    for (final StreamWithState s : dbStreams) {
                        if (s != null && s.getStream() != null) {
                            addDeduplicated(CAT_WHATS_NEW, s.getStream().toStreamInfoItem());
                        }
                    }
                }
            } catch (final Throwable ignored) {
                // Ignore DB error and fallback to network content
            }

            // 2. Fetch Kiosks in parallel / sequentially on worker thread
            final List<String> kiosksToFetch = new ArrayList<>();
            // If DB subscriptions gave fewer than 24 items, supplement What's New with Trending
            if (categoryPools.get(CAT_WHATS_NEW).size() < 24 && available.contains("Trending")) {
                kiosksToFetch.add("Trending");
            }
            if (available.contains(CAT_MUSIC)) {
                kiosksToFetch.add(CAT_MUSIC);
            }
            if (available.contains(CAT_GAMING)) {
                kiosksToFetch.add(CAT_GAMING);
            }
            if (available.contains(CAT_MOVIES)) {
                kiosksToFetch.add(CAT_MOVIES);
            }
            if (available.contains(CAT_LIVE)) {
                kiosksToFetch.add(CAT_LIVE);
            }
            if (available.contains(CAT_PODCASTS)) {
                kiosksToFetch.add(CAT_PODCASTS);
            }

            KioskInfo baseKioskInfo = null;

            for (final String kioskId : kiosksToFetch) {
                try {
                    final ListLinkHandlerFactory factory = service.getKioskList()
                            .getListLinkHandlerFactoryByType(kioskId);
                    final String kioskUrl = factory.fromId(kioskId).getUrl();
                    categoryUrls.put(kioskId, kioskUrl);

                    final KioskInfo info = ExtractorHelper
                            .getKioskInfo(serviceId, kioskUrl, forceReload)
                            .blockingGet();

                    if (info != null) {
                        if (baseKioskInfo == null) {
                            baseKioskInfo = info;
                        }
                        if (info.getNextPage() != null) {
                            categoryNextPages.put(kioskId, info.getNextPage());
                        }
                        final String targetCategory = "Trending".equals(kioskId)
                                ? CAT_WHATS_NEW : kioskId;
                        for (final StreamInfoItem item : info.getRelatedItems()) {
                            if (item != null) {
                                // For Trending supplemented into What's New, skip live streams
                                if ("Trending".equals(kioskId)
                                        && item.getStreamType() == StreamType.LIVE_STREAM) {
                                    continue;
                                }
                                addDeduplicated(targetCategory, item);
                            }
                        }
                    }
                } catch (final Throwable ignored) {
                    // Failures in individual kiosks do not break the whole feed
                }
            }

            // 3. Interleave first block of 20 items
            final List<StreamInfoItem> initialBlock = generateInterleavedBlock();

            // 4. Wrap into result KioskInfo
            if (baseKioskInfo == null) {
                final String fallbackUrl = service.getKioskList()
                        .getListLinkHandlerFactoryByType("Trending").fromId("Trending").getUrl();
                baseKioskInfo = ExtractorHelper.getKioskInfo(serviceId, fallbackUrl, false)
                        .blockingGet();
            }

            baseKioskInfo.setRelatedItems(initialBlock);
            baseKioskInfo.setNextPage(new Page("home_feed_page_" + pageCounter));
            return baseKioskInfo;
        });
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        return Single.fromCallable(() -> {
            serviceId = ServiceHelper.getSelectedServiceId(requireContext());
            final StreamingService service = NewPipe.getService(serviceId);

            // Refill pools if low
            for (final Map.Entry<String, Page> entry : categoryNextPages.entrySet()) {
                final String cat = entry.getKey();
                final Page page = entry.getValue();
                final String targetPool = "Trending".equals(cat) ? CAT_WHATS_NEW : cat;
                final List<StreamInfoItem> pool = categoryPools.get(targetPool);

                if (pool != null && pool.size() < 10 && page != null) {
                    try {
                        final String kioskUrl = categoryUrls.get(cat);
                        if (!TextUtils.isEmpty(kioskUrl)) {
                            final ListExtractor.InfoItemsPage<StreamInfoItem> nextPageItems =
                                    ExtractorHelper.getMoreKioskItems(serviceId, kioskUrl, page)
                                            .blockingGet();
                            if (nextPageItems != null) {
                                categoryNextPages.put(cat, nextPageItems.getNextPage());
                                for (final StreamInfoItem item : nextPageItems.getItems()) {
                                    if (item != null) {
                                        if ("Trending".equals(cat) && item.getStreamType()
                                                == StreamType.LIVE_STREAM) {
                                            continue;
                                        }
                                        addDeduplicated(targetPool, item);
                                    }
                                }
                            }
                        }
                    } catch (final Throwable ignored) {
                        // Ignore failure to paginate individual kiosk
                    }
                }
            }

            final List<StreamInfoItem> nextBlock = generateInterleavedBlock();
            final Page token = nextBlock.isEmpty() ? null
                    : new Page("home_feed_page_" + (++pageCounter));
            return new ListExtractor.InfoItemsPage<>(nextBlock, token, Collections.emptyList());
        });
    }

    private void addDeduplicated(final String category, final StreamInfoItem item) {
        if (item == null || TextUtils.isEmpty(item.getUrl())) {
            return;
        }
        if (seenUrls.add(item.getUrl())) {
            final List<StreamInfoItem> pool = categoryPools.get(category);
            if (pool != null) {
                pool.add(item);
            }
        }
    }

    /**
     * Interleaves items using the weighted distribution:
     * What's New: 60% (12), Movies & Shows: 15% (3), Music: 10% (2),
     * Gaming: 5% (1), Live: 5% (1), Podcasts: 5% (1).
     *
     * @return the list of interleaved stream items
     */
    private List<StreamInfoItem> generateInterleavedBlock() {
        final List<StreamInfoItem> block = new ArrayList<>();
        final Map<String, Integer> targets = new HashMap<>();
        targets.put(CAT_WHATS_NEW, TARGET_WHATS_NEW);
        targets.put(CAT_MOVIES, TARGET_MOVIES);
        targets.put(CAT_MUSIC, TARGET_MUSIC);
        targets.put(CAT_GAMING, TARGET_GAMING);
        targets.put(CAT_LIVE, TARGET_LIVE);
        targets.put(CAT_PODCASTS, TARGET_PODCASTS);

        for (int i = 0; i < BLOCK_SIZE; i++) {
            final double targetWn = (i + 1) * (double) TARGET_WHATS_NEW / BLOCK_SIZE;
            final int currentWnCount = TARGET_WHATS_NEW - targets.getOrDefault(CAT_WHATS_NEW, 0);

            final boolean preferWn = (currentWnCount < targetWn)
                    && (targets.getOrDefault(CAT_WHATS_NEW, 0) > 0)
                    && hasItems(CAT_WHATS_NEW);

            final List<String> eligible = new ArrayList<>();
            for (final Map.Entry<String, Integer> entry : targets.entrySet()) {
                if (entry.getValue() > 0 && hasItems(entry.getKey())) {
                    eligible.add(entry.getKey());
                }
            }

            if (eligible.isEmpty()) {
                // Fallback to any category with remaining items
                for (final Map.Entry<String, List<StreamInfoItem>> entry
                        : categoryPools.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        eligible.add(entry.getKey());
                    }
                }
            }

            if (eligible.isEmpty()) {
                break;
            }

            List<String> candidates = new ArrayList<>();
            if (preferWn && !CAT_WHATS_NEW.equals(previousCategory)
                    && eligible.contains(CAT_WHATS_NEW)) {
                candidates.add(CAT_WHATS_NEW);
            } else if (preferWn && eligible.contains(CAT_WHATS_NEW)) {
                for (final String cat : eligible) {
                    if (!CAT_WHATS_NEW.equals(cat)) {
                        candidates.add(cat);
                    }
                }
                if (candidates.isEmpty()) {
                    candidates.add(CAT_WHATS_NEW);
                }
            } else {
                for (final String cat : eligible) {
                    if (!CAT_WHATS_NEW.equals(cat) && !cat.equals(previousCategory)) {
                        candidates.add(cat);
                    }
                }
                if (candidates.isEmpty()) {
                    for (final String cat : eligible) {
                        if (!cat.equals(previousCategory)) {
                            candidates.add(cat);
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    candidates.addAll(eligible);
                }
            }

            // Prefer candidate with different uploader/channel
            final List<String> betterCandidates = new ArrayList<>();
            for (final String cat : candidates) {
                final List<StreamInfoItem> pool = categoryPools.get(cat);
                if (pool != null && !pool.isEmpty()) {
                    final StreamInfoItem top = pool.get(0);
                    if (top != null && !TextUtils.equals(top.getUploaderName(), previousUploader)) {
                        betterCandidates.add(cat);
                    }
                }
            }
            if (!betterCandidates.isEmpty()) {
                candidates = betterCandidates;
            }

            final String chosenCategory = candidates.get(random.nextInt(candidates.size()));
            final List<StreamInfoItem> chosenPool = categoryPools.get(chosenCategory);
            if (chosenPool != null && !chosenPool.isEmpty()) {
                final StreamInfoItem item = chosenPool.remove(0);
                block.add(item);
                previousCategory = chosenCategory;
                previousUploader = item.getUploaderName();
                targets.put(chosenCategory, Math.max(0,
                        targets.getOrDefault(chosenCategory, 1) - 1));
            }
        }

        return block;
    }

    private boolean hasItems(final String category) {
        final List<StreamInfoItem> pool = categoryPools.get(category);
        return pool != null && !pool.isEmpty();
    }
}
