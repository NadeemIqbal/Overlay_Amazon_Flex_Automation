package com.nadeem.service_overlay_app

import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {
    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var instructions: TextView
    private lateinit var enableAccessibilityButton: Button

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.e("MainActivity", "Overlay permission result received")
        updateStatus()
        if (Settings.canDrawOverlays(this)) {
            Log.e("MainActivity", "Overlay permission granted")
            checkAccessibilityPermission()
        } else {
            Log.e("MainActivity", "Overlay permission denied")
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.e("MainActivity", "onCreate called")

        // Configure system UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        
        // Get the current theme mode
        val isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        
        // Set status bar and navigation bar colors based on theme
        if (isDarkMode) {
            // Use a slightly lighter dark color for better visibility
            window.statusBarColor = Color.parseColor("#1F1F1F")
            window.navigationBarColor = Color.parseColor("#1F1F1F")
            
            // Ensure system UI elements are visible in dark mode
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            
            // Use light icons for dark theme
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        } else {
            window.statusBarColor = Color.parseColor("#FFFFFF")
            window.navigationBarColor = Color.parseColor("#FFFFFF")
            
            // Ensure system UI elements are visible in light mode
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            
            // Use dark icons for light theme
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }

        // Initialize views
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        overlayStatus = findViewById(R.id.overlayStatus)
        instructions = findViewById(R.id.instructions)
        enableAccessibilityButton = findViewById(R.id.enableAccessibilityButton)

        // Set up button click listener
        enableAccessibilityButton.setOnClickListener {
            requestAccessibilityPermission()
        }

        updateStatus()

        if (Settings.canDrawOverlays(this)) {
            Log.e("MainActivity", "Overlay permission already granted")
            checkAccessibilityPermission()
        } else {
            Log.e("MainActivity", "Requesting overlay permission")
            requestOverlayPermission()
        }
    }

    private fun updateStatus() {
        val overlayEnabled = Settings.canDrawOverlays(this)
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        // Update status texts
        accessibilityStatus.text = "Accessibility Service: ${if (accessibilityEnabled) "Enabled" else "Disabled"}"
        overlayStatus.text = "Overlay Permission: ${if (overlayEnabled) "Granted" else "Not Granted"}"

        // Update button visibility
        enableAccessibilityButton.visibility = if (accessibilityEnabled) View.GONE else View.VISIBLE

        // Update instructions
        val instructionsText = buildString {
            append("To enable the service:\n\n")
            if (!overlayEnabled) {
                append("1. Grant overlay permission by clicking the button below\n")
            }
            if (!accessibilityEnabled) {
                append("2. Click the 'Enable Accessibility Service' button above\n")
            }
            if (overlayEnabled && accessibilityEnabled) {
                append("All permissions are granted. The service should be running.")
            }
        }
        instructions.text = instructionsText
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun checkAccessibilityPermission() {
        Log.e("MainActivity", "Checking accessibility permission")
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        if (accessibilityEnabled) {
            Log.e("MainActivity", "Accessibility service enabled, starting overlay service")
            startOverlayService()
        } else {
            Log.e("MainActivity", "Accessibility service not enabled, requesting permission")
            requestAccessibilityPermission()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }

        if (accessibilityEnabled == 1) {
            val service = "${packageName}/${OverlayAccessibilityService::class.java.canonicalName}"
            val settingValue = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            Log.e("MainActivity", "Accessibility service string: $settingValue")
            return settingValue?.contains(service) == true
        }
        return false
    }

    private fun requestAccessibilityPermission() {
        Toast.makeText(
            this,
            "Please enable accessibility service for this app",
            Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startOverlayService() {
        Log.e("MainActivity", "Starting overlay service")
        try {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                putExtra("foregroundServiceType", ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.e("MainActivity", "Overlay service started successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting overlay service", e)
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.e("MainActivity", "onResume called")
        updateStatus()
        // Check if we need to start the service when returning from settings
        if (Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
            Log.e("MainActivity", "Permissions granted, starting service in onResume")
            startOverlayService()
        }
    }
}