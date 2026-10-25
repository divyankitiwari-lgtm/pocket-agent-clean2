package com.example.pocketagent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads visible UI labels locally only while the user has started a session.
 * It does not click, type, submit forms, or transmit screen content.
 */
class AgentAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)
        if (!prefs.getBoolean("session_active", false)) return

        val root = rootInActiveWindow ?: return
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

    override fun onInterrupt() = Unit
}
