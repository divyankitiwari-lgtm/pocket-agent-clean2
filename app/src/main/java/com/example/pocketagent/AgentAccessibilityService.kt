package com.example.pocketagent

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads visible UI labels locally only while the user has started a session.
 * It does not click, type, submit forms, or transmit screen content.
 */
class AgentAccessibilityService : AccessibilityService() {
    private var agentTab: LinearLayout? = null
    private var agentStatus: TextView? = null
    private var windowManager: WindowManager? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pill(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun syncAgentTab() {
        val prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)
        val active = prefs.getBoolean("session_active", false)
        if (!active) {
            removeAgentTab()
            return
        }
        if (agentTab != null) {
            agentStatus?.text = "● Pocket Agent · Running"
            return
        }
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(7), dp(7), dp(7))
                background = pill(Color.rgb(27, 30, 47), 24)
                elevation = dp(8).toFloat()
            }
            val label = TextView(this).apply {
                text = "● Pocket Agent · Running"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 0, dp(8), 0)
            }
            panel.addView(label, LinearLayout.LayoutParams(-2, dp(38)))
            val pause = Button(this).apply {
                text = "Pause"
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = pill(Color.rgb(117, 92, 255), 18)
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    prefs.edit().putBoolean("session_active", false).apply()
                    removeAgentTab()
                }
            }
            panel.addView(pause, LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(5) })
            val stop = Button(this).apply {
                text = "Stop"
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = pill(Color.rgb(190, 55, 72), 18)
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    prefs.edit().putBoolean("session_active", false)
                        .putString("latest_screen_text", "")
                        .putString("pending_action", "").apply()
                    removeAgentTab()
                }
            }
            panel.addView(stop, LinearLayout.LayoutParams(-2, dp(38)))
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(10)
                y = dp(90)
            }
            windowManager?.addView(panel, params)
            agentTab = panel
            agentStatus = label
        } catch (_: Exception) {
            // The core service remains usable if this Android version blocks the overlay.
        }
    }

    private fun removeAgentTab() {
        try {
            agentTab?.let { windowManager?.removeView(it) }
        } catch (_: Exception) { }
        agentTab = null
        agentStatus = null
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
            .putBoolean("accessibility_connected", true)
            .putString("accessibility_status", "connected")
            .apply()
    }


    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val poller = object : Runnable {
        override fun run() {
            syncAgentTab()
            processActiveWindow()
            handler.postDelayed(this, 650)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
            .putBoolean("accessibility_connected", true)
            .putString("accessibility_status", "connected")
            .apply()
        handler.removeCallbacks(poller)
        handler.post(poller)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        syncAgentTab()
        processActiveWindow()
    }

    private fun processActiveWindow() {
        val prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)
        prefs.edit().putLong("last_accessibility_event", System.currentTimeMillis()).apply()
        if (!prefs.getBoolean("session_active", false)) return
        val root = rootInActiveWindow ?: return
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
                    else -> prefs.edit().putString("last_action_result", "Unsupported action").apply()
                }
            } catch (e: Exception) {
                prefs.edit().putString("last_action_result", "Action error: " + (e.message ?: "unknown")).apply()
            }
        }
        val labels = ArrayList<String>()
        collectVisibleLabels(root, labels, 0)
        prefs.edit().putString("latest_screen_text", labels.distinct().take(100).joinToString("\\n")).apply()
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

    override fun onDestroy() {
        removeAgentTab()
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
            .putBoolean("accessibility_connected", false)
            .putString("accessibility_status", "disconnected")
            .apply()
        return super.onUnbind(intent)
    }
}
