package io.adshield.android

import android.content.Context
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class AdShieldE2ETest {

    private lateinit var configServer: MockWebServer
    private lateinit var probeServer: MockWebServer
    private lateinit var reportServer: MockWebServer

    @Before
    fun setUp() {
        configServer = MockWebServer().also { it.start() }
        probeServer = MockWebServer().also { it.start() }
        reportServer = MockWebServer().also { it.start() }

        ConfigManager.endpoint = configServer.url("/config").toString()
        ConfigManager.kv = emptyMap()

        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("adshield_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @After
    fun tearDown() {
        configServer.shutdown()
        probeServer.shutdown()
        reportServer.shutdown()
        ConfigManager.endpoint = null
        ConfigManager.kv = emptyMap()
    }

    private fun encryptConfig(config: JSONObject): String {
        val keyHex = "a6be11212141a6ba6cd7b9213fc4d84c98db63c2574824d452dcf56ee8cd6e42"
        val keyBytes = ByteArray(keyHex.length / 2) { i ->
            keyHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(config.toString().toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun makeEncryptedConfig(
        sampleRatio: Double = 1.0,
        transmissionIntervalMs: Long = 60000,
        detectionUrls: List<String>? = null,
        reportEndpoints: List<String>? = null,
    ): String {
        val probeUrls = detectionUrls ?: listOf(probeServer.url("/ad.js").toString())
        val endpoints = reportEndpoints ?: listOf(reportServer.url("/event").toString())

        val config = JSONObject()
        config.put("sampleRatio", sampleRatio)
        config.put("transmissionIntervalMs", transmissionIntervalMs)
        config.put("adblockDetectionUrls", JSONArray(probeUrls))
        config.put("reportEndpoints", JSONArray(endpoints))

        return encryptConfig(config)
    }

    @Test
    fun `sampleRatio zero skips detection and transmission`() {
        configServer.enqueue(MockResponse().setBody(makeEncryptedConfig(sampleRatio = 0.0)))

        AdShield.measure(RuntimeEnvironment.getApplication())
        Thread.sleep(3000)

        assertEquals(0, probeServer.requestCount)
        assertEquals(0, reportServer.requestCount)
    }

    @Test
    fun `sampleRatio one triggers detection and transmission`() {
        configServer.enqueue(MockResponse().setBody(makeEncryptedConfig(sampleRatio = 1.0)))
        probeServer.enqueue(MockResponse().setResponseCode(200))
        reportServer.enqueue(MockResponse().setResponseCode(200))

        AdShield.measure(RuntimeEnvironment.getApplication())
        Thread.sleep(3000)

        assertTrue(probeServer.requestCount > 0)
        val reportReq = reportServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(reportReq)
    }

    @Test
    fun `low transmission interval allows quick re-measure`() {
        configServer.enqueue(MockResponse().setBody(makeEncryptedConfig(transmissionIntervalMs = 1000)))
        probeServer.enqueue(MockResponse().setResponseCode(200))
        reportServer.enqueue(MockResponse().setResponseCode(200))

        val context = RuntimeEnvironment.getApplication()
        AdShield.measure(context)
        Thread.sleep(3000)

        // After 1s interval should have passed
        assertTrue(ConfigManager.isAllowed(context))
    }

    @Test
    fun `high transmission interval blocks next measure`() {
        configServer.enqueue(MockResponse().setBody(makeEncryptedConfig(transmissionIntervalMs = 3_600_000)))
        probeServer.enqueue(MockResponse().setResponseCode(200))
        reportServer.enqueue(MockResponse().setResponseCode(200))

        val context = RuntimeEnvironment.getApplication()
        AdShield.measure(context)
        Thread.sleep(3000)

        assertFalse(ConfigManager.isAllowed(context))
    }

    @Test
    fun `probes all configured detection URLs`() {
        val probe1 = probeServer.url("/ad1.js").toString()
        val probe2 = probeServer.url("/ad2.js").toString()
        val probe3 = probeServer.url("/ad3.js").toString()

        configServer.enqueue(
            MockResponse().setBody(
                makeEncryptedConfig(detectionUrls = listOf(probe1, probe2, probe3))
            )
        )
        // Enqueue responses for all probes (each may retry up to 3 times)
        repeat(9) { probeServer.enqueue(MockResponse().setResponseCode(200)) }
        reportServer.enqueue(MockResponse().setResponseCode(200))

        AdShield.measure(RuntimeEnvironment.getApplication())
        Thread.sleep(5000)

        val probePaths = mutableListOf<String>()
        repeat(probeServer.requestCount) {
            val req = probeServer.takeRequest(1, TimeUnit.SECONDS)
            if (req != null) probePaths.add(req.path ?: "")
        }
        assertTrue(probePaths.any { it.contains("ad1.js") })
        assertTrue(probePaths.any { it.contains("ad2.js") })
        assertTrue(probePaths.any { it.contains("ad3.js") })
    }

    @Test
    fun `sends results to all configured report endpoints`() {
        val report1Url = reportServer.url("/event1").toString()
        val report2Url = reportServer.url("/event2").toString()

        configServer.enqueue(
            MockResponse().setBody(
                makeEncryptedConfig(reportEndpoints = listOf(report1Url, report2Url))
            )
        )
        probeServer.enqueue(MockResponse().setResponseCode(200))
        reportServer.enqueue(MockResponse().setResponseCode(200))
        reportServer.enqueue(MockResponse().setResponseCode(200))

        AdShield.measure(RuntimeEnvironment.getApplication())
        Thread.sleep(3000)

        val reportPaths = mutableListOf<String>()
        repeat(reportServer.requestCount) {
            val req = reportServer.takeRequest(1, TimeUnit.SECONDS)
            if (req != null && req.method == "POST") reportPaths.add(req.path ?: "")
        }
        assertTrue(reportPaths.any { it.contains("event1") })
        assertTrue(reportPaths.any { it.contains("event2") })
    }

    @Test
    fun `event payload contains required fields`() {
        configServer.enqueue(MockResponse().setBody(makeEncryptedConfig()))
        probeServer.enqueue(MockResponse().setResponseCode(200))
        reportServer.enqueue(MockResponse().setResponseCode(200))

        AdShield.measure(RuntimeEnvironment.getApplication())
        Thread.sleep(3000)

        val req = reportServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(req)
        assertEquals("POST", req!!.method)
        assertEquals("application/json", req.getHeader("Content-Type"))

        val body = JSONObject(req.body.readUtf8())
        assertEquals("android", body.getString("platform"))
        assertNotNull(body.getString("deviceId"))
        assertNotNull(body.getString("bundleId"))
        assertNotNull(body.getJSONArray("results"))
        assertTrue(body.getJSONArray("results").length() > 0)
    }

    @Test
    fun `event payload contains kv when configured`() {
        configServer.enqueue(MockResponse().setBody(makeEncryptedConfig()))
        probeServer.enqueue(MockResponse().setResponseCode(200))
        reportServer.enqueue(MockResponse().setResponseCode(200))

        AdShield.configure(
            endpoint = configServer.url("/config").toString(),
            kv = mapOf("user_type" to "new", "segment" to "premium")
        )
        AdShield.measure(RuntimeEnvironment.getApplication())
        Thread.sleep(3000)

        val req = reportServer.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(req)
        val body = JSONObject(req!!.body.readUtf8())
        val kv = body.getJSONObject("kv")
        assertEquals("new", kv.getString("user_type"))
        assertEquals("premium", kv.getString("segment"))
    }
}
