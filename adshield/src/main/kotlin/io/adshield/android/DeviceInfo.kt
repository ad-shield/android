package io.adshield.android

import android.content.Context
import android.os.Build
import java.util.Locale
import java.util.UUID

internal object DeviceInfo {

    private const val PREFS_NAME = "adshield_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getBundleId(context: Context): String = context.packageName

    /**
     * Host app metadata for report kv: app_version (versionName) and app_version_code (decimal).
     * A missing or blank name is omitted; package lookup failure omits both values.
     */
    @Suppress("DEPRECATION")
    fun getAppVersionMetadata(context: Context): Map<String, String> {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                info.versionCode.toLong()
            }
            buildMap {
                info.versionName?.takeIf { it.isNotBlank() }?.let { put("app_version", it) }
                put("app_version_code", versionCode.toString())
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getOsVersion(): String = Build.VERSION.RELEASE

    fun getLocale(): String {
        val locale = Locale.getDefault()
        return "${locale.language}_${locale.country}"
    }
}
