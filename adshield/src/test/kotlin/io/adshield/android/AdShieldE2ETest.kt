package io.adshield.android

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class AdShieldE2ETest {

    private lateinit var controlServer: MockWebServer
    private lateinit var adServer: MockWebServer
    private lateinit var eventServer: MockWebServer

    private val originalControlUrl = AdBlockDetector.controlUrl
    private val originalAdUrl = AdBlockDetector.adUrl
    private val originalEndpoint = EventLogger.endpoint

    @Before
    fun setUp() {
        controlServer = MockWebServer()
        adServer = MockWebServer()
        eventServer = MockWebServer()

        controlServer.start()
        adServer.start()
        eventServer.start()

        AdBlockDetector.controlUrl = controlServer.url("/").toString()
        AdBlockDetector.adUrl = adServer.url("/").toString()
        EventLogger.endpoint = eventServer.url("/bq/event").toString()

        AdShield.reset()
    }

    @After
    fun tearDown() {
        AdBlockDetector.controlUrl = originalControlUrl
        AdBlockDetector.adUrl = originalAdUrl
        EventLogger.endpoint = originalEndpoint

        controlServer.shutdown()
        adServer.shutdown()
        eventServer.shutdown()
    }

    @Test
    fun `when ad url reachable, reports no adblock`() {
        controlServer.enqueue(MockResponse().setResponseCode(200))
        adServer.enqueue(MockResponse().setResponseCode(200))
        eventServer.enqueue(MockResponse().setResponseCode(200))

        val context = RuntimeEnvironment.getApplication()
        AdShield.measure(context)
        Thread.sleep(2000)

        val request = eventServer.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull(request)
        val body = request!!.body.readUtf8()
        assertTrue(body.contains("\"is_adblock_detected\":false"))
    }

    @Test
    fun `when ad url blocked, reports adblock detected`() {
        controlServer.enqueue(MockResponse().setResponseCode(200))
        adServer.enqueue(MockResponse().setResponseCode(403))
        eventServer.enqueue(MockResponse().setResponseCode(200))

        val context = RuntimeEnvironment.getApplication()
        AdShield.measure(context)
        Thread.sleep(2000)

        val request = eventServer.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull(request)
        val body = request!!.body.readUtf8()
        assertTrue(body.contains("\"is_adblock_detected\":true"))
    }

    @Test
    fun `when network offline, no event sent`() {
        controlServer.enqueue(MockResponse().setResponseCode(500))

        val context = RuntimeEnvironment.getApplication()
        AdShield.measure(context)
        Thread.sleep(2000)

        val request = eventServer.takeRequest(1, TimeUnit.SECONDS)
        assertNull(request)
    }

    @Test
    fun `event payload contains correct fields`() {
        controlServer.enqueue(MockResponse().setResponseCode(200))
        adServer.enqueue(MockResponse().setResponseCode(200))
        eventServer.enqueue(MockResponse().setResponseCode(200))

        val context = RuntimeEnvironment.getApplication()
        AdShield.measure(context)
        Thread.sleep(2000)

        val request = eventServer.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("POST", request!!.method)
        assertEquals("application/json", request.getHeader("Content-Type"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"table\":\"mobile_measure\""))
        assertTrue(body.contains("\"platform\":\"android\""))
        assertTrue(body.contains("\"event_id\":"))
        assertTrue(body.contains("\"package\":"))
    }

    @Test
    fun `measure only executes once per session`() {
        controlServer.enqueue(MockResponse().setResponseCode(200))
        adServer.enqueue(MockResponse().setResponseCode(200))
        eventServer.enqueue(MockResponse().setResponseCode(200))

        val context = RuntimeEnvironment.getApplication()
        AdShield.measure(context)
        AdShield.measure(context)
        Thread.sleep(2000)

        assertEquals(1, eventServer.requestCount)
    }

    @Test
    fun `event sent to configured endpoint`() {
        controlServer.enqueue(MockResponse().setResponseCode(200))
        adServer.enqueue(MockResponse().setResponseCode(200))
        eventServer.enqueue(MockResponse().setResponseCode(200))

        val context = RuntimeEnvironment.getApplication()
        AdShield.measure(context)
        Thread.sleep(2000)

        val request = eventServer.takeRequest(3, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("/bq/event", request!!.path)
    }
}
