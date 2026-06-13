package com.pogobuddytrainer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_COMPLETED || action == ACTION_ABORT) {
            val serviceIntent = Intent(context, BuddyTrainingService::class.java).apply {
                this.action = action
            }
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val ACTION_COMPLETED = "com.pogobuddytrainer.ACTION_COMPLETED"
        const val ACTION_ABORT = "com.pogobuddytrainer.ACTION_ABORT"
    }
}
