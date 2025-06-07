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

    companion object {
        private const val TAG = "OverlayService"
        private const val TARGET_PACKAGE = "com.nadeem.service_overlay_app"
        private var instance: OverlayAccessibilityService? = null

        // Navigation drawer related class names to exclude
        private val NAVIGATION_DRAWER_CLASSES = setOf(
            "androidx.drawerlayout.widget.DrawerLayout",
            "com.google.android.material.navigation.NavigationView",
            "android.widget.DrawerLayout",
            "android.support.v4.widget.DrawerLayout"
        )

        fun getInstance(): OverlayAccessibilityService? = instance
        private fun setInstance(service: OverlayAccessibilityService?) {
            instance = service
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        Log.d(TAG, "Event received: ${event.eventType}, package: $packageName")
        
        // Only process events from our target package
        if (packageName != TARGET_PACKAGE) {
            Log.d(TAG, "Ignoring event from package: $packageName")
            return
        }
        
        // Process the event only if service is running
        if (!isServiceRunning) {
            Log.d(TAG, "Service is not running, ignoring event")
            return
        }

        // Process the event based on its type
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Handle window state/content changes
                Log.d(TAG, "Processing window event from package: $packageName")
            }
        }
    }

    /**
     * Attempts to find and click the first job in the list. Returns true if found and click attempted.
     */
    fun tryClickFirstJob(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "rootInActiveWindow is null")
            return false
        }
        
        Log.d(TAG, "Attempting to click first job")
        val jobNode = findFirstJobNode(rootNode)
        
        if (jobNode == null) {
            Log.d(TAG, "No job node found")
            return false
        }
        
        val bounds = Rect()
        jobNode.getBoundsInScreen(bounds)
        Log.d(TAG, "Found job node: " +
            "text=${jobNode.text}, " +
            "desc=${jobNode.contentDescription}, " +
            "bounds=$bounds")
        
        val actionResult = jobNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "ACTION_CLICK on job result: $actionResult")
        showToast("Clicked first job via accessibility service")
        
        if (!actionResult) {
            try {
                performClick(bounds.centerX(), bounds.centerY())
            } catch (e: Exception) {
                Log.e(TAG, "Error performing click: ${e.message}")
                e.printStackTrace()
            }
            Log.d(TAG, "Falling back to gesture click on job at: $bounds")
        }
        return true
    }

    /**
     * Attempts to find and click the Accept button. Returns true if found and click attempted.
     */
    fun tryClickAcceptButton(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "rootInActiveWindow is null")
            return false
        }
        val acceptNode = findAcceptButton(rootNode)
        if (acceptNode == null) {
            Log.d(TAG, "No accept button found")
            return false
        }
        val bounds = Rect()
        acceptNode.getBoundsInScreen(bounds)
        Log.d(TAG, "Accept button: text=${acceptNode.text}, " +
            "clickable=${acceptNode.isClickable}, " +
            "enabled=${acceptNode.isEnabled}, " +
            "visible=${acceptNode.isVisibleToUser}, " +
            "bounds=$bounds")
        
        val actionResult = acceptNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "ACTION_CLICK on accept result: $actionResult")
        showToast("Clicked accept via accessibility service")
        
        if (!actionResult) {
            performClick(bounds.centerX(), bounds.centerY())
            Log.d(TAG, "Falling back to gesture click on accept at: $bounds")
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

    /**
     * Returns true if a refresh button is found in the current window.
     */
    fun hasRefreshButton(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return findRefreshButton(rootNode) != null
    }

    /**
     * Checks if a node is part of the navigation drawer
     */
    private fun isNavigationDrawer(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: return false
        return NAVIGATION_DRAWER_CLASSES.any { drawerClass -> 
            className.contains(drawerClass, ignoreCase = true) 
        }
    }

    private fun findFirstJobNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val jobs = mutableListOf<AccessibilityNodeInfo>()
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val topThreshold = screenHeight * 0.1 // Top 25% of screen
        val bottomThreshold = screenHeight * 0.8 // Bottom 20% of screen
        
        // Define the target text patterns
        val targetPatterns = listOf("DBI2", "DBI3", "DBI4", "DBI5", "DOX2", "DWR1")
        
        Log.d(TAG, "Starting job search with screen height: $screenHeight")
        Log.d(TAG, "Thresholds - top: $topThreshold, bottom: $bottomThreshold")
        
        fun findClickableNodes(node: AccessibilityNodeInfo) {
            try {
                // Skip navigation drawer nodes
                if (isNavigationDrawer(node)) {
                    Log.d(TAG, "Skipping navigation drawer node: ${node.className}")
                    return
                }

                // Log node details for debugging
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                
                Log.d(TAG, "Examining node: " +
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
                        
                        // Check if the node text contains any of the target patterns
                        val containsTargetPattern = targetPatterns.any { pattern ->
                            nodeText.contains(pattern, ignoreCase = true) || 
                            nodeDesc.contains(pattern, ignoreCase = true)
                        }
                        
                        if (containsTargetPattern) {
                            Log.d(TAG, "Found clickable node with target pattern: " +
                                "text=$nodeText, " +
                                "desc=$nodeDesc, " +
                                "bounds=$bounds")
                            jobs.add(node)
                        }
                    } else {
                        Log.d(TAG, "Node outside middle portion: " +
                            "top=${bounds.top}, bottom=${bounds.bottom}, " +
                            "thresholds: top=$topThreshold, bottom=$bottomThreshold")
                    }
                }
                
                // Recursively check children
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { findClickableNodes(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing node: ${e.message}")
            }
        }
        
        try {
            // Start the search
            findClickableNodes(root)
            Log.d(TAG, "Search complete. Found ${jobs.size} potential job nodes")
            
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
                Log.d(TAG, "Job $index details: " +
                    "text=${job.text}, " +
                    "desc=${job.contentDescription}, " +
                    "clickable=${job.isClickable}, " +
                    "enabled=${job.isEnabled}, " +
                    "visible=${job.isVisibleToUser}, " +
                    "bounds=$bounds")
            }
            
            // Return the first job node (topmost) if any found
            return if (jobs.isNotEmpty()) {
                Log.d(TAG, "Selected first job node (index 0)")
                jobs[0]
            } else {
                Log.d(TAG, "No job nodes found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding job nodes: ${e.message}")
            return null
        }
    }

    private fun findAcceptButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val bottomThreshold = screenHeight * 0.8 // Consider bottom 20% of screen
            
            // Try different variations of the accept button text
            val acceptTexts = listOf("Schedule", "SCHEDULE", "schedule")
            val acceptNodes = mutableListOf<AccessibilityNodeInfo>()
            
            for (text in acceptTexts) {
                root.findAccessibilityNodeInfosByText(text).forEach { node ->
                    // Skip navigation drawer nodes
                    if (!isNavigationDrawer(node)) {
                        acceptNodes.add(node)
                    } else {
                        Log.d(TAG, "Skipping navigation drawer node for text: $text")
                    }
                }
            }
            
            Log.d(TAG, "Found ${acceptNodes.size} nodes with accept-like text")
            
            return acceptNodes.firstOrNull { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                Log.d(TAG, "Checking accept node: text=${node.text}, " +
                    "desc=${node.contentDescription}, " +
                    "clickable=${node.isClickable}, " +
                    "enabled=${node.isEnabled}, " +
                    "visible=${node.isVisibleToUser}, " +
                    "bounds=$bounds")
                
                node.isClickable && bounds.bottom > bottomThreshold
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding accept button: ${e.message}")
            return null
        }
    }

    fun tryClickRefreshButton(): Boolean {
        // Always perform pull-to-refresh gesture regardless of button status
        Log.d(TAG, "Performing pull-to-refresh gesture")
        performPullToRefresh()

        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "rootInActiveWindow is null")
            return false
        }
        
        // Try to find and click the refresh button
        val button = findRefreshButton(rootNode)
        if (button != null) {
            val bounds = Rect()
            button.getBoundsInScreen(bounds)
            Log.d(TAG, "Refresh button found: text=${button.text}, " +
                "clickable=${button.isClickable}, " +
                "enabled=${button.isEnabled}, " +
                "visible=${button.isVisibleToUser}, " +
                "bounds=$bounds")
            
            val actionResult = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "ACTION_CLICK result: $actionResult")
            showToast("Clicked refresh button via accessibility service")
            
            if (!actionResult) {
                performClick(bounds.centerX(), bounds.centerY())
                Log.d(TAG, "Falling back to gesture click at: $bounds")
            }
        } else {
            Log.d(TAG, "No refresh button found")
        }
        
    
        
        return true
    }

    fun performPullToRefresh() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Calculate start and end points for the gesture
        val startX = screenWidth / 2f
        val startY = screenHeight * 0.3f  // Start at 30% from top
        val endY = screenHeight * 0.7f    // End at 70% from top
        
        Log.d(TAG, "Performing pull-to-refresh gesture: startY=$startY, endY=$endY")
        
        // Create the path for the gesture
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, endY)
        
        // Create and dispatch the gesture
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 500)) // 500ms duration
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                Log.d(TAG, "Pull-to-refresh gesture completed")
                showToast("Pull-to-refresh gesture completed")
            }
            
            override fun onCancelled(gestureDescription: GestureDescription) {
                Log.d(TAG, "Pull-to-refresh gesture cancelled")
                showToast("Pull-to-refresh gesture cancelled")
            }
        }, null)
    }

    private fun findRefreshButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            val displayMetrics = resources.displayMetrics
//            val screenHeight = displayMetrics.heightPixels
//            val bottomThreshold = screenHeight * 0.6 // Consider bottom 20% of screen
            
            // Try different variations of the refresh button text
            val refreshTexts = listOf("Refresh", "REFRESH", "refresh")
            val refreshNodes = mutableListOf<AccessibilityNodeInfo>()
            
            for (text in refreshTexts) {
                root.findAccessibilityNodeInfosByText(text).forEach { node ->
                    // Skip navigation drawer nodes
                    if (!isNavigationDrawer(node)) {
                        refreshNodes.add(node)
                    } else {
                        Log.d(TAG, "Skipping navigation drawer node for text: $text")
                    }
                }
            }
            
            Log.d(TAG, "Found ${refreshNodes.size} nodes with refresh-like text")
            
            return refreshNodes.firstOrNull { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                Log.d(TAG, "Checking refresh node: text=${node.text}, " +
                    "desc=${node.contentDescription}, " +
                    "clickable=${node.isClickable}, " +
                    "enabled=${node.isEnabled}, " +
                    "visible=${node.isVisibleToUser}, " +
                    "bounds=$bounds")
                
                node.isClickable //&& bounds.bottom > bottomThreshold
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding refresh button: ${e.message}")
            return null
        }
    }

    fun performClick(x: Int, y: Int) {
        Log.d(TAG, "Performing gesture click at: x=$x, y=$y")
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
        Log.d(TAG, "Service interrupted")
        isServiceRunning = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        setInstance(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        setInstance(null)
        isServiceRunning = false
    }
} 