package com.example.pocketagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ConnectionsActivity : Activity() {
    private val ink = Color.rgb(31, 38, 58)
    private val muted = Color.rgb(111, 119, 139)
    private val accent = Color.rgb(105, 86, 220)
    private val page = Color.rgb(246, 247, 251)
    private val line = Color.rgb(231, 233, 242)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = "JARVIS"
            textSize = 13f
            letterSpacing = 0.16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        top.addView(button("Back to home", Color.WHITE, ink).apply {
            setOnClickListener { finish() }
        })
        content.addView(top, margins(12))

        content.addView(TextView(this).apply {
            text = "Connections"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
        }, margins(5))
        content.addView(TextView(this).apply {
            text = "Connect private accounts when you need deeper access. Basic app automation does not require connecting every app."
            textSize = 14f
            setTextColor(muted)
            setLineSpacing(dp(4).toFloat(), 1f)
        }, margins(18))

        val automation = card()
        automation.addView(eyebrow("DEVICE AUTOMATION"))
        automation.addView(heading("Works without app connections"))
        automation.addView(body("JARVIS can open installed apps and work with visible screens using Android Accessibility. It can identify controls, tap, type, and scroll where the app and Android allow it."))
        val enabled = isAccessibilityEnabled()
        automation.addView(TextView(this).apply {
            text = if (enabled) "● Accessibility enabled" else "○ Accessibility permission needed"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (enabled) Color.rgb(35, 139, 91) else Color.rgb(180, 116, 35))
            setPadding(0, dp(12), 0, dp(10))
        })
        automation.addView(button(
            if (enabled) "Review accessibility settings" else "Enable accessibility",
            Color.rgb(239, 237, 255), accent
        ).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, margins(8))
        content.addView(automation, margins(14))

        content.addView(eyebrow("OPTIONAL ACCOUNT CONNECTIONS"), margins(8))
        content.addView(TextView(this).apply {
            text = "These secure connections are for private account data and official API actions. They are separate from normal on-screen app control."
            textSize = 13f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
        }, margins(10))

        val integrations = listOf(
            Triple("Gmail", "Mail search and account-level actions", "Gmail"),
            Triple("Google Drive", "Private files and cloud storage", "Drive"),
            Triple("Canva", "Design files and account-level workflows", "Canva"),
            Triple("GitHub", "Repositories, issues, and code workflows", "GitHub"),
            Triple("Spotify", "Library and playlist account actions", "Spotify")
        )
        integrations.forEach { item ->
            val panel = card()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = item.first
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ink)
            })
            labels.addView(TextView(this).apply {
                text = item.second
                textSize = 12f
                setTextColor(muted)
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply {
                text = "OPTIONAL"
                textSize = 9f
                letterSpacing = 0.08f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(accent)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(Color.rgb(242, 240, 255), 10)
            })
            panel.addView(row, margins(10))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            actions.addView(button("Open app", Color.rgb(239, 237, 255), accent).apply {
                setOnClickListener { openInstalledApp(item.third) }
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
            actions.addView(button("Connection info", Color.rgb(247, 248, 251), ink).apply {
                setOnClickListener { showConnectionInfo(item.first) }
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
            panel.addView(actions)
            content.addView(panel, margins(10))
        }

        val note = card()
        note.addView(eyebrow("APP-BASED AUTOMATION"))
        note.addView(heading("YouTube · WhatsApp · Spotify · and more"))
        note.addView(body("For ordinary navigation and visible-screen tasks, JARVIS should launch the app and use supported accessibility actions. A separate OAuth connection is not required just to open an app or search its visible interface. Private data, sending messages, publishing, purchases, and other sensitive actions need extra permission and confirmation."))
        content.addView(note, margins(14))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        footer.addView(button("⌂  Home", Color.rgb(247, 248, 251), ink).apply {
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        footer.addView(button("⌘  Connections", Color.rgb(239, 237, 255), accent).apply {
            isEnabled = false
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(64)))
        setContentView(root)
    }

    private fun showConnectionInfo(service: String) {
        AlertDialog.Builder(this)
            .setTitle("$service connection")
            .setMessage("Secure account connection is not configured in this build yet. JARVIS can still try to open the installed app and use visible-screen automation when supported. It will not claim private API access until an official sign-in flow is actually connected.")
            .setPositiveButton("Understood", null)
            .show()
    }

    private fun openInstalledApp(label: String) {
        val wanted = normalize(label)
        val match = packageManager.getInstalledApplications(0).firstOrNull { app ->
            val launch = packageManager.getLaunchIntentForPackage(app.packageName)
            launch != null && normalize(packageManager.getApplicationLabel(app).toString()).let {
                it == wanted || it.contains(wanted) || wanted.contains(it)
            }
        }
        val launch = match?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
        if (launch == null) {
            Toast.makeText(this, "$label is not installed or has no launcher screen.", Toast.LENGTH_SHORT).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launch)
            Toast.makeText(this, "Opened $label. JARVIS can continue with supported screen actions.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open $label.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        } catch (_: Exception) { false }
        if (!enabled) return false
        val services = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return services.split(':').any {
            it.contains(AgentAccessibilityService::class.java.name, ignoreCase = true)
        }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), line)
        }
        elevation = dp(1).toFloat()
    }

    private fun eyebrow(value: String) = TextView(this).apply {
        text = value
        textSize = 10f
        letterSpacing = 0.12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(accent)
        setPadding(0, 0, 0, dp(8))
    }

    private fun heading(value: String) = TextView(this).apply {
        text = value
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
        setPadding(0, 0, 0, dp(7))
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(muted)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun button(label: String, backgroundColor: Int, textColor: Int) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        background = rounded(backgroundColor, 15)
        setPadding(dp(10), 0, dp(10), 0)
        minHeight = dp(44)
        stateListAnimator = null
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun margins(bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
