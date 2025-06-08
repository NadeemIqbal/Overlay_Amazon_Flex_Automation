package com.nadeem.service_overlay_app

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nadeem.service_overlay_app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.e(TAG, "Overlay permission result received")
        updateStatus()
        if (Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission granted")
            checkAccessibilityPermission()
        } else {
            Log.e(TAG, "Overlay permission denied")
            showToast("Overlay permission is required")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.e(TAG, "onCreate called")

        setupSystemUI()
        initializeViews()
        checkPermissions()
    }

    private fun setupSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        val isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        configureSystemUI(isDarkMode)
    }

    private fun configureSystemUI(isDarkMode: Boolean) {
        val backgroundColor = if (isDarkMode) DARK_THEME_COLOR else LIGHT_THEME_COLOR
        window.apply {
            statusBarColor = Color.parseColor(backgroundColor)
            navigationBarColor = Color.parseColor(backgroundColor)
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    private fun initializeViews() {
        binding.enableAccessibilityButton.setOnClickListener {
            requestAccessibilityPermission()
        }

        // Initialize height percentage from preferences
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedHeightPercentage = prefs.getFloat(KEY_HEIGHT_PERCENTAGE, 93f)
        binding.heightPercentageInput.setText(savedHeightPercentage.toString())

        // Initialize width percentage from preferences
        val savedWidthPercentage = prefs.getFloat(KEY_WIDTH_PERCENTAGE, 75f)
        binding.widthPercentageInput.setText(savedWidthPercentage.toString())

        binding.saveHeightButton.setOnClickListener {
            val heightPercentage = binding.heightPercentageInput.text.toString().toFloatOrNull()
            if (heightPercentage != null && heightPercentage in 0f..100f) {
                prefs.edit().putFloat(KEY_HEIGHT_PERCENTAGE, heightPercentage).apply()
                showToast("Height percentage saved: $heightPercentage%")
            } else {
                showToast("Please enter a valid percentage between 0 and 100")
            }
        }

        binding.saveWidthButton.setOnClickListener {
            val widthPercentage = binding.widthPercentageInput.text.toString().toFloatOrNull()
            if (widthPercentage != null && widthPercentage in 0f..100f) {
                prefs.edit().putFloat(KEY_WIDTH_PERCENTAGE, widthPercentage).apply()
                showToast("Width percentage saved: $widthPercentage%")
            } else {
                showToast("Please enter a valid percentage between 0 and 100")
            }
        }
    }

    private fun checkPermissions() {
        updateStatus()
        if (Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission already granted")
            checkAccessibilityPermission()
        } else {
            Log.e(TAG, "Requesting overlay permission")
            requestOverlayPermission()
        }
    }

    private fun updateStatus() {
        val overlayEnabled = Settings.canDrawOverlays(this)
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        binding.apply {
            accessibilityStatus.text = getString(
                R.string.accessibility_status_format,
                if (accessibilityEnabled) "Enabled" else "Disabled"
            )
            overlayStatus.text = getString(
                R.string.overlay_status_format,
                if (overlayEnabled) "Granted" else "Not Granted"
            )
            enableAccessibilityButton.visibility =
                if (accessibilityEnabled) View.GONE else View.VISIBLE
            instructions.text = buildInstructionsText(overlayEnabled, accessibilityEnabled)
        }
    }

    private fun buildInstructionsText(
        overlayEnabled: Boolean,
        accessibilityEnabled: Boolean
    ): String {
        return buildString {
            append(getString(R.string.instructions_header))
            if (!overlayEnabled) {
                append(getString(R.string.overlay_instruction))
            }
            if (!accessibilityEnabled) {
                append(getString(R.string.accessibility_instruction))
            }
            if (overlayEnabled && accessibilityEnabled) {
                append(getString(R.string.all_permissions_granted))
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun checkAccessibilityPermission() {
        Log.e(TAG, "Checking accessibility permission")
        if (isAccessibilityServiceEnabled()) {
            Log.e(TAG, "Accessibility service enabled, starting overlay service")
            startOverlayService()
        } else {
            Log.e(TAG, "Accessibility service not enabled, requesting permission")
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
            Log.e(TAG, "Accessibility service string: $settingValue")
            return settingValue?.contains(service) == true
        }
        return false
    }

    private fun requestAccessibilityPermission() {
        showToast(getString(R.string.enable_accessibility_message))
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun startOverlayService() {
        Log.e(TAG, "Starting overlay service")
        try {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                putExtra("foregroundServiceType", ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.e(TAG, "Overlay service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting overlay service", e)
            showToast(getString(R.string.error_starting_service, e.message))
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "onResume called")
        updateStatus()
        if (Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
            Log.e(TAG, "Permissions granted, starting service in onResume")
            startOverlayService()
        }
    }

    companion object {
        fun getHeightPercentage(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_HEIGHT_PERCENTAGE, 95f)
        }

        fun getWidthPercentage(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_WIDTH_PERCENTAGE, 75f)
        }

        private const val TAG = "MainActivity"
        private const val DARK_THEME_COLOR = "#1F1F1F"
        private const val LIGHT_THEME_COLOR = "#FFFFFF"
        private const val PREFS_NAME = "OverlaySettings"
        private const val KEY_HEIGHT_PERCENTAGE = "height_percentage"
        private const val KEY_WIDTH_PERCENTAGE = "width_percentage"
    }
}