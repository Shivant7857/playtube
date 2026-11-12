package org.schabi.newpipe.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;

public class SettingsTabFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final View header = view.findViewById(R.id.settings_header);
        if (header != null) {
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, windowInsets) -> {
                final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(0, insets.top, 0, 0);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(header);
        }

        final View searchPill = view.findViewById(R.id.settings_header_search_pill);
        if (searchPill != null) {
            searchPill.setOnClickListener(v -> {
                final Intent intent = new Intent(requireContext(), SettingsActivity.class);
                intent.putExtra("search_active", true);
                startActivity(intent);
            });
        }

        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.settings_tab_content, new MainSettingsFragment())
                    .commit();
        }
    }
}
