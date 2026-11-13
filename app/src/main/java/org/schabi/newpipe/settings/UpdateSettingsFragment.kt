package org.schabi.newpipe.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.update.PlayTubeUpdateWorker
import org.schabi.newpipe.update.UpdateActivity
import org.schabi.newpipe.update.UpdateInfo
import org.schabi.newpipe.update.UpdateManager

class UpdateSettingsFragment : Fragment() {

    private var txtSystemUpdateStatus: TextView? = null
    private var progressSystemUpdate: ProgressBar? = null
    private var chevronSystemUpdate: ImageView? = null
    private var txtCurrentVersion: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_update_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide SettingsActivity header while this fragment is displayed
        val act = activity
        if (act is SettingsActivity) {
            act.findViewById<View>(R.id.settings_header)?.visibility = View.GONE
        }

        // Toolbar back button
        view.findViewById<View>(R.id.btn_back)?.setOnClickListener {
            requireActivity().onBackPressed()
        }

        // Handle insets on update_toolbar
        val toolbar = view.findViewById<View>(R.id.update_toolbar)
        if (toolbar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                v.setPadding(v.paddingLeft, statusBar.top, v.paddingRight, v.paddingBottom)
                insets
            }
            ViewCompat.requestApplyInsets(toolbar)
        }

        txtSystemUpdateStatus = view.findViewById(R.id.txt_system_update_status)
        progressSystemUpdate = view.findViewById(R.id.progress_system_update)
        chevronSystemUpdate = view.findViewById(R.id.chevron_system_update)
        txtCurrentVersion = view.findViewById(R.id.txt_current_version)

        refreshCurrentVersionText()
        refreshSystemUpdateStatus()

        // 1. System update row click
        view.findViewById<View>(R.id.row_system_update)?.setOnClickListener {
            val cached = UpdateManager.cachedUpdate
            if (cached != null) {
                openUpdateDetails(cached)
            } else {
                performManualUpdateCheck()
            }
        }

        // 3. Automatic update check toggle
        val switchAuto = view.findViewById<SwitchCompat>(R.id.switch_auto_check)
        switchAuto?.isChecked = UpdateManager.isAutoCheckEnabled(requireContext())
        switchAuto?.setOnCheckedChangeListener { _, isChecked ->
            UpdateManager.setAutoCheckEnabled(requireContext(), isChecked)
            if (isChecked) {
                PlayTubeUpdateWorker.schedule(requireContext())
            } else {
                PlayTubeUpdateWorker.cancel(requireContext())
            }
        }

        // 4. Update notifications toggle
        val switchNotif = view.findViewById<SwitchCompat>(R.id.switch_notifications)
        switchNotif?.isChecked = UpdateManager.isNotificationsEnabled(requireContext())
        switchNotif?.setOnCheckedChangeListener { _, isChecked ->
            UpdateManager.setNotificationsEnabled(requireContext(), isChecked)
        }

        // 5. Beta updates toggle
        val switchBeta = view.findViewById<SwitchCompat>(R.id.switch_beta)
        switchBeta?.isChecked = UpdateManager.isBetaEnabled(requireContext())
        switchBeta?.setOnCheckedChangeListener { _, isChecked ->
            UpdateManager.setBetaEnabled(requireContext(), isChecked)
            refreshCurrentVersionText()
            performManualUpdateCheck()
        }

        // 6. Clear downloaded updates row
        view.findViewById<View>(R.id.row_clear_updates)?.setOnClickListener {
            showClearUpdatesDialog()
        }

        // Section 2: Changelog
        view.findViewById<View>(R.id.row_changelog)?.setOnClickListener {
            showChangelogDialog()
        }

        // Section 2: Commits
        view.findViewById<View>(R.id.row_commits)?.setOnClickListener {
            showCommitsDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentVersionText()
        refreshSystemUpdateStatus()
    }

    override fun onDestroyView() {
        // Restore SettingsActivity header when leaving this fragment
        val act = activity
        if (act is SettingsActivity) {
            act.findViewById<View>(R.id.settings_header)?.visibility = View.VISIBLE
        }
        super.onDestroyView()
    }

    private fun refreshCurrentVersionText() {
        val isBeta = UpdateManager.isBetaEnabled(requireContext())
        val channelStr = if (isBeta) getString(R.string.beta_channel) else getString(R.string.stable_channel)
        txtCurrentVersion?.text = "PlayTube ${BuildConfig.VERSION_NAME} · $channelStr"
    }

    private fun refreshSystemUpdateStatus() {
        val cached = UpdateManager.cachedUpdate
        if (cached != null) {
            val vName = cached.versionName.trimStart('v', 'V')
            txtSystemUpdateStatus?.text = "Update available (v$vName)"
            txtSystemUpdateStatus?.setTextColor(Color.parseColor("#E50914"))
        } else {
            txtSystemUpdateStatus?.text = getString(R.string.up_to_date)
            txtSystemUpdateStatus?.setTextColor(Color.parseColor("#888888"))
        }
    }

    private fun performManualUpdateCheck() {
        progressSystemUpdate?.visibility = View.VISIBLE
        chevronSystemUpdate?.visibility = View.GONE
        txtSystemUpdateStatus?.text = getString(R.string.checking_for_updates)
        txtSystemUpdateStatus?.setTextColor(Color.parseColor("#888888"))

        UpdateManager.checkForUpdate(
            requireContext(),
            isManual = true,
            object : UpdateManager.UpdateCheckListener {
                override fun onStatusChanged(
                    status: UpdateManager.Status,
                    updateInfo: UpdateInfo?,
                    message: String?
                ) {
                    if (!isAdded) return

                    progressSystemUpdate?.visibility = View.GONE
                    chevronSystemUpdate?.visibility = View.VISIBLE

                    when (status) {
                        UpdateManager.Status.UPDATE_AVAILABLE -> {
                            refreshSystemUpdateStatus()
                            if (updateInfo != null) {
                                openUpdateDetails(updateInfo)
                            }
                        }

                        UpdateManager.Status.UP_TO_DATE -> {
                            refreshSystemUpdateStatus()
                            val ch = if (UpdateManager.isBetaEnabled(requireContext())) "Beta" else "Stable"
                            Toast.makeText(
                                requireContext(),
                                "You're up to date! PlayTube ${BuildConfig.VERSION_NAME} ($ch)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        UpdateManager.Status.FAILED -> {
                            refreshSystemUpdateStatus()
                            Toast.makeText(
                                requireContext(),
                                "Could not connect to update server. Please check your internet connection.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        else -> {}
                    }
                }
            }
        )
    }

    private fun openUpdateDetails(info: UpdateInfo) {
        val intent = Intent(requireContext(), UpdateActivity::class.java).apply {
            putExtra(UpdateActivity.EXTRA_UPDATE_INFO, info)
        }
        startActivity(intent)
    }

    private fun showClearUpdatesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_updates_dialog_title)
            .setMessage(R.string.clear_updates_dialog_msg)
            .setPositiveButton(R.string.clear_button) { d, _ ->
                d.dismiss()
                val cleared = UpdateManager.clearDownloadedUpdates(requireContext())
                if (cleared) {
                    Toast.makeText(
                        requireContext(),
                        R.string.updates_cleared_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showChangelogDialog() {
        val message = """
            PlayTube ${BuildConfig.VERSION_NAME} Highlights:

            • Full YouTube-style redesign of Video Description & Comments overlay pages
            • Dedicated Software Update Center with GitHub in-app downloader & updater
            • High-performance video streaming & smart background buffering
            • Seamless shared-element thumbnail transitions
            • Pure AMOLED dark mode optimizations & battery saving
            • Custom lock-free UI and enhanced privacy safeguards
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("PlayTube ${BuildConfig.VERSION_NAME} Changelog")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showCommitsDialog() {
        val loadingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Recent Commits")
            .setMessage("Loading recent changes from GitHub...")
            .setCancelable(true)
            .show()

        UpdateManager.fetchRecentCommits { commits ->
            if (!isAdded) return@fetchRecentCommits
            loadingDialog.dismiss()

            val text = if (commits.isEmpty()) {
                "No recent commits found or network unavailable."
            } else {
                commits.joinToString("\n\n") { "• $it" }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Recent Commits (GitHub)")
                .setMessage(text)
                .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
                .show()
        }
    }

    companion object {
        @JvmStatic
        fun askForConsentToUpdateChecks(context: Context) {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.check_for_updates))
                .setMessage(context.getString(R.string.auto_update_check_description))
                .setPositiveButton(context.getString(R.string.yes)) { d, _ ->
                    d.dismiss()
                    setAutoUpdateCheckEnabled(context, true)
                }
                .setNegativeButton(R.string.no) { d, _ ->
                    d.dismiss()
                    setAutoUpdateCheckEnabled(context, false)
                }
                .show()
        }

        @JvmStatic
        fun setAutoUpdateCheckEnabled(context: Context, enabled: Boolean) {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(context.getString(R.string.update_app_key), enabled)
                .putBoolean(context.getString(R.string.update_check_consent_key), true)
                .apply()
            UpdateManager.setAutoCheckEnabled(context, enabled)
        }

        @JvmStatic
        fun wasUserAskedForConsent(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.update_check_consent_key), false)
        }
    }
}
