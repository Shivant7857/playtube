package org.schabi.newpipe.info_list.holder;

import static org.schabi.newpipe.util.ServiceHelper.getServiceById;
import static org.schabi.newpipe.util.text.TouchUtils.getOffsetForHorizontalLine;

import android.content.Context;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.util.image.ImageStrategy;
import org.schabi.newpipe.util.text.TextEllipsizer;
import org.schabi.newpipe.views.CommentConnectorView;

public class CommentInfoItemHolder extends InfoItemHolder {

    private static final int COMMENT_DEFAULT_LINES = 2;
    private final int commentHorizontalPadding;
    private final int commentVerticalPadding;

    private final View itemRoot;
    private final ImageView itemThumbnailView;
    private final TextView itemContentView;
    private final ImageView itemThumbsUpView;
    private final TextView itemLikesCountView;
    private final TextView itemTitleView;
    private final ImageView itemHeartView;
    private final ImageView itemPinnedView;
    private final TextView repliesButton;
    private final CommentConnectorView commentConnectorView;
    private final View interactionRowContainer;
    private final TextView repliesBullet;
    private final ImageView itemOverflowMenu;

    @NonNull
    private final TextEllipsizer textEllipsizer;

    public CommentInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                 final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_comment_item, parent);

        itemRoot = itemView.findViewById(R.id.itemRoot);
        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemContentView = itemView.findViewById(R.id.itemCommentContentView);
        itemThumbsUpView = itemView.findViewById(R.id.detail_thumbs_up_img_view);
        itemLikesCountView = itemView.findViewById(R.id.detail_thumbs_up_count_view);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemHeartView = itemView.findViewById(R.id.detail_heart_image_view);
        itemPinnedView = itemView.findViewById(R.id.detail_pinned_view);
        repliesButton = itemView.findViewById(R.id.replies_button);
        commentConnectorView = itemView.findViewById(R.id.comment_connector_view);
        interactionRowContainer = itemView.findViewById(R.id.interaction_row_container);
        repliesBullet = itemView.findViewById(R.id.replies_bullet);
        itemOverflowMenu = itemView.findViewById(R.id.itemOverflowMenu);

        commentHorizontalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_horizontal_padding);
        commentVerticalPadding = (int) infoItemBuilder.getContext()
                .getResources().getDimension(R.dimen.comments_vertical_padding);

        textEllipsizer = new TextEllipsizer(itemContentView, COMMENT_DEFAULT_LINES, null);
        textEllipsizer.setStateChangeListener(isEllipsized -> {
            if (Boolean.TRUE.equals(isEllipsized)) {
                denyLinkFocus();
            } else {
                determineMovementMethod();
            }
        });
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof CommentsInfoItem item)) {
            return;
        }

        // load the author avatar
        CoilHelper.INSTANCE.loadAvatar(itemThumbnailView, item.getUploaderAvatars());
        if (ImageStrategy.shouldLoadImages()) {
            itemThumbnailView.setVisibility(View.VISIBLE);
            itemRoot.setPadding(commentVerticalPadding, commentVerticalPadding,
                    commentVerticalPadding, commentVerticalPadding);
        } else {
            itemThumbnailView.setVisibility(View.GONE);
            itemRoot.setPadding(commentHorizontalPadding, commentVerticalPadding,
                    commentHorizontalPadding, commentVerticalPadding);
        }
        itemThumbnailView.setOnClickListener(view -> openCommentAuthor(item));

        // setup the top row, with pinned icon, author name and comment date
        itemPinnedView.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
        final String uploaderName = Localization.localizeUserName(item.getUploaderName());
        itemTitleView.setText(Localization.concatenateStrings(
                uploaderName,
                Localization.relativeTimeOrTextual(
                        itemBuilder.getContext(),
                        item.getUploadDate(),
                        item.getTextualUploadDate())));

        // setup bottom row, with likes, heart and replies button
        if (item.getLikeCount() > 0) {
            itemLikesCountView.setVisibility(View.VISIBLE);
            itemLikesCountView.setText(
                    Localization.likeCount(itemBuilder.getContext(), item.getLikeCount()));
        } else {
            itemLikesCountView.setVisibility(View.GONE);
        }

        itemHeartView.setVisibility(item.isHeartedByUploader() ? View.VISIBLE : View.GONE);

        final boolean hasReplies = item.getReplies() != null && item.getReplyCount() > 0;
        repliesButton.setOnClickListener(hasReplies ? v -> openCommentReplies(item) : null);
        repliesButton.setVisibility(hasReplies ? View.VISIBLE : View.GONE);
        if (commentConnectorView != null) {
            commentConnectorView.setVisibility(hasReplies ? View.VISIBLE : View.GONE);
        }
        if (repliesBullet != null) {
            repliesBullet.setVisibility(hasReplies ? View.VISIBLE : View.GONE);
        }
        if (interactionRowContainer != null) {
            interactionRowContainer.setPadding(hasReplies ? 0
                    : DeviceUtils.dpToPx(44, itemBuilder.getContext()), 0, 0, 0);
        }
        repliesButton.setText(hasReplies
                ? Localization.replyCount(itemBuilder.getContext(), item.getReplyCount()) : "");

        if (itemOverflowMenu != null) {
            itemOverflowMenu.setOnClickListener(v -> {
                final Context context = itemBuilder.getContext();
                final PopupMenu popup = new PopupMenu(context, itemOverflowMenu);
                popup.getMenu().add(0, 1, 0, R.string.share);
                popup.getMenu().add(0, 2, 1, R.string.copy_text);
                popup.setOnMenuItemClickListener(menuItem -> {
                    if (menuItem.getItemId() == 1) {
                        ShareUtils.shareText(context, item.getUploaderName(), item.getUrl());
                        return true;
                    } else if (menuItem.getItemId() == 2) {
                        final CharSequence text = itemContentView.getText();
                        if (text != null) {
                            ShareUtils.copyToClipboard(context, text.toString());
                        }
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }


        // setup comment content and click listeners to expand/ellipsize it
        textEllipsizer.setStreamingService(getServiceById(item.getServiceId()));
        textEllipsizer.setStreamUrl(item.getUrl());
        textEllipsizer.setContent(item.getCommentText());
        textEllipsizer.ellipsize();

        //noinspection ClickableViewAccessibility
        itemContentView.setOnTouchListener((v, event) -> {
            final CharSequence text = itemContentView.getText();
            if (text instanceof Spanned buffer) {
                final int action = event.getAction();

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
                    final int offset = getOffsetForHorizontalLine(itemContentView, event);
                    final var links = buffer.getSpans(offset, offset, ClickableSpan.class);

                    if (links.length != 0) {
                        if (action == MotionEvent.ACTION_UP) {
                            links[0].onClick(itemContentView);
                        }
                        // we handle events that intersect links, so return true
                        return true;
                    }
                }
            }
            return false;
        });

        itemView.setOnClickListener(view -> {
            textEllipsizer.toggle();
            if (itemBuilder.getOnCommentsSelectedListener() != null) {
                itemBuilder.getOnCommentsSelectedListener().selected(item);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (DeviceUtils.isTv(itemBuilder.getContext())) {
                openCommentAuthor(item);
            } else {
                final CharSequence text = itemContentView.getText();
                if (text != null) {
                    ShareUtils.copyToClipboard(itemBuilder.getContext(), text.toString());
                }
            }
            return true;
        });
    }

    private void openCommentAuthor(@NonNull final CommentsInfoItem item) {
        NavigationHelper.openCommentAuthorIfPresent((FragmentActivity) itemBuilder.getContext(),
                item);
    }

    private void openCommentReplies(@NonNull final CommentsInfoItem item) {
        NavigationHelper.openCommentRepliesFragment((FragmentActivity) itemBuilder.getContext(),
                item);
    }

    private void allowLinkFocus() {
        itemContentView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void denyLinkFocus() {
        itemContentView.setMovementMethod(null);
    }

    private boolean shouldFocusLinks() {
        if (itemView.isInTouchMode()) {
            return false;
        }

        final URLSpan[] urls = itemContentView.getUrls();

        return urls != null && urls.length != 0;
    }

    private void determineMovementMethod() {
        if (shouldFocusLinks()) {
            allowLinkFocus();
        } else {
            denyLinkFocus();
        }
    }
}
