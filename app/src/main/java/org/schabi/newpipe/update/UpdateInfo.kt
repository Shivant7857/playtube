package org.schabi.newpipe.update

import java.io.Serializable

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val channel: String = "stable",
    val updateType: String = "minor",
    val mandatory: Boolean = false,
    val minSupportedVersionCode: Int = 0,
    val publishedAt: String = "",
    val apkAsset: String = "",
    val apkUrl: String = "",
    val apkSize: Long = 0L,
    val sha256: String? = null,
    val changelog: List<String> = emptyList()
) : Serializable {

    fun isMandatory(currentVersionCode: Int, currentVersionName: String): Boolean {
        if (mandatory) {
            return true
        }
        if (minSupportedVersionCode > 0 && currentVersionCode < minSupportedVersionCode) {
            return true
        }
        if (updateType.equals("major", ignoreCase = true)) {
            return true
        }
        val cleanCurrent = currentVersionName.trimStart('v', 'V')
        val cleanNew = versionName.trimStart('v', 'V')
        val currentMajor = cleanCurrent.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val newMajor = cleanNew.split(".").firstOrNull()?.toIntOrNull() ?: 0
        return newMajor > currentMajor && currentMajor > 0
    }
}
