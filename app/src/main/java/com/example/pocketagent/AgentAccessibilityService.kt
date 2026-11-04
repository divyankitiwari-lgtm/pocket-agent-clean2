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
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Screen-control layer for JARVIS.
 * Actions only run after the user explicitly starts an agent session.
 * Screen labels stay local until the foreground agent sends them to Gemini.
 */
class AgentAccessibilityService : AccessibilityService() {
    private var agentTab: LinearLayout? = null
    private var agentStatus: TextView? = null
    private var agentPauseButton: Button? = null
    private var windowManager: WindowManager? = null
    private var tapMarker: View? = null
    private var lastEventTimestampSaved = 0L
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
            agentStatus?.text = prefs.getString("current_agent_status", "● JARVIS · Running").orEmpty()
            agentPauseButton?.text = if (prefs.getBoolean("agent_paused", false)) "Resume" else "Pause"
            return
        }
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(7), dp(7), dp(7))
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(255, 255, 255), Color.rgb(238, 241, 255))
                ).apply {
                    cornerRadius = dp(24).toFloat()
                    setStroke(dp(1), Color.rgb(218, 224, 245))
                }
                elevation = dp(8).toFloat()
            }
            val label = TextView(this).apply {
                text = prefs.getString("current_agent_status", "● JARVIS · Running").orEmpty()
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(37, 43, 65))
                setPadding(0, 0, dp(8), 0)
            }
            panel.addView(label, LinearLayout.LayoutParams(-2, dp(38)))
            val pause = Button(this).apply {
                text = "Pause"
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(127, 111, 239), Color.rgb(94, 78, 208))
                ).apply { cornerRadius = dp(18).toFloat() }
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    val paused = prefs.getBoolean("agent_paused", false)
                    prefs.edit().putBoolean("agent_paused", !paused)
                        .putString("pending_action", "")
                        .putString("current_agent_status", if (paused) "● JARVIS · Resuming" else "Ⅱ JARVIS · Paused")
                        .apply()
                    text = if (paused) "Pause" else "Resume"
                }
            }
            panel.addView(pause, LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(5) })
            agentPauseButton = pause
            val stop = Button(this).apply {
                text = "Stop"
                textSize = 11f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(235, 113, 126), Color.rgb(190, 55, 72))
                ).apply { cornerRadius = dp(18).toFloat() }
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    prefs.edit().putBoolean("session_active", false).putBoolean("agent_paused", false)
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
        agentPauseButton = null
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
        val now = System.currentTimeMillis()
        if (now - lastEventTimestampSaved >= 3000L) {
            getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
                .putLong("last_accessibility_event", now).apply()
            lastEventTimestampSaved = now
        }
        syncAgentTab()
        processActiveWindow()
    }

    private fun processActiveWindow() {
        val prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)
        if (!prefs.getBoolean("session_active", false)) return

        // Pause blocks execution but still refreshes the screen snapshot so resume plans
        // against the current screen rather than stale labels.
        val paused = prefs.getBoolean("agent_paused", false)
        val pending = if (paused) "" else prefs.getString("pending_action", "").orEmpty()
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
                        else clickNodeByText(rootInActiveWindow, value)
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
        val snapshot = labels.distinct().take(120).joinToString("\n")
        // Avoid repeated SharedPreferences writes while the visible screen is unchanged.
        if (prefs.getString("latest_screen_text", "").orEmpty() != snapshot) {
            prefs.edit().putString("latest_screen_text", snapshot).apply()
        }
    }

    private fun openAppByLabel(query: String): String {
        if (query.isBlank()) return "Open app failed: app name was empty"
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        fun normalize(value: String): String = value.lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        val wanted = normalize(query)
        val launchable = apps.mapNotNull { app ->
            val launch = pm.getLaunchIntentForPackage(app.packageName) ?: return@mapNotNull null
            val label = pm.getApplicationLabel(app).toString()
            Triple(app, launch, normalize(label))
        }
        // Prefer exact normalized matches, then containment, then all meaningful words.
        val match = launchable.firstOrNull { it.third == wanted }
            ?: launchable.firstOrNull { it.third.contains(wanted) || wanted.contains(it.third) }
            ?: launchable.firstOrNull { candidate ->
                val words = wanted.split(" ").filter { it.length > 2 }
                words.isNotEmpty() && words.all { candidate.third.contains(it) }
            }
            ?: return "Could not find an installed app matching: $query. Check that it is installed and visible in the launcher."
        val launch = match.second
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(launch)
            "Opened app: " + pm.getApplicationLabel(match.first)
        } catch (e: Exception) {
            "Open app failed: " + (e.message ?: e.javaClass.simpleName)
        }
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

    private fun normalizeLabel(value: CharSequence?): String =
        value?.toString()?.lowercase()?.replace("&", " and ")
            ?.replace(Regex("[^a-z0-9]+"), " ")?.trim()
            ?.replace(Regex("\\s+"), " ").orEmpty()

    private fun clickNodeByText(root: AccessibilityNodeInfo?, target: String): String {
        if (root == null || target.isBlank()) return "Tap failed: no active accessibility window"
        val wanted = normalizeLabel(target)
        data class Match(val node: AccessibilityNodeInfo, val exact: Boolean)
        data class TapTarget(val node: AccessibilityNodeInfo, val bounds: android.graphics.Rect, val area: Long, val exact: Boolean)
        val candidates = mutableListOf<Match>()

        fun collectMatches(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 28 || candidates.size >= 180) return
            if (!node.isPassword) {
                val labels = listOf(node.text, node.contentDescription)
                    .mapNotNull { it?.toString() }
                    .map(::normalizeLabel)
                    .filter { it.isNotBlank() }
                val matched = labels.firstOrNull { label ->
                    label == wanted || label.contains(wanted) || (wanted.length >= 4 && wanted.contains(label))
                }
                if (matched != null && candidates.none { it.node.boundsInScreen().sameBounds(node.boundsInScreen()) }) {
                    candidates.add(Match(node, matched == wanted))
                }
            }
            for (i in 0 until node.childCount) collectMatches(node.getChild(i), depth + 1)
        }
        collectMatches(root, 0)
        if (candidates.isEmpty()) return "Tap failed: no visible control matched '$target'"

        val metrics = resources.displayMetrics
        val screenArea = metrics.widthPixels.toLong() * metrics.heightPixels.toLong()
        val minTarget = dp(40)
        val clickableTargets = mutableListOf<TapTarget>()
        val fallbackTargets = mutableListOf<TapTarget>()
        for (match in candidates) {
            var current: AccessibilityNodeInfo? = match.node
            var depth = 0
            while (current != null && depth++ < 10) {
                val bounds = android.graphics.Rect()
                current.getBoundsInScreen(bounds)
                val area = bounds.width().toLong() * bounds.height().toLong()
                val visible = bounds.right > 0 && bounds.bottom > 0 &&
                    bounds.left < metrics.widthPixels && bounds.top < metrics.heightPixels
                val usable = visible && bounds.width() >= minTarget && bounds.height() >= minTarget &&
                    area > 0 && area < screenArea * 3 / 4
                if (usable) {
                    val candidate = TapTarget(current, android.graphics.Rect(bounds), area, match.exact)
                    if (fallbackTargets.none { it.bounds == bounds }) fallbackTargets.add(candidate)
                    if (current.isClickable && current.isEnabled &&
                        clickableTargets.none { it.bounds == bounds }) clickableTargets.add(candidate)
                }
                current = current.parent
            }
        }

        val bestClickable = clickableTargets.sortedWith(
            compareByDescending<TapTarget> { it.exact }.thenBy { it.area }
        ).firstOrNull()
        if (bestClickable != null &&
            bestClickable.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            showTapMarker(bestClickable.bounds)
            return "Accessibility click accepted: '$target'; checking the next screen"
        }

        // If semantic click is unavailable, use the centre of the nearest reasonable
        // visible target and wait for Android's gesture callback before reporting dispatch.
        val fallback = fallbackTargets.sortedWith(
            compareByDescending<TapTarget> { it.exact }.thenBy { it.area }
        ).firstOrNull()
        if (fallback != null) {
            val dispatch = tapCenter(fallback.bounds, target)
            if (dispatch) return "Tap gesture queued: '$target'"
            return "Tap failed: Android rejected the gesture for '$target'"
        }
        return "Tap failed: matched '$target' but no safe visible tap target was available"
    }

    private fun AccessibilityNodeInfo.boundsInScreen(): android.graphics.Rect =
        android.graphics.Rect().also { getBoundsInScreen(it) }

    private fun android.graphics.Rect.sameBounds(other: android.graphics.Rect): Boolean = this == other

    private fun tapCenter(bounds: android.graphics.Rect, target: String): Boolean {
        val metrics = resources.displayMetrics
        val x = bounds.centerX().coerceIn(1, metrics.widthPixels - 2).toFloat()
        val y = bounds.centerY().coerceIn(1, metrics.heightPixels - 2).toFloat()
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 120))
            .build()
        return dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                showTapMarker(bounds)
                getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
                    .putString("last_action_result", "Tap gesture completed: '$target'; verify the next screen")
                    .apply()
            }

            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                getSharedPreferences("pocket_agent", MODE_PRIVATE).edit()
                    .putString("last_action_result", "Tap gesture cancelled: '$target'")
                    .apply()
            }
        }, handler)
    }

    private fun showTapMarker(bounds: android.graphics.Rect) {
        try {
            if (bounds.width() < dp(12) || bounds.height() < dp(12)) return
            val marker = object : View(this) {
                private val outline = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(105, 86, 220)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = dp(2).toFloat()
                }
                private val centerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(105, 86, 220)
                    style = android.graphics.Paint.Style.FILL
                }
                override fun onDraw(canvas: android.graphics.Canvas) {
                    super.onDraw(canvas)
                    val cx = width / 2f
                    val cy = height / 2f
                    canvas.drawCircle(cx, cy, dp(15).toFloat(), outline)
                    canvas.drawCircle(cx, cy, dp(3).toFloat(), centerPaint)
                }
            }.apply {
                setBackgroundColor(Color.TRANSPARENT)
                elevation = dp(18).toFloat()
            }
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try { tapMarker?.let { wm.removeView(it) } } catch (_: Exception) { }
            // Small visual marker around the tap center; actual gesture coordinates remain unchanged.
            val markerWidth = dp(38)
            val markerHeight = dp(38)
            val params = WindowManager.LayoutParams(
                markerWidth,
                markerHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (bounds.centerX() - markerWidth / 2).coerceAtLeast(0)
                y = (bounds.centerY() - markerHeight / 2).coerceAtLeast(0)
            }
            wm.addView(marker, params)
            tapMarker = marker
            handler.postDelayed({
                try { tapMarker?.let { wm.removeView(it) } } catch (_: Exception) { }
                tapMarker = null
            }, 900)
        } catch (_: Exception) { }
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
