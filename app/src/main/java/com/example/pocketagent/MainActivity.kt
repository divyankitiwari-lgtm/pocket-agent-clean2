package com.example.pocketagent

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var model: EditText
    private lateinit var apiKey: EditText
    private lateinit var task: EditText
    private lateinit var screenPreview: TextView
    private lateinit var answerView: TextView
    private lateinit var useScreen: CheckBox
    private lateinit var askButton: Button
    private lateinit var prefs: android.content.SharedPreferences

    private val ink = Color.rgb(27, 30, 47)
    private val muted = Color.rgb(115, 119, 139)
    private val accent = Color.rgb(117, 92, 255)
    private val page = Color.rgb(246, 245, 252)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)

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
            text = "Tell Gemini what you need help with. This build can give guidance and read visible screen labels, but it cannot tap other apps yet."
            textSize = 14f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        content.addView(hero, margins(bottom = 14))

        val setup = card(white)
        setup.addView(sectionTitle("01  ·  CONNECTION"))
        setup.addView(label("Gemini API key"))
        apiKey = field("Paste your key (kept in memory only)", true)
        setup.addView(apiKey, margins(bottom = 10))
        setup.addView(label("Model"))
        model = field("gemini-3.8-flash", false).apply { setText("gemini-3.8-flash") }
        setup.addView(model)
        content.addView(setup, margins(bottom = 14))

        val taskCard = card(white)
        taskCard.addView(sectionTitle("02  ·  YOUR TASK"))
        taskCard.addView(label("What should Gemini help with?"))
        task = field("Describe your task in plain English", false).apply {
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
            text = "Ready · API key is not saved"
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(247, 247, 252), 14)
        }
        controlsCard.addView(status)
        content.addView(controlsCard, margins(bottom = 12))

        content.addView(TextView(this).apply {
            text = "Privacy note: the key stays in memory only. If you enable screen text in your request, those labels are sent to Google Gemini. Avoid passwords and private information. Automatic tapping and typing are not implemented in this version."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(4), dp(4), dp(12))
        })

        setContentView(root)
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
        text = title
        textSize = 13f
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        setTextColor(fg)
        background = rounded(bg, 16)
        minHeight = dp(48)
        stateListAnimator = null
        elevation = 0f
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun refreshScreenText() {
        val text = prefs.getString("latest_screen_text", "").orEmpty()
        screenPreview.text = if (text.isBlank()) {
            "No screen text captured. Enable Pocket Agent in Accessibility settings, open the target app, then tap Refresh."
        } else text
    }

    private fun sendGeminiRequest() {
        val key = apiKey.text.toString().trim()
        val chosenModel = model.text.toString().trim().ifEmpty { "gemini-3.8-flash" }
        val userTask = task.text.toString().trim()
        if (key.isEmpty()) {
            status.text = "Missing API key"
            answerView.text = "Paste your Gemini API key in the Connection section first. The key is not saved by this app."
            return
        }
        if (userTask.isEmpty()) {
            status.text = "Missing task"
            answerView.text = "Type what you want Gemini to help with, then tap Ask Gemini."
            return
        }

        val screen = prefs.getString("latest_screen_text", "").orEmpty()
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
        answerView.text = "Waiting for Gemini's response. Keep this screen open."
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val safeModel = java.net.URLEncoder.encode(chosenModel, "UTF-8")
                connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent?key=$key")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject()
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                    .put("generationConfig", JSONObject().put("temperature", 0.3))
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
                runOnUiThread {
                    answerView.text = answer
                    status.text = "Response received"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val message = "Request failed: " + (e.message ?: "unknown error") +
                        "\n\nCheck internet, API key, model name, and Gemini quota."
                    answerView.text = message
                    status.text = "Gemini request failed"
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
