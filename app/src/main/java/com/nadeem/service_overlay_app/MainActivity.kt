package com.nadeem.service_overlay_app

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkAccessibilityPermission()
        } else {
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Settings.canDrawOverlays(this)) {
            checkAccessibilityPermission()
        } else {
            requestOverlayPermission()
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
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        if (accessibilityEnabled) {
            startOverlayService()
        } else {
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
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra("foregroundServiceType", ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
        startForegroundService(serviceIntent)
        finish()
    }
}