Goal Description
Update the Pokémon Buddy Trainer notification to utilize Android's new "Live Update" (Promoted Ongoing) API introduced in Android 16. This will ensure the notification appears prominently in the status bar (as a chip) and lock screen, and behaves as a native "live notification" for time-sensitive, trackable events.

User Review Required
Please review this plan. This utilizes the newest Android API features for live updates. On devices running older versions of Android, these extras will simply be ignored, and the notification will gracefully fall back to the standard ongoing notification behavior.

Proposed Changes
app/src/main/AndroidManifest.xml
To qualify as a Live Update, the app must request the specific permission for promoted notifications.

[MODIFY] AndroidManifest.xml
Add <uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />.
app/src/main/java/com/pogobuddytrainer/BuddyTrainingService.kt
We need to attach the correct metadata flag to opt the notification into the Live Update system.

[MODIFY] BuddyTrainingService.kt
In buildTrainingNotification(), add builder.extras.putBoolean("android.requestPromotedOngoing", true).
We will keep .setOngoing(true) and .setContentTitle("Pokemon Buddy Trainer") as they are already correctly implemented and required by the Live Update API.
The channel importance will remain IMPORTANCE_LOW, which satisfies the API requirements (the only restriction is that it cannot be IMPORTANCE_MIN).
Verification Plan
Automated Tests
Build the APK using the confirmed working JDK: JAVA_HOME="/opt/homebrew/opt/openjdk@17" ./gradlew assembleDebug
Manual Verification
Install the newly built APK on a device or emulator.
Start the trainer service and verify that the notification is promoted. Note that full "Live Update" status chip behavior will specifically trigger on Android 16+ devices, while older devices will show the standard ongoing UI.