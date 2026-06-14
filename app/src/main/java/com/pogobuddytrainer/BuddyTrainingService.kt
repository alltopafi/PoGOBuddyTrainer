package com.pogobuddytrainer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class BuddyTrainingService : Service() {

    enum class StepType { TASK, TIMER, COMPLETED }

    data class Step(
        val id: Int,
        val type: StepType,
        val message: String,
        val criticalText: String,
        val durationMinutes: Int = 0
    )

    private val steps = listOf(
        Step(1, StepType.TASK, "1. Take a snapshot 📸\n2. Feed all berries 🍓\n3. Play with buddy 🛝\n4. Battle once. ⚔️", "Task!"),
        Step(2, StepType.TIMER, "Next Step: Snapshot Only 📸", "minutes remaining", 15),
        Step(3, StepType.TASK, "Snapshot Only 📸", "Task!"),
        Step(4, StepType.TIMER, "Next Step: 1 berry 🍓", "minutes remaining", 15),
        Step(5, StepType.TASK, "1. Take a snapshot 📸\n2. Feed 1 berry 🍓\n3. Play with buddy 🛝\n4. Battle once. ⚔️", "Task!"),
        Step(6, StepType.TIMER, "Next Step 2 berries 🍓", "minutes remaining", 30),
        Step(7, StepType.TASK, "1. Take a snapshot 📸\n2. Feed 2 berries 🍓\n3. Play with buddy 🛝\n4. Battle once. ⚔️", "Task!"),
        Step(8, StepType.TIMER, "Next Step: 1 berry 🍓", "minutes remaining", 30),
        Step(9, StepType.TASK, "1. Take a snapshot 📸\n2. Feed 1 berry 🍓\n3. Play with buddy 🛝\n4. Battle once. ⚔️", "Task!"),
        Step(10, StepType.COMPLETED, "Congratulations, another buddy is happy!", "Completed!")
    )

    private var currentStepIndex = 0
    private var currentState = "IDLE" // IDLE, TASK, TIMER, COMPLETED, ABORTED, MISSED

    // Timer state
    private var secondsElapsed = 0
    private var secondsTotal = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    // Task 5-minute timeout state
    private var taskTimeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startRoutine()
            NotificationReceiver.ACTION_COMPLETED -> handleCompletedAction()
            NotificationReceiver.ACTION_ABORT, ACTION_ABORT_UI -> abortRoutine(isMissed = false)
            ACTION_QUERY_STATUS -> {
                if (currentState == "IDLE") {
                    stopServiceInternal()
                } else {
                    sendStateBroadcast()
                }
            }
            ACTION_STOP_SERVICE -> stopServiceInternal()
        }
        return START_NOT_STICKY
    }

    private fun startRoutine() {
        if (currentState != "IDLE" && currentState != "ABORTED" && currentState != "MISSED" && currentState != "COMPLETED") {
            return
        }
        currentStepIndex = 0
        currentState = "TASK"
        secondsElapsed = 0
        secondsTotal = 0

        // Start as Foreground Service
        val notification = buildTrainingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startTaskTimeout()
        vibrateAndAlert(steps[0])
        sendStateBroadcast()
    }

    private fun handleCompletedAction() {
        if (currentState != "TASK") return
        moveToNextStep()
    }

    private fun moveToNextStep() {
        stopTimer()
        stopTaskTimeout()

        if (currentStepIndex < steps.size - 1) {
            currentStepIndex++
            val nextStep = steps[currentStepIndex]
            secondsElapsed = 0

            when (nextStep.type) {
                StepType.TASK -> {
                    currentState = "TASK"
                    startTaskTimeout()
                    vibrateAndAlert(nextStep)
                }
                StepType.TIMER -> {
                    currentState = "TIMER"
                    secondsTotal = nextStep.durationMinutes * 60
                    startTimer()
                }
                StepType.COMPLETED -> {
                    // Fallthrough, should not happen since step 10 is handled
                }
            }
            updateNotification()
            sendStateBroadcast()
        } else {
            completeRoutine()
        }
    }

    private fun vibrateAndAlert(step: Step) {
        // 1. Post high-priority alert notification (triggers channel vibration)
        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚠️ New Task Ready!")
            .setContentText(step.message.lines().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(step.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)

        // 2. Vibrate directly
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 300, 150, 300), -1)
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (secondsElapsed < secondsTotal) {
                    secondsElapsed++
                    updateNotification()
                    sendStateBroadcast()
                    mainHandler.postDelayed(this, 1000)
                } else {
                    // Timer complete, auto-advance to next step
                    moveToNextStep()
                }
            }
        }
        mainHandler.postDelayed(timerRunnable!!, 1000)
    }

    private fun stopTimer() {
        timerRunnable?.let { mainHandler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun startTaskTimeout() {
        // Enforce 5-minute timeout (300 seconds)
        taskTimeoutRunnable = Runnable {
            abortRoutine(isMissed = true)
        }
        mainHandler.postDelayed(taskTimeoutRunnable!!, 300000) // 5 minutes in ms
    }

    private fun stopTaskTimeout() {
        taskTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        taskTimeoutRunnable = null
    }

    private fun completeRoutine() {
        currentState = "COMPLETED"
        currentStepIndex = steps.size - 1 // Last step (Step 10)
        stopTimer()
        stopTaskTimeout()

        // Post completed notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val compNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("🎉 Routine Complete!")
            .setContentText("Congratulations, another buddy is happy!")
            .setSubText("Completed!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, compNotification)

        sendStateBroadcast()
        stopServiceInternal()
    }

    private fun abortRoutine(isMissed: Boolean) {
        currentState = if (isMissed) "MISSED" else "ABORTED"
        stopTimer()
        stopTaskTimeout()

        // Post abort notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val abortNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle("❌ Pokémon Buddy Guide")
            .setContentText("Trainer session was manually aborted!")
            .setSubText("Got Away!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, abortNotification)

        sendStateBroadcast()
        stopServiceInternal()
    }

    private fun stopServiceInternal() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val notification = buildTrainingNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildTrainingNotification(): Notification {
        val step = steps[currentStepIndex]
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pokemon Buddy Trainer")
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        builder.extras.putBoolean("android.requestPromotedOngoing", true)

        // Intent to open MainActivity on click
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(openPendingIntent)

        // Actions
        val abortIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_ABORT
        }
        val abortPendingIntent = PendingIntent.getBroadcast(
            this, 1, abortIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Abort", abortPendingIntent)

        when (step.type) {
            StepType.TASK -> {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(step.message))
                builder.setContentText(step.message.replace("\n", "  "))
                builder.setSubText(step.criticalText)

                val completedIntent = Intent(this, NotificationReceiver::class.java).apply {
                    action = NotificationReceiver.ACTION_COMPLETED
                }
                val completedPendingIntent = PendingIntent.getBroadcast(
                    this, 2, completedIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.checkbox_on_background, "Completed", completedPendingIntent)
            }
            StepType.TIMER -> {
                val minsRemaining = ((secondsTotal - secondsElapsed) + 59) / 60
                val criticalText = "$minsRemaining mins remaining"
                val elapsedMins = secondsElapsed / 60
                val totalMins = secondsTotal / 60
                
                builder.setContentText("${step.message} ($elapsedMins m / $totalMins m)")
                builder.setSubText(criticalText)
                builder.setProgress(secondsTotal, secondsElapsed, false)
            }
            StepType.COMPLETED -> {
                builder.setContentText(step.message)
                builder.setSubText(step.criticalText)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Pokemon Buddy Trainer Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground channel for Pokémon Buddy excitement scheduling"
                enableVibration(false)
                vibrationPattern = null
            }
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Buddy Task Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Vibrates when a new Pokémon Buddy task is ready"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun sendStateBroadcast() {
        val step = steps[currentStepIndex]
        val intent = Intent(ACTION_STATE_UPDATE).apply {
            putExtra(EXTRA_STATE, currentState)
            putExtra(EXTRA_STEP_INDEX, currentStepIndex + 1)
            putExtra(EXTRA_STEP_TYPE, step.type.name)
            putExtra(EXTRA_STEP_MESSAGE, step.message)
            putExtra(EXTRA_CRITICAL_TEXT, step.criticalText)
            putExtra(EXTRA_SECONDS_ELAPSED, secondsElapsed)
            putExtra(EXTRA_SECONDS_TOTAL, secondsTotal)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        stopTimer()
        stopTaskTimeout()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "buddy_trainer_channel"
        const val ALERT_CHANNEL_ID = "buddy_alert_channel"
        const val ALERT_NOTIFICATION_ID = 2001

        // Actions
        const val ACTION_START = "com.pogobuddytrainer.ACTION_START"
        const val ACTION_ABORT_UI = "com.pogobuddytrainer.ACTION_ABORT_UI"
        const val ACTION_QUERY_STATUS = "com.pogobuddytrainer.ACTION_QUERY_STATUS"
        const val ACTION_STOP_SERVICE = "com.pogobuddytrainer.ACTION_STOP_SERVICE"

        // Broadcast actions & extras
        const val ACTION_STATE_UPDATE = "com.pogobuddytrainer.ACTION_STATE_UPDATE"
        const val EXTRA_STATE = "extra_state"
        const val EXTRA_STEP_INDEX = "extra_step_index"
        const val EXTRA_STEP_TYPE = "extra_step_type"
        const val EXTRA_STEP_MESSAGE = "extra_step_message"
        const val EXTRA_CRITICAL_TEXT = "extra_critical_text"
        const val EXTRA_SECONDS_ELAPSED = "extra_seconds_elapsed"
        const val EXTRA_SECONDS_TOTAL = "extra_seconds_total"
    }
}
