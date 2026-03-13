package io.adshield.android

import java.net.HttpURLConnection
import java.net.URL

internal object AdBlockDetector {

    private const val TIMEOUT_MS = 5000
    private const val MAX_RETRIES = 3

    data class Result(val url: String, val accessible: Boolean)

    fun detectAll(urls: List<String>): List<Result> {
        return urls.map { url ->
            val accessible = probeWithRetry(url)
            Result(url, accessible)
        }
    }

    private fun probeWithRetry(urlString: String): Boolean {
        repeat(MAX_RETRIES) {
            if (probe(urlString)) return true
        }
        return false
    }

    private fun probe(urlString: String): Boolean {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }
}
