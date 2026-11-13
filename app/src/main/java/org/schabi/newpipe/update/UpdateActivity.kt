package org.schabi.newpipe.update

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.Locale
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R

class UpdateActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_UPDATE_INFO = "extra_update_info"
    }

    private var updateInfo: UpdateInfo? = null
    private var isMandatory: Boolean = false
    private var isDownloading: Boolean = false

    // Views
    private lateinit var btnBack: ImageView
    private lateinit var layoutOptional: LinearLayout
    private lateinit var layoutDownloading: LinearLayout
    private lateinit var layoutMandatory: LinearLayout

    private lateinit var bottomOptional: LinearLayout
    private lateinit var bottomDownloading: LinearLayout
    private lateinit var bottomMandatory: LinearLayout

    // Optional Views
    private lateinit var txtOptionalTitle: TextView
    private lateinit var badgeOptionalChannel: TextView
    private lateinit var txtOptionalReleaseDate: TextView
    private lateinit var txtOptionalSize: TextView
    private lateinit var txtOptionalChangelog: TextView
    private lateinit var btnLater: AppCompatButton
    private lateinit var btnUpdate: AppCompatButton

    // Downloading Views
    private lateinit var txtDownloadStatus: TextView
    private lateinit var progressBarDownload: ProgressBar
    private lateinit var txtDownloadBytes: TextView
    private lateinit var txtDownloadEta: TextView
    private lateinit var step1Icon: ImageView
    private lateinit var step1Text: TextView
    private lateinit var step2Icon: ImageView
    private lateinit var step2Text: TextView
    private lateinit var step2Subtext: TextView
    private lateinit var step3Icon: ImageView
    private lateinit var step3Text: TextView
    private lateinit var step4Icon: ImageView
    private lateinit var step4Text: TextView
    private lateinit var btnCancelDownload: AppCompatButton

    // Mandatory Views
    private lateinit var txtMandatoryDesc: TextView
    private lateinit var badgeMandatoryVersion: TextView
    private lateinit var txtMandatoryChangelog: TextView
    private lateinit var btnUpdateNow: AppCompatButton

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_details)

        // Setup Black Status & Navigation Bars
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_UPDATE_INFO, UpdateInfo::class.java)
        } else {
            intent.getSerializableExtra(EXTRA_UPDATE_INFO) as? UpdateInfo
        } ?: UpdateManager.cachedUpdate

        if (info == null) {
            Toast.makeText(this, "No update information available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        updateInfo = info
        isMandatory = info.isMandatory(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)

        initViews()
        setupInsets()
        bindData(info)

        if (isMandatory) {
            showMandatoryMode()
        } else {
            showOptionalMode()
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_update_back)
        layoutOptional = findViewById(R.id.layout_optional_update)
        layoutDownloading = findViewById(R.id.layout_downloading_update)
        layoutMandatory = findViewById(R.id.layout_mandatory_update)

        bottomOptional = findViewById(R.id.bottom_optional_buttons)
        bottomDownloading = findViewById(R.id.bottom_downloading_buttons)
        bottomMandatory = findViewById(R.id.bottom_mandatory_buttons)

        // Optional
        txtOptionalTitle = findViewById(R.id.txt_optional_version_title)
        badgeOptionalChannel = findViewById(R.id.badge_optional_channel)
        txtOptionalReleaseDate = findViewById(R.id.txt_optional_release_date)
        txtOptionalSize = findViewById(R.id.txt_optional_size)
        txtOptionalChangelog = findViewById(R.id.txt_optional_changelog_body)
        btnLater = findViewById(R.id.btn_later)
        btnUpdate = findViewById(R.id.btn_update)

        // Downloading
        txtDownloadStatus = findViewById(R.id.txt_download_status_label)
        progressBarDownload = findViewById(R.id.progress_bar_download)
        txtDownloadBytes = findViewById(R.id.txt_download_bytes)
        txtDownloadEta = findViewById(R.id.txt_download_eta)
        step1Icon = findViewById(R.id.step1_icon)
        step1Text = findViewById(R.id.step1_text)
        step2Icon = findViewById(R.id.step2_icon)
        step2Text = findViewById(R.id.step2_text)
        step2Subtext = findViewById(R.id.step2_subtext)
        step3Icon = findViewById(R.id.step3_icon)
        step3Text = findViewById(R.id.step3_text)
        step4Icon = findViewById(R.id.step4_icon)
        step4Text = findViewById(R.id.step4_text)
        btnCancelDownload = findViewById(R.id.btn_cancel_download)

        // Mandatory
        txtMandatoryDesc = findViewById(R.id.txt_mandatory_desc)
        badgeMandatoryVersion = findViewById(R.id.badge_mandatory_version)
        txtMandatoryChangelog = findViewById(R.id.txt_mandatory_changelog_body)
        btnUpdateNow = findViewById(R.id.btn_update_now)

        btnBack.setOnClickListener {
            if (!isMandatory && !isDownloading) {
                finish()
            }
        }

        btnLater.setOnClickListener {
            if (!isMandatory) {
                finish()
            }
        }

        btnUpdate.setOnClickListener {
            startUpdateProcess()
        }

        btnUpdateNow.setOnClickListener {
            startUpdateProcess()
        }

        btnCancelDownload.setOnClickListener {
            cancelUpdateProcess()
        }
    }

    private fun setupInsets() {
        val toolbar = findViewById<View>(R.id.update_details_toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBar.top, v.paddingRight, v.paddingBottom)
            insets
        }

        val bottomBar = findViewById<View>(R.id.update_bottom_bar)
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBar.bottom + 16)
            insets
        }
    }

    private fun bindData(info: UpdateInfo) {
        val cleanVersion = info.versionName.trimStart('v', 'V')
        val formattedChangelog = formatChangelog(info.changelog)

        // Optional Mode Views
        txtOptionalTitle.text = getString(R.string.new_update_title, cleanVersion)
        val isBeta = info.channel.equals("beta", ignoreCase = true)
        badgeOptionalChannel.text = if (isBeta) {
            getString(R.string.beta_channel)
        } else {
            getString(R.string.stable_channel)
        }

        if (info.publishedAt.isNotBlank()) {
            txtOptionalReleaseDate.text = "Released on: ${info.publishedAt}"
            txtOptionalReleaseDate.visibility = View.VISIBLE
        } else {
            txtOptionalReleaseDate.visibility = View.GONE
        }

        if (info.apkSize > 0) {
            val sizeMb = info.apkSize / 1048576f
            txtOptionalSize.text = String.format(Locale.ENGLISH, "Size: %.1f MB", sizeMb)
            txtOptionalSize.visibility = View.VISIBLE
        } else {
            txtOptionalSize.visibility = View.GONE
        }

        txtOptionalChangelog.text = formattedChangelog

        // Mandatory Mode Views
        badgeMandatoryVersion.text = "v$cleanVersion"
        txtMandatoryDesc.text = getString(R.string.update_required_desc, "v$cleanVersion")
        txtMandatoryChangelog.text = formattedChangelog
    }

    private fun formatChangelog(lines: List<String>): String {
        if (lines.isEmpty()) {
            return "• Performance and stability improvements."
        }
        val sb = StringBuilder()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            if (!line.startsWith("•") && !line.startsWith("-") && !line.startsWith("*")) {
                sb.append("• ").append(line)
            } else {
                sb.append(line)
            }
            if (i < lines.size - 1) {
                sb.append("\n")
            }
        }
        return sb.toString()
    }

    private fun showOptionalMode() {
        isDownloading = false
        btnBack.visibility = View.VISIBLE
        layoutOptional.visibility = View.VISIBLE
        layoutDownloading.visibility = View.GONE
        layoutMandatory.visibility = View.GONE

        bottomOptional.visibility = View.VISIBLE
        bottomDownloading.visibility = View.GONE
        bottomMandatory.visibility = View.GONE
    }

    private fun showMandatoryMode() {
        isDownloading = false
        btnBack.visibility = View.GONE
        layoutOptional.visibility = View.GONE
        layoutDownloading.visibility = View.GONE
        layoutMandatory.visibility = View.VISIBLE

        bottomOptional.visibility = View.GONE
        bottomDownloading.visibility = View.GONE
        bottomMandatory.visibility = View.VISIBLE
    }

    private fun showDownloadingMode() {
        isDownloading = true
        btnBack.visibility = View.GONE
        layoutOptional.visibility = View.GONE
        layoutMandatory.visibility = View.GONE
        layoutDownloading.visibility = View.VISIBLE

        bottomOptional.visibility = View.GONE
        bottomMandatory.visibility = View.GONE
        bottomDownloading.visibility = View.VISIBLE

        progressBarDownload.progress = 0
        txtDownloadBytes.text = "0.0 MB / " + formatMb(updateInfo?.apkSize ?: 0L)
        txtDownloadEta.text = "Calculating..."
        step2Subtext.text = "0.0 MB / " + formatMb(updateInfo?.apkSize ?: 0L)
        updateStepIndicators(1)
    }

    private fun startUpdateProcess() {
        val info = updateInfo ?: return

        // Check if APK already exists in cache
        val cachedApk = UpdateManager.getDownloadedApkFile(this, info)
        if (cachedApk != null) {
            showDownloadingMode()
            updateStepIndicators(4)
            UpdateManager.installApk(this, cachedApk)
            return
        }

        showDownloadingMode()

        UpdateManager.startDownload(
            this,
            info,
            object : UpdateManager.DownloadProgressListener {
                override fun onProgress(
                    downloaded: Long,
                    total: Long,
                    percent: Int,
                    speed: String,
                    eta: String
                ) {
                    progressBarDownload.progress = percent
                    val dMb = String.format(Locale.ENGLISH, "%.1f MB", downloaded / 1048576f)
                    val tMb = String.format(Locale.ENGLISH, "%.1f MB", total / 1048576f)
                    val progressText = "$dMb / $tMb"

                    txtDownloadBytes.text = progressText
                    txtDownloadEta.text = eta
                    step2Subtext.text = progressText
                }

                override fun onStepChanged(step: Int) {
                    updateStepIndicators(step)
                }

                override fun onComplete(apkFile: File) {
                    isDownloading = false
                    updateStepIndicators(4)
                    txtDownloadStatus.text = getString(R.string.installation_pending)
                    UpdateManager.installApk(this@UpdateActivity, apkFile)
                }

                override fun onError(error: String) {
                    isDownloading = false
                    Toast.makeText(this@UpdateActivity, "Download failed: $error", Toast.LENGTH_LONG)
                        .show()
                    if (isMandatory) {
                        showMandatoryMode()
                    } else {
                        showOptionalMode()
                    }
                }
            }
        )
    }

    private fun cancelUpdateProcess() {
        UpdateManager.cancelDownload()
        isDownloading = false
        if (isMandatory) {
            showMandatoryMode()
        } else {
            showOptionalMode()
        }
    }

    private fun updateStepIndicators(step: Int) {
        // Step 1: Preparing
        if (step > 1) {
            step1Icon.setImageResource(R.drawable.ic_step_check)
            step1Text.setTextColor(Color.WHITE)
        } else {
            step1Icon.setImageResource(R.drawable.ic_step_active)
            step1Text.setTextColor(Color.WHITE)
        }

        // Step 2: Downloading
        if (step > 2) {
            step2Icon.setImageResource(R.drawable.ic_step_check)
            step2Text.setTextColor(Color.WHITE)
        } else if (step == 2) {
            step2Icon.setImageResource(R.drawable.ic_step_active)
            step2Text.setTextColor(Color.WHITE)
        } else {
            step2Icon.setImageResource(R.drawable.ic_step_inactive)
            step2Text.setTextColor(Color.parseColor("#777777"))
        }

        // Step 3: Verifying
        if (step > 3) {
            step3Icon.setImageResource(R.drawable.ic_step_check)
            step3Text.setTextColor(Color.WHITE)
        } else if (step == 3) {
            step3Icon.setImageResource(R.drawable.ic_step_active)
            step3Text.setTextColor(Color.WHITE)
        } else {
            step3Icon.setImageResource(R.drawable.ic_step_inactive)
            step3Text.setTextColor(Color.parseColor("#777777"))
        }

        // Step 4: Install
        if (step >= 4) {
            step4Icon.setImageResource(R.drawable.ic_step_check)
            step4Text.setTextColor(Color.WHITE)
        } else {
            step4Icon.setImageResource(R.drawable.ic_step_inactive)
            step4Text.setTextColor(Color.parseColor("#777777"))
        }
    }

    private fun formatMb(bytes: Long): String {
        return String.format(Locale.ENGLISH, "%.1f MB", bytes / 1048576f)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isMandatory) {
            Toast.makeText(
                this,
                "A major update is required to continue using PlayTube.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (isDownloading) {
            Toast.makeText(this, "Please cancel the download first", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }
}
