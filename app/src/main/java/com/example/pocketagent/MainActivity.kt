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
    private lateinit var task: EditText
    private lateinit var screenPreview: TextView
    private lateinit var answerView: TextView
    private lateinit var useScreen: CheckBox
    private lateinit var askButton: Button
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var securePrefs: android.content.SharedPreferences

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
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val topBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val menuButton = actionButton("☰  Menu & History", white, accent)
        menuButton.setOnClickListener { showHistoryMenu() }
        topBar.addView(menuButton, LinearLayout.LayoutParams(-2, dp(46)))
        val liveButton = actionButton("◉ Live Communication", Color.rgb(30, 32, 48), Color.WHITE)
        liveButton.setOnClickListener {
            val log = prefs.getString("live_communication_log", "").orEmpty().ifBlank { "No Gemini events yet. Ask Gemini to populate this log." }
            AlertDialog.Builder(this).setTitle("Pocket Agent · Live Communication").setMessage(log)
                .setPositiveButton("Close", null)
                .setNeutralButton("Clear log") { _, _ -> prefs.edit().putString("live_communication_log", "").apply() }
                .show()
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
            text = "A phone-first AI agent prototype. Gemini chooses one action at a time; Android Accessibility attempts visible taps and text entry. Review each step and use Pause or Stop whenever needed."
            textSize = 14f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        content.addView(hero, margins(bottom = 14))

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
                "Gemma 4 26B MoE IT — gemma-4-26b-a4b-it"
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
        taskCard.addView(label("What should Gemini help with?"))
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
        askButton = actionButton("Ask Gemini", accent, white)
        askButton.setOnClickListener { sendGeminiRequest() }
        taskCard.addView(askButton, margins(bottom = 8))
        val agentButton = actionButton("▶ Start autonomous agent", Color.rgb(30, 32, 48), Color.WHITE)
        agentButton.setOnClickListener { startAutonomousAgent(agentButton) }
        taskCard.addView(agentButton, margins(bottom = 8))
        content.addView(taskCard, margins(bottom = 14))

        val answerCard = card(white)
        answerCard.addView(sectionTitle("03  ·  GEMINI RESPONSE"))
        answerView = TextView(this).apply {
            text = "Your answer will appear here. After tapping Ask Gemini, wait for the response or an error message."
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
        }
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
        val options = arrayOf("History", "Clear history", "Connection & privacy")
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
                        .setMessage("Your API key is not saved. Task history is stored locally on this phone. Screen labels are sent to Gemini only when included in a request. Automatic tapping and typing are not available in this build.")
                        .setPositiveButton("OK", null).show()
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

    private fun startAutonomousAgent(agentButton: Button) {
        val key = apiKey.text.toString().trim().ifBlank { securePrefs.getString("gemini_api_key", "").orEmpty() }
        val userTask = task.text.toString().trim()
        val chosenModel = model.selectedItem.toString().substringAfter("—").trim()
        if (key.isBlank() || userTask.isBlank()) { status.text = if (key.isBlank()) "Add your Gemini API key first" else "Describe the task first"; return }
        if (agentRunning) {
            agentRunning = false
            prefs.edit().putBoolean("session_active", false).putString("pending_action", "").apply()
            status.text = "Stopping agent…"; agentButton.text = "▶ Start autonomous agent"; return
        }
        agentRunning = true
        prefs.edit().putBoolean("session_active", true).putString("current_agent_status", "● Pocket Agent · Starting").putString("last_task", userTask).putString("pending_action", "").putString("last_action_result", "").apply()
        agentButton.text = "■ Stop autonomous agent"
        status.text = "Agent starting · switch to the target app"
        logCommunication("AGENT START", userTask)
        Thread {
            try {
                var lastResult = ""
                for (step in 1..25) {
                    if (!agentRunning || !prefs.getBoolean("session_active", false)) break
                    var screen = prefs.getString("latest_screen_text", "").orEmpty()
                    if (screen.isBlank()) { runOnUiThread { status.text = "Waiting for readable screen text · open target app" }; Thread.sleep(1200); screen = prefs.getString("latest_screen_text", "").orEmpty() }
                    val instruction = """
You control an Android screen through an accessibility service. Return ONLY one JSON object, no markdown:
{"action":"tap_text","text":"exact visible label","reason":"brief reason"}
Allowed actions: tap_text, type_text, back, done, ask_user.
For type_text use {"action":"type_text","text":"text to enter","reason":"..."} only when an editable field is visibly focused.
For back use {"action":"back","text":"","reason":"..."}; done uses {"action":"done","text":"completion summary","reason":"..."}; ask_user uses {"action":"ask_user","text":"what user must decide","reason":"..."}.
User task: ${userTask}
Step: ${step} of 25
Visible screen text:
${screen.take(9000)}
Previous action result: ${lastResult.take(500)}
Rules: act only on visible labels. Never invent buttons. Do not enter passwords, API keys, payment details, verification codes, or personal secrets. Do not make purchases, send messages, delete content, or accept paid subscriptions. If unclear or unsafe, use ask_user. If complete, use done. Choose one small action at a time.
""".trimIndent()
                    val raw = requestGeminiText(key, chosenModel, instruction)
                    val match = Regex("\\{[\\s\\S]*?\\}").find(raw)?.value ?: throw IllegalStateException("Gemini did not return a valid action JSON.")
                    val command = JSONObject(match)
                    val action = command.optString("action", "ask_user")
                    val textValue = command.optString("text", "")
                    val reason = command.optString("reason", "")
                    prefs.edit().putString("current_agent_status", "● Step " + step + " · " + action).apply()
                    logCommunication("AGENT STEP " + step, "Screen: " + screen.take(700) + "\\nDecision: " + action + " " + textValue + "\\nReason: " + reason)
                    runOnUiThread { answerView.text = "Step " + step + "/25\\nAction: " + action + "\\n" + textValue + "\\n\\nReason: " + reason; status.text = "Agent step " + step + " · " + action }
                    when (action) {
                        "done" -> { runOnUiThread { answerView.text = "Task finished\\n\\n" + textValue; status.text = "Agent completed" }; break }
                        "ask_user" -> { runOnUiThread { answerView.text = "Agent needs your input\\n\\n" + textValue; status.text = "Waiting for user" }; break }
                        "tap_text", "type_text", "back" -> {
                            prefs.edit().putString("last_action_result", "").putString("pending_action", JSONObject().put("action", action).put("text", textValue).toString()).apply()
                            var waited = 0
                            while (agentRunning && waited < 5000) {
                                val result = prefs.getString("last_action_result", "").orEmpty()
                                if (result.isNotBlank()) { lastResult = result; break }
                                Thread.sleep(250); waited += 250
                            }
                            if (lastResult.isBlank()) lastResult = "No action confirmation received; checking screen again."
                            Thread.sleep(650)
                        }
                        else -> { runOnUiThread { answerView.text = "Unsupported action: " + action; status.text = "Agent paused for safety" }; break }
                    }
                }
            } catch (e: Exception) {
                val message = "Agent error: " + (e.message ?: "unknown error")
                logCommunication("AGENT ERROR", message)
                runOnUiThread { answerView.text = message; status.text = "Agent error" }
            } finally {
                agentRunning = false
                prefs.edit().putBoolean("session_active", false).putString("current_agent_status", "● Pocket Agent · Stopped").putString("pending_action", "").apply()
                runOnUiThread { agentButton.text = "▶ Start autonomous agent" }
                logCommunication("AGENT STOP", "Agent loop ended.")
            }
        }.start()
    }

    private fun requestGeminiText(key: String, chosenModel: String, prompt: String): String {
        var connection: HttpURLConnection? = null
        try {
            val safeModel = java.net.URLEncoder.encode(chosenModel, "UTF-8")
            connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/" + safeModel + ":generateContent?key=" + key).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                .put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 500))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = try { JSONObject(response).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                throw IllegalStateException("Gemini HTTP " + code + ": " + (message ?: response.take(400)))
            }
            return JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                .ifBlank { throw IllegalStateException("Gemini returned an empty response.") }
        } finally { connection?.disconnect() }
    }

    private fun sendGeminiRequest() {
        val key = apiKey.text.toString().trim().ifBlank { securePrefs.getString("gemini_api_key", "").orEmpty() }
        val chosenModel = model.selectedItem.toString().substringAfter("—").trim()
        val userTask = task.text.toString().trim()
        if (key.isEmpty()) {
            status.text = "Missing API key"
            answerView.text = "Paste your Gemini API key in the Connection section first. You can save it on this phone using the secure-save button."
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
        askButton.text = "Connecting…"
        status.text = "Sending request to Gemini…"
        logCommunication("REQUEST", "Model: " + chosenModel + "\nTask: " + userTask.take(500))
        answerView.text = "Waiting for Gemini's response. Keep this screen open."
        Thread {
            var connection: HttpURLConnection? = null
            try {
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
