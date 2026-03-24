package io.adshield.android

import android.content.Context
import android.util.Log

object AdShield {

    private const val TAG = "AdShield"

    @JvmStatic
    fun configure(endpoint: String, kv: Map<String, String> = emptyMap()) {
        ConfigManager.configure(endpoint, kv)
    }

    @JvmStatic
    fun measure(context: Context) {
        val appContext = context.applicationContext

        Thread {
            try {
                if (!ConfigManager.isAllowed(appContext)) {
                    Log.d(TAG, "Skipping: throttled")
                    return@Thread
                }

                val config = ConfigManager.fetchConfig()
                if (config == null) {
                    Log.d(TAG, "Skipping: config fetch failed, backing off 24h")
                    ConfigManager.scheduleNext(appContext, ConfigManager.FAILURE_BACKOFF_MS)
                    return@Thread
                }

                if (Math.random() >= config.sampleRatio) {
                    Log.d(TAG, "Skipping transmission: not sampled (sampleRatio=${config.sampleRatio})")
                    ConfigManager.scheduleNext(appContext, config.transmissionIntervalMs)
                    return@Thread
                }

                if (config.adblockDetectionUrls.isEmpty()) {
                    Log.d(TAG, "Skipping: no detection URLs configured")
                    ConfigManager.scheduleNext(appContext, config.transmissionIntervalMs)
                    return@Thread
                }

                val results = AdBlockDetector.detectAll(config.adblockDetectionUrls)
                val accessible = results.count { it.accessible }
                val blocked = results.size - accessible
                Log.d(TAG, "Detection complete: $accessible accessible, $blocked blocked out of ${results.size} URLs")

                EventLogger.log(
                    context = appContext,
                    endpoints = config.reportEndpoints,
                    deviceId = DeviceInfo.getDeviceId(appContext),
                    bundleId = DeviceInfo.getBundleId(appContext),
                    osVersion = DeviceInfo.getOsVersion(),
                    locale = DeviceInfo.getLocale(),
                    results = results,
                    sampleRatio = config.sampleRatio,
                    transmissionIntervalMs = config.transmissionIntervalMs,
                    kv = ConfigManager.kv,
                )
                Log.d(TAG, "Event sent successfully to ${config.reportEndpoints.size} endpoint(s)")

                ConfigManager.scheduleNext(appContext, config.transmissionIntervalMs)
            } catch (e: Throwable) {
                Log.e(TAG, "measure failed", e)
            }
        }.start()
    }
}
