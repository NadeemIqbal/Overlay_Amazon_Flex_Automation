package com.nadeem.service_overlay_app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var isClickingJob = false
    private var isClickingAccept = false



    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                val accessibilityService = OverlayAccessibilityService.getInstance()
                if (accessibilityService != null) {
                    // Trigger the new workflow in OverlayAccessibilityService
                    accessibilityService.handleJobFlow()
                    // Schedule next check after a short delay
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupFloatingView()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Overlay Service")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupFloatingView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // Make the view draggable
        var initialX: Int = 0
        var initialY: Int = 0
        var initialTouchX: Float = 0f
        var initialTouchY: Float = 0f

        // Set up the toggle button click listener first
        val toggleButton = floatingView?.findViewById<ImageButton>(R.id.toggleButton)
        toggleButton?.setOnClickListener {
            isRunning = !isRunning
            toggleButton.setImageResource(
                if (isRunning) R.drawable.ic_stop
                else R.drawable.ic_play
            )
            OverlayAccessibilityService.getInstance()?.setServiceRunning(isRunning)
            if (isRunning) {
                handler.post(refreshRunnable)
            }
        }


        // wakeLock
        val wakeLock: PowerManager.WakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OverlayService::MyWakelockTag").apply {
                    acquire()
                }
            }

        // Make the entire view draggable
        floatingView?.setOnTouchListener { view, event ->
            Log.e("OverlayService", "Touch event: ${event.action}")
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.e("OverlayService", "ACTION_DOWN")
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    Log.e("OverlayService", "ACTION_MOVE: x=${event.rawX}, y=${event.rawY}")
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    params.x = newX
                    params.y = newY
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.e("OverlayService", "Error updating layout: ${e.message}")
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    Log.e("OverlayService", "ACTION_UP")
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    // Get screen width
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    
                    // Snap to nearest edge
                    params.x = if (params.x < screenWidth / 2) 0 else screenWidth - view.width
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.e("OverlayService", "Error updating layout: ${e.message}")
                    }
                    false
                }

                else -> false
            }
        }

        // Add the view to window manager
        try {
            windowManager?.addView(floatingView, params)
            Log.e("OverlayService", "View added to window manager")
        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding view: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(refreshRunnable)
        if (floatingView != null && windowManager != null) {
            windowManager?.removeView(floatingView)
        }
    }

    companion object {
        private const val CHANNEL_ID = "OverlayServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
} 