package com.nadeem.service_overlay_app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class OverlayAccessibilityService : AccessibilityService() {
    private var isServiceRunning = false
    private var isWaitingForJobClick = false

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
                handleJobFlow()
            }
        }
    }

    private fun handleJobFlow() {
        val rootNode = rootInActiveWindow ?: return

        // If we're waiting for job click delay, don't do anything
        if (isWaitingForJobClick) {
            return
        }

        // First check if we can click accept button
        if (tryClickAcceptButton()) {
            Log.d(TAG, "Clicked accept button")
            return
        }

        // Then check if we can click a job
        if (tryClickFirstJob()) {
            Log.d(TAG, "Clicked first job, waiting 1000ms before next action")
            isWaitingForJobClick = true
            android.os.Handler(mainLooper).postDelayed({
                isWaitingForJobClick = false
                // Trigger another event to continue the flow
                onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
            }, 1000)
            return
        }

        // If no job found, try to refresh
        Log.d(TAG, "No job found, attempting refresh")
//        tryClickRefreshButton()
    }

    /**
     * Attempts to find and click the first job in the list. Returns true if found and click attempted.
     */
    fun tryClickFirstJob(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "rootInActiveWindow is null")
            return false
        }

        val jobNode = findFirstJobNode(rootNode) ?: return false

        val bounds = Rect()
        jobNode.getBoundsInScreen(bounds)
        Log.d(TAG, "Found job node: bounds=$bounds")

        val actionResult = jobNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "ACTION_CLICK on job result: $actionResult")

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
        val acceptNode = findAcceptButton(rootNode) ?: return false

        val bounds = Rect()
        acceptNode.getBoundsInScreen(bounds)
        Log.d(TAG, "Accept button found: bounds=$bounds")

        val handler = android.os.Handler(mainLooper)
        handler.post {
            Toast.makeText(applicationContext, "Accept Clicked 1", Toast.LENGTH_SHORT).show()
        }

        val actionResult = acceptNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "ACTION_CLICK on accept result: $actionResult")

        if (!actionResult) {
            performClick(bounds.centerX(), bounds.centerY())
            val handler = android.os.Handler(mainLooper)
            handler.post {
                Toast.makeText(applicationContext, "Accept Clicked 2", Toast.LENGTH_SHORT).show()
            }
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
        val topThreshold = screenHeight * 0.1 // Top 10% of screen
        val bottomThreshold = screenHeight * 0.8 // Bottom 20% of screen

        // Define the target text patterns
        //val targetPatterns = listOf("DBI2", "DBI3", "DBI4", "DBI5", "DOX2", "DWR1")
        val targetPatterns = listOf("DBI2", "CUK3", "DBI3", "DBI4", "VAP4", "DBI5", "CU46", "CU49", "CC16", "CU03", "DWR1", "DB84", "DBI7", "CU63", "DOX2", "CU74", "DST1", "CU30", "DXB1")

        fun findJobNodes(node: AccessibilityNodeInfo) {
            try {
                // Skip navigation drawer nodes
                if (isNavigationDrawer(node)) {
                    return
                }

                val nodeText = node.text?.toString() ?: ""
                val nodeDesc = node.contentDescription?.toString() ?: ""

                // Check if the node text contains any of the target patterns
                val containsTargetPattern = targetPatterns.any { pattern ->
                    nodeText.contains(pattern, ignoreCase = true) ||
                            nodeDesc.contains(pattern, ignoreCase = true)
                }

                if (containsTargetPattern) {
                    // Find the first clickable parent
                    var parent = node.parent
                    while (parent != null) {
                        val parentClassName = parent.className?.toString() ?: ""

                        // Stop if we reach a ViewGroup
                        if (parentClassName.contains("ViewGroup") || parentClassName.contains("Layout")) {
                            if (parent.isClickable) {
                                val bounds = Rect()
                                parent.getBoundsInScreen(bounds)

                                if (bounds.top > topThreshold && bounds.bottom < bottomThreshold) {
                                    jobs.add(parent)
                                }
                            }
                            break
                        }
                        parent = parent.parent
                    }
                }

                // Recursively check children
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { findJobNodes(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing node: ${e.message}")
            }
        }

        try {
            findJobNodes(root)

            // Sort jobs by their vertical position (top to bottom)
            jobs.sortBy { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.top
            }

            // Return the first job node (topmost) if any found
            return jobs.firstOrNull()
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
            val acceptTexts =
                listOf("Schedule", "SCHEDULE", "schedule", "ACCEPT", "accept", "Accept")
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
                Log.d(
                    TAG, "Checking accept node: text=${node.text}, " +
                            "desc=${node.contentDescription}, " +
                            "clickable=${node.isClickable}, " +
                            "enabled=${node.isEnabled}, " +
                            "visible=${node.isVisibleToUser}, " +
                            "bounds=$bounds"
                )

                node.isClickable && bounds.bottom > bottomThreshold
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding accept button: ${e.message}")
            return null
        }
    }

    fun tryClickRefreshButton(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "rootInActiveWindow is null")
            return false
        }

        // Try to find and click the refresh button
        val button = findRefreshButton(rootNode)
        if (button != null) {
            val bounds = Rect()
            button.getBoundsInScreen(bounds)
            Log.d(TAG, "Refresh button found: bounds=$bounds")

            val actionResult = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "ACTION_CLICK result: $actionResult")

            if (!actionResult) {
                performClick(bounds.centerX(), bounds.centerY())
                Log.d(TAG, "Falling back to gesture click at: $bounds")
            }
        } else {
            Log.d(TAG, "No refresh button found, trying pull-to-refresh")
            performPullToRefresh()
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
        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(
                path,
                0,
                500
            )
        ) // 500ms duration
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
                Log.d(
                    TAG, "Checking refresh node: text=${node.text}, " +
                            "desc=${node.contentDescription}, " +
                            "clickable=${node.isClickable}, " +
                            "enabled=${node.isEnabled}, " +
                            "visible=${node.isVisibleToUser}, " +
                            "bounds=$bounds"
                )

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
//        val handler = android.os.Handler(mainLooper)
//        handler.post {
//            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
//        }
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