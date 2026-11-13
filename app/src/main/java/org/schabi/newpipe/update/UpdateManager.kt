package org.schabi.newpipe.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_OWNER = "Shivant7857"
    private const val GITHUB_REPO = "playtube"
    private const val API_BASE = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO"

    const val PREF_AUTO_CHECK = "playtube_auto_update_check"
    const val PREF_NOTIFICATIONS = "playtube_update_notifications"
    const val PREF_BETA = "playtube_beta_updates"
    private const val PREF_LAST_CHECK_TIME = "playtube_last_update_check_time"
    private const val PREF_CACHED_UPDATE = "playtube_cached_update_json"
    private const val NOTIFICATION_CHANNEL_ID = "playtube_app_updates"
    private const val NOTIFICATION_ID = 2026

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isDownloading = AtomicBoolean(false)
    private var downloadThread: Thread? = null

    enum class Status {
        IDLE,
        CHECKING,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        DOWNLOADING,
        VERIFYING,
        READY_TO_INSTALL,
        FAILED
    }

    var currentStatus: Status = Status.IDLE
        private set
    var cachedUpdate: UpdateInfo? = null
        private set

    interface UpdateCheckListener {
        fun onStatusChanged(status: Status, updateInfo: UpdateInfo?, message: String?)
    }

    interface DownloadProgressListener {
        fun onProgress(downloaded: Long, total: Long, percent: Int, speed: String, eta: String)
        fun onStepChanged(step: Int) // 1: Preparing, 2: Downloading, 3: Verifying, 4: Ready
        fun onComplete(apkFile: File)
        fun onError(error: String)
    }

    fun isAutoCheckEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_AUTO_CHECK, true)
    }

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PREF_AUTO_CHECK, enabled).apply()
    }

    fun isNotificationsEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_NOTIFICATIONS, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PREF_NOTIFICATIONS, enabled).apply()
    }

    fun isBetaEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_BETA, false)
    }

    fun setBetaEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PREF_BETA, enabled).apply()
    }

    fun init(context: Context) {
        loadCachedUpdate(context)
    }

    private fun loadCachedUpdate(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString(PREF_CACHED_UPDATE, null) ?: return
        try {
            val obj = JsonParser.`object`().from(json)
            val info = parseUpdateInfoFromJsonObject(obj)
            if (isNewerVersion(info.versionName, info.versionCode)) {
                cachedUpdate = info
                currentStatus = Status.UPDATE_AVAILABLE
            } else {
                prefs.edit().remove(PREF_CACHED_UPDATE).apply()
                cachedUpdate = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached update", e)
        }
    }

    fun checkForUpdate(
        context: Context,
        isManual: Boolean,
        listener: UpdateCheckListener?
    ) {
        currentStatus = Status.CHECKING
        listener?.onStatusChanged(Status.CHECKING, null, null)

        executor.execute {
            try {
                val beta = isBetaEnabled(context)
                val updateInfo = fetchReleaseFromGitHub(beta)

                mainHandler.post {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    prefs.edit().putLong(PREF_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

                    if (updateInfo != null && isNewerVersion(
                            updateInfo.versionName,
                            updateInfo.versionCode
                        )
                    ) {
                        cachedUpdate = updateInfo
                        currentStatus = Status.UPDATE_AVAILABLE
                        saveCachedUpdate(prefs, updateInfo)

                        if (isNotificationsEnabled(context)) {
                            showUpdateNotification(context, updateInfo)
                        }

                        listener?.onStatusChanged(Status.UPDATE_AVAILABLE, updateInfo, null)
                    } else {
                        cachedUpdate = null
                        currentStatus = Status.UP_TO_DATE
                        prefs.edit().remove(PREF_CACHED_UPDATE).apply()
                        listener?.onStatusChanged(Status.UP_TO_DATE, null, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for update", e)
                mainHandler.post {
                    currentStatus = Status.FAILED
                    listener?.onStatusChanged(Status.FAILED, null, e.localizedMessage)
                }
            }
        }
    }

    private fun fetchReleaseFromGitHub(checkBeta: Boolean): UpdateInfo? {
        val endpoint = if (checkBeta) {
            "$API_BASE/releases"
        } else {
            "$API_BASE/releases/latest"
        }

        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "PlayTube-Updater")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            Log.w(TAG, "GitHub API returned code $responseCode")
            return null
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        return if (checkBeta) {
            val array = JsonParser.array().from(body)
            var bestCandidate: UpdateInfo? = null
            for (i in 0 until array.size) {
                val rel = array.getObject(i)
                if (rel.getBoolean("draft", false)) continue
                val info = parseReleaseJsonObject(rel)
                if (info != null && isNewerVersion(info.versionName, info.versionCode)) {
                    bestCandidate = info
                    break
                }
            }
            bestCandidate
        } else {
            val rel = JsonParser.`object`().from(body)
            parseReleaseJsonObject(rel)
        }
    }

    private fun parseReleaseJsonObject(rel: JsonObject): UpdateInfo? {
        val tagName = rel.getString("tag_name", "")
        val rawVersion = tagName.trimStart('v', 'V')
        if (rawVersion.isEmpty()) return null

        val isPrerelease = rel.getBoolean("prerelease", false)
        val publishedAt = formatReleaseDate(rel.getString("published_at", ""))
        val body = rel.getString("body", "")

        val changelogLines = mutableListOf<String>()
        body.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                changelogLines.add(trimmed.substring(2).trim())
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                changelogLines.add(trimmed)
            }
        }
        if (changelogLines.isEmpty()) {
            changelogLines.add("Performance and stability improvements")
        }

        val assets = rel.getArray("assets")
        var manifestObj: JsonObject? = null
        var apkUrl = ""
        var apkName = ""
        var apkSize = 0L

        if (assets != null) {
            for (i in 0 until assets.size) {
                val asset = assets.getObject(i)
                val name = asset.getString("name", "")
                if (name == "playtube-update.json") {
                    val downloadUrl = asset.getString("browser_download_url", "")
                    manifestObj = downloadJsonAsset(downloadUrl)
                } else if (name.endsWith(".apk")) {
                    if (apkUrl.isEmpty() || name.contains("universal") || name.contains("debug")) {
                        apkUrl = asset.getString("browser_download_url", "")
                        apkName = name
                        apkSize = asset.getLong("size", 0L)
                    }
                }
            }
        }

        if (manifestObj != null) {
            return parseUpdateInfoFromJsonObject(manifestObj)
        }

        val versionCode = parseVersionCode(rawVersion)
        val channel = if (isPrerelease) "beta" else "stable"
        val currentVer = BuildConfig.VERSION_NAME.trimStart('v', 'V')
        val currentMajor = currentVer.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val newMajor = rawVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val isMajor = newMajor > currentMajor && currentMajor > 0

        return UpdateInfo(
            versionName = rawVersion,
            versionCode = versionCode,
            channel = channel,
            updateType = if (isMajor) "major" else "minor",
            mandatory = isMajor,
            minSupportedVersionCode = if (isMajor) versionCode else 0,
            publishedAt = publishedAt,
            apkAsset = apkName,
            apkUrl = apkUrl,
            apkSize = apkSize,
            sha256 = null,
            changelog = changelogLines
        )
    }

    private fun downloadJsonAsset(urlStr: String): JsonObject? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JsonParser.`object`().from(body)
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch playtube-update.json", e)
            null
        }
    }

    private fun parseUpdateInfoFromJsonObject(obj: JsonObject): UpdateInfo {
        val changelogArray = obj.getArray("changelog")
        val changelog = mutableListOf<String>()
        if (changelogArray != null) {
            for (i in 0 until changelogArray.size) {
                changelog.add(changelogArray.getString(i, ""))
            }
        }

        return UpdateInfo(
            versionName = obj.getString("versionName", ""),
            versionCode = obj.getInt("versionCode", 0),
            channel = obj.getString("channel", "stable"),
            updateType = obj.getString("updateType", "minor"),
            mandatory = obj.getBoolean("mandatory", false),
            minSupportedVersionCode = obj.getInt("minSupportedVersionCode", 0),
            publishedAt = obj.getString("publishedAt", ""),
            apkAsset = obj.getString("apkAsset", ""),
            apkUrl = obj.getString("apkUrl", ""),
            apkSize = obj.getLong("apkSize", 0L),
            sha256 = obj.getString("sha256", null),
            changelog = changelog
        )
    }

    private fun saveCachedUpdate(prefs: SharedPreferences, info: UpdateInfo) {
        try {
            val writer = JsonWriter.string()
                .`object`()
                .value("versionName", info.versionName)
                .value("versionCode", info.versionCode)
                .value("channel", info.channel)
                .value("updateType", info.updateType)
                .value("mandatory", info.mandatory)
                .value("minSupportedVersionCode", info.minSupportedVersionCode)
                .value("publishedAt", info.publishedAt)
                .value("apkAsset", info.apkAsset)
                .value("apkUrl", info.apkUrl)
                .value("apkSize", info.apkSize)
                .value("sha256", info.sha256)
                .array("changelog")
            for (c in info.changelog) {
                writer.value(c)
            }
            writer.end().end()
            val jsonString = writer.done()
            prefs.edit().putString(PREF_CACHED_UPDATE, jsonString).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cached update", e)
        }
    }

    private fun isNewerVersion(remoteVersionName: String, remoteVersionCode: Int): Boolean {
        if (remoteVersionCode > 0 && BuildConfig.VERSION_CODE > 0) {
            if (remoteVersionCode > BuildConfig.VERSION_CODE) return true
            if (remoteVersionCode < BuildConfig.VERSION_CODE) return false
        }
        return compareSemver(remoteVersionName, BuildConfig.VERSION_NAME) > 0
    }

    private fun compareSemver(v1: String, v2: String): Int {
        val s1 = v1.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }
        val s2 = v2.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }
        val len = maxOf(s1.size, s2.size)
        for (i in 0 until len) {
            val n1 = s1.getOrElse(i) { 0 }
            val n2 = s2.getOrElse(i) { 0 }
            if (n1 != n2) return n1.compareTo(n2)
        }
        return 0
    }

    private fun parseVersionCode(versionName: String): Int {
        val parts = versionName.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
            2 -> parts[0] * 10000 + parts[1] * 100
            1 -> parts[0] * 10000
            else -> 1000
        }
    }

    private fun formatReleaseDate(iso: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val date = sdf.parse(iso) ?: return iso
            val out = SimpleDateFormat("dd MMMM yyyy, h:mm a", Locale.ENGLISH)
            out.format(date)
        } catch (e: Exception) {
            iso
        }
    }

    fun startDownload(
        context: Context,
        info: UpdateInfo,
        listener: DownloadProgressListener
    ) {
        if (isDownloading.get()) return
        isDownloading.set(true)
        currentStatus = Status.DOWNLOADING

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()
        val targetFile = File(updatesDir, "PlayTube-${info.versionName}.apk")

        downloadThread = Thread {
            var input: InputStream? = null
            var output: FileOutputStream? = null
            var connection: HttpURLConnection? = null
            try {
                mainHandler.post { listener.onStepChanged(1) } // Preparing
                Thread.sleep(300)

                val url = URL(info.apkUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.connect()

                val totalLength = if (connection.contentLengthLong > 0) {
                    connection.contentLengthLong
                } else {
                    info.apkSize
                }

                input = connection.inputStream
                output = FileOutputStream(targetFile)

                mainHandler.post { listener.onStepChanged(2) } // Downloading

                val buffer = ByteArray(64 * 1024)
                var downloaded: Long = 0
                var read: Int
                var lastTime = System.currentTimeMillis()
                var lastDownloaded: Long = 0

                while (input.read(buffer).also { read = it } != -1) {
                    if (!isDownloading.get()) {
                        targetFile.delete()
                        mainHandler.post { listener.onError("Download cancelled") }
                        return@Thread
                    }
                    output.write(buffer, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 500) {
                        val percent = if (totalLength > 0) ((downloaded * 100) / totalLength).toInt() else 0
                        val bytesSince = downloaded - lastDownloaded
                        val speedBytesSec = (bytesSince * 1000) / elapsed
                        val speedStr = String.format(Locale.ENGLISH, "%.1f MB/s", speedBytesSec / 1048576f)
                        val remainingBytes = totalLength - downloaded
                        val etaSec = if (speedBytesSec > 0) remainingBytes / speedBytesSec else 0
                        val etaStr = if (etaSec > 0) "≈ ${etaSec}s left" else "Almost done"

                        lastTime = now
                        lastDownloaded = downloaded

                        mainHandler.post {
                            listener.onProgress(downloaded, totalLength, percent, speedStr, etaStr)
                        }
                    }
                }
                output.flush()

                // Step 3: Verifying (SHA-256)
                mainHandler.post { listener.onStepChanged(3) }
                if (info.sha256 != null && !verifySha256(targetFile, info.sha256)) {
                    targetFile.delete()
                    mainHandler.post { listener.onError("SHA-256 checksum mismatch") }
                    return@Thread
                }

                // Step 4: Ready to install
                currentStatus = Status.READY_TO_INSTALL
                mainHandler.post {
                    listener.onStepChanged(4)
                    listener.onComplete(targetFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                targetFile.delete()
                mainHandler.post { listener.onError(e.localizedMessage ?: "Download failed") }
            } finally {
                isDownloading.set(false)
                try {
                    input?.close()
                } catch (ignored: Exception) {}
                try {
                    output?.close()
                } catch (ignored: Exception) {}
                connection?.disconnect()
            }
        }.apply { start() }
    }

    fun cancelDownload() {
        isDownloading.set(false)
        downloadThread?.interrupt()
        currentStatus = Status.IDLE
    }

    private fun verifySha256(file: File, expectedHash: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var count: Int
                while (stream.read(buffer).also { count = it } > 0) {
                    digest.update(buffer, 0, count)
                }
            }
            val hashBytes = digest.digest()
            val computedHash = hashBytes.joinToString("") { "%02x".format(it) }
            computedHash.equals(expectedHash.trim(), ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify SHA-256", e)
            false
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist for install: ${apkFile.absolutePath}")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    fun clearDownloadedUpdates(context: Context): Boolean {
        return try {
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists()) {
                updatesDir.deleteRecursively()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear updates", e)
            false
        }
    }

    fun getDownloadedApkFile(context: Context, info: UpdateInfo): File? {
        val file = File(File(context.cacheDir, "updates"), "PlayTube-${info.versionName}.apk")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun fetchRecentCommits(callback: (List<String>) -> Unit) {
        executor.execute {
            val list = mutableListOf<String>()
            try {
                val url = URL("$API_BASE/commits?per_page=10")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "PlayTube-Updater")

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JsonParser.array().from(body)
                    for (i in 0 until array.size) {
                        val item = array.getObject(i)
                        val commitObj = item.getObject("commit")
                        val message = commitObj.getString("message", "").lines().firstOrNull() ?: ""
                        val author = commitObj.getObject("author").getString("name", "")
                        if (message.isNotBlank()) {
                            list.add("$message ($author)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch commits", e)
            }
            mainHandler.post { callback(list) }
        }
    }

    private fun showUpdateNotification(context: Context, info: UpdateInfo) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about new PlayTube updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, UpdateActivity::class.java).apply {
            putExtra(UpdateActivity.EXTRA_UPDATE_INFO, info)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_badge_updates)
            .setContentTitle("PlayTube")
            .setContentText("Update available: v${info.versionName}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Update available: v${info.versionName}. Tap to view what's new and update.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
