package org.schabi.newpipe.info_list.holder;

import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.Localization;

/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p?
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class StreamInfoItemHolder extends StreamMiniInfoItemHolder {
    public final TextView itemAdditionalDetails;

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_item, parent);
    }

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                                final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        itemAdditionalDetails.setText(getStreamInfoDetailLine(item));
    }

    /**
     * Detail line prefix for positioning alongside the uploader name.
     *
     * @return prefix string
     */
    protected String getDetailLinePrefix() {
        return "";
    }

    private String getStreamInfoDetailLine(final StreamInfoItem infoItem) {
        String viewsAndDate = "";
        if (infoItem.getViewCount() >= 0) {
            if (infoItem.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                viewsAndDate = "▶ " + Localization
                        .shortCount(itemBuilder.getContext(), infoItem.getViewCount());
            } else if (infoItem.getStreamType().equals(StreamType.LIVE_STREAM)) {
                viewsAndDate = "● " + Localization
                        .shortWatchingCount(itemBuilder.getContext(), infoItem.getViewCount());
            } else {
                viewsAndDate = "▶ " + Localization
                        .shortCount(itemBuilder.getContext(), infoItem.getViewCount());
            }
        }

        final String rawDate = Localization.relativeTimeOrTextual(itemBuilder.getContext(),
                infoItem.getUploadDate(),
                infoItem.getTextualUploadDate());
        final String uploadDate = formatShortTime(rawDate);
        if (!TextUtils.isEmpty(uploadDate)) {
            if (viewsAndDate.isEmpty()) {
                return getDetailLinePrefix() + uploadDate;
            }

            return getDetailLinePrefix()
                    + Localization.concatenateStrings(viewsAndDate, uploadDate);
        }

        return viewsAndDate.isEmpty() ? "" : (getDetailLinePrefix() + viewsAndDate);
    }

    private String formatShortTime(final String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?i)\\bhours?\\b", "hr")
                .replaceAll("(?i)\\bminutes?\\b", "min")
                .replaceAll("(?i)\\bseconds?\\b", "sec")
                .replaceAll("(?i)\\bweeks?\\b", "wk")
                .replaceAll("(?i)\\bmonths?\\b", "mo")
                .replaceAll("(?i)\\byears?\\b", "yr")
                .trim();
    }
}
