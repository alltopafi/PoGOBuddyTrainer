package com.pogobuddytrainer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pogobuddytrainer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BuddyTrainingService.ACTION_STATE_UPDATE) {
                val state = intent.getStringExtra(BuddyTrainingService.EXTRA_STATE) ?: "IDLE"
                val stepIndex = intent.getIntExtra(BuddyTrainingService.EXTRA_STEP_INDEX, 1)
                val stepType = intent.getStringExtra(BuddyTrainingService.EXTRA_STEP_TYPE) ?: "TASK"
                val stepMessage = intent.getStringExtra(BuddyTrainingService.EXTRA_STEP_MESSAGE) ?: ""
                val secondsElapsed = intent.getIntExtra(BuddyTrainingService.EXTRA_SECONDS_ELAPSED, 0)
                val secondsTotal = intent.getIntExtra(BuddyTrainingService.EXTRA_SECONDS_TOTAL, 0)

                updateUI(state, stepIndex, stepType, stepMessage, secondsElapsed, secondsTotal)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            binding.cardPermission.visibility = View.GONE
            Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            binding.cardPermission.visibility = View.VISIBLE
            Toast.makeText(this, "Notification permission is required for timer updates.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        checkNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Register receiver for state updates from the service
        val filter = IntentFilter(BuddyTrainingService.ACTION_STATE_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }

        // Query status of running service (if any)
        queryServiceStatus()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(stateReceiver)
    }

    private fun setupClickListeners() {
        binding.btnStartSession.setOnClickListener {
            startTrainingService(BuddyTrainingService.ACTION_START)
        }

        binding.btnAbortSession.setOnClickListener {
            startTrainingService(BuddyTrainingService.ACTION_ABORT_UI)
        }

        binding.btnGrantPermission.setOnClickListener {
            requestNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                binding.cardPermission.visibility = View.GONE
            } else {
                binding.cardPermission.visibility = View.VISIBLE
            }
        } else {
            binding.cardPermission.visibility = View.GONE
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startTrainingService(action: String) {
        val intent = Intent(this, BuddyTrainingService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun queryServiceStatus() {
        val intent = Intent(this, BuddyTrainingService::class.java).apply {
            action = BuddyTrainingService.ACTION_QUERY_STATUS
        }
        // Just call startService; if running, it will reply with broadcast
        startService(intent)
    }

    private fun updateUI(
        state: String,
        stepIndex: Int,
        stepType: String,
        stepMessage: String,
        secondsElapsed: Int,
        secondsTotal: Int
    ) {
        when (state) {
            "TASK", "TIMER" -> {
                binding.tvCurrentState.text = "State: Active"
                binding.tvCurrentState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                binding.tvCurrentStep.text = "Step $stepIndex / 10 ($stepType)"
                binding.tvStepDescription.text = stepMessage

                binding.btnStartSession.visibility = View.GONE
                binding.btnAbortSession.visibility = View.VISIBLE

                if (stepType == "TIMER" && secondsTotal > 0) {
                    binding.layoutProgress.visibility = View.VISIBLE
                    val progress = (secondsElapsed * 100) / secondsTotal
                    binding.progressBar.progress = progress

                    val elapsedMins = secondsElapsed / 60
                    val totalMins = secondsTotal / 60
                    binding.tvProgressFraction.text = "${elapsedMins}m / ${totalMins}m"

                    val minsRemaining = ((secondsTotal - secondsElapsed) + 59) / 60
                    binding.tvTimeRemaining.text = "$minsRemaining mins remaining"
                } else {
                    binding.layoutProgress.visibility = View.GONE
                }
            }
            "COMPLETED" -> {
                binding.tvCurrentState.text = "State: Completed"
                binding.tvCurrentState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                binding.tvCurrentStep.text = "Step 10: Routine Complete!"
                binding.tvStepDescription.text = "Congratulations! Your Buddy is happy."
                binding.layoutProgress.visibility = View.GONE

                binding.btnStartSession.visibility = View.VISIBLE
                binding.btnAbortSession.visibility = View.GONE
            }
            "ABORTED" -> {
                binding.tvCurrentState.text = "State: Aborted"
                binding.tvCurrentState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                binding.tvCurrentStep.text = "Session Terminated"
                binding.tvStepDescription.text = "Trainer session was manually aborted! Got Away!"
                binding.layoutProgress.visibility = View.GONE

                binding.btnStartSession.visibility = View.VISIBLE
                binding.btnAbortSession.visibility = View.GONE
            }
            "MISSED" -> {
                binding.tvCurrentState.text = "State: Missed"
                binding.tvCurrentState.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                binding.tvCurrentStep.text = "Session Terminated"
                binding.tvStepDescription.text = "Task was not completed within 5 minutes! Got Away!"
                binding.layoutProgress.visibility = View.GONE

                binding.btnStartSession.visibility = View.VISIBLE
                binding.btnAbortSession.visibility = View.GONE
            }
            else -> { // IDLE
                binding.tvCurrentState.text = "State: Idle"
                binding.tvCurrentState.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                binding.tvCurrentStep.text = "Step: Not Started"
                binding.tvStepDescription.text = "Press the button below to start tracking your Pokémon Buddy excitement routine."
                binding.layoutProgress.visibility = View.GONE

                binding.btnStartSession.visibility = View.VISIBLE
                binding.btnAbortSession.visibility = View.GONE
            }
        }
    }
}
