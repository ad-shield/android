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
                val intervalMs = ConfigManager.getLastIntervalMs(appContext)
                if (!ConfigManager.shouldTransmit(appContext, intervalMs)) return@Thread

                val config = ConfigManager.fetchConfig() ?: return@Thread

                ConfigManager.saveIntervalMs(appContext, config.transmissionIntervalMs)

                if (Math.random() >= config.sampleRatio) {
                    ConfigManager.recordTransmission(appContext)
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

                ConfigManager.recordTransmission(appContext)
            } catch (_: Exception) {
            }
        }.start()
    }
}
