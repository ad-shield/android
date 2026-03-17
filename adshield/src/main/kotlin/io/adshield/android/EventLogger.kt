package io.adshield.android

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal object EventLogger {

    private const val TAG = "AdShield"
    private const val SDK_VERSION = "1.0.0"
    private const val TIMEOUT_MS = 10000

    fun log(
        endpoints: List<String>,
        deviceId: String,
        bundleId: String,
        osVersion: String,
        locale: String,
        results: List<AdBlockDetector.Result>,
        sampleRatio: Double,
        transmissionIntervalMs: Long,
    ) {
        val json = buildPayload(deviceId, bundleId, osVersion, locale, results, sampleRatio, transmissionIntervalMs)
        Log.d(TAG, "Sending report: $json")

        for (endpoint in endpoints) {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream).use { it.write(json) }
                val code = conn.responseCode
                conn.disconnect()
                Log.d(TAG, "Report sent to $endpoint, status=$code")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send report to $endpoint", e)
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
    ): String {
        val obj = JSONObject()
        obj.put("deviceId", deviceId)
        obj.put("bundleId", bundleId)
        obj.put("platform", "android")
        obj.put("sdkVersion", SDK_VERSION)
        obj.put("osVersion", osVersion)
        obj.put("locale", locale)
        obj.put("event_sample_rate", sampleRatio)
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
