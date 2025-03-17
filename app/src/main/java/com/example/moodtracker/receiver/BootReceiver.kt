package com.example.moodtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.moodtracker.worker.MoodCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver to restart the mood tracking after device reboot
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Ensure we're not doing network operations on the main thread
            val pendingResult = goAsync()

            // Use a coroutine to schedule work
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // Use scheduleCheck with isBoot=true
                    // It will internally check if tracking was active
                    MoodCheckWorker.scheduleCheck(
                        context.applicationContext,
                        isImmediate = false,
                        isBoot = true
                    )
                } finally {
                    // Must call finish() so the BroadcastReceiver can be recycled
                    pendingResult.finish()
                }
            }
        }
    }
}