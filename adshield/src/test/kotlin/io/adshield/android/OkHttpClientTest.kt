package io.adshield.android

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.SocketTimeoutException
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OkHttpClientTest {
    private val servers = mutableListOf<MockWebServer>()
    private val context get() = RuntimeEnvironment.getApplication()

    private fun server() = MockWebServer().also {
        it.start(InetAddress.getByName("127.0.0.1"), 0)
        servers.add(it)
    }

    @After fun tearDown() {
        servers.forEach { it.shutdown() }
        ConfigManager.endpoint = null
        ConfigManager.kv = emptyMap()
    }

    private fun report(server: MockWebServer, results: List<AdBlockDetector.Result> = emptyList()) {
        EventLogger.log(context, listOf(server.url("/event").toString()), "fixture-id", "fixture.app",
            "fixture-os", "en-US", results, 1.0, 60000, mapOf("test" to "local"))
    }

    // Uses the same encrypted-config fixture contract as the existing end-to-end tests.
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

    @Test fun `actual config GET probe HEAD and report POST bypass installed global factory`() {
        val source = server(); val probe = server(); val destination = server()
        val calls = AtomicInteger()
        val factoryField = URL::class.java.getDeclaredField("factory").apply { isAccessible = true }
        val previous = factoryField.get(null) as URLStreamHandlerFactory?
        factoryField.set(null, null)
        URL.setURLStreamHandlerFactory { scheme ->
            if (scheme != "http" && scheme != "https") null else object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection {
                    calls.incrementAndGet()
                    return object : HttpURLConnection(url) {
                        override fun connect() = Unit
                        override fun disconnect() = Unit
                        override fun usingProxy() = false
                        override fun getResponseCode() = 204
                        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
                    }
                }
            }
        }
        try {
            val original = probe.url("/ad").toString()
            assertEquals(204, (URL(original).openConnection() as HttpURLConnection).responseCode)
            assertEquals(1, calls.get()) // Positive control: the factory would recover the blocked URL.
            val config = JSONObject().put("sampleRatio", 1.0).put("transmissionIntervalMs", 60000)
                .put("adblockDetectionUrls", JSONArray(listOf(original)))
                .put("reportEndpoints", JSONArray(listOf(destination.url("/event").toString())))
            source.enqueue(MockResponse().setBody(encryptConfig(config)))
            repeat(3) { probe.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0")) }
            destination.enqueue(MockResponse().setResponseCode(204))
            AdShield.configure(source.url("/config").toString())
            val parsed = requireNotNull(ConfigManager.fetchConfig())
            val results = AdBlockDetector.detectAll(parsed.adblockDetectionUrls)
            report(destination, results)
            assertEquals(1, calls.get())
            assertEquals("GET", source.takeRequest(1, TimeUnit.SECONDS)!!.method)
            repeat(3) { assertEquals("HEAD", probe.takeRequest(1, TimeUnit.SECONDS)!!.method) }
            assertEquals(3, probe.requestCount)
            val request = destination.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals("application/json", request.getHeader("Content-Type"))
            assertEquals("okhttp/3.12.13", request.getHeader("User-Agent"))
            val payload = JSONObject(request.body.readUtf8())
            assertEquals(context.getString(R.string.adshield_sdk_version), payload.getString("sdkVersion"))
            assertEquals("fixture-id", payload.getString("deviceId"))
            assertEquals("local", payload.getJSONObject("kv").getString("test"))
            val result = payload.getJSONArray("results").getJSONObject(0)
            assertEquals(original, result.getString("url")); assertFalse(result.getBoolean("accessible"))
        } finally {
            factoryField.set(null, null)
            URL.setURLStreamHandlerFactory(previous)
        }
    }

    @Test fun `POST error statuses do not retry including retry after zero`() {
        for (status in listOf(408, 429, 500, 503)) {
            val target = server()
            repeat(2) { target.enqueue(MockResponse().setResponseCode(status).setHeader("Retry-After", "0")) }
            report(target)
            assertEquals("status=$status", 1, target.requestCount)
            assertEquals("POST", target.takeRequest(1, TimeUnit.SECONDS)!!.method)
        }
    }

    @Test fun `POST ordinary redirects become GET and temporary redirects do not replay body`() {
        for (status in listOf(301, 302, 303, 307, 308)) {
            val target = server()
            target.enqueue(MockResponse().setResponseCode(status).setHeader("Location", "/destination"))
            target.enqueue(MockResponse().setResponseCode(204))
            report(target)
            val post = target.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("POST", post.method); assertTrue(post.bodySize > 0)
            if (status in listOf(307, 308)) assertEquals(1, target.requestCount)
            else {
                assertEquals(2, target.requestCount)
                val followup = target.takeRequest(1, TimeUnit.SECONDS)!!
                assertEquals("GET", followup.method); assertEquals("/destination", followup.path)
                assertEquals(0L, followup.bodySize); assertNull(followup.getHeader("Content-Type"))
            }
        }
    }

    @Test fun `HEAD same scheme redirect keeps method and original result URL`() {
        val target = server()
        target.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/destination"))
        target.enqueue(MockResponse().setResponseCode(204))
        val original = target.url("/ad").toString()
        assertEquals(listOf(AdBlockDetector.Result(original, true)), AdBlockDetector.detectAll(listOf(original)))
        assertEquals("HEAD", target.takeRequest(1, TimeUnit.SECONDS)!!.method)
        val followup = target.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("HEAD", followup.method); assertEquals("/destination", followup.path)
        assertEquals(1, followup.sequenceNumber) // Prior response was closed and connection is reusable.
    }

    @Test fun `HEAD cross scheme redirect is not followed and status range is unchanged`() {
        for (status in listOf(200, 299, 300, 302, 399, 400)) {
            val target = server()
            repeat(3) {
                target.enqueue(MockResponse().setResponseCode(status)
                    .setHeader("Location", target.url("/secure").toString().replace("http:", "https:")))
            }
            val result = AdBlockDetector.detectAll(listOf(target.url("/ad").toString())).single()
            assertEquals("status=$status", status in 200..399, result.accessible)
            assertEquals(if (status < 400) 1 else 3, target.requestCount)
        }
    }

    @Test fun `redirect loop is bounded`() {
        val target = server()
        repeat(22) { target.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/loop")) }
        try {
            OkHttpClient(timeoutSeconds = 5).execute(Request.Builder().url(target.url("/loop")).head().build())
            fail("Expected redirect limit")
        } catch (expected: IOException) {
            assertEquals("Too many redirects", expected.message)
        }
        assertEquals(21, target.requestCount)
    }

    @Test fun `HEAD read timeout remains five seconds and next SDK attempt can succeed`() {
        val target = server()
        target.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        target.enqueue(MockResponse().setResponseCode(200))
        val started = System.nanoTime()
        assertTrue(AdBlockDetector.detectAll(listOf(target.url("/ad").toString())).single().accessible)
        val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
        assertTrue("elapsed=$elapsed", elapsed >= 4.0 && elapsed < 9.0)
        assertEquals(2, target.requestCount)
    }

    @Test fun `config preserves non error response parsing and rejects HTTP errors`() {
        val config = JSONObject().put("sampleRatio", 0.25).put("transmissionIntervalMs", 1234)
            .put("adblockDetectionUrls", JSONArray()).put("reportEndpoints", JSONArray())
        for (status in listOf(200, 302, 400, 503)) {
            val target = server()
            target.enqueue(MockResponse().setResponseCode(status).setBody(encryptConfig(config)))
            ConfigManager.configure(target.url("/config").toString(), emptyMap())
            val parsed = ConfigManager.fetchConfig()
            if (status < 400) {
                assertNotNull(parsed); assertEquals(0.25, parsed!!.sampleRatio, 0.0)
                assertEquals(1234L, parsed.transmissionIntervalMs)
            } else assertNull(parsed)
            assertEquals(1, target.requestCount)
        }
    }

    @Test fun `config HTTP error does not wait for delayed response body`() {
        val target = server()
        target.enqueue(MockResponse().setResponseCode(500).setBody("unused error body")
            .setBodyDelay(5, TimeUnit.SECONDS))
        ConfigManager.configure(target.url("/config").toString(), emptyMap())
        val started = System.nanoTime()
        assertNull(ConfigManager.fetchConfig())
        val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
        assertTrue("elapsed=$elapsed", elapsed < 2.0)
        assertEquals(1, target.requestCount)
    }

    @Test fun `clients with different timeout policies reuse the same connection pool`() {
        val target = server()
        target.enqueue(MockResponse().setBody("first"))
        target.enqueue(MockResponse().setBody("second"))
        val first = OkHttpClient(timeoutSeconds = 5)
        val second = OkHttpClient(timeoutSeconds = 10)
        assertEquals("first", first.execute(Request.Builder().url(target.url("/first")).build(), readBody = true).body)
        assertEquals("second", second.execute(Request.Builder().url(target.url("/second")).build(), readBody = true).body)
        val firstRequest = target.takeRequest(1, TimeUnit.SECONDS)!!
        val secondRequest = target.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/first", firstRequest.path); assertEquals(0, firstRequest.sequenceNumber)
        assertEquals("/second", secondRequest.path); assertEquals(1, secondRequest.sequenceNumber)
    }

    @Test fun `GET uses caller timeout policy instead of an HTTP method default`() {
        val target = server()
        target.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = OkHttpClient(timeoutSeconds = 1)
        val started = System.nanoTime()
        try {
            client.execute(Request.Builder().url(target.url("/slow")).build(), readBody = true)
            fail("Expected caller-configured read timeout")
        } catch (_: SocketTimeoutException) {
            val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
            assertTrue("elapsed=$elapsed", elapsed >= 0.75 && elapsed < 5.0)
        }
        assertEquals(1, target.requestCount)
        assertEquals("GET", target.takeRequest(1, TimeUnit.SECONDS)!!.method)
    }
}
