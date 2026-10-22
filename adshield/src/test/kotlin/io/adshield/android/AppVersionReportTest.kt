package io.adshield.android

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 28])
class AppVersionReportTest {
    private lateinit var context: Context
    private lateinit var reportServer: MockWebServer

    @Before
    @Suppress("DEPRECATION")
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName = "2.4.6-host"
        info.versionCode = 246
        shadowOf(context.packageManager).installPackage(info)
        reportServer = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        reportServer.shutdown()
    }

    @Test
    fun `report contains host app versions separately from SDK version`() {
        val payload = sendReport()
        val kv = payload.getJSONObject("kv")

        assertEquals("2.4.6-host", kv.getString("app_version"))
        assertEquals("246", kv.getString("app_version_code"))
        assertEquals(context.getString(R.string.adshield_sdk_version), payload.getString("sdkVersion"))
        assertNotEquals(kv.getString("app_version"), payload.getString("sdkVersion"))
        assertEquals(context.packageName, payload.getString("bundleId"))
        assertFalse(payload.has("app_version"))
        assertFalse(payload.has("app_version_code"))
    }

    @Test
    @Config(sdk = [28])
    fun `report preserves version code beyond 32 bits as decimal string`() {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.longVersionCode = 4_294_967_337L
        shadowOf(context.packageManager).installPackage(info)

        val kv = sendReport().getJSONObject("kv")

        assertEquals("4294967337", kv.get("app_version_code"))
        assertEquals("2.4.6-host", kv.getString("app_version"))
    }

    @Test
    fun `null or blank name is omitted while code and caller metadata survive`() {
        for (name in listOf(null, "", " \t")) {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName = name
            shadowOf(context.packageManager).installPackage(info)

            val kv = sendReport(kv = mapOf("segment" to "synthetic")).getJSONObject("kv")

            assertFalse(kv.has("app_version"))
            assertEquals("246", kv.getString("app_version_code"))
            assertEquals("synthetic", kv.getString("segment"))
        }
    }

    @Test
    fun `missing package omits automatic metadata and still sends report`() {
        val missingPackageContext = object : ContextWrapper(context) {
            override fun getPackageName(): String = "test.missing.application"
        }
        assertThrows(PackageManager.NameNotFoundException::class.java) {
            context.packageManager.getPackageInfo(missingPackageContext.packageName, 0)
        }

        val payload = sendReport(missingPackageContext, mapOf("segment" to "synthetic"))
        val kv = payload.getJSONObject("kv")

        assertEquals(1, kv.length())
        assertEquals("synthetic", kv.getString("segment"))
        assertEquals("android", payload.getString("platform"))
        assertEquals(context.getString(R.string.adshield_sdk_version), payload.getString("sdkVersion"))
    }

    @Test
    fun `explicit caller versions take precedence without changing the caller map`() {
        val callerKv = Collections.unmodifiableMap(
            linkedMapOf("app_version" to "caller-name", "app_version_code" to "caller-code", "segment" to "synthetic")
        )
        val before = callerKv.toMap()

        val kv = sendReport(kv = callerKv).getJSONObject("kv")

        assertEquals("caller-name", kv.getString("app_version"))
        assertEquals("caller-code", kv.getString("app_version_code"))
        assertEquals("synthetic", kv.getString("segment"))
        assertEquals(3, kv.length())
        assertEquals(before, callerKv)
    }

    private fun sendReport(reportContext: Context = context, kv: Map<String, String> = emptyMap()): JSONObject {
        reportServer.enqueue(MockResponse().setResponseCode(204))
        EventLogger.log(
            context = reportContext,
            endpoints = listOf(reportServer.url("/report").toString()),
            deviceId = "synthetic-test-device",
            bundleId = reportContext.packageName,
            osVersion = "test-os",
            locale = "en_US",
            results = emptyList(),
            sampleRatio = 1.0,
            transmissionIntervalMs = 60_000L,
            kv = kv,
        )
        val request = reportServer.takeRequest(1, TimeUnit.SECONDS)
            ?: throw AssertionError("Expected a local report POST")
        assertEquals("POST", request.method)
        assertEquals("/report", request.path)
        return JSONObject(request.body.readUtf8())
    }
}
