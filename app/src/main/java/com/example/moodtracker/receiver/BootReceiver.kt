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
                    // Start the mood tracking via WorkManager when the device boots up
                    MoodCheckWorker.schedule(context.applicationContext, true)
                } finally {
                    // Must call finish() so the BroadcastReceiver can be recycled
                    pendingResult.finish()
                }
            }
        }
    }
}