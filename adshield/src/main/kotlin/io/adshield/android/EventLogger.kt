package io.adshield.android

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal object EventLogger {

    private const val TAG = "AdShield"
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
        kv: Map<String, String> = emptyMap(),
    ) {
        val sdkVersion = try {
            context.getString(R.string.adshield_sdk_version)
        } catch (_: Exception) {
            "unknown"
        }
        val json = buildPayload(deviceId, bundleId, osVersion, locale, results, sampleRatio, transmissionIntervalMs, sdkVersion, kv)

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
                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    Log.d(TAG, "Event sent to $endpoint (HTTP $responseCode)")
                } else {
                    Log.w(TAG, "Event send to $endpoint returned HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Event send to $endpoint failed: ${e.message}")
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
        kv: Map<String, String> = emptyMap(),
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
        obj.put("kv", JSONObject(kv))

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
