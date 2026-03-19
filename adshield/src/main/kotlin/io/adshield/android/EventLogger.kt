package io.adshield.android

import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal object EventLogger {

    private const val TAG = "AdShield"
    private const val ENDPOINT = "https://css-load.com/bq/event"

    @VisibleForTesting
    internal var endpoint = ENDPOINT

    fun log(packageName: String, platform: String, isAdBlockDetected: Boolean) {
        try {
            val eventId = UUID.randomUUID().toString()
            val json = """{"table":"mobile_measure","data":[{"event_id":"$eventId","package":"$packageName","platform":"$platform","is_adblock_detected":$isAdBlockDetected}]}"""

            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(json) }
            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode in 200..299) {
                Log.d(TAG, "Event sent successfully (HTTP $responseCode): adblock_detected=$isAdBlockDetected, event_id=$eventId")
            } else {
                Log.w(TAG, "Event send returned unexpected status (HTTP $responseCode): event_id=$eventId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Event send failed: ${e.message}")
        }
    }
}
