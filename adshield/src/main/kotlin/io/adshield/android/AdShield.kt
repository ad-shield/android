package io.adshield.android

import android.content.Context
import android.util.Log

object AdShield {

    private const val TAG = "AdShield"

    @JvmStatic
    fun configure(endpoint: String) {
        ConfigManager.configure(endpoint)
        Log.d(TAG, "Configured with endpoint: $endpoint")
    }

    @JvmStatic
    fun measure(context: Context) {
        val appContext = context.applicationContext

        Thread {
            try {
                val intervalMs = ConfigManager.getLastIntervalMs(appContext)
                if (!ConfigManager.shouldTransmit(appContext, intervalMs)) {
                    Log.d(TAG, "Skipping: transmissionIntervalMs not elapsed")
                    return@Thread
                }

                val config = ConfigManager.fetchConfig()
                if (config == null) {
                    Log.e(TAG, "Failed to fetch config, aborting")
                    return@Thread
                }
                Log.d(TAG, "Config fetched: ${config.adblockDetectionUrls.size} URLs to test")

                ConfigManager.saveIntervalMs(appContext, config.transmissionIntervalMs)

                if (Math.random() >= config.sampleRatio) {
                    Log.d(TAG, "Skipping: not sampled (sampleRatio=${config.sampleRatio})")
                    ConfigManager.recordTransmission(appContext)
                    return@Thread
                }

                val results = AdBlockDetector.detectAll(config.adblockDetectionUrls)
                for (r in results) {
                    Log.d(TAG, "  ${r.url} -> accessible=${r.accessible}")
                }

                EventLogger.log(
                    endpoints = config.reportEndpoints,
                    deviceId = DeviceInfo.getDeviceId(appContext),
                    bundleId = DeviceInfo.getBundleId(appContext),
                    osVersion = DeviceInfo.getOsVersion(),
                    locale = DeviceInfo.getLocale(),
                    results = results,
                )

                ConfigManager.recordTransmission(appContext)
                Log.d(TAG, "Measure complete")
            } catch (e: Exception) {
                Log.e(TAG, "measure failed", e)
            }
        }.start()
    }
}
