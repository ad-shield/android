package io.adshield.android

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody

internal object EventLogger {

    private val httpClient = OkHttpClient(timeoutSeconds = 10)

    private const val TAG = "AdShield"

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
            try {
                val body = RequestBody.create(MediaType.parse("application/json"), json.toByteArray(Charsets.UTF_8))
                val request = Request.Builder().url(endpoint).post(body).build()
                val responseCode = httpClient.execute(request).code
                if (responseCode in 200..299) {
                    Log.d(TAG, "Event sent to $endpoint (HTTP $responseCode)")
                } else {
                    Log.w(TAG, "Event send to $endpoint returned HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Event send to $endpoint failed: ${e.message}")
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
