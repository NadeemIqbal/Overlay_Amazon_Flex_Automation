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
        val packageName = event.packageName?.toString()
        Log.e("OverlayService", "Event received: ${event.eventType}, package: $packageName")
        
        // Only process events from our target packages
        if (packageName != "com.android.settings" && packageName != "com.nadeem.service_overlay_app") {
            Log.e("OverlayService", "Ignoring event from package: $packageName")
            return
        }
        
        // Process the event only if service is running
        if (!isServiceRunning) {
            Log.e("OverlayService", "Service is not running, ignoring event")
            return
        }

        // Process the event based on its type
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Handle window state/content changes
                Log.e("OverlayService", "Processing window event from package: $packageName")
            }
        }
    }

    /**
     * Attempts to find and click the first job in the list. Returns true if found and click attempted.
     */
    fun tryClickFirstJob(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.e("OverlayService", "rootInActiveWindow is null")
            return false
        }
        
        Log.e("OverlayService", "Attempting to click first job")
        val jobNode = findFirstJobNode(rootNode)
        
        if (jobNode == null) {
            Log.e("OverlayService", "No job node found")
            return false
        }
        
        val bounds = Rect()
        jobNode.getBoundsInScreen(bounds)
        Log.e("OverlayService", "Found job node: " +
            "text=${jobNode.text}, " +
            "desc=${jobNode.contentDescription}, " +
            "bounds=$bounds")
        
        val actionResult = jobNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.e("OverlayService", "ACTION_CLICK on job result: $actionResult")
        showToast("Clicked first job via accessibility service")
        
        if (!actionResult) {
            try {
                performClick(bounds.centerX(), bounds.centerY())
            } catch (e: Exception) {
                Log.e("OverlayService", "Error performing click: ${e.message}")
                e.printStackTrace()
            }
            Log.e("OverlayService", "Falling back to gesture click on job at: $bounds")
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
            Log.e("OverlayService", "No accept button found")
            return false
        }
        val bounds = Rect()
        acceptNode.getBoundsInScreen(bounds)
        Log.e(
            "OverlayService",
            "Accept button: text=${acceptNode.text}, clickable=${acceptNode.isClickable}, enabled=${acceptNode.isEnabled}, visible=${acceptNode.isVisibleToUser}, bounds=$bounds"
        )
        val actionResult = acceptNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.e("OverlayService", "ACTION_CLICK on accept result: $actionResult")
        showToast("Clicked accept via accessibility service")
        if (!actionResult) {
            performClick(bounds.centerX(), bounds.centerY())
            Log.e("OverlayService", "Falling back to gesture click on accept at: $bounds")
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
        val topThreshold = screenHeight * 0.01 // Top 20% of screen
        val bottomThreshold = screenHeight * 0.8 // Bottom 20% of screen
        
        Log.e("OverlayService", "Starting job search with screen height: $screenHeight")
        Log.e("OverlayService", "Thresholds - top: $topThreshold, bottom: $bottomThreshold")
        
        fun findClickableNodes(node: AccessibilityNodeInfo) {
            // Log node details for debugging
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            Log.e("OverlayService", "Examining node: " +
                "text=${node.text}, " +
                "desc=${node.contentDescription}, " +
                "clickable=${node.isClickable}, " +
                "enabled=${node.isEnabled}, " +
                "visible=${node.isVisibleToUser}, " +
                "className=${node.className}, " +
                "bounds=$bounds")
            
            // Check if node is clickable and in the middle portion
            if (node.isClickable) {
                if (bounds.top > topThreshold && bounds.bottom < bottomThreshold) {
                    val nodeText = node.text?.toString() ?: ""
                    val nodeDesc = node.contentDescription?.toString() ?: ""
                    
                    Log.e("OverlayService", "Found clickable node in middle portion: " +
                        "text=$nodeText, " +
                        "desc=$nodeDesc, " +
                        "bounds=$bounds")
                    
                    // Add any clickable node in the middle portion
                    jobs.add(node)
                } else {
                    Log.e("OverlayService", "Node outside middle portion: " +
                        "top=${bounds.top}, bottom=${bounds.bottom}, " +
                        "thresholds: top=$topThreshold, bottom=$bottomThreshold")
                }
            }
            
            // Recursively check children
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { findClickableNodes(it) }
            }
        }
        
        // Start the search
        findClickableNodes(root)
        Log.e("OverlayService", "Search complete. Found ${jobs.size} potential job nodes")
        
        // Sort jobs by their vertical position (top to bottom)
        jobs.sortBy { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            bounds.top
        }
        
        // Log details of all found nodes
        jobs.forEachIndexed { index, job ->
            val bounds = Rect()
            job.getBoundsInScreen(bounds)
            Log.e("OverlayService", "Job $index details: " +
                "text=${job.text}, " +
                "desc=${job.contentDescription}, " +
                "clickable=${job.isClickable}, " +
                "enabled=${job.isEnabled}, " +
                "visible=${job.isVisibleToUser}, " +
                "bounds=$bounds")
        }
        
        // Return the first job node (topmost) if any found
        return if (jobs.isNotEmpty()) {
            Log.e("OverlayService", "Selected first job node (index 0)")
            jobs[0]
        } else {
            Log.e("OverlayService", "No job nodes found")
            null
        }
    }

    private fun findAcceptButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomThreshold = screenHeight * 0.8 // Consider bottom 20% of screen
        
        // Try different variations of the accept button text
        val acceptTexts = listOf("Accept", "ACCEPT", "accept", "OK", "Ok", "ok", "Confirm", "CONFIRM", "confirm")
        val acceptNodes = mutableListOf<AccessibilityNodeInfo>()
        
        for (text in acceptTexts) {
            acceptNodes.addAll(root.findAccessibilityNodeInfosByText(text))
        }
        
        Log.e("OverlayService", "Found ${acceptNodes.size} nodes with accept-like text")
        
        return acceptNodes.firstOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            Log.e("OverlayService", "Checking accept node: text=${node.text}, " +
                "desc=${node.contentDescription}, " +
                "clickable=${node.isClickable}, " +
                "enabled=${node.isEnabled}, " +
                "visible=${node.isVisibleToUser}, " +
                "bounds=$bounds")
            
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
            Log.e("OverlayService", "No refresh button found")
            return false
        }
        val bounds = Rect()
        button.getBoundsInScreen(bounds)
        Log.e(
            "OverlayService",
            "Refresh button: text=${button.text}, clickable=${button.isClickable}, enabled=${button.isEnabled}, visible=${button.isVisibleToUser}, bounds=$bounds"
        )
        val actionResult = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.e("OverlayService", "ACTION_CLICK result: $actionResult")
        showToast("Clicked refresh via accessibility service")
        if (!actionResult) {
            performClick(bounds.centerX(), bounds.centerY())
            Log.e("OverlayService", "Falling back to gesture click at: $bounds")
        }
        return true
    }

    private fun findRefreshButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val bottomThreshold = screenHeight * 0.8 // Consider bottom 20% of screen
        
        // Try different variations of the refresh button text
        val refreshTexts = listOf("Refresh", "REFRESH", "refresh", "Update", "UPDATE", "update")
        val refreshNodes = mutableListOf<AccessibilityNodeInfo>()
        
        for (text in refreshTexts) {
            refreshNodes.addAll(root.findAccessibilityNodeInfosByText(text))
        }
        
        Log.e("OverlayService", "Found ${refreshNodes.size} nodes with refresh-like text")
        
        return refreshNodes.firstOrNull { node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            Log.e("OverlayService", "Checking refresh node: text=${node.text}, " +
                "desc=${node.contentDescription}, " +
                "clickable=${node.isClickable}, " +
                "enabled=${node.isEnabled}, " +
                "visible=${node.isVisibleToUser}, " +
                "bounds=$bounds")
            
            node.isClickable && bounds.bottom > bottomThreshold
        }
    }

    fun performClick(x: Int, y: Int) {
        Log.e("OverlayService", "Performing gesture click at: x=$x, y=$y")
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