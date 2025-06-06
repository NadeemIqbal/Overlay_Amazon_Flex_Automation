package com.nadeem.service_overlay_app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class OverlayAccessibilityService : AccessibilityService() {
    private var isServiceRunning = false
    private var acceptButtonBounds: Rect? = null
    private var hasJobs = false
    private var firstJobBounds: Rect? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d("OverlayService", "Event received: ${event.eventType}, package: ${event.packageName}")
        if (!isServiceRunning) return

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.e("OverlayService", "rootInActiveWindow is null")
            return
        }
        // Find accept button
        val acceptButton = findAcceptButton(rootNode)
        if (acceptButton != null) {
            acceptButtonBounds = Rect()
            acceptButton.getBoundsInScreen(acceptButtonBounds)
        }
        // Check for jobs
        val jobs = findJobs(rootNode)
        hasJobs = jobs.isNotEmpty()
        if (hasJobs) {
            firstJobBounds = Rect()
            jobs[0].getBoundsInScreen(firstJobBounds)
            Log.d("OverlayService", "Found ${jobs.size} jobs")
        } else {
            firstJobBounds = null
            Log.d("OverlayService", "No jobs found")
        }
    }

    /**
     * Attempts to click the refresh button. Returns true if found and click attempted.
     */
    fun tryClickRefreshButton(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.e("OverlayService", "rootInActiveWindow is null")
            return false
        }
        val button = findRefreshButton(rootNode)
        if (button == null) {
            Log.d("OverlayService", "No refresh button found")
            return false
        }
        val bounds = Rect()
        button.getBoundsInScreen(bounds)
        Log.d(
            "OverlayService",
            "Refresh button: text=${button.text}, clickable=${button.isClickable}, enabled=${button.isEnabled}, visible=${button.isVisibleToUser}, bounds=$bounds"
        )
        // Try performAction first
        val actionResult = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d("OverlayService", "ACTION_CLICK result: $actionResult")
        if (!actionResult) {
            performClick(bounds.centerX(), bounds.centerY())
            Log.d("OverlayService", "Falling back to gesture click at: $bounds")
        }
        return true
    }

    private fun findRefreshButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomThreshold = screenHeight * 0.8 // Consider bottom 20% of screen
        val refreshNodes = root.findAccessibilityNodeInfosByText("Refresh")
        Log.d("OverlayService", "Found ${refreshNodes.size} nodes with text 'Refresh'")
        return refreshNodes.firstOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            Log.d(
                "OverlayService",
                "Node: text=${node.text}, clickable=${node.isClickable}, enabled=${node.isEnabled}, visible=${node.isVisibleToUser}, bounds=$bounds"
            )
            node.isClickable && bounds.bottom > bottomThreshold
        }
    }

    private fun findJobs(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val jobs = mutableListOf<AccessibilityNodeInfo>()
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val topThreshold = screenHeight * 0.2 // Top 20% of screen
        val bottomThreshold = screenHeight * 0.8 // Bottom 20% of screen
        fun findClickableNodes(node: AccessibilityNodeInfo) {
            if (node.isClickable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.top > topThreshold && bounds.bottom < bottomThreshold) {
                    jobs.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { findClickableNodes(it) }
            }
        }
        findClickableNodes(root)
        return jobs
    }

    private fun findAcceptButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return root.findAccessibilityNodeInfosByText("Accept").firstOrNull()
    }

    fun getAcceptButtonBounds(): Rect? = acceptButtonBounds
    fun getFirstJobBounds(): Rect? = firstJobBounds
    fun hasJobs(): Boolean = hasJobs

    fun performClick(x: Int, y: Int) {
        Log.d("OverlayService", "Performing gesture click at: x=$x, y=$y")
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun setServiceRunning(running: Boolean) {
        isServiceRunning = running
    }

    override fun onInterrupt() {
        isServiceRunning = false
    }

    companion object {
        private var instance: OverlayAccessibilityService? = null
        fun getInstance(): OverlayAccessibilityService? = instance
        fun setInstance(service: OverlayAccessibilityService?) {
            instance = service
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        setInstance(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        setInstance(null)
    }
} 