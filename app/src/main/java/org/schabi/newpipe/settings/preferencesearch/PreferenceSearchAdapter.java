package org.schabi.newpipe.settings.preferencesearch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.SettingsPreferencesearchListItemResultBinding;

import java.util.function.Consumer;

class PreferenceSearchAdapter
        extends ListAdapter<PreferenceSearchItem, PreferenceSearchAdapter.PreferenceViewHolder> {
    private static final int CARD_GAP_DP = 6;

    private Consumer<PreferenceSearchItem> onItemClickListener;

    PreferenceSearchAdapter() {
        super(new PreferenceCallback());
    }

    @NonNull
    @Override
    public PreferenceViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
        return new PreferenceViewHolder(SettingsPreferencesearchListItemResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final PreferenceViewHolder holder, final int position) {
        final PreferenceSearchItem item = getItem(position);

        holder.binding.title.setText(item.getTitle());

        if (item.getSummary().isEmpty()) {
            holder.binding.summary.setVisibility(View.GONE);
        } else {
            holder.binding.summary.setVisibility(View.VISIBLE);
            holder.binding.summary.setText(item.getSummary());
        }

        if (item.getBreadcrumbs().isEmpty()) {
            holder.binding.breadcrumbs.setVisibility(View.GONE);
        } else {
            holder.binding.breadcrumbs.setVisibility(View.VISIBLE);
            holder.binding.breadcrumbs.setText(item.getBreadcrumbs());
        }

        groupIntoCards(holder, position, item);

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.accept(item);
            }
        });
    }

    /**
     * Merge results that belong to the same settings page into one rounded card, so the
     * flat result list reads like the rest of the redesigned settings screens.
     *
     * @param holder the view holding the result being bound
     * @param position the adapter position of the result
     * @param item the result being bound
     */
    private void groupIntoCards(@NonNull final PreferenceViewHolder holder,
                                final int position,
                                @NonNull final PreferenceSearchItem item) {
        final String breadcrumbs = item.getBreadcrumbs();
        final boolean joinsPrevious = position > 0
                && breadcrumbs.equals(getItem(position - 1).getBreadcrumbs());
        final boolean joinsNext = position + 1 < getItemCount()
                && breadcrumbs.equals(getItem(position + 1).getBreadcrumbs());

        final int background;
        if (joinsPrevious) {
            background = joinsNext ? R.drawable.bg_settings_card_middle
                    : R.drawable.bg_settings_card_bottom;
        } else {
            background = joinsNext ? R.drawable.bg_settings_card_top
                    : R.drawable.bg_settings_card_alone;
        }

        holder.binding.searchResultCard.setBackgroundResource(background);
        holder.binding.searchResultDivider
                .setVisibility(joinsPrevious ? View.VISIBLE : View.GONE);

        final ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            final float density = holder.itemView.getResources().getDisplayMetrics().density;
            ((ViewGroup.MarginLayoutParams) params).topMargin =
                    joinsPrevious ? 0 : Math.round(CARD_GAP_DP * density);
        }
    }

    void setOnItemClickListener(final Consumer<PreferenceSearchItem> onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    static class PreferenceViewHolder extends RecyclerView.ViewHolder {
        final SettingsPreferencesearchListItemResultBinding binding;

        PreferenceViewHolder(final SettingsPreferencesearchListItemResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class PreferenceCallback
            extends DiffUtil.ItemCallback<PreferenceSearchItem> {
        @Override
        public boolean areItemsTheSame(@NonNull final PreferenceSearchItem oldItem,
                                       @NonNull final PreferenceSearchItem newItem) {
            return oldItem.getKey().equals(newItem.getKey());
        }

        @Override
        public boolean areContentsTheSame(@NonNull final PreferenceSearchItem oldItem,
                                          @NonNull final PreferenceSearchItem newItem) {
            return oldItem.getAllRelevantSearchFields().equals(newItem
                    .getAllRelevantSearchFields());
        }
    }
}
