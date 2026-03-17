package io.adshield.android

import android.content.Context

object AdShield {

    @JvmStatic
    fun configure(endpoint: String) {
        ConfigManager.configure(endpoint)
    }

    @JvmStatic
    fun measure(context: Context) {
        val appContext = context.applicationContext

        Thread {
            try {
                if (!ConfigManager.isAllowed(appContext)) return@Thread

                val config = ConfigManager.fetchConfig()
                if (config == null) {
                    ConfigManager.scheduleNext(appContext, ConfigManager.FAILURE_BACKOFF_MS)
                    return@Thread
                }

                if (Math.random() >= config.sampleRatio) {
                    ConfigManager.scheduleNext(appContext, config.transmissionIntervalMs)
                    return@Thread
                }

                val results = AdBlockDetector.detectAll(config.adblockDetectionUrls)

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
                )

                ConfigManager.scheduleNext(appContext, config.transmissionIntervalMs)
            } catch (_: Exception) {
            }
        }.start()
    }
}
