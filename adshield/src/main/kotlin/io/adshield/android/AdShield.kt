package io.adshield.android

import android.content.Context
import android.util.Log

object AdShield {

    private const val TAG = "AdShield"
    @Volatile
    private var measured = false

    @JvmStatic
    fun measure(context: Context) {
        if (measured) return
        measured = true

        val packageName = context.packageName

        Thread {
            try {
                val result = AdBlockDetector.detect()
                if (result == null) {
                    Log.d(TAG, "Network offline, skipping")
                    return@Thread
                }
                Log.d(TAG, "Adblock detected: $result")
                EventLogger.log(
                    packageName = packageName,
                    platform = "android",
                    isAdBlockDetected = result,
                )
            } catch (e: Exception) {
                Log.e(TAG, "measure failed", e)
            }
        }.start()
    }
}
