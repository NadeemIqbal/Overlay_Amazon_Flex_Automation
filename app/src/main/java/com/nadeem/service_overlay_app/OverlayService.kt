package com.nadeem.service_overlay_app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat

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
                    when {
                        // If we're in the middle of clicking a job, wait
                        isClickingJob -> {
                            handler.postDelayed(this, 500)
                        }
                        // If we're in the middle of clicking accept, wait
                        isClickingAccept -> {
                            handler.postDelayed(this, 500)
                        }
                        // If there are jobs, click the first one and then accept
                        accessibilityService.hasJobsNow() -> {
                            val jobClicked = accessibilityService.tryClickFirstJob()
                            if (jobClicked) {
                                // Wait a bit for the job details to load
                                handler.postDelayed({
                                    accessibilityService.tryClickAcceptButton()
                                }, 1000)
                            }
                            handler.postDelayed(this, 500)
                        }
                        // If no jobs, click refresh
                        else -> {
                            accessibilityService.tryClickRefreshButton()
                            handler.postDelayed(this, 500)
                        }
                    }
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

        // Make the view draggable
        var initialX: Int = 0
        var initialY: Int = 0
        var initialTouchX: Float = 0f
        var initialTouchY: Float = 0f

        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }

                else -> false
            }
        }

        windowManager?.addView(floatingView, params)
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