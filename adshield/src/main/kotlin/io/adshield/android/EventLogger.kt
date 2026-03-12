package io.adshield.android

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal object EventLogger {

    private const val ENDPOINT = "https://css-load.com/bq/event"

    fun log(packageName: String, platform: String, isAdBlockDetected: Boolean) {
        try {
            val eventId = UUID.randomUUID().toString()
            val json = """{"table":"mobile_measure","data":[{"event_id":"$eventId","package":"$packageName","platform":"$platform","is_adblock_detected":$isAdBlockDetected}]}"""

            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(json) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
            // fire-and-forget
        }
    }
}
