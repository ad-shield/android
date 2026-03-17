package io.adshield.android

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object AdBlockDetector {

    private const val TIMEOUT_MS = 5000
    private const val MAX_RETRIES = 3
    private const val POOL_TIMEOUT_SEC = 30L

    data class Result(val url: String, val accessible: Boolean)

    fun detectAll(urls: List<String>): List<Result> {
        val executor = Executors.newFixedThreadPool(urls.size.coerceAtMost(4))
        return try {
            val futures = urls.map { url ->
                executor.submit(Callable { Result(url, probeWithRetry(url)) })
            }
            futures.map { it.get(POOL_TIMEOUT_SEC, TimeUnit.SECONDS) }
        } catch (_: Exception) {
            urls.map { Result(it, false) }
        } finally {
            executor.shutdown()
        }
    }

    private fun probeWithRetry(urlString: String): Boolean {
        repeat(MAX_RETRIES) {
            if (probe(urlString)) return true
        }
        return false
    }

    private fun probe(urlString: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlString).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            code in 200..399
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
