package com.example.pocketagent

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
    private lateinit var useScreen: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 22)
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(TextView(this).apply {
            text = "Pocket Agent"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(20, 25, 35))
        })
        content.addView(TextView(this).apply {
            text = "Gemini-powered screen assistant"
            textSize = 14f
            setPadding(0, 4, 0, 14)
        })

        content.addView(label("Gemini model"))
        model = field("Model name", false).apply { setText("gemini-2.5-flash") }
        content.addView(model)

        content.addView(label("Gemini API key"))
        apiKey = field("Paste key here — kept in memory only", true)
        apiKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        content.addView(apiKey)

        content.addView(label("Describe your task"))
        task = field("What do you want help with?", false).apply {
            minLines = 2
            setSingleLine(false)
        }
        content.addView(task)

        useScreen = CheckBox(this).apply {
            text = "Include latest observed screen text in request"
            isChecked = true
        }
        content.addView(useScreen)

        content.addView(Button(this).apply {
            text = "Ask Gemini"
            setOnClickListener { sendGeminiRequest() }
        })

        content.addView(Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        content.addView(Button(this).apply {
            text = "Refresh observed screen text"
            setOnClickListener { refreshScreenText() }
        })

        content.addView(label("Latest observed screen text (local)"))
        screenPreview = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(65, 70, 80))
            setPadding(10, 10, 10, 10)
            setBackgroundColor(Color.WHITE)
            text = prefs.getString("latest_screen_text", "").orEmpty().ifBlank {
                "No screen text yet. Start a session and enable Pocket Agent in Accessibility settings."
            }
        }
        content.addView(screenPreview)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(Button(this).apply {
            text = "Start"
            setOnClickListener {
                prefs.edit().putBoolean("session_active", true).apply()
                status.text = "Session started. Screen labels are observed locally; automatic taps are off."
                refreshScreenText()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply {
            text = "Pause"
            setOnClickListener {
                prefs.edit().putBoolean("session_active", false).apply()
                status.text = "Session paused. Screen observation is off."
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                prefs.edit().putBoolean("session_active", false)
                    .putString("latest_screen_text", "").apply()
                screenPreview.text = "Screen text cleared."
                status.text = "Session stopped. Local screen text cleared."
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(controls)

        status = TextView(this).apply {
            text = "Ready. Your API key is not saved."
            textSize = 14f
            setPadding(0, 14, 0, 8)
            setTextColor(Color.rgb(35, 70, 50))
        }
        content.addView(status)

        content.addView(TextView(this).apply {
            text = "Privacy and control: your key stays in memory only. Screen labels stay on your phone unless you choose Ask Gemini with screen text enabled. This version does not tap, type, submit forms, install apps, or make purchases automatically."
            textSize = 12f
            setPadding(0, 8, 0, 12)
        })
        setContentView(root)
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 10, 0, 4)
    }

    private fun field(hintText: String, password: Boolean): EditText = EditText(this).apply {
        hint = hintText
        textSize = 14f
        setSingleLine(true)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(12, 8, 12, 8)
    }

    private fun refreshScreenText() {
        val text = getSharedPreferences("pocket_agent", MODE_PRIVATE)
            .getString("latest_screen_text", "").orEmpty()
        screenPreview.text = if (text.isBlank()) {
            "No screen text captured. Enable Pocket Agent in Accessibility settings, open the target app, then tap Refresh."
        } else text
    }

    private fun sendGeminiRequest() {
        val key = apiKey.text.toString().trim()
        val chosenModel = model.text.toString().trim().ifEmpty { "gemini-2.5-flash" }
        val userTask = task.text.toString().trim()
        if (key.isEmpty()) {
            status.text = "Paste your Gemini API key first. It is not saved."
            return
        }
        if (userTask.isEmpty()) {
            status.text = "Describe your task first."
            return
        }

        val screen = getSharedPreferences("pocket_agent", MODE_PRIVATE)
            .getString("latest_screen_text", "").orEmpty()
        val prompt = buildString {
            append("You are Pocket Agent, a careful Android screen assistant. Give concise, practical guidance. Never claim you performed actions. Do not request passwords, payment details, or secret codes.\n\n")
            append("User task:\n")
            append(userTask)
            if (useScreen.isChecked && screen.isNotBlank()) {
                append("\n\nVisible screen labels observed locally:\n")
                append(screen.take(12000))
            }
        }

        status.text = "Contacting Gemini…"
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
                runOnUiThread { status.text = answer }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Request failed: " + (e.message ?: "unknown error") +
                        ". Check internet, key, quota, and model."
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
