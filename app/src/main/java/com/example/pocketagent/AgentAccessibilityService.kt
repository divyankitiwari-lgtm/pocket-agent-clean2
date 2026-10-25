package com.example.pocketagent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Screen-control layer for Pocket Agent.
 * Actions only run after the user explicitly starts an agent session.
 * Screen labels stay local until the foreground agent sends them to Gemini.
 */
class AgentAccessibilityService : AccessibilityService() {
    private var agentTab: LinearLayout? = null
    private var agentStatus: TextView? = null
    private var windowManager: WindowManager? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pill(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun syncAgentTab() {
        val prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)
        if (!prefs.getBoolean("session_active", false)) {
            removeAgentTab()
            return
        }
        if (agentTab != null) {
            agentStatus?.text = prefs.getString("current_agent_status", "● Pocket Agent · Running").orEmpty()
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
                text = prefs.getString("current_agent_status", "● Pocket Agent · Running").orEmpty()
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
                    prefs.edit().putBoolean("session_active", false).putString("pending_action", "").apply()
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
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(10)
                y = dp(90)
            }
            windowManager?.addView(panel, params)
            agentTab = panel
            agentStatus = label
        } catch (_: Exception) {
            // Overlay failure does not disable the underlying accessibility service.
        }
    }

    private fun removeAgentTab() {
        try { agentTab?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        agentTab = null
        agentStatus = null
    }

    private val poller = object : Runnable {
        override fun run() {
            syncAgentTab()
            processActiveWindow()
            handler.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
            .putBoolean("accessibility_connected", true)
            .putString("accessibility_status", "connected").apply()
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

        val pending = prefs.getString("pending_action", "").orEmpty()
        if (pending.isNotBlank()) {
            // Clear first so a repeated accessibility event cannot execute the same command twice.
            prefs.edit().putString("pending_action", "").apply()
            try {
                val command = org.json.JSONObject(pending)
                val action = command.optString("action")
                val value = command.optString("text").trim()
                val result = when (action) {
                    "tap_text" -> {
                        if (value.isEmpty()) "Tap skipped: target text was empty"
                        else if (clickNodeByText(rootInActiveWindow, value)) "Tapped visible control: $value"
                        else "Could not find/click visible control: $value"
                    }
                    "type_text" -> {
                        val target = findFocusedEditable(rootInActiveWindow)
                        if (target == null) "Typing failed: no focused editable field"
                        else {
                            val args = Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                            }
                            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
                                "Entered text in focused field"
                            else "Typing failed: field rejected text"
                        }
                    }
                    "scroll_down" -> if (scrollWindow(true)) "Scrolled down" else "Scroll down failed"
                    "scroll_up" -> if (scrollWindow(false)) "Scrolled up" else "Scroll up failed"
                    "back" -> if (performGlobalAction(GLOBAL_ACTION_BACK)) "Pressed Back" else "Back action failed"
                    "home" -> if (performGlobalAction(GLOBAL_ACTION_HOME)) "Pressed Home" else "Home action failed"
                    "open_app" -> openAppByLabel(value)
                    else -> "Unsupported action: $action"
                }
                prefs.edit().putString("last_action_result", result).apply()
            } catch (e: Exception) {
                prefs.edit().putString("last_action_result", "Action error: " + (e.message ?: "unknown")).apply()
            }
        }

        val root = rootInActiveWindow ?: return
        val labels = ArrayList<String>()
        collectVisibleLabels(root, labels, 0)
        prefs.edit().putString("latest_screen_text", labels.distinct().take(120).joinToString("\n")).apply()
    }

    private fun openAppByLabel(query: String): String {
        if (query.isBlank()) return "Open app failed: app name was empty"
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val match = apps.firstOrNull { app ->
            val launch = pm.getLaunchIntentForPackage(app.packageName)
            launch != null && (pm.getApplicationLabel(app).toString().equals(query, true) ||
                pm.getApplicationLabel(app).toString().contains(query, true))
        } ?: return "Could not find an installed app matching: $query"
        val launch = pm.getLaunchIntentForPackage(match.packageName)
            ?: return "No launcher activity for: $query"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        return "Opened app: " + pm.getApplicationLabel(match)
    }

    private fun scrollWindow(down: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        if (scrollNode(root, down)) return true
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = if (down) metrics.heightPixels * 0.78f else metrics.heightPixels * 0.28f
        val endY = if (down) metrics.heightPixels * 0.28f else metrics.heightPixels * 0.78f
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 380)
        return dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun scrollNode(node: AccessibilityNodeInfo?, down: Boolean): Boolean {
        if (node == null) return false
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (node.isScrollable && node.performAction(action)) return true
        for (i in 0 until node.childCount) if (scrollNode(node.getChild(i), down)) return true
        return false
    }

    private fun collectVisibleLabels(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 20 || out.size >= 120) return
        if (!node.isPassword) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        }
        for (i in 0 until node.childCount) collectVisibleLabels(node.getChild(i), out, depth + 1)
    }

    private fun clickNodeByText(root: AccessibilityNodeInfo?, target: String): Boolean {
        if (root == null) return false
        val candidates = root.findAccessibilityNodeInfosByText(target)
        for (candidate in candidates) {
            if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            var parent = candidate.parent
            var depth = 0
            while (parent != null && depth++ < 6) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                parent = parent.parent
            }
        }
        return false
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
        handler.removeCallbacks(poller)
        removeAgentTab()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
            .putBoolean("accessibility_connected", false)
            .putString("accessibility_status", "disconnected").apply()
        return super.onUnbind(intent)
    }
}
