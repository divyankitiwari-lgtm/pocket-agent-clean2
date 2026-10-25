package com.example.pocketagent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads visible UI labels locally only while the user has started a session.
 * It does not click, type, submit forms, or transmit screen content.
 */
class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
            .putBoolean("accessibility_connected", true)
            .putString("accessibility_status", "connected")
            .apply()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)
        if (event == null) return
        prefs.edit().putLong("last_accessibility_event", System.currentTimeMillis()).apply()
        if (!prefs.getBoolean("session_active", false)) return

        val root = rootInActiveWindow ?: return
        // Execute only a single, explicit action queued by the agent controller.
        // Actions are consumed once to avoid repeating a tap on every accessibility event.
        val pending = prefs.getString("pending_action", "").orEmpty()
        if (pending.isNotBlank()) {
            prefs.edit().putString("pending_action", "").apply()
            try {
                val command = org.json.JSONObject(pending)
                when (command.optString("action")) {
                    "tap_text" -> {
                        val target = command.optString("text").trim()
                        if (target.isNotEmpty()) {
                            val node = findNodeByText(root, target)
                            val clicked = node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                            prefs.edit().putString("last_action_result",
                                if (clicked) "Tapped: $target" else "Could not find/click: $target").apply()
                        }
                    }
                    "type_text" -> {
                        val target = findFocusedEditable(root)
                        val value = command.optString("text")
                        val args = android.os.Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                        }
                        val typed = target?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
                        prefs.edit().putString("last_action_result",
                            if (typed) "Entered text in focused field" else "No editable field focused").apply()
                    }
                    "back" -> {
                        val ok = performGlobalAction(GLOBAL_ACTION_BACK)
                        prefs.edit().putString("last_action_result", if (ok) "Pressed Back" else "Back action failed").apply()
                    }
                }
            } catch (e: Exception) {
                prefs.edit().putString("last_action_result", "Action error: " + (e.message ?: "unknown")).apply()
            }
        }
        val labels = ArrayList<String>()
        collectVisibleLabels(root, labels, 0)
        prefs.edit().putString("latest_screen_text", labels.distinct().take(100).joinToString("\n")).apply()
    }

    private fun collectVisibleLabels(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 18 || out.size >= 100) return
        if (!node.isPassword) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        }
        for (i in 0 until node.childCount) collectVisibleLabels(node.getChild(i), out, depth + 1)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val found = node.findAccessibilityNodeInfosByText(target)
        for (candidate in found) {
            if (candidate.isClickable) return candidate
            var parent = candidate.parent
            var depth = 0
            while (parent != null && depth++ < 5) {
                if (parent.isClickable) return parent
                parent = parent.parent
            }
        }
        return null
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.isEditable && !node.isPassword) return node
        for (i in 0 until node.childCount) {
            val found = findFocusedEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
            .putBoolean("accessibility_connected", false)
            .putString("accessibility_status", "disconnected")
            .apply()
        return super.onUnbind(intent)
    }
}
