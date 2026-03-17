package io.adshield.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal object EventLogger {

    private const val TIMEOUT_MS = 10000

    fun log(
        context: Context,
        endpoints: List<String>,
        deviceId: String,
        bundleId: String,
        osVersion: String,
        locale: String,
        results: List<AdBlockDetector.Result>,
        sampleRatio: Double,
        transmissionIntervalMs: Long,
    ) {
        val sdkVersion = try {
            context.getString(R.string.adshield_sdk_version)
        } catch (_: Exception) {
            "unknown"
        }
        val json = buildPayload(deviceId, bundleId, osVersion, locale, results, sampleRatio, transmissionIntervalMs, sdkVersion)

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream).use { it.write(json) }
                conn.responseCode
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun buildPayload(
        deviceId: String,
        bundleId: String,
        osVersion: String,
        locale: String,
        results: List<AdBlockDetector.Result>,
        sampleRatio: Double,
        transmissionIntervalMs: Long,
        sdkVersion: String,
    ): String {
        val obj = JSONObject()
        obj.put("deviceId", deviceId)
        obj.put("bundleId", bundleId)
        obj.put("platform", "android")
        obj.put("sdkVersion", sdkVersion)
        obj.put("osVersion", osVersion)
        obj.put("locale", locale)
        obj.put("sampleRatio", sampleRatio)
        obj.put("transmissionIntervalMs", transmissionIntervalMs)

        val arr = JSONArray()
        for (r in results) {
            val item = JSONObject()
            item.put("url", r.url)
            item.put("accessible", r.accessible)
            arr.put(item)
        }
        obj.put("results", arr)

        return obj.toString()
    }
}
