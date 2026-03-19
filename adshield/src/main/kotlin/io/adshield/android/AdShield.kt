package io.adshield.android

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting

object AdShield {

    private const val TAG = "AdShield"
    @Volatile
    private var measured = false

    @VisibleForTesting
    internal fun reset() { measured = false }

    @JvmStatic
    fun measure(context: Context) {
        if (measured) {
            Log.d(TAG, "Skipping: already measured this session")
            return
        }
        measured = true

        val packageName = context.packageName

        Thread {
            try {
                val result = AdBlockDetector.detect()
                if (result == null) {
                    Log.d(TAG, "Skipping transmission: network offline (control probe failed)")
                    return@Thread
                }
                Log.d(TAG, "Detection result: adblock_detected=$result")
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
