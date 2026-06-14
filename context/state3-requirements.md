There is a bug in the notification it does not appear to be a live notification, in the notification bar you cannot click the icon to view it like I can in other similar live notifications.

Here some reasearch with with instuctions on craeteing a live notifications. 

-----

Previous research:

To create a "live notification" in Android that counts down, you need to create a notification, assign it a specific ID, and repeatedly update that same notification ID in a loop.
Here is a complete, simple Kotlin example using Coroutines to handle the 10-second countdown. It includes the progress bar, the specific title, the message format, and the critical text (using the subtext field).
Kotlin Example: 10-Second Live Notification
Kotlin
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay

suspend fun showCountdownNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "countdown_channel"
    val notificationId = 101
    val maxSeconds = 10

    // 1. Create the Notification Channel (Required for Android 8.0+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Live Notifications",
            NotificationManager.IMPORTANCE_LOW // Low importance prevents sounds on every update
        )
        notificationManager.createNotificationChannel(channel)
    }

    // 2. Initialize the Notification Builder
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_popup_sync) // Replace with your app's icon
        .setContentTitle("live notification test")
        .setOngoing(true) // Prevents the user from swiping it away during the countdown
        .setOnlyAlertOnce(true) // Ensures the phone doesn't vibrate/chime every single second

    // 3. Loop to update the notification every second
    for (remaining in maxSeconds downTo 0) {
        
        // Configure the dynamic parts of the notification
        builder.setContentText("$remaining seconds / ${maxSeconds}s")
               .setSubText(remaining.toString()) // Critical text: Just the number
               .setProgress(maxSeconds, maxSeconds - remaining, false)
        
        // Issue the update
        notificationManager.notify(notificationId, builder.build())
        
        // Wait 1 second before the next update
        if (remaining > 0) {
            delay(1000L)
        }
    }

    // 4. Clean up when finished
    builder.setContentText("Countdown complete!")
           .setSubText("0")
           .setProgress(0, 0, false) // Remove the progress bar
           .setOngoing(false) // Allow the user to dismiss it now
    
    notificationManager.notify(notificationId, builder.build())
}
Key Concepts Explained:
notificationManager.notify(notificationId, builder.build()): Because we use the exact same notificationId (101) inside the loop, Android updates the existing notification rather than spawning a new one. This is what creates the "live" effect.
setSubText(remaining.toString()): This handles your "critical text" requirement. It places the raw number in the small header text area next to the app name/timestamp, giving it visual priority.
setProgress(max, progress, indeterminate): We pass 10 as the max. The current progress is calculated as maxSeconds - remaining so the bar fills up as the time ticks down. If you want the bar to empty out instead, you would just pass remaining as the current progress.
setOnlyAlertOnce(true): This is crucial for live notifications. Without it, the user's phone would vibrate or play a notification sound every single second.
setOngoing(true): This pins the notification to the tray so the user can't accidentally swipe it away while the task is still running.
