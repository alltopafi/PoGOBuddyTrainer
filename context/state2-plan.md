# State 2 Plan: Vibration on New Task Transition

## Goal

When the app moves to a **new TASK step** (i.e., a step requiring user action), the phone should **vibrate** to alert the user — even if the app is in the background. This complements the existing live notification system already in place.

---

## Background & Research

### Android Vibration APIs

Android provides two primary approaches for vibration:

#### 1. `VibrationEffect` + `Vibrator` / `VibratorManager` (API 26+)
- `Vibrator` (deprecated in API 31) and `VibratorManager` (API 31+) are the standard system services.
- `VibrationEffect.createOneShot(durationMs, amplitude)` — single pulse.
- `VibrationEffect.createWaveform(timings, amplitudes, repeat)` — custom pattern.
- Requires `android.permission.VIBRATE` in the manifest.

#### 2. Notification Channel Vibration (recommended for foreground service scenario)
- Android notification channels support built-in vibration via `NotificationChannel.enableVibration(true)` and `.setVibrationPattern(longArrayOf(...))`.
- This vibrates automatically when a **high-priority notification** is posted — no extra code needed beyond channel configuration.
- Works reliably even when the phone is locked or the app is backgrounded.
- **This is the preferred approach** because the app already uses a foreground service with an ongoing notification and a `BroadcastReceiver`.

### Key Insight: Two-Channel Strategy

The current implementation uses a **single notification channel** (`buddy_trainer_channel`) with `IMPORTANCE_DEFAULT` for the ongoing foreground notification that updates every second during timers. Vibrating on every update would be extremely annoying.

The cleanest solution is:
1. **Keep the existing channel** (`buddy_trainer_channel`, `IMPORTANCE_DEFAULT`, no vibration) for the ongoing timer/progress notification.
2. **Create a new "alert" channel** (`buddy_alert_channel`, `IMPORTANCE_HIGH`, vibration enabled) used only when transitioning to a new TASK step.
3. On task transition, post a **separate, auto-cancel alert notification** on the alert channel. Android's notification system will vibrate the device automatically.
4. Optionally, also call the `Vibrator` API directly for immediate haptic feedback (belt-and-suspenders approach).

---

## Required Changes

### 1. `AndroidManifest.xml`

#### Add `VIBRATE` permission
```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

---

### 2. `BuddyTrainingService.kt`

#### A) Add a new `ALERT_CHANNEL_ID` constant
```kotlin
const val ALERT_CHANNEL_ID = "buddy_alert_channel"
const val ALERT_NOTIFICATION_ID = 2001
```

#### B) Create the alert notification channel in `createNotificationChannel()`
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val alertChannel = NotificationChannel(
        ALERT_CHANNEL_ID,
        "Buddy Task Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Vibrates when a new Pokémon Buddy task is ready"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 300, 150, 300) // pause, buzz, pause, buzz
    }
    manager.createNotificationChannel(alertChannel)
}
```

#### C) Add a `vibrateAndAlert()` helper method
```kotlin
private fun vibrateAndAlert(step: Step) {
    // 1. Post a high-priority alert notification (triggers channel vibration)
    val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("⚠️ New Task Ready!")
        .setContentText(step.message.lines().first()) // show first line
        .setStyle(NotificationCompat.BigTextStyle().bigText(step.message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)

    // 2. Also trigger vibrator directly (works pre-Oreo & as belt-and-suspenders)
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
```

#### D) Call `vibrateAndAlert()` inside `moveToNextStep()` when landing on a TASK step
```kotlin
StepType.TASK -> {
    currentState = "TASK"
    startTaskTimeout()
    vibrateAndAlert(nextStep) // <-- NEW
}
```

Also vibrate on the **first step** (session start), inside `startRoutine()`:
```kotlin
private fun startRoutine() {
    // ... existing code ...
    startTaskTimeout()
    vibrateAndAlert(steps[0]) // <-- NEW: alert for step 1
    sendStateBroadcast()
}
```

---

## What Will NOT Change

- The existing `buddy_trainer_channel` ongoing notification and its update loop — no change.
- `MainActivity.kt` — no change needed; vibration is handled entirely in the service.
- `NotificationReceiver.kt` — no change needed.
- All existing step data, timer logic, and broadcast logic — unchanged.

---

## Required Imports to Add to `BuddyTrainingService.kt`

```kotlin
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.VibratorManager  // API 31+
```

---

## Vibration Pattern Rationale

| Segment | Duration (ms) | Description |
|---------|---------------|-------------|
| 0       | 0             | No initial delay |
| 300     | 300           | First buzz |
| 150     | 150           | Brief pause |
| 300     | 300           | Second buzz |

This creates a distinctive double-pulse "da-dum" pattern that is clearly noticeable without being obnoxious.

---

## Steps Where Vibration Triggers

| Step | Type | Vibrates? |
|------|------|-----------|
| 1    | TASK | ✅ Yes (session start) |
| 2    | TIMER | ❌ No |
| 3    | TASK | ✅ Yes |
| 4    | TIMER | ❌ No |
| 5    | TASK | ✅ Yes |
| 6    | TIMER | ❌ No |
| 7    | TASK | ✅ Yes |
| 8    | TIMER | ❌ No |
| 9    | TASK | ✅ Yes |
| 10   | COMPLETED | ❌ No (completion notification already has its own alert) |

---

## Verification Plan

1. Build the app and install on a physical device (vibration does not work in emulators).
2. Start a session → phone should vibrate (double-pulse) and a "New Task Ready!" notification appears.
3. Press "Completed" on a TASK step → wait for timer to expire → phone should vibrate again on the next TASK step.
4. Confirm NO vibration occurs during the timer countdown ticks.
5. Confirm the ongoing foreground notification continues to update normally without vibration.
6. Test with phone in silent/DnD mode — vibration from `Vibrator` API will still fire; notification channel vibration respects DnD settings (expected Android behavior).
