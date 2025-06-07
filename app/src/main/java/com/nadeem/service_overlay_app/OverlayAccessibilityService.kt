package com.nadeem.service_overlay_app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class OverlayAccessibilityService : AccessibilityService() {
    private var isServiceRunning = false

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d("OverlayService", "Event received: ${event.eventType}, package: ${event.packageName}")
        // No longer caching job or accept button bounds here
    }

    /**
     * Attempts to find and click the first job in the list. Returns true if found and click attempted.
     */
    fun tryClickFirstJob(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.e("OverlayService", "rootInActiveWindow is null")
            return false
        }
        val jobNode = findFirstJobNode(rootNode)
        if (jobNode == null) {
            Log.d("OverlayService", "No job found")
            return false
        }
        val bounds = Rect()
        jobNode.getBoundsInScreen(bounds)
        Log.d(
            "OverlayService",
            "First job: text=${jobNode.text}, clickable=${jobNode.isClickable}, enabled=${jobNode.isEnabled}, visible=${jobNode.isVisibleToUser}, bounds=$bounds"
        )
        val actionResult = jobNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d("OverlayService", "ACTION_CLICK on job result: $actionResult")
        showToast("Clicked first job via accessibility service")
        if (!actionResult) {
            performClick(bounds.centerX(), bounds.centerY())
            Log.d("OverlayService", "Falling back to gesture click on job at: $bounds")
        }
        return true
    }

    /**
     * Attempts to find and click the Accept button. Returns true if found and click attempted.
     */
    fun tryClickAcceptButton(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.e("OverlayService", "rootInActiveWindow is null")
            return false
        }
        val acceptNode = findAcceptButton(rootNode)
        if (acceptNode == null) {
            Log.d("OverlayService", "No accept button found")
            return false
        }
        val bounds = Rect()
        acceptNode.getBoundsInScreen(bounds)
        Log.d(
            "OverlayService",
            "Accept button: text=${acceptNode.text}, clickable=${acceptNode.isClickable}, enabled=${acceptNode.isEnabled}, visible=${acceptNode.isVisibleToUser}, bounds=$bounds"
        )
        val actionResult = acceptNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d("OverlayService", "ACTION_CLICK on accept result: $actionResult")
        showToast("Clicked accept via accessibility service")
        if (!actionResult) {
            performClick(bounds.centerX(), bounds.centerY())
            Log.d("OverlayService", "Falling back to gesture click on accept at: $bounds")
        }
        return true
    }

    /**
     * Returns true if any jobs are found in the current window.
     */
    fun hasJobsNow(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return findFirstJobNode(rootNode) != null
    }

    private fun findFirstJobNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
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
        Log.d("OverlayService", "Found ${jobs.size} clickable job nodes")
        return jobs.firstOrNull()
    }

    private fun findAcceptButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomThreshold = screenHeight * 0.8 // Consider bottom 20% of screen
        val acceptNodes = root.findAccessibilityNodeInfosByText("Accept")
        Log.d("OverlayService", "Found ${acceptNodes.size} nodes with text 'Accept'")
        return acceptNodes.firstOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            Log.d(
                "OverlayService",
                "Node: text=${node.text}, clickable=${node.isClickable}, enabled=${node.isEnabled}, visible=${node.isVisibleToUser}, bounds=$bounds"
            )
            node.isClickable && bounds.bottom > bottomThreshold
        }
    }

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
        val actionResult = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d("OverlayService", "ACTION_CLICK result: $actionResult")
        showToast("Clicked refresh via accessibility service")
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

    fun performClick(x: Int, y: Int) {
        Log.d("OverlayService", "Performing gesture click at: x=$x, y=$y")
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    private fun showToast(message: String) {
        // Use main thread for Toast
        val handler = android.os.Handler(mainLooper)
        handler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
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