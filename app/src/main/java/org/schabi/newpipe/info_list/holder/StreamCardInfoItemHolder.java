package org.schabi.newpipe.info_list.holder;

import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.image.LetterAvatarGenerator;

/**
 * Card layout for stream.
 */
public class StreamCardInfoItemHolder extends StreamInfoItemHolder {
    private final ImageView itemUploaderAvatarView;

    public StreamCardInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                    final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_card_item, parent);
        itemUploaderAvatarView = itemView.findViewById(R.id.itemUploaderAvatarView);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        if (itemUploaderAvatarView != null) {
            String avatarUrl = null;
            if (item.getUploaderAvatars() != null && !item.getUploaderAvatars().isEmpty()) {
                avatarUrl = ImageStrategy.choosePreferredImage(item.getUploaderAvatars());
                if (avatarUrl != null) {
                    if (item.getUploaderUrl() != null) {
                        CoilHelper.INSTANCE.cacheAvatarUrl(item.getUploaderUrl(), avatarUrl);
                    }
                    if (item.getUploaderName() != null) {
                        CoilHelper.INSTANCE.cacheAvatarUrl(item.getUploaderName(), avatarUrl);
                    }
                }
            } else {
                if (item.getUploaderUrl() != null) {
                    avatarUrl = CoilHelper.INSTANCE.getCachedAvatarUrl(item.getUploaderUrl());
                }
                if (avatarUrl == null && item.getUploaderName() != null) {
                    avatarUrl = CoilHelper.INSTANCE.getCachedAvatarUrl(item.getUploaderName());
                }
            }

            if (avatarUrl != null) {
                CoilHelper.INSTANCE.loadAvatar(itemUploaderAvatarView, avatarUrl);
            } else {
                final int sizePx = (int) (48 * itemUploaderAvatarView.getResources()
                        .getDisplayMetrics().density);
                final Drawable letterAvatar = LetterAvatarGenerator.getLetterAvatar(
                        itemUploaderAvatarView.getResources(),
                        item.getUploaderName(),
                        sizePx);
                itemUploaderAvatarView.setImageDrawable(letterAvatar);
            }

            itemUploaderAvatarView.setOnClickListener(v -> {
                if (item.getUploaderUrl() != null
                        && itemBuilder.getContext() instanceof AppCompatActivity) {
                    final AppCompatActivity activity = (AppCompatActivity) itemBuilder.getContext();
                    try {
                        NavigationHelper.openChannelFragment(activity.getSupportFragmentManager(),
                                item.getServiceId(), item.getUploaderUrl(), item.getUploaderName());
                    } catch (final Exception ignored) {
                        // ignore if navigation fails
                    }
                }
            });
        }
    }

    @Override
    protected String getDetailLinePrefix() {
        return "• ";
    }
}
