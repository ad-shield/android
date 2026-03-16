package io.adshield.android

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object ConfigManager {

    private const val TAG = "AdShield"
    private const val PREFS_NAME = "adshield_prefs"
    private const val KEY_LAST_TRANSMISSION = "last_transmission_ms"
    private const val KEY_INTERVAL_MS = "transmission_interval_ms"
    private const val DEFAULT_INTERVAL_MS = 3_600_000L // 1 hour
    private const val TIMEOUT_MS = 10000
    private const val AES_KEY_HEX = "a6be11212141a6ba6cd7b9213fc4d84c98db63c2574824d452dcf56ee8cd6e42"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

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
            val body = conn.inputStream.bufferedReader().use { it.readText().trim() }
            conn.disconnect()

            val jsonStr = decrypt(body)
            val json = JSONObject(jsonStr)

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

    private fun decrypt(base64Data: String): String {
        val raw = Base64.decode(base64Data, Base64.DEFAULT)
        val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = raw.copyOfRange(GCM_IV_LENGTH, raw.size)

        val keyBytes = ByteArray(AES_KEY_HEX.length / 2) { i ->
            AES_KEY_HEX.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val key = SecretKeySpec(keyBytes, "AES")
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decrypted = cipher.doFinal(ciphertext)

        return String(decrypted, Charsets.UTF_8)
    }

    fun getLastIntervalMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS)
    }

    fun saveIntervalMs(context: Context, intervalMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_INTERVAL_MS, intervalMs).apply()
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
