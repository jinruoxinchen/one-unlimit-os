package com.jinruoxinchen.oneunlimitos.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jinruoxinchen.oneunlimitos.agents.AgentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Core accessibility service that enables AI agents to observe and interact with the system UI.
 * This service leverages Android's accessibility framework to monitor UI events and provide
 * a bridge for AI agents to perform actions.
 */
class OneAccessibilityService : AccessibilityService() {
    
    private val TAG = "OneAccessibilityService"
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    
    // System state observer to track the current UI state
    private lateinit var systemStateObserver: SystemStateObserver
    
    // Interaction controller to perform UI actions
    private lateinit var interactionController: InteractionController
    
    override fun onServiceConnected() {
        Log.i(TAG, "OneAccessibilityService connected")
        
        // Configure accessibility service
        val info = AccessibilityServiceInfo().apply {
            // Request all types of accessibility events
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            
            // Request content capture for UI analysis
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            
            // Set feedback type
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            // Request notifications from all packages
            notificationTimeout = 100
            packageNames = null // Listen to events from all packages
        }
        
        // Apply configuration
        serviceInfo = info
        
        // Initialize components
        systemStateObserver = SystemStateObserver(this)
        interactionController = InteractionController(this)
        
        // Notify agent system that accessibility service is ready
        notifyAgentSystemReady()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Process event in a coroutine to avoid blocking the main thread
        serviceScope.launch {
            try {
                processAccessibilityEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing accessibility event", e)
            }
        }
    }
    
    private suspend fun processAccessibilityEvent(event: AccessibilityEvent) {
        // Process different types of accessibility events
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Window changes, app switches, etc.
                val packageName = event.packageName?.toString() ?: ""
                val className = event.className?.toString() ?: ""
                val rootNode = rootInActiveWindow
                
                // Update system state with new window information
                systemStateObserver.onWindowStateChanged(packageName, className, rootNode)
            }
            
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // User interactions
                systemStateObserver.onUserInteraction(event)
            }
            
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Notifications
                systemStateObserver.onNotificationEvent(event)
            }
            
            // Handle other event types as needed
        }
        
        // Notify agent system of the event for further processing
        AgentManager.getInstance().onAccessibilityEvent(event, rootInActiveWindow)
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "OneAccessibilityService interrupted")
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "OneAccessibilityService unbound")
        return super.onUnbind(intent)
    }
    
    private fun notifyAgentSystemReady() {
        // Inform the agent system that the accessibility service is ready
        AgentManager.getInstance().onAccessibilityServiceConnected(this)
    }
    
    /**
     * Public methods for agent system to interact with UI
     */
    
    /**
     * Performs a click on the specified node
     */
    fun performClick(nodeInfo: AccessibilityNodeInfo): Boolean {
        return interactionController.performClick(nodeInfo)
    }
    
    /**
     * Types text into the focused input field
     */
    fun typeText(text: String): Boolean {
        return interactionController.typeText(text)
    }
    
    /**
     * Scrolls in the specified direction
     */
    fun scroll(direction: InteractionController.ScrollDirection): Boolean {
        return interactionController.scroll(direction)
    }
    
    /**
     * Gets the current UI hierarchy as a structured representation
     */
    fun getCurrentUiState(): UiState {
        return systemStateObserver.getCurrentUiState()
    }
    
    /**
     * Finds a UI element by text
     */
    fun findElementByText(text: String): AccessibilityNodeInfo? {
        return rootInActiveWindow?.let { rootNode ->
            findNodeByText(rootNode, text)
        }
    }
    
    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Implementation for finding nodes containing specified text
        if (root.text?.contains(text, ignoreCase = true) == true) {
            return root
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val foundNode = findNodeByText(child, text)
            if (foundNode != null) {
                return foundNode
            }
            child.recycle()
        }
        
        return null
    }
}
