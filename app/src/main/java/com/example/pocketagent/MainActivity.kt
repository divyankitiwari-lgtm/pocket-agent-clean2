package com.example.pocketagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var model: Spinner
    private lateinit var apiKey: EditText
    private lateinit var openRouterKey: EditText
    private lateinit var task: EditText
    private lateinit var screenPreview: TextView
    private lateinit var answerView: TextView
    private lateinit var useScreen: CheckBox
    private lateinit var askButton: Button
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var securePrefs: android.content.SharedPreferences
    private lateinit var contentLayout: LinearLayout

    private val ink = Color.rgb(27, 30, 47)
    private val muted = Color.rgb(115, 119, 139)
    private val accent = Color.rgb(117, 92, 255)
    private val page = Color.rgb(246, 245, 252)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)
        securePrefs = try {
            val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(this, "pocket_agent_secrets", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        } catch (_: Exception) {
            getSharedPreferences("pocket_agent_secrets_fallback", MODE_PRIVATE)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        contentLayout = content
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val topBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val menuButton = actionButton("☰  Menu & History", white, accent)
        menuButton.setOnClickListener { showHistoryMenu() }
        topBar.addView(menuButton, LinearLayout.LayoutParams(-2, dp(46)))
        val logoutButton = actionButton("↪ Logout", Color.rgb(255, 235, 237), Color.rgb(180, 50, 70))
        logoutButton.setOnClickListener { confirmLogout() }
        topBar.addView(logoutButton, LinearLayout.LayoutParams(-2, dp(46)).apply { marginStart = dp(6) })
        val liveButton = actionButton("◉ Live Activity", Color.rgb(30, 32, 48), Color.WHITE)
        liveButton.setOnClickListener {
            val log = prefs.getString("live_communication_log", "").orEmpty().ifBlank {
                "No activity yet. Start a task to see each planned action and its result here."
            }
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(8))
                setBackgroundColor(Color.rgb(247, 247, 252))
            }
            panel.addView(TextView(this).apply {
                text = "LIVE TASK TRACE"
                textSize = 11f
                letterSpacing = 0.12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accent)
            }, margins(bottom = 6))
            panel.addView(TextView(this).apply {
                text = "Actions, tap results, and pauses · stored on this phone"
                textSize = 12f
                setTextColor(muted)
            }, margins(bottom = 10))
            val logView = TextView(this).apply {
                text = log
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(ink)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(Color.WHITE, 14)
                setTextIsSelectable(true)
            }
            val logScroll = ScrollView(this).apply { addView(logView) }
            panel.addView(logScroll, LinearLayout.LayoutParams(-1, dp(340)))
            val liveDialog = AlertDialog.Builder(this)
                .setTitle("Pocket Agent · Live Activity")
                .setView(panel)
                .setPositiveButton("Close", null)
                .setNeutralButton("Clear log", null)
                .setNegativeButton("Copy log", null)
                .create()
            liveDialog.setOnShowListener {
                liveDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    prefs.edit().putString("live_communication_log", "").apply()
                    logView.text = "Log cleared."
                }
                liveDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Pocket Agent activity", logView.text.toString()))
                    status.text = "Activity log copied"
                }
            }
            liveDialog.show()
        }
        topBar.addView(liveButton, LinearLayout.LayoutParams(-2, dp(46)).apply { marginStart = dp(6) })
        val topStatus = TextView(this).apply {
            text = if (prefs.getBoolean("accessibility_connected", false)) "● Service connected" else "○ Service not connected"
            textSize = 11f
            setTextColor(if (prefs.getBoolean("accessibility_connected", false)) Color.rgb(35, 140, 90) else muted)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        topBar.addView(topStatus, LinearLayout.LayoutParams(0, dp(46), 1f))
        content.addView(topBar, margins(bottom = 10))

        val hero = card(white).apply { setPadding(dp(20), dp(20), dp(20), dp(20)) }
        hero.addView(TextView(this).apply {
            text = "✦  POCKET AGENT"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.12f
            setTextColor(accent)
        })
        hero.addView(TextView(this).apply {
            text = "Your AI screen assistant"
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ink)
            setPadding(0, dp(10), 0, dp(5))
        })
        hero.addView(TextView(this).apply {
            text = "Give one task. Pocket Agent plans privately, launches apps, taps, types, scrolls, and checks each result. Gemini stays in the background; this screen shows actions, not chat answers."
            textSize = 14f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        content.addView(hero, margins(bottom = 14))

        val plansCard = card(white)
        plansCard.addView(sectionTitle("PLANS · FREE / PREMIUM / MAX"))
        plansCard.addView(TextView(this).apply {
            text = "Free: 60 steps per task. Premium: up to 4 hours per task. Max: no app-imposed step cap. API quota, connection, and safety limits still apply."
            textSize = 13f
            setTextColor(muted)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        })
        val plansButton = actionButton("View plans & select tier", Color.rgb(238, 235, 255), accent)
        plansButton.setOnClickListener { showPlanOptions() }
        plansCard.addView(plansButton, margins(bottom = 10))
        content.addView(plansCard, margins(bottom = 14))

        val setup = card(white)
        setup.addView(sectionTitle("01  ·  CONNECTION"))
        setup.addView(label("Gemini API key"))
        apiKey = field("Paste your Gemini API key once", true).apply {
            setText(securePrefs.getString("gemini_api_key", "").orEmpty())
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) securePrefs.edit().putString("gemini_api_key", text.toString().trim()).apply()
            }
        }
        setup.addView(apiKey, margins(bottom = 6))
        val saveKeyButton = actionButton("Save key securely on this phone", Color.rgb(30, 215, 96), Color.rgb(10, 18, 14))
        saveKeyButton.setOnClickListener {
            val value = apiKey.text.toString().trim()
            if (value.isBlank()) status.text = "Paste your key first"
            else {
                securePrefs.edit().putString("gemini_api_key", value).apply()
                status.text = "API key saved encrypted on this phone"
            }
        }
        setup.addView(saveKeyButton, margins(bottom = 10))
        setup.addView(label("Optional backup provider · OpenRouter"))
        openRouterKey = field("OpenRouter API key (optional)", true).apply {
            setText(securePrefs.getString("openrouter_api_key", "").orEmpty())
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) securePrefs.edit().putString("openrouter_api_key", text.toString().trim()).apply()
            }
        }
        setup.addView(openRouterKey, margins(bottom = 6))
        val saveOpenRouterKeyButton = actionButton("Save OpenRouter key", Color.rgb(238, 235, 255), accent)
        saveOpenRouterKeyButton.setOnClickListener {
            val value = openRouterKey.text.toString().trim()
            if (value.isBlank()) status.text = "Paste an OpenRouter key first"
            else {
                securePrefs.edit().putString("openrouter_api_key", value).apply()
                status.text = "OpenRouter key saved on this phone"
            }
        }
        setup.addView(saveOpenRouterKeyButton, margins(bottom = 10))
        setup.addView(label("Model"))
        model = Spinner(this).apply {
            val modelNames = listOf(
                "Gemini 3.8 Flash · recommended — gemini-3.8-flash",
                "Gemini 3.6 Flash — gemini-3.6-flash",
                "Gemini 3.5 Flash-Lite — gemini-3.5-flash-lite",
                "Gemini 3.1 Flash-Lite — gemini-3.1-flash-lite",
                "Gemini 3 Flash Preview — gemini-3-flash-preview",
                "Gemini 3.1 Pro Preview — gemini-3.1-pro-preview",
                "Gemini 2.5 Flash — gemini-2.5-flash",
                "Gemini 2.5 Flash-Lite — gemini-2.5-flash-lite",
                "Gemini 2.5 Pro — gemini-2.5-pro",
                "Gemma 4 31B IT — gemma-4-31b-it",
                "Gemma 4 26B MoE IT — gemma-4-26b-a4b-it",
                "OpenRouter Free Router · backup provider — openrouter/free"
            )
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, modelNames).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(0)
            background = rounded(Color.rgb(247, 247, 252), 16)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        setup.addView(model, margins(bottom = 8))
        setup.addView(TextView(this).apply {
            text = "Free API keys are supported. Free-tier access and daily limits depend on Google and the selected model; this app cannot unlock paid-only access. Choose a model below if one is unavailable for your key."
            textSize = 12f
            setTextColor(muted)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        content.addView(setup, margins(bottom = 14))

        val taskCard = card(white)
        taskCard.addView(sectionTitle("02  ·  YOUR TASK"))
        taskCard.addView(label("What should the agent do?"))
        task = field("Describe your task in plain English", false).apply {
            setText(prefs.getString("last_task", "").orEmpty())
            minLines = 3
            maxLines = 5
            gravity = Gravity.TOP or Gravity.START
            setSingleLine(false)
        }
        taskCard.addView(task, margins(bottom = 8))
        useScreen = CheckBox(this).apply {
            text = "Include observed screen text"
            textSize = 13f
            setTextColor(ink)
            isChecked = true
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        taskCard.addView(useScreen, margins(bottom = 12))
        askButton = actionButton("Ask Gemini", accent, white).apply { visibility = View.GONE }
        askButton.setOnClickListener { sendGeminiRequest() }
        taskCard.addView(askButton, margins(bottom = 8))
        val agentButton = actionButton("▶ Run task", Color.rgb(30, 32, 48), Color.WHITE)
        agentButton.setOnClickListener { startAutonomousAgent(agentButton) }
        taskCard.addView(agentButton, margins(bottom = 8))
        content.addView(taskCard, margins(bottom = 14))

        val answerCard = card(white)
        answerCard.addView(sectionTitle("03  ·  ACTION LOG"))
        answerView = TextView(this).apply {
            text = "When the agent runs, this area records actions such as “Opened YouTube”, “Tapped Search”, and “Scrolled down”. Gemini text answers are hidden."
            textSize = 14f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(247, 247, 252), 16)
        }
        answerCard.addView(answerView, margins(bottom = 8))
        content.addView(answerCard, margins(bottom = 14))

        val screenCard = card(white)
        screenCard.addView(sectionTitle("04  ·  SCREEN OBSERVATION"))
        screenCard.addView(TextView(this).apply {
            text = "Enable the accessibility service, open the app you want to inspect, then return here and refresh."
            textSize = 13f
            setTextColor(muted)
        }, margins(bottom = 10))
        val accessButton = actionButton("Open Accessibility Settings", Color.rgb(238, 235, 255), accent)
        accessButton.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        screenCard.addView(accessButton, margins(bottom = 8))
        val backgroundButton = actionButton("Allow background running", Color.rgb(230, 244, 255), Color.rgb(25, 75, 125))
        backgroundButton.setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
            status.text = "In Battery settings, allow Pocket Agent to run without battery restrictions if your phone offers that option."
        }
        screenCard.addView(backgroundButton, margins(bottom = 8))
        val refreshButton = actionButton("Refresh observed screen text", Color.rgb(239, 240, 247), ink)
        refreshButton.setOnClickListener { refreshScreenText() }
        screenCard.addView(refreshButton, margins(bottom = 10))
        screenPreview = TextView(this).apply {
            textSize = 12f
            setTextColor(ink)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(247, 247, 252), 16)
            text = prefs.getString("latest_screen_text", "").orEmpty().ifBlank {
                "No screen text captured yet."
            }
        }
        screenCard.addView(screenPreview)
        content.addView(screenCard, margins(bottom = 14))

        val controlsCard = card(white)
        controlsCard.addView(sectionTitle("05  ·  SESSION CONTROL"))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val start = actionButton("Start", accent, white)
        start.setOnClickListener {
            prefs.edit().putBoolean("session_active", true).apply()
            status.text = "Observation started"
            refreshScreenText()
        }
        val pause = actionButton("Pause", Color.rgb(238, 235, 255), accent)
        pause.setOnClickListener {
            prefs.edit().putBoolean("session_active", false).apply()
            status.text = "Observation paused"
        }
        val stop = actionButton("Stop", Color.rgb(255, 235, 237), Color.rgb(180, 50, 70))
        stop.setOnClickListener {
            prefs.edit().putBoolean("session_active", false).putString("latest_screen_text", "").apply()
            screenPreview.text = "Screen text cleared."
            status.text = "Observation stopped"
        }
        controls.addView(start, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        controls.addView(pause, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5); marginEnd = dp(5) })
        controls.addView(stop, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        controlsCard.addView(controls, margins(bottom = 12))
        status = TextView(this).apply {
            text = if (securePrefs.getString("gemini_api_key", "").isNullOrBlank()) "Ready · add your API key" else "Ready · encrypted key found"
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(247, 247, 252), 14)
        }
        controlsCard.addView(status)
        content.addView(controlsCard, margins(bottom = 12))

        content.addView(TextView(this).apply {
            text = "Privacy note: your key is saved on this phone using encrypted preferences when available. Visible screen labels and your task are sent to Google Gemini while the agent runs. Never automate passwords, payments, messages, deletion, or private information. Accessibility actions can fail; verify each step."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(4), dp(4), dp(12))
        })

        setContentView(root)
        if (!prefs.getBoolean("demo_account_created", false)) {
            content.post { showWelcomeGate() }
        } else if (isAdminAccount()) {
            content.post { showAdminBanner() }
        }
    }

    private fun localModelFile(): File = File(filesDir, "pocket-agent-local-model.task")

    @Deprecated("Use document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7412 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        status.text = "Importing local model…"
        Thread {
            try {
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw IllegalStateException("Could not read selected model file.")
                    localModelFile().outputStream().use { output -> input.copyTo(output) }
                }
                runOnUiThread {
                    status.text = "Local model imported · " + (localModelFile().length() / (1024 * 1024)) + " MB"
                    logCommunication("LOCAL MODEL", "Imported model file to app-private storage.")
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Model import failed: " + (e.message ?: "unknown error") }
            }
        }.start()
    }

    private fun generateLocalAi(prompt: String): String {
        val options = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
            .setModelPath(localModelFile().absolutePath)
            .setMaxTokens(512)
            .build()
        val engine = com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(this, options)
        return try { engine.generateResponse(prompt) } finally { engine.close() }
    }

    private fun localRulePlan(userTask: String, screen: String, previousResult: String): String {
        val lower = screen.lowercase()
        val taskLower = userTask.lowercase()
        fun command(action: String, text: String, reason: String) =
            JSONObject().put("action", action).put("text", text).put("reason", reason).toString()
        if (screen.isBlank() || (lower.contains("pocket agent") && !lower.contains("quiz and earn") && !lower.contains("mega offer"))) {
            val app = if (taskLower.contains("quiz and earn")) "Quiz and Earn" else
                Regex("(?:open|launch|go to)\\s+([A-Za-z0-9][A-Za-z0-9 &_-]{1,40})", RegexOption.IGNORE_CASE)
                    .find(userTask)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', '?') ?: ""
            if (app.isNotBlank()) return command("open_app", app, "The target app is not open.")
            return command("ask_user", "Open the target app, then start the task again.", "Local rules need a visible target app.")
        }
        if (previousResult.contains("Could not find", true) || previousResult.contains("failed", true)) {
            return command("ask_user", "The last action failed. Check the screen and choose the next step.", previousResult.take(180))
        }
        val nodes = screen.lines().map { it.trim() }.filter { it.length in 2..90 }.distinct()
        val keywords = when {
            taskLower.contains("mega offer") -> listOf("mega offer", "offer", "continue", "claim", "start")
            taskLower.contains("claim") || taskLower.contains("reward") -> listOf("claim", "collect", "reward", "continue", "done")
            taskLower.contains("quiz") -> listOf("quiz", "play", "start", "continue", "next")
            taskLower.contains("search") -> listOf("search", "next", "continue")
            else -> listOf("continue", "next", "done", "close")
        }
        val candidate = nodes.firstOrNull { line -> keywords.any { kw -> line.equals(kw, true) } }
            ?: nodes.firstOrNull { line -> keywords.any { kw -> line.contains(kw, true) } }
        if (candidate != null) return command("tap_text", candidate, "Matched a visible label to task keywords.")
        if (taskLower.contains("scroll") || screen.length > 1200) return command("scroll_down", "", "No matching label; inspect lower screen content.")
        return command("ask_user", "I couldn't match a safe button. Tell me which visible button to use.", "Local rules won't guess randomly.")
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log out of Pocket Agent?")
            .setMessage("This ends the current local demo session and stops any running agent. Your saved API key and on-phone task history will remain on this device.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                agentRunning = false
                prefs.edit()
                    .putBoolean("session_active", false)
                    .putString("pending_action", "")
                    .putString("current_agent_status", "● Pocket Agent · Logged out")
                    .putBoolean("demo_account_created", false)
                    .remove("demo_display_name")
                    .remove("demo_email")
                    .apply()
                status.text = "Logged out · automation stopped"
                answerView.text = "You are logged out. Create a demo profile to use the dashboard again."
                screenPreview.text = "Screen observation paused while logged out."
                showWelcomeGate()
            }
            .show()
    }

    private fun showAdminBanner() {
        if (!isAdminAccount()) return
        val banner = TextView(this).apply {
            text = "YOU ARE ADMIN · MAX PLAN ACTIVE"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(39, 35, 76))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(153, 132, 255))
            }
        }
        contentLayout.addView(banner, 0, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(16), dp(8), dp(16), dp(12)) })
        banner.postDelayed({
            (banner.parent as? android.view.ViewGroup)?.removeView(banner)
        }, 10_000L)
    }

    private fun isAdminAccount(): Boolean =
        prefs.getString("demo_email", "").orEmpty().trim().equals("divyankitiwari@gmail.com", ignoreCase = true)

    private fun currentPlan(): String {
        if (isAdminAccount()) return "Max"
        val chosen = prefs.getString("selected_plan", "Free").orEmpty()
        return if (chosen in listOf("Free", "Premium", "Max")) chosen else "Free"
    }

    private fun showPlanOptions() {
        if (isAdminAccount()) {
            prefs.edit().putString("selected_plan", "Max").apply()
            AlertDialog.Builder(this).setTitle("Admin plan · Max")
                .setMessage("This prototype recognises the configured admin email locally. This is NOT secure authentication: anyone who types this email can impersonate the admin until verified sign-in is implemented. API limits still apply.")
                .setPositiveButton("OK", null).show()
            return
        }
        val plans = arrayOf("Free · ₹0 · 60 steps per task", "Premium · 4-hour task sessions", "Max · no app-imposed step cap")
        AlertDialog.Builder(this).setTitle("Choose a plan")
            .setSingleChoiceItems(plans, listOf("Free", "Premium", "Max").indexOf(currentPlan())) { dialog, which ->
                val selected = listOf("Free", "Premium", "Max")[which]
                prefs.edit().putString("selected_plan", selected).apply()
                status.text = "$selected plan selected · prototype only"
                dialog.dismiss()
                AlertDialog.Builder(this).setTitle("$selected selected")
                    .setMessage("Local prototype selection only. Real payments and verified entitlements are not connected. API limits still apply.")
                    .setPositiveButton("OK", null).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showWelcomeGate() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val name = field("Your display name", false)
        val email = field("Email (demo only)", false).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = field("Password (not saved)", true)
        panel.addView(name, margins(bottom = 8))
        panel.addView(email, margins(bottom = 8))
        panel.addView(password, margins(bottom = 8))
        panel.addView(TextView(this).apply {
            text = "DEMO SIGN-UP · Local test profile only. No real online account is created. Passwords are not stored."
            textSize = 12f
            setTextColor(muted)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Welcome to Pocket Agent")
            .setMessage("Create a demo profile to explore the agent dashboard.")
            .setView(panel)
            .setCancelable(false)
            .setPositiveButton("Create demo account", null)
            .setNeutralButton("Demo payment options", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val displayName = name.text.toString().trim()
                val emailValue = email.text.toString().trim()
                if (displayName.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() || password.text.isNullOrBlank()) {
                    status.text = "Enter a display name, valid email, and test password"
                    return@setOnClickListener
                }
                prefs.edit().putBoolean("demo_account_created", true)
                    .putString("demo_display_name", displayName)
                    .putString("demo_email", emailValue).apply()
                dialog.dismiss()
                status.text = "Demo profile created locally · no real account was made"
                if (emailValue.equals("divyankitiwari@gmail.com", ignoreCase = true)) {
                    contentLayout.post { showAdminBanner() }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showDemoPayments()
            }
        }
        dialog.show()
    }

    private fun showDemoPayments() {
        val options = arrayOf(
            "Free · ₹0 · No payment",
            "Plus demo · ₹99 · SIMULATION ONLY",
            "Pro demo · ₹199 · SIMULATION ONLY",
            "Google Play balance · preview only"
        )
        AlertDialog.Builder(this)
            .setTitle("Demo plans & payments")
            .setMessage("TEST MODE ONLY — no payment provider is connected. These choices never charge money and do not unlock paid services.")
            .setItems(options) { _, which ->
                val choice = options[which]
                prefs.edit().putString("demo_plan_selection", choice).apply()
                AlertDialog.Builder(this)
                    .setTitle("Demo selection recorded")
                    .setMessage("$choice\n\nSimulation only. No payment was attempted, no Google Play transaction occurred, and no real subscription was created.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun card(color: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(color, 22)
        elevation = dp(2).toFloat()
    }

    private fun margins(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        letterSpacing = 0.06f
        setTypeface(null, Typeface.BOLD)
        setTextColor(accent)
        setPadding(0, 0, 0, dp(12))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setTextColor(ink)
        setPadding(0, dp(3), 0, dp(6))
    }

    private fun field(hintText: String, password: Boolean): EditText = EditText(this).apply {
        hint = hintText
        textSize = 14f
        setTextColor(ink)
        setHintTextColor(muted)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(Color.rgb(247, 247, 252), 16)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }

    private fun actionButton(title: String, bg: Int, fg: Int) = Button(this).apply {
        val normalBackground = rounded(bg, 16)
        text = title
        textSize = 13f
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        setTextColor(fg)
        background = normalBackground
        // Clear visual press feedback: briefly turn grey while the finger is down.
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.background = rounded(Color.rgb(185, 188, 198), 16)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.background = normalBackground
            }
            false // preserve normal click handling
        }
        minHeight = dp(48)
        stateListAnimator = null
        elevation = 0f
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    override fun onResume() {
        super.onResume()
        if (::screenPreview.isInitialized) refreshScreenText()
        if (::status.isInitialized) status.text =
            if (prefs.getBoolean("accessibility_connected", false)) "Accessibility service connected"
            else "Accessibility service not connected"
    }

    private fun refreshScreenText() {
        val text = prefs.getString("latest_screen_text", "").orEmpty()
        val connected = prefs.getBoolean("accessibility_connected", false)
        screenPreview.text = when {
            text.isNotBlank() -> text
            !connected -> "Pocket Agent has not confirmed an active Accessibility connection. Reopen this app or toggle the service off/on."
            !prefs.getBoolean("session_active", false) -> "Accessibility is connected, but observation is paused. Tap Start, switch to the target app, wait, then return and refresh."
            else -> "Service connected, but no readable labels were captured. Some apps hide their content from accessibility services."
        }
    }

    private fun showHistoryMenu() {
        val options = arrayOf("History", "Clear history", "Connection & privacy", "Account & logout")
        AlertDialog.Builder(this).setTitle("Pocket Agent")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showHistory()
                    1 -> AlertDialog.Builder(this).setTitle("Clear history?")
                        .setMessage("Delete saved prompts and responses from this phone?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear") { _, _ ->
                            prefs.edit().putString("history", "[]").apply()
                            status.text = "History cleared"
                        }.show()
                    2 -> AlertDialog.Builder(this).setTitle("Connection & privacy")
                        .setMessage("Your Gemini API key is stored on this phone using encrypted preferences when available. Task history is stored locally. Screen labels and task text may be sent to Google Gemini while the agent runs. This profile is a local demo profile, not a secure online account.")
                        .setPositiveButton("OK", null).show()
                    3 -> confirmLogout()
                }
            }.show()
    }

    private fun showHistory() {
        val entries = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        if (entries.length() == 0) {
            AlertDialog.Builder(this).setTitle("History").setMessage("No saved requests yet.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = Array(entries.length()) { i ->
            val item = entries.optJSONObject(i) ?: JSONObject()
            item.optString("task", "Task").take(60) + "\n" + item.optString("time", "") +
                " · " + item.optString("answer", "").take(75)
        }
        AlertDialog.Builder(this).setTitle("Saved history").setItems(labels) { _, index ->
            val item = entries.optJSONObject(index) ?: return@setItems
            AlertDialog.Builder(this).setTitle(item.optString("task", "Saved task"))
                .setMessage("Model: " + item.optString("model", "") + "\n" + item.optString("time", "") +
                    "\n\n" + item.optString("answer", ""))
                .setNeutralButton("Use prompt") { _, _ -> task.setText(item.optString("task", "")) }
                .setPositiveButton("Close", null).show()
        }.setPositiveButton("Close", null).show()
    }

    private fun saveHistory(userTask: String, chosenModel: String, answer: String) {
        val old = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        val updated = JSONArray().put(JSONObject()
            .put("task", userTask).put("model", chosenModel).put("answer", answer)
            .put("time", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())))
        for (i in 0 until minOf(old.length(), 49)) updated.put(old.getJSONObject(i))
        prefs.edit().putString("history", updated.toString()).putString("last_task", userTask).apply()
    }

    private fun logCommunication(kind: String, message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = "[" + time + "] " + kind + "\n" + message
        val old = prefs.getString("live_communication_log", "").orEmpty()
        prefs.edit().putString("live_communication_log", (old + "\n\n" + entry).takeLast(12000)).apply()
    }

    @Volatile private var agentRunning = false

    private fun actionLabel(action: String, value: String): String {
        val label = when (action) {
            "open_app" -> "Opening app: $value"
            "tap_text" -> "Tapping: $value"
            "type_text" -> "Entering text"
            "scroll_down" -> "Scrolling down"
            "scroll_up" -> "Scrolling up"
            "back" -> "Pressing Back"
            "home" -> "Going to Home"
            else -> action.replaceFirstChar { it.uppercase() } + if (value.isBlank()) "" else ": $value"
        }
        return label
    }

    private fun startAutonomousAgent(agentButton: Button) {
        val chosenModel = model.selectedItem.toString().substringAfter("—").trim()
        val isOpenRouter = chosenModel.startsWith("openrouter/")
        val key = if (isOpenRouter) openRouterKey.text.toString().trim().ifBlank { securePrefs.getString("openrouter_api_key", "").orEmpty() }
            else apiKey.text.toString().trim().ifBlank { securePrefs.getString("gemini_api_key", "").orEmpty() }
        val userTask = task.text.toString().trim()
        if (key.isBlank() || userTask.isBlank()) { status.text = if (key.isBlank()) "Add the API key for the selected provider first" else "Describe the task first"; return }
        if (agentRunning) {
            agentRunning = false
            prefs.edit().putBoolean("session_active", false).putString("pending_action", "").apply()
            status.text = "Stopping agent…"; agentButton.text = "▶ Start autonomous agent"; return
        }
        agentRunning = true
        prefs.edit().putBoolean("session_active", true).putString("current_agent_status", "● Pocket Agent · Starting").putString("last_task", userTask).putString("pending_action", "").putString("last_action_result", "").apply()
        agentButton.text = "■ Stop agent"
        status.text = "Agent starting · it will open the requested app and act step by step"
        logCommunication("AGENT START", userTask)
        runOnUiThread { answerView.text = "Starting task…"; status.text = "Planning privately · no Gemini chat replies" }
        Thread {
            try {
                var lastResult = ""
                var step = 1
                val sessionStartedAt = System.currentTimeMillis()
                while (agentRunning &&
                    (currentPlan() != "Free" || step <= 60) &&
                    (currentPlan() != "Premium" || System.currentTimeMillis() - sessionStartedAt < 4L * 60L * 60L * 1000L)) {
                    if (!agentRunning || !prefs.getBoolean("session_active", false)) break
                    var screen = prefs.getString("latest_screen_text", "").orEmpty()
                    if (screen.isBlank()) { runOnUiThread { status.text = "Waiting for readable screen text · open target app" }; Thread.sleep(1200); screen = prefs.getString("latest_screen_text", "").orEmpty() }
                    // Deterministic launch bootstrap: don't rely on Gemini to discover that it must leave Pocket Agent.
                    // This prevents an empty/stale screen snapshot from producing idle or unrelated actions.
                    val taskNamesQuizAndEarn = userTask.contains("Quiz and Earn", ignoreCase = true)
                    val screenLooksLikeAgent = screen.isBlank() ||
                        (screen.contains("POCKET AGENT", ignoreCase = true) &&
                         !screen.contains("Mega Offer", ignoreCase = true) &&
                         !screen.contains("Quiz and Earn", ignoreCase = true))
                    val alreadyTriedLaunch = lastResult.contains("Opened app: Quiz and Earn", ignoreCase = true) ||
                        lastResult.contains("Could not find an installed app matching", ignoreCase = true) ||
                        lastResult.contains("Open app failed", ignoreCase = true) ||
                        lastResult.contains("No launcher activity", ignoreCase = true)
                    if (taskNamesQuizAndEarn && screenLooksLikeAgent && !alreadyTriedLaunch) {
                        val bootstrapAction = JSONObject().put("action", "open_app").put("text", "Quiz and Earn").toString()
                        prefs.edit().putString("last_action_result", "").putString("pending_action", bootstrapAction).apply()
                        logCommunication("BOOTSTRAP", "Launching Quiz and Earn before asking Gemini to plan screen actions.")
                        runOnUiThread {
                            answerView.append("\\n" + step + ". Opening Quiz and Earn")
                            status.text = "Opening Quiz and Earn…"
                        }
                        var waitedForLaunch = 0
                        var launchResult = ""
                        while (agentRunning && prefs.getBoolean("session_active", false) && waitedForLaunch < 8000) {
                            launchResult = prefs.getString("last_action_result", "").orEmpty()
                            if (launchResult.isNotBlank()) break
                            Thread.sleep(200)
                            waitedForLaunch += 200
                        }
                        lastResult = launchResult.ifBlank { "No confirmation received from Accessibility Service while opening Quiz and Earn." }
                        logCommunication("BOOTSTRAP RESULT", lastResult)
                        runOnUiThread { answerView.append("\\n   ↳ " + lastResult) }
                        Thread.sleep(1200)
                        screen = prefs.getString("latest_screen_text", "").orEmpty()
                    }
                    val instruction = """
You are the silent planning engine for Pocket Agent, an Android UI-control agent. You do not chat with the user. Return ONLY one JSON object and nothing else:
{"action":"tap_text","text":"exact visible label","reason":"short internal reason"}
Allowed actions: open_app, tap_text, type_text, scroll_down, scroll_up, back, home, done, ask_user.
Plan one safe, small action from the current screen, then inspect the next screen. Prefer exact visible labels. BOOTSTRAP RULE: if visible screen text is empty, unreadable, or shows only Pocket Agent while the task names a target app, your first action MUST be {"action":"open_app","text":"<the exact app name from the task>","reason":"Target app is not open; launch it first"}. For this user's known workflow, the target app name is "Quiz and Earn". Do not choose done or ask_user before attempting to launch the named target app. If opening fails, use the action result to report the exact failure; do not silently claim the app is open. Use open_app only when the requested app is not already open. Never reopen an app just because a tap failed.
Use scroll_down/up when the required control is off-screen. If the screen is an unexpected Google Play Store page or an app-install listing and the task was not to use Play Store, press Back ONCE, inspect the screen again, and do not tap the same link/ad again. If Play Store or the same unwanted page appears again, stop with ask_user and briefly state which screen is blocking progress. Never loop repeatedly between an app and Play Store.
If the target screen is already open and the requested action is complete, return done. If the same action fails twice or the screen does not change after two attempts, stop and ask for the specific help needed; do not keep tapping randomly.
Use type_text only when a text field is visibly focused and only for ordinary task text, never passwords, API keys, OTPs, payment details, or private information. Do not make purchases, accept subscriptions, send messages, delete content, or confirm sensitive actions. Never claim success unless the action result or next screen confirms it. Never output a normal conversational answer; the user-facing UI should show action steps only.
For done use {"action":"done","text":"short completion status","reason":"confirmed by screen"}. For ask_user use {"action":"ask_user","text":"specific screen or choice needed","reason":"why progress is paused"}.
User task: ${userTask}
Current plan: ${currentPlan()}. Step number: ${step}.
Plan rules: Free has a 60-step app limit; Premium has a 4-hour session limit; Max has NO app-imposed step-count limit. For Max, continue beyond step 60 when the task is still in progress. Do not stop just because the step number is high. Stop only when the task is confirmed complete, the user needs to decide something, a safety pause is required, or a real technical/API error prevents continuing.
Visible screen text:
${screen.take(9000)}
Previous action result: ${lastResult.take(500)}
""".trimIndent()
                    val raw = requestGeminiText(key, chosenModel, instruction)
                    val match = Regex("\\{[\\s\\S]*?\\}").find(raw)?.value ?: throw IllegalStateException("Gemini did not return a valid action JSON.")
                    val command = JSONObject(match)
                    val action = command.optString("action", "ask_user")
                    val textValue = command.optString("text", "")
                    val reason = command.optString("reason", "")
                    prefs.edit().putString("current_agent_status", "● Step " + step + " · " + action).apply()
                    logCommunication("AGENT STEP " + step, "Action: " + action + " " + textValue + "\\nReason: " + reason)
                    runOnUiThread { answerView.append("\\n" + step + ". " + actionLabel(action, textValue)); status.text = "Working · step " + step + " · " + action }
                    when (action) {
                        "done" -> { runOnUiThread { answerView.append("\\n✓ Task completed"); status.text = "Agent completed" }; break }
                        "ask_user" -> { runOnUiThread { answerView.append("\\n⏸ Needs your input: " + textValue); status.text = "Paused · user input required" }; break }
                        "open_app", "tap_text", "type_text", "scroll_down", "scroll_up", "back", "home" -> {
                            // Reset the previous result so a timeout cannot be mistaken for a successful action.
                            prefs.edit().putString("last_action_result", "")
                                .putString("pending_action", JSONObject().put("action", action).put("text", textValue).toString()).apply()
                            var waited = 0
                            var actionResult = ""
                            while (agentRunning && prefs.getBoolean("session_active", false) && waited < 6000) {
                                actionResult = prefs.getString("last_action_result", "").orEmpty()
                                if (actionResult.isNotBlank()) break
                                Thread.sleep(200)
                                waited += 200
                            }
                            lastResult = actionResult.ifBlank { "No confirmation received. Inspect the current screen and choose a different safe action if needed." }
                            runOnUiThread { answerView.append("\\n   ↳ " + lastResult) }
                            Thread.sleep(700)
                        }
                        else -> { runOnUiThread { answerView.text = "Unsupported action: " + action; status.text = "Agent paused for safety" }; break }
                    }
                    step++
                }
                if (agentRunning && currentPlan() == "Free" && step > 60) {
                    runOnUiThread { answerView.append("\\n\\nFree plan limit reached (60 steps). Start a new task to continue."); status.text = "Free plan · 60-step limit reached" }
                } else if (agentRunning && currentPlan() == "Premium" && System.currentTimeMillis() - sessionStartedAt >= 4L * 60L * 60L * 1000L) {
                    runOnUiThread { answerView.append("\\n\\nPremium session limit reached (4 hours)."); status.text = "Premium · 4-hour session ended" }
                }
            } catch (e: Exception) {
                val message = "Agent error: " + (e.message ?: "unknown error")
                logCommunication("AGENT ERROR", message)
                runOnUiThread { answerView.append("\\n⚠ Agent stopped: " + (e.message ?: "request failed")); status.text = "Agent stopped · check connection or model settings" }
            } finally {
                agentRunning = false
                prefs.edit().putBoolean("session_active", false).putString("current_agent_status", "● Pocket Agent · Stopped").putString("pending_action", "").apply()
                runOnUiThread { agentButton.text = "▶ Run task" }
                logCommunication("AGENT STOP", "Agent loop ended.")
            }
        }.start()
    }

    private fun requestGeminiText(key: String, chosenModel: String, prompt: String): String {
        var connection: HttpURLConnection? = null
        try {
            val isOpenRouter = chosenModel.startsWith("openrouter/")
            val endpoint = if (isOpenRouter) "https://openrouter.ai/api/v1/chat/completions"
                else "https://generativelanguage.googleapis.com/v1beta/models/" +
                    java.net.URLEncoder.encode(chosenModel, "UTF-8") + ":generateContent?key=" + key
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (isOpenRouter) setRequestProperty("Authorization", "Bearer " + key)
            }
            val body = if (isOpenRouter) {
                JSONObject().put("model", chosenModel).put("messages", JSONArray()
                    .put(JSONObject().put("role", "user").put("content", prompt)))
                    .put("temperature", 0.1).put("max_tokens", 500)
            } else {
                JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                    .put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 500))
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = try {
                    if (isOpenRouter) JSONObject(response).optJSONObject("error")?.optString("message")
                    else JSONObject(response).optJSONObject("error")?.optString("message")
                } catch (_: Exception) { null }
                throw IllegalStateException((if (isOpenRouter) "OpenRouter HTTP " else "Gemini HTTP ") + code + ": " + (message ?: response.take(400)))
            }
            val answer = if (isOpenRouter) JSONObject(response).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
            else JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            return answer.ifBlank { throw IllegalStateException("The selected provider returned an empty response.") }
        } finally { connection?.disconnect() }
    }

    private fun sendGeminiRequest() {
        val chosenModel = model.selectedItem.toString().substringAfter("—").trim()
        val isOpenRouter = chosenModel.startsWith("openrouter/")
        val key = if (isOpenRouter) openRouterKey.text.toString().trim().ifBlank { securePrefs.getString("openrouter_api_key", "").orEmpty() }
            else apiKey.text.toString().trim().ifBlank { securePrefs.getString("gemini_api_key", "").orEmpty() }
        val userTask = task.text.toString().trim()
        if (key.isEmpty()) {
            status.text = "Missing API key"
            answerView.text = if (isOpenRouter) "Paste an OpenRouter API key in the Connection section and save it." else "Paste your Gemini API key in the Connection section first. You can save it on this phone using the secure-save button."
            return
        }
        if (userTask.isEmpty()) {
            status.text = "Missing task"
            answerView.text = "Type what you want Gemini to help with, then tap Ask Gemini."
            return
        }

        val screen = prefs.getString("latest_screen_text", "").orEmpty()
        prefs.edit().putString("last_task", userTask).apply()
        val prompt = buildString {
            append("You are Pocket Agent, an Android screen assistant. Give clear, concise, actionable guidance based on the user's task. Be explicit that you cannot perform actions on the phone. Never claim you performed actions. Never request passwords, payment details, or secret codes.\n\n")
            append("User task:\n")
            append(userTask)
            if (useScreen.isChecked && screen.isNotBlank()) {
                append("\n\nVisible screen labels observed locally:\n")
                append(screen.take(12000))
            }
        }

        askButton.isEnabled = false
        askButton.text = "Working…"
        status.text = when (mode) { 1 -> "Running local AI on this phone…"; 2 -> "Planning with local rules…"; else -> "Sending request to cloud AI…" }
        logCommunication("REQUEST", "Mode: " + modeSpinner.selectedItem.toString() + "\nModel: " + chosenModel + "\nTask: " + userTask.take(500))
        answerView.text = if (mode == 2) "Using local rules. No API or model required." else if (mode == 1) "Running inference locally; no task text is sent to cloud AI." else "Waiting for cloud AI response. Keep this screen open."
        Thread {
            var connection: HttpURLConnection? = null
            try {
                if (mode != 0) {
                    val localResponse = if (mode == 1) generateLocalAi(prompt) else localRulePlan(userTask, screen, "")
                    runOnUiThread {
                        answerView.text = localResponse
                        status.text = if (mode == 1) "Local AI response · no API used" else "Local rules response · no API used"
                        saveHistory(userTask, modeSpinner.selectedItem.toString(), localResponse)
                    }
                    return@Thread
                }
                val safeModel = java.net.URLEncoder.encode(chosenModel, "UTF-8")
                connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent?key=$key")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject()
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                    .put("generationConfig", JSONObject().put("temperature", 0.3).put("maxOutputTokens", 700))

                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) {
                    val message = try {
                        JSONObject(response).optJSONObject("error")?.optString("message")
                    } catch (_: Exception) { null }
                    throw IllegalStateException("Gemini HTTP $code: " + (message ?: response.take(500)))
                }
                val json = JSONObject(response)
                val answer = json.optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                if (answer.isBlank()) throw IllegalStateException("Gemini returned no text. Check the model name and API key.")
                saveHistory(userTask, chosenModel, answer)
                runOnUiThread {
                    answerView.text = answer
                    status.text = "Response received · saved to History"
                    logCommunication("RESPONSE", answer.take(1200))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val message = "Request failed: " + (e.message ?: "unknown error") +
                        "\n\nCheck internet, API key, model name, and Gemini quota."
                    saveHistory(userTask, chosenModel, message)
                    answerView.text = message
                    status.text = "Request failed · saved to History"
                    logCommunication("ERROR", message.take(900))
                }
            } finally {
                connection?.disconnect()
                runOnUiThread {
                    askButton.isEnabled = true
                    askButton.text = "Ask Gemini"
                }
            }
        }.start()
    }
}
