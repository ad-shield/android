package io.adshield.android

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object ConfigManager {

    private const val TAG = "AdShield"
    private const val PREFS_NAME = "adshield_prefs"
    private const val KEY_LAST_TRANSMISSION = "last_transmission_ms"
    private const val TIMEOUT_MS = 10000

    @Volatile
    var endpoint: String? = null

    fun configure(endpoint: String) {
        this.endpoint = endpoint
    }

    fun fetchConfig(): Config? {
        val url = endpoint ?: return null
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val reportEndpoints = mutableListOf<String>()
            val endpoints = json.getJSONArray("reportEndpoints")
            for (i in 0 until endpoints.length()) {
                reportEndpoints.add(endpoints.getString(i))
            }

            val urls = mutableListOf<String>()
            val urlsArray = json.getJSONArray("adblockDetectionUrls")
            for (i in 0 until urlsArray.length()) {
                urls.add(urlsArray.getString(i))
            }

            val intervalMs = json.optLong("transmissionIntervalMs", 0L)

            Config(reportEndpoints, urls, intervalMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch config", e)
            null
        }
    }

    fun shouldTransmit(context: Context, intervalMs: Long): Boolean {
        if (intervalMs <= 0) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTransmission = prefs.getLong(KEY_LAST_TRANSMISSION, 0L)
        val now = System.currentTimeMillis()
        return (now - lastTransmission) >= intervalMs
    }

    fun recordTransmission(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_TRANSMISSION, System.currentTimeMillis()).apply()
    }

    data class Config(
        val reportEndpoints: List<String>,
        val adblockDetectionUrls: List<String>,
        val transmissionIntervalMs: Long,
    )
}
