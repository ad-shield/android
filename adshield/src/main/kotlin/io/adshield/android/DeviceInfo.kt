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

    fun getOsVersion(): String = Build.VERSION.RELEASE

    fun getLocale(): String {
        val locale = Locale.getDefault()
        return "${locale.language}_${locale.country}"
    }
}
