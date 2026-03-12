package io.adshield.android

import java.net.HttpURLConnection
import java.net.URL

internal object AdBlockDetector {

    private const val CONTROL_URL = "https://www.google.com"
    private const val AD_URL = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"
    private const val TIMEOUT_MS = 5000

    /**
     * Returns true if adblock detected, false if not, null if network offline.
     */
    fun detect(): Boolean? {
        val controlOk = probe(CONTROL_URL)
        if (!controlOk) return null
        val adOk = probe(AD_URL)
        return !adOk
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
