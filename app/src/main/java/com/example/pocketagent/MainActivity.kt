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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var model: Spinner
    private lateinit var modeSpinner: Spinner
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
    private lateinit var orbView: LiquidOrbView
    private lateinit var planPill: TextView
    private lateinit var agentButton: Button
    private var voiceReceiver: android.content.BroadcastReceiver? = null
    private var voiceReceiverRegistered = false
    private var permissionDialog: AlertDialog? = null
    private var firebaseAuth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var welcomeDialog: AlertDialog? = null
    // Keep the on-device model loaded between agent steps; loading a ~500 MB model every step is too slow.
    private var gemmaEngine: GemmaEngine? = null

    private val ink = Color.rgb(31, 38, 58)
    private val muted = Color.rgb(111, 119, 139)
    private val accent = Color.rgb(105, 86, 220)
    private val page = Color.rgb(246, 247, 251)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(247, 248, 252)
        window.navigationBarColor = Color.rgb(247, 248, 252)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        prefs = getSharedPreferences("pocket_agent", MODE_PRIVATE)
        try {
            if (FirebaseApp.initializeApp(this) != null) {
                firebaseAuth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()
            }
        } catch (_: Exception) {
            firebaseAuth = null
            firestore = null
        }
        securePrefs = try {
            val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(this, "pocket_agent_secrets", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        } catch (_: Exception) {
            getSharedPreferences("pocket_agent_secrets_fallback", MODE_PRIVATE)
        }

        window.statusBarColor = Color.rgb(5, 8, 15)
        window.navigationBarColor = Color.rgb(5, 8, 15)
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(4, 8, 15), Color.rgb(9, 17, 29), Color.rgb(5, 9, 18))
            )
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(225, 244, 250))
            background = glassSurface(48)
            contentDescription = "Open menu"
            setOnClickListener { showJarvisMenu() }
        }
        topBar.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        topBar.addView(TextView(this).apply {
            text = "J A R V I S"
            textSize = 14f
            letterSpacing = 0.18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(220, 242, 248))
            setPadding(dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(topBar, LinearLayout.LayoutParams(-1, dp(52)))

        planPill = TextView(this).apply {
            text = "YOU ARE ON ${currentPlan().uppercase(Locale.US)} PLAN"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(203, 245, 241))
            setPadding(dp(18), dp(11), dp(18), dp(11))
            background = glassSurface(24)
            elevation = dp(8).toFloat()
        }
        val planRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        planRow.addView(planPill, LinearLayout.LayoutParams(-2, -2))
        root.addView(planRow, LinearLayout.LayoutParams(-1, dp(58)))

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        orbView = LiquidOrbView(this).apply {
            setState(LiquidOrbView.State.IDLE)
            setOnClickListener { toggleVoiceSession() }
        }
        center.addView(orbView, LinearLayout.LayoutParams(dp(300), dp(300)))
        status = TextView(this).apply {
            text = "TAP THE ORB TO TALK"
            textSize = 11f
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(156, 194, 205))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        center.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))

        contentLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(contentLayout, LinearLayout.LayoutParams(-1, -2))

        // Retain the existing authenticated AI/agent engine behind the voice-first UI.
        // These compatibility controls are intentionally not visible or touchable.
        val hidden = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item,
                listOf("Cloud AI", "Bundled Gemma · offline", "Local rules")).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(1)
        }
        model = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item,
                listOf("Gemini 2.5 Flash — gemini-2.5-flash", "OpenRouter Free — openrouter/free")).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(0)
        }
        apiKey = field("Gemini API key", true)
        openRouterKey = field("OpenRouter API key", true)
        task = field("Voice task", false)
        useScreen = CheckBox(this).apply { isChecked = true }
        askButton = Button(this)
        agentButton = actionButton("Start JARVIS", Color.rgb(25, 185, 180), Color.WHITE)
        screenPreview = TextView(this)
        answerView = TextView(this)
        hidden.addView(modeSpinner); hidden.addView(model); hidden.addView(apiKey)
        hidden.addView(openRouterKey); hidden.addView(task); hidden.addView(useScreen)
        hidden.addView(askButton); hidden.addView(agentButton); hidden.addView(screenPreview)
        hidden.addView(answerView)
        contentLayout.addView(hidden)

        setContentView(root)
        updatePlanPill()
        voiceReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                val spoken = intent?.getStringExtra(VoiceSessionService.EXTRA_TEXT).orEmpty()
                when (intent?.action) {
                    VoiceSessionService.ACTION_TASK -> {
                        if (spoken.isNotBlank() && !agentRunning) {
                            task.setText(spoken)
                            setOrbState(LiquidOrbView.State.THINKING, "UNDERSTANDING YOUR REQUEST")
                            startAutonomousAgent(agentButton)
                        }
                    }
                    VoiceSessionService.ACTION_FOLLOWUP -> answerVoiceFollowup(spoken)
                    VoiceSessionService.ACTION_STATE -> {
                        val state = intent?.getStringExtra(VoiceSessionService.EXTRA_TEXT).orEmpty()
                        when (state) {
                            "LISTENING" -> setOrbState(LiquidOrbView.State.LISTENING, "LISTENING · SPEAK NATURALLY")
                            "SPEAKING" -> setOrbState(LiquidOrbView.State.SPEAKING, "JARVIS IS SPEAKING")
                            "PAUSED" -> setOrbState(LiquidOrbView.State.PAUSED, "TASK PAUSED · SAY CONTINUE")
                            "AUTOMATING" -> setOrbState(LiquidOrbView.State.AUTOMATING, "JARVIS IS WORKING")
                            "IDLE" -> setOrbState(LiquidOrbView.State.IDLE, "TAP THE ORB TO TALK")
                        }
                    }
                }
            }
        }
        val voiceFilter = android.content.IntentFilter().apply {
            addAction(VoiceSessionService.ACTION_TASK)
            addAction(VoiceSessionService.ACTION_FOLLOWUP)
            addAction(VoiceSessionService.ACTION_STATE)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(voiceReceiver, voiceFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(voiceReceiver, voiceFilter)
        }
        voiceReceiverRegistered = true

        val signedInUser = firebaseAuth?.currentUser
        if (signedInUser == null) root.post { showWelcomeGate() }
        else {
            refreshAdminClaim(signedInUser)
            root.post {
                showAccountBanner(signedInUser)
                showPermissionOnboardingIfNeeded()
            }
        }
    }

    private fun glassSurface(radius: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(175, 28, 43, 58), Color.argb(115, 13, 22, 36))
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(105, 180, 235, 239))
        }

    private fun updatePlanPill() {
        if (::planPill.isInitialized) {
            planPill.text = "YOU ARE ON ${currentPlan().uppercase(Locale.US)} PLAN"
        }
    }

    private fun setOrbState(state: LiquidOrbView.State, caption: String) {
        runOnUiThread {
            if (::orbView.isInitialized) orbView.setState(state)
            if (::status.isInitialized) status.text = caption.uppercase(Locale.US)
        }
    }

    private fun toggleVoiceSession() {
        if (prefs.getBoolean("voice_session_requested", false)) {
            prefs.edit().putBoolean("voice_session_requested", false).apply()
            stopService(Intent(this, VoiceSessionService::class.java).setAction(VoiceSessionService.ACTION_STOP))
            setOrbState(LiquidOrbView.State.IDLE, "VOICE SESSION OFF · TAP TO SPEAK")
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("voice_session_requested", true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 8821)
            return
        }
        startVoiceSessionNow()
    }

    private fun startVoiceSessionNow() {
        prefs.edit().putBoolean("voice_session_requested", true).apply()
        val intent = Intent(this, VoiceSessionService::class.java).setAction(VoiceSessionService.ACTION_START)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            setOrbState(LiquidOrbView.State.LISTENING, "LISTENING · SPEAK NATURALLY")
        } catch (error: Exception) {
            prefs.edit().putBoolean("voice_session_requested", false).apply()
            setOrbState(LiquidOrbView.State.ERROR, "VOICE SESSION COULD NOT START")
        }
    }

    private fun speakThroughVoiceService(message: String) {
        if (message.isBlank()) return
        val intent = Intent(this, VoiceSessionService::class.java)
            .setAction(VoiceSessionService.ACTION_SPEAK)
            .putExtra(VoiceSessionService.EXTRA_TEXT, message)
        try { startService(intent) } catch (_: Exception) {}
    }

    private fun answerVoiceFollowup(question: String) {
        if (question.isBlank()) return
        Thread {
            val taskNow = prefs.getString("last_task", "the current task").orEmpty()
            val statusNow = prefs.getString("current_agent_status", "Working").orEmpty()
            val latest = prefs.getString("last_action_result", "No action result yet").orEmpty()
            val prompt = """
You are JARVIS, a concise voice assistant. The user is speaking to you while an Android automation task is running.
Answer the user's question directly in natural spoken English. Do not return JSON, do not issue an automation action, and do not claim an action succeeded unless the status below confirms it.
Current task: $taskNow
Current status: $statusNow
Latest verified action result: $latest
User's spoken question: $question
Keep the answer short and useful. If the question asks for progress, explain the current state and any blocker. If you cannot know something from this context, say so honestly.
""".trimIndent()
            val response = try {
                generateLocalAi(prompt).trim().take(650)
            } catch (_: Exception) {
                val problem = latest.take(160)
                "I'm still working on $taskNow. My current status is $statusNow. Latest action information: $problem. I can't answer that in more detail from the current device state."
            }
            runOnUiThread { speakThroughVoiceService(response) }
        }.start()
    }

    private fun showJarvisMenu() {
        val uid = firebaseAuth?.currentUser?.uid ?: "Offline session"
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
            background = glassSurface(26)
        }
        panel.addView(TextView(this).apply {
            text = "ACCOUNT ID"
            textSize = 10f
            letterSpacing = 0.14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(120, 211, 211))
        })
        panel.addView(TextView(this).apply {
            text = uid
            textSize = 12f
            setTextColor(Color.rgb(225, 240, 245))
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, dp(14))
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("JARVIS MENU")
            .setView(panel)
            .setItems(arrayOf("Settings", "Log out")) { _, which ->
                if (which == 0) showJarvisSettings() else confirmLogout()
            }
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(8, 13, 22)))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.rgb(140, 225, 220))
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(8, 13, 22)))
    }

    private fun showJarvisSettings() {
        val options = arrayOf("Permissions & voice", "Connections", "Saved history")
        AlertDialog.Builder(this)
            .setTitle("SETTINGS")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPermissionOnboarding()
                    1 -> startActivity(Intent(this, ConnectionsActivity::class.java))
                    2 -> showHistory()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPermissionOnboardingIfNeeded() {
        val user = firebaseAuth?.currentUser ?: return
        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val accessibilityReady = prefs.getBoolean("accessibility_connected", false)
        if (!micGranted || !accessibilityReady) showPermissionOnboarding()
        updatePlanPill()
    }

    private fun showPermissionOnboarding() {
        if (permissionDialog?.isShowing == true) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(10))
            background = glassSurface(26)
        }
        panel.addView(TextView(this).apply {
            text = "ONE LAST SETUP"
            textSize = 11f
            letterSpacing = 0.15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(104, 226, 218))
        }, margins(bottom = 10))
        panel.addView(TextView(this).apply {
            text = "JARVIS needs microphone access to hear your voice commands, and Accessibility access to understand and control visible app interfaces when you ask it to."
            textSize = 14f
            setTextColor(Color.rgb(218, 231, 239))
            setLineSpacing(dp(4).toFloat(), 1f)
        }, margins(bottom = 18))
        val mic = actionButton("Allow microphone", Color.rgb(27, 104, 112), Color.WHITE).apply {
            setOnClickListener {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 8821)
            }
        }
        val access = actionButton("Enable app control", Color.rgb(24, 38, 55), Color.rgb(207, 236, 241)).apply {
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        panel.addView(mic, margins(bottom = 9))
        panel.addView(access, margins(bottom = 9))
        panel.addView(TextView(this).apply {
            text = "You stay in control. Voice listening starts only when you tap the JARVIS orb."
            textSize = 11f
            setTextColor(Color.rgb(145, 172, 186))
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("JARVIS PERMISSIONS")
            .setView(panel)
            .setPositiveButton("Continue", null)
            .setCancelable(false)
            .create()
        permissionDialog = dialog
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(5, 9, 16)))
            dialog.window?.setLayout(-1, -1)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(116, 230, 219))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hasMic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasAccess = prefs.getBoolean("accessibility_connected", false)
                if (hasMic && hasAccess) {
                    permissionDialog = null
                    dialog.dismiss()
                    updatePlanPill()
                } else {
                    status.text = "ENABLE BOTH PERMISSIONS TO CONTINUE"
                }
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(5, 9, 16)))
        dialog.window?.setLayout(-1, -1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 8821) {
            if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (prefs.getBoolean("voice_session_requested", false)) startVoiceSessionNow()
                permissionDialog?.let { if (prefs.getBoolean("accessibility_connected", false)) it.dismiss() }
            } else {
                setOrbState(LiquidOrbView.State.ERROR, "MICROPHONE PERMISSION DENIED")
            }
        }
    }

    private fun localModelFile(): File = File(filesDir, "pocket-agent-local-model.task")

    @Deprecated("Use document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7413) {
            if (resultCode != RESULT_OK || data == null) {
                status.text = "Google sign-in was cancelled or did not return an account."
                AlertDialog.Builder(this)
                    .setTitle("Google sign-in didn't finish")
                    .setMessage("Google returned without signing you in. If you selected your account and came back here, check the Firebase Google provider and the SHA-1 certificate for this exact APK. Try again after checking the setup.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                firebaseAuth?.signInWithCredential(credential)
                    ?.addOnSuccessListener { result ->
                        val user = result.user
                        if (user == null) {
                            status.text = "Google sign-in returned no Firebase user"
                            return@addOnSuccessListener
                        }
                        welcomeDialog?.dismiss()
                        welcomeDialog = null
                        refreshAdminClaim(user)
                        showAccountBanner(user)
                        status.text = "Signed in with Google"
                        val profile = hashMapOf<String, Any>(
                            "displayName" to (user.displayName ?: account.displayName ?: "JARVIS user"),
                            "email" to (user.email ?: account.email.orEmpty()),
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        firestore?.collection("users")?.document(user.uid)
                            ?.set(profile, SetOptions.merge())
                            ?.addOnFailureListener { error ->
                                status.text = "Google sign-in succeeded; profile sync failed: " + (error.message ?: "check Firestore rules")
                            }
                    }
                    ?.addOnFailureListener { error ->
                        val message = "Firebase Google sign-in failed: " + (error.message ?: "check provider setup")
                        status.text = message
                        AlertDialog.Builder(this)
                            .setTitle("Google sign-in failed")
                            .setMessage(message + "\n\nCheck that Google is enabled in Firebase Authentication and that the SHA-1 certificate for this APK is registered in Firebase.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
            } catch (error: Exception) {
                val message = "Google sign-in failed: " + (error.message ?: "check Firebase OAuth setup")
                status.text = message
                AlertDialog.Builder(this)
                    .setTitle("Google sign-in failed")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
            return
        }
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

    private fun ensureGemmaModelBundled(): File {
        val target = localModelFile()
        if (target.isFile && target.length() > 100_000_000L) return target
        val temp = File(filesDir, "gemma3-1b-it-int4.task.copying")
        try {
            assets.open("gemma3-1b-it-int4.task").use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) output.write(buffer, 0, count)
                    }
                }
            }
            check(temp.length() > 100_000_000L) { "Bundled Gemma model is missing or incomplete in this APK." }
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not prepare bundled Gemma model in app storage." }
            runOnUiThread { status.text = "Bundled Gemma model ready · running locally" }
            return target
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }
    @Synchronized
    private fun generateLocalAi(prompt: String): String {
        val modelFile = ensureGemmaModelBundled()
        val engine = gemmaEngine ?: GemmaEngine(applicationContext, modelFile).also {
            gemmaEngine = it
            logCommunication("LOCAL AI", "Gemma runtime loaded from app-private model storage.")
        }
        return engine.generate(prompt)
    }

    override fun onDestroy() {
        if (voiceReceiverRegistered) {
            try { unregisterReceiver(voiceReceiver) } catch (_: Exception) {}
            voiceReceiverRegistered = false
        }
        // Keep the user-started voice foreground service alive while another app is in front.
        try { gemmaEngine?.close() } catch (_: Exception) {}
        gemmaEngine = null
        super.onDestroy()
    }

    private fun extractRequestedApp(userTask: String): String {
        val match = Regex(
            """\b(?:open|launch|start|switch to|go to)\s+(?:the\s+)?([A-Za-z0-9][A-Za-z0-9 ._&'’-]{0,48}?)(?=\s+(?:and|then|so|inside|where)\b|[,;.!?]|$)""",
            RegexOption.IGNORE_CASE
        ).find(userTask) ?: return ""
        var app = match.groupValues[1].trim().trimEnd('.', '?', '!')
        app = app.replace(Regex("^(my|the)\\s+", RegexOption.IGNORE_CASE), "")
        app = app.replace(Regex("\\s+app$", RegexOption.IGNORE_CASE), "").trim()
        val generic = setOf("app", "my app", "editing", "video editing", "editing app",
            "video editing app", "my editing app", "my video editing app", "browser", "editor")
        if (app.lowercase() in generic || app.length < 2) return ""
        return app
    }

    private fun localRulePlan(userTask: String, screen: String, previousResult: String): String {
        val lower = screen.lowercase()
        val taskLower = userTask.lowercase()
        fun command(action: String, text: String, reason: String) =
            JSONObject().put("action", action).put("text", text).put("reason", reason).toString()
        if (screen.isBlank() || lower.contains("jarvis")) {
            val app = extractRequestedApp(userTask)
            if (app.isNotBlank()) return command("open_app", app, "Opening the app explicitly named in the task.")
            return command("ask_user", "Which installed app should I open?", "The task does not identify a specific app.")
        }
        if (previousResult.contains("Could not find", true) || previousResult.contains("failed", true)) {
            return command("ask_user", "The last action failed. Check the screen and choose the next step.", previousResult.take(180))
        }
        val nodes = screen.lines().map { it.trim() }.filter { it.length in 2..90 }.distinct()
        // Generic cross-app fallback: only tap a label explicitly requested by the user.
        // Do not guess common buttons such as Continue/Claim, which can trigger the wrong action.
        val requestedLabel = Regex("(?:tap|click|press|select|open)\\s+[\"']?([^,.;!?\\n]{2,60})[\"']?", RegexOption.IGNORE_CASE)
            .find(userTask)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', '?', '!')
        if (!requestedLabel.isNullOrBlank()) {
            val normalize = { value: String -> value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ") }
            val wanted = normalize(requestedLabel)
            val exact = nodes.firstOrNull { normalize(it) == wanted }
            if (exact != null) return command("tap_text", exact, "Exact visible label requested by the user.")
        }
        if (taskLower.contains("scroll") || screen.length > 1200) return command("scroll_down", "", "No matching label; inspect lower screen content.")
        return command("ask_user", "I couldn't match a safe button. Tell me which visible button to use.", "Local rules won't guess randomly.")
    }

    private fun todayInIndia(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return formatter.format(Date())
    }

    private fun dailyStepsUsed(): Long {
        val today = todayInIndia()
        if (prefs.getString("usage_day", "") != today) {
            prefs.edit().putString("usage_day", today).putLong("daily_steps_used", 0L).apply()
        }
        return prefs.getLong("daily_steps_used", 0L)
    }

    private fun recordSuccessfulStep(result: String) {
        val success = result.startsWith("Opened app:", true) ||
            result.startsWith("Tapped visible control:", true) ||
            result.startsWith("Tap gesture completed:", true) ||
            result.startsWith("Accessibility click accepted:", true) ||
            result.startsWith("Scrolled down", true) ||
            result.startsWith("Scrolled up", true) ||
            result.startsWith("Pressed Back", true) ||
            result.startsWith("Pressed Home", true) ||
            result.startsWith("Entered text", true)
        if (success) prefs.edit().putLong("daily_steps_used", dailyStepsUsed() + 1L).apply()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log out of JARVIS?")
            .setMessage("This stops any running agent and signs out of your Firebase account. Your API key and local history remain on this phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                agentRunning = false
                prefs.edit().putBoolean("session_active", false).putBoolean("agent_paused", false)
                    .putBoolean("admin_verified_by_firebase", false).putString("pending_action", "").apply()
                firebaseAuth?.signOut()
                contentLayout.findViewWithTag<View>("pocket_agent_admin_panel")?.let { contentLayout.removeView(it) }
                status.text = "Signed out · automation stopped"
                answerView.text = "Sign in with your account to continue."
                screenPreview.text = "Screen observation paused while signed out."
                showWelcomeGate()
            }.show()
    }

    private fun showAdminBanner() {
        if (!isAdminAccount() || contentLayout.findViewWithTag<View>("pocket_agent_admin_panel") != null) return
        val panel = card(Color.WHITE).apply { tag = "pocket_agent_admin_panel" }
        panel.addView(TextView(this).apply {
            text = "✦ ADMIN · VERIFIED ACCOUNT"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(74, 47, 180), Color.rgb(45, 122, 220))).apply { cornerRadius = dp(16).toFloat() }
        }, margins(bottom = 8))
        panel.addView(TextView(this).apply {
            text = "Signed in as ${firebaseAuth?.currentUser?.email ?: "Admin"}\\nRole: ADMIN · verified by Firebase"
            textSize = 13f
            setTextColor(ink)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(247, 247, 255), 16)
        }, margins(bottom = 8))
        panel.addView(TextView(this).apply {
            text = "Plan previews do not grant paid entitlements."
            textSize = 12f; setTextColor(muted)
        }, margins(bottom = 8))
        panel.addView(actionButton("View plans and usage", Color.rgb(238, 235, 255), accent).apply {
            setOnClickListener { showPlanOptions() }
        }, margins(bottom = 8))
        panel.addView(actionButton("Report a bug", Color.rgb(230, 244, 255), Color.rgb(25, 75, 125)).apply {
            setOnClickListener { showBugReportForm() }
        }, margins(bottom = 8))
        panel.addView(actionButton("Review bug reports", Color.rgb(235, 250, 240), Color.rgb(20, 110, 65)).apply {
            setOnClickListener { showAdminBugInbox() }
        })
        contentLayout.addView(panel, 0, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(16), dp(8), dp(16), dp(12))
        })
    }

    private fun showBugReportForm() {
        if (firebaseAuth?.currentUser == null || firestore == null) {
            status.text = "Sign in with Firebase to submit a cloud bug report"; return
        }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(4)) }
        val titleField = field("Short bug title", false)
        val categoryField = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item,
                listOf("UI / layout", "Accessibility / tapping", "AI connection", "Login / security", "Other")).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        val stepsField = field("Steps to reproduce", false).apply { minLines = 3; gravity = Gravity.TOP }
        val expectedField = field("Expected behaviour", false)
        val actualField = field("Actual behaviour", false)
        form.addView(titleField, margins(bottom = 8)); form.addView(categoryField, margins(bottom = 8))
        form.addView(stepsField, margins(bottom = 8)); form.addView(expectedField, margins(bottom = 8))
        form.addView(actualField, margins(bottom = 8))
        form.addView(TextView(this).apply {
            text = "Never include passwords, API keys, OTPs, payment data, or private screen text."
            textSize = 12f; setTextColor(Color.rgb(160, 60, 55))
        })
        val dialog = AlertDialog.Builder(this).setTitle("Report a bug").setView(form)
            .setNegativeButton("Cancel", null).setPositiveButton("Submit report", null).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(5, 9, 16)))
            dialog.window?.setLayout(-1, -1)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(111, 229, 219))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.rgb(216, 236, 242))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val titleValue = titleField.text.toString().trim()
                val steps = stepsField.text.toString().trim()
                if (titleValue.isBlank() || steps.isBlank()) { status.text = "Add a title and reproduction steps"; return@setOnClickListener }
                val user = firebaseAuth?.currentUser ?: return@setOnClickListener
                val data = hashMapOf<String, Any>(
                    "uid" to user.uid, "title" to titleValue.take(160),
                    "category" to categoryField.selectedItem.toString(), "steps" to steps.take(4000),
                    "expected" to expectedField.text.toString().trim().take(2000),
                    "actual" to actualField.text.toString().trim().take(2000),
                    "appVersion" to "1.0.2",
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "status" to "new"
                )
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                firestore!!.collection("bugReports").add(data).addOnSuccessListener { ref ->
                    dialog.dismiss()
                    status.text = "Bug report submitted · " + ref.id
                    logCommunication("ADMIN BUG REPORT", "Report ID " + ref.id + " · " + titleValue)
                    AlertDialog.Builder(this).setTitle("Report submitted")
                        .setMessage("Reference: " + ref.id + "\\nSaved to Firebase. This does not automatically notify ChatGPT or fix the issue in the background.")
                        .setPositiveButton("OK", null).show()
                }.addOnFailureListener { error ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    status.text = "Report failed: " + (error.message ?: "check Firestore rules")
                }
            }
        }
        dialog.show()
    }

    private fun showAdminBugInbox() {
        if (!isAdminAccount() || firestore == null) { status.text = "Verified Firebase admin access required"; return }
        status.text = "Loading bug reports…"
        firestore!!.collection("bugReports").limit(50).get().addOnSuccessListener { snapshot ->
            val docs = snapshot.documents.sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }
            if (docs.isEmpty()) {
                AlertDialog.Builder(this).setTitle("Bug reports").setMessage("No reports found.")
                    .setPositiveButton("OK", null).show(); return@addOnSuccessListener
            }
            val labels = docs.map { "[" + it.getString("status").orEmpty() + "] " +
                it.getString("title").orEmpty() + "\\n" + it.getString("category").orEmpty() + " · " + it.id.take(10) }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Admin bug inbox · newest 50").setItems(labels) { _, index ->
                val doc = docs[index]
        val details = "FREE · ₹0\n500 successful actions per day\n\nPRO · ₹199/month (proposed)\n5,000 successful actions per day\n\nMAX · ₹499/month (proposed)\nUp to 2 billion theoretical actions/day, subject to fair-use, rate, and infrastructure safeguards\n\nADMIN · private access\nUnlimited for accounts with an authorised Firebase admin claim.\n\nPaid billing is not enabled in this build. These are proposed launch prices; paid limits will unlock only after secure billing and server-verified entitlements are configured."
                    "\\nCategory: " + doc.getString("category").orEmpty() + "\\n\\nSteps: " + doc.getString("steps").orEmpty() +
                    "\\n\\nExpected: " + doc.getString("expected").orEmpty() + "\\n\\nActual: " + doc.getString("actual").orEmpty()
                val states = arrayOf("new", "investigating", "fixed")
                AlertDialog.Builder(this).setTitle(doc.getString("title") ?: "Bug report").setMessage(details)
                    .setItems(states) { _, which ->
                        doc.reference.update("status", states[which])
                            .addOnSuccessListener { status.text = "Report status updated: " + states[which] }
                            .addOnFailureListener { err -> status.text = "Update failed: " + (err.message ?: "unknown error") }
                    }.setPositiveButton("Close", null).show()
            }.setPositiveButton("Close", null).show()
        }.addOnFailureListener { error -> status.text = "Could not load reports: " + (error.message ?: "check admin claims and rules") }
    }

    private fun isAdminAccount(): Boolean =
        prefs.getBoolean("admin_verified_by_firebase", false) && firebaseAuth?.currentUser != null

    private fun refreshAdminClaim(user: FirebaseUser?) {
        // Clear any cached role before checking the currently signed-in user's token.
        prefs.edit().putBoolean("admin_verified_by_firebase", false).putString("verified_plan", "Free").apply()
        if (user == null) return
        user.getIdToken(true).addOnSuccessListener { token ->
            val admin = token.claims["admin"] == true
            val claimedPlan = token.claims["plan"] as? String
            val verifiedPlan = if (admin) "Admin" else if (claimedPlan == "Pro" || claimedPlan == "Max") claimedPlan else "Free"
            prefs.edit().putBoolean("admin_verified_by_firebase", admin)
                .putString("verified_plan", verifiedPlan).apply()
            runOnUiThread {
                updatePlanPill()
                if (::status.isInitialized && !prefs.getBoolean("voice_session_requested", false)) {
                    status.text = "TAP THE ORB TO TALK"
                }
            }
        }.addOnFailureListener {
            prefs.edit().putBoolean("admin_verified_by_firebase", false).putString("verified_plan", "Free").apply()
            runOnUiThread { status.text = "ACCOUNT · ${user.email ?: "Signed-in user"} · ROLE NOT VERIFIED" }
        }
    }

    private fun showAccountBanner(user: FirebaseUser) {
        updatePlanPill()
        if (::status.isInitialized) status.text = "TAP THE ORB TO TALK"
        showPermissionOnboardingIfNeeded()
    }

    private fun currentPlan(): String {
        if (isAdminAccount()) return "Admin"
        val verified = prefs.getString("verified_plan", "Free").orEmpty()
        return if (verified == "Pro" || verified == "Max") verified else "Free"
    }

    private fun showPlanOptions() {
        val current = currentPlan()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(250, 251, 255), Color.rgb(239, 241, 255))
            ).apply { cornerRadius = dp(26).toFloat() }
        }
        panel.addView(TextView(this).apply {
            text = "Your current JARVIS plan"
            textSize = 14f
            setTextColor(muted)
            setPadding(dp(4), dp(4), dp(4), dp(14))
        })
        fun addPlan(name: String, price: String, description: String, active: Boolean) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    if (active) intArrayOf(Color.rgb(244, 242, 255), Color.WHITE)
                    else intArrayOf(Color.WHITE, Color.rgb(247, 249, 255))
                ).apply {
                    cornerRadius = dp(22).toFloat()
                    setStroke(dp(1), if (active) accent else Color.rgb(225, 229, 243))
                }
                elevation = dp(2).toFloat()
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(TextView(this).apply {
                text = name
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ink)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(TextView(this).apply {
                text = if (active) "CURRENT PLAN" else price
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (active) accent else muted)
            })
            item.addView(header, margins(bottom = 7))
            item.addView(TextView(this).apply {
                text = description
                textSize = 12f
                setTextColor(muted)
                setLineSpacing(dp(2).toFloat(), 1f)
            })
            panel.addView(item, margins(bottom = 10))
        }
        addPlan("Free", "₹0", "500 successful actions per day · Local AI included", current == "Free")
        addPlan("Pro", "₹199 / month", "Proposed: 5,000 successful actions per day. Billing is not active yet.", current == "Pro")
        addPlan("Max", "₹499 / month", "Proposed higher-usage tier. Billing and server-side limits are not active yet.", current == "Max")
        if (isAdminAccount()) {
            addPlan("Admin", "PRIVATE", "Verified admin role · internal testing access", true)
        } else {
            addPlan("Admin", "PRIVATE", "Reserved for accounts with a verified Firebase admin claim.", false)
        }
        panel.addView(TextView(this).apply {
            text = "Paid plans are previews only. No payment will be taken, and tapping a plan cannot grant paid access until secure billing is implemented."
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(4), dp(2), dp(4), dp(8))
        })
        val scroll = ScrollView(this).apply { addView(panel) }
        AlertDialog.Builder(this)
            .setTitle("JARVIS plans")
            .setView(scroll)
            .setPositiveButton("Done", null)
            .show()
    }
    private fun showWelcomeGate() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val name = field("Display name (new accounts)", false)
        val email = field("Email address", false).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = field("Password · at least 6 characters", true)
        val googleSignInButton = actionButton("Continue with Google", Color.rgb(23, 35, 48), Color.rgb(235, 249, 250)).apply {
            setOnClickListener {
                if (firebaseAuth == null || firestore == null) {
                    status.text = "Google sign-in needs the owner's Firebase configuration"
                } else {
                    val clientIdResource = resources.getIdentifier("default_web_client_id", "string", packageName)
                    if (clientIdResource == 0) {
                        status.text = "Add a Web OAuth client in Firebase Console, then rebuild with google-services.json"
                    } else {
                        try {
                            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(getString(clientIdResource))
                                .requestEmail()
                                .build()
                            startActivityForResult(GoogleSignIn.getClient(this@MainActivity, options).signInIntent, 7413)
                        } catch (error: Exception) {
                            status.text = "Could not start Google sign-in: " + (error.message ?: "unknown error")
                        }
                    }
                }
            }
        }
        panel.addView(googleSignInButton, margins(bottom = 10))
        val setupHelpButton = actionButton("Firebase setup help", Color.rgb(230, 244, 255), Color.rgb(25, 75, 125)).apply {
            minHeight = dp(44)
            minimumHeight = dp(44)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Firebase setup")
                    .setMessage("1. Register Android package com.example.pocketagent in Firebase. 2. Add the generated google-services.json privately. 3. Enable Email/Password and Google sign-in. 4. Configure the Google Web OAuth client and signing-certificate SHA-1. 5. Create Firestore and publish tested firestore.rules. Read FIREBASE_SECURITY_SETUP.md.")
                    .setPositiveButton("OK", null).show()
            }
        }
        panel.addView(setupHelpButton, margins(bottom = 10))
        panel.addView(name, margins(bottom = 8))
        panel.addView(email, margins(bottom = 8))
        panel.addView(password, margins(bottom = 8))
        val resetPasswordButton = actionButton("Forgot password?", Color.rgb(238, 235, 255), accent).apply {
            minHeight = dp(44)
            minimumHeight = dp(44)
            setOnClickListener {
                val emailValue = email.text.toString().trim()
                if (firebaseAuth == null || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
                    status.text = "Enter your account email first; Firebase must be configured"
                } else {
                    isEnabled = false
                    firebaseAuth!!.sendPasswordResetEmail(emailValue)
                        .addOnSuccessListener {
                            status.text = "Password reset email sent if that account exists"
                            isEnabled = true
                        }
                        .addOnFailureListener { error ->
                            status.text = "Could not send reset email: " + (error.message ?: "check connection")
                            isEnabled = true
                        }
                }
            }
        }
        panel.addView(resetPasswordButton, margins(bottom = 8))
        panel.addView(TextView(this).apply {
            text = if (firebaseAuth == null) "Firebase is not configured. Add app/google-services.json, enable Email/Password in Firebase Console, and create Firestore. No demo password is accepted as a real login."
                else "Firebase Authentication manages passwords. JARVIS does not save passwords in local preferences."
            textSize = 12f
            setTextColor(muted)
        })
        listOf(name, email, password).forEach { input ->
            input.setTextColor(Color.rgb(235, 245, 248))
            input.setHintTextColor(Color.rgb(130, 154, 168))
            input.background = glassSurface(16)
        }
        panel.background = glassSurface(26)
        val dialog = AlertDialog.Builder(this).setTitle("WELCOME BACK")
            .setMessage("Sign in with Google or your email and password, or create your account.")
            .setView(panel)
            .setCancelable(false).setPositiveButton("Create account", null)
            .setNegativeButton("Sign in", null).create()
        welcomeDialog = dialog
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(5, 9, 16)))
            dialog.window?.setLayout(-1, -1)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(105, 228, 219))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.rgb(220, 238, 243))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val displayName = name.text.toString().trim()
                val emailValue = email.text.toString().trim()
                val passwordValue = password.text.toString()
                if (firebaseAuth == null || firestore == null) {
                    status.text = "Firebase setup required"
                    AlertDialog.Builder(this).setTitle("Firebase not connected")
                        .setMessage("Register package com.example.pocketagent in Firebase, enable Email/Password Authentication and Firestore, then add app/google-services.json. Read FIREBASE_SECURITY_SETUP.md.")
                        .setPositiveButton("OK", null).show()
                    return@setOnClickListener
                }
                if (displayName.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() || passwordValue.length < 6) {
                    status.text = "Enter a name, valid email and password with at least 6 characters"
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                firebaseAuth!!.createUserWithEmailAndPassword(emailValue, passwordValue)
                    .addOnSuccessListener { result ->
                        val user = result.user
                        if (user == null) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            status.text = "Account creation returned no user"
                            return@addOnSuccessListener
                        }
                        val profile = hashMapOf<String, Any>(
                            "displayName" to displayName, "email" to emailValue,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        dialog.dismiss()
                        refreshAdminClaim(user)
                        showAccountBanner(user)
                        status.text = "Account created · sending verification email"
                        user.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(displayName).build())
                        user.sendEmailVerification().addOnSuccessListener {
                            status.text = "Account created · verification email sent to " + emailValue
                        }.addOnFailureListener { error ->
                            status.text = "Account created, but verification email could not be sent: " + (error.message ?: "unknown error")
                        }
                        firestore!!.collection("users").document(user.uid).set(profile)
                            .addOnFailureListener { error ->
                                status.text = "Account created, but profile sync failed: " + (error.message ?: "check Firestore rules")
                            }
                    }.addOnFailureListener { error ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        status.text = "Sign-up failed: " + (error.message ?: "check connection and settings")
                    }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                val emailValue = email.text.toString().trim()
                val passwordValue = password.text.toString()
                if (firebaseAuth == null || emailValue.isBlank() || passwordValue.isBlank()) {
                    status.text = "Firebase setup, email and password are required"
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                firebaseAuth!!.signInWithEmailAndPassword(emailValue, passwordValue)
                    .addOnSuccessListener { result ->
                        val user = result.user
                        if (user == null) {
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                            status.text = "Sign-in returned no user"
                            return@addOnSuccessListener
                        }
                        dialog.dismiss()
                        refreshAdminClaim(user)
                        showAccountBanner(user)
                        status.text = "Signed in successfully"
                    }.addOnFailureListener { error ->
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                        status.text = "Sign-in failed: " + (error.message ?: "check email and password")
                    }
            }
        }
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.rgb(229, 229, 234))
        }

    private fun card(color: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        // Lightweight frosted-glass treatment compatible with Android 10.
        // Translucent layered surfaces, a bright hairline border and soft elevation
        // create the glass effect without relying on newer RenderEffect APIs.
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (color == Color.WHITE) intArrayOf(
                Color.argb(242, 255, 255, 255),
                Color.argb(226, 245, 247, 255)
            ) else intArrayOf(
                Color.argb(236, Color.red(blendWithWhite(color)), Color.green(blendWithWhite(color)), Color.blue(blendWithWhite(color))),
                Color.argb(224, Color.red(color), Color.green(color), Color.blue(color))
            )
        ).apply {
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), Color.argb(205, 255, 255, 255))
        }
        elevation = dp(7).toFloat()
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
        background = GradientDrawable().apply {
            setColor(Color.argb(224, 255, 255, 255))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(226, 230, 244))
        }
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }

    private fun actionButton(title: String, bg: Int, fg: Int) = Button(this).apply {
        val isPrimary = bg == accent || bg == Color.rgb(30, 32, 48)
        val normalBackground = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (isPrimary) intArrayOf(Color.rgb(126, 109, 239), Color.rgb(91, 76, 207))
            else intArrayOf(Color.WHITE, blendWithWhite(bg))
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), if (isPrimary) Color.rgb(112, 96, 222) else Color.rgb(225, 229, 243))
        }
        text = title
        textSize = 13f
        isAllCaps = false
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        setTypeface(null, Typeface.BOLD)
        setTextColor(if (isPrimary) Color.WHITE else fg)
        background = normalBackground
        // Keep the touch target unchanged; show a subtle pressed state instead of moving bounds.
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.78f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }
        minHeight = dp(48)
        minimumHeight = dp(48)
        stateListAnimator = null
        elevation = 0f
        setPadding(dp(16), dp(11), dp(16), dp(11))
        contentDescription = title
    }

    private fun blendWithWhite(color: Int): Int = Color.rgb(
        (Color.red(color) + 255) / 2,
        (Color.green(color) + 255) / 2,
        (Color.blue(color) + 255) / 2
    )

    override fun onResume() {
        super.onResume()
        if (::planPill.isInitialized) updatePlanPill()
        if (::screenPreview.isInitialized) refreshScreenText()
        if (firebaseAuth?.currentUser != null && permissionDialog?.isShowing == true) {
            val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val accessReady = prefs.getBoolean("accessibility_connected", false)
            if (micGranted && accessReady) {
                permissionDialog?.dismiss()
                permissionDialog = null
            }
        }
        if (::status.isInitialized) {
            status.text = when {
                agentRunning && prefs.getBoolean("agent_paused", false) -> "TASK PAUSED · SAY CONTINUE"
                agentRunning -> "JARVIS IS WORKING · ASK FOR PROGRESS BY VOICE"
                firebaseAuth?.currentUser != null -> "TAP THE ORB TO TALK"
                else -> "TAP THE ORB TO TALK"
            }
        }
    }

    private fun refreshScreenText() {
        val text = prefs.getString("latest_screen_text", "").orEmpty()
        val connected = prefs.getBoolean("accessibility_connected", false)
        screenPreview.text = when {
            text.isNotBlank() -> text
            !connected -> "JARVIS has not confirmed an active Accessibility connection. Reopen this app or toggle the service off/on."
            !prefs.getBoolean("session_active", false) -> "Accessibility is connected, but observation is paused. Tap Start, switch to the target app, wait, then return and refresh."
            else -> "Service connected, but no readable labels were captured. Some apps hide their content from accessibility services."
        }
    }

    private fun showHistoryMenu() {
        val options = arrayOf("History", "Clear history", "Connection & privacy", "Report a bug", "Account & logout")
        AlertDialog.Builder(this).setTitle("JARVIS")
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
                        .setMessage("Provider API keys and task history are stored on this phone using encrypted preferences when available. In Cloud mode, your task and optionally observed screen labels are sent to the selected AI provider. Firebase account sync and cloud bug reports require the owner to configure Firebase. Offline mode does not create a fake account.")
                        .setPositiveButton("OK", null).show()
                    3 -> showBugReportForm()
                    4 -> confirmLogout()
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
        val mode = modeSpinner.selectedItemPosition
        val chosenModel = model.selectedItem.toString().substringAfter("—").trim()
        val isOpenRouter = chosenModel.startsWith("openrouter/")
        val key = if (isOpenRouter) openRouterKey.text.toString().trim().ifBlank { securePrefs.getString("openrouter_api_key", "").orEmpty() }
            else apiKey.text.toString().trim().ifBlank { securePrefs.getString("gemini_api_key", "").orEmpty() }
        val userTask = task.text.toString().trim()
        val shareScreenText = useScreen.isChecked
        if (userTask.isBlank()) { status.text = "Describe your task first"; return }
        if (!prefs.getBoolean("accessibility_connected", false)) {
            AlertDialog.Builder(this)
                .setTitle("Enable JARVIS app control")
                .setMessage("Android requires you to enable the JARVIS Accessibility Service before it can read app labels, open apps, or tap controls. This permission stays under your control.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .show()
            status.text = "App control needs the JARVIS Accessibility Service"
            return
        }
        if (mode == 0 && key.isBlank()) { status.text = "Cloud engine not configured · open Advanced AI engine settings or choose an offline mode"; return }
        if (agentRunning) {
            agentRunning = false
            prefs.edit().putBoolean("session_active", false).putString("pending_action", "").apply()
            status.text = "Stopping JARVIS…"; agentButton.text = "▶ Start JARVIS"; return
        }
        agentRunning = true
        setOrbState(LiquidOrbView.State.AUTOMATING, "JARVIS IS WORKING")
        speakThroughVoiceService("I'm starting your task now.")
        prefs.edit().putBoolean("session_active", true).putBoolean("agent_paused", false)
            .putString("current_agent_status", "● JARVIS · Starting").putString("last_task", userTask)
            .putString("pending_action", "").putString("last_action_result", "").apply()
        agentButton.text = "■ Stop agent"
        status.text = "Agent starting · it will open the requested app and act step by step"
        logCommunication("AGENT START", userTask)
        runOnUiThread { answerView.text = "Starting task…"; status.text = "Planning privately · no Gemini chat replies" }
        Thread {
            try {
                var lastResult = ""
                var step = 1
                var launchAttempted = false
                val dailyLimit = when (currentPlan()) {
                    "Free" -> 500L
                    "Pro" -> 5_000L
                    "Max" -> 2_000_000_000L
                    else -> Long.MAX_VALUE
                }
                while (agentRunning && dailyStepsUsed() < dailyLimit) {
                    while (agentRunning && prefs.getBoolean("agent_paused", false)) {
                        Thread.sleep(200)
                    }
                    if (!agentRunning || !prefs.getBoolean("session_active", false)) break
                    var screen = prefs.getString("latest_screen_text", "").orEmpty()
                    if (screen.isBlank()) { runOnUiThread { status.text = "Waiting for readable screen text · open target app" }; Thread.sleep(1200); screen = prefs.getString("latest_screen_text", "").orEmpty() }
                    // Generic app launch bootstrap: no target app or workflow is hard-coded.
                    val requestedApp = extractRequestedApp(userTask)
                    val screenLooksLikeAgent = screen.isBlank() ||
                        (screen.contains("JARVIS", ignoreCase = true) &&
                         (requestedApp.isBlank() || !screen.contains(requestedApp, ignoreCase = true)))
                    if (requestedApp.isNotBlank() && screenLooksLikeAgent && !launchAttempted) {
                        launchAttempted = true
                        val bootstrapAction = JSONObject().put("action", "open_app").put("text", requestedApp).toString()
                        prefs.edit().putString("last_action_result", "").putString("pending_action", bootstrapAction).apply()
                        logCommunication("BOOTSTRAP", "Launching the app explicitly named by the user: " + requestedApp)
                        runOnUiThread {
                            answerView.append("\\n" + step + ". Opening " + requestedApp)
                            status.text = "Opening " + requestedApp + "…"
                        }
                        var waitedForLaunch = 0
                        var launchResult = ""
                        while (agentRunning && prefs.getBoolean("session_active", false) && waitedForLaunch < 8000) {
                            launchResult = prefs.getString("last_action_result", "").orEmpty()
                            if (launchResult.isNotBlank()) break
                            Thread.sleep(200)
                            waitedForLaunch += 200
                        }
                        lastResult = launchResult.ifBlank { "No confirmation received from Accessibility Service while opening " + requestedApp + "." }
                        logCommunication("BOOTSTRAP RESULT", lastResult)
                        runOnUiThread { answerView.append("\\n   ↳ " + lastResult) }
                        Thread.sleep(1200)
                        screen = prefs.getString("latest_screen_text", "").orEmpty()
                    }
                    val instruction = """
You are the silent planning engine for JARVIS, a general-purpose Android AI agent. You do not chat with the user. Return ONLY one JSON object and nothing else:
{"action":"tap_text","text":"exact visible label","reason":"short internal reason"}
Allowed actions: open_app, tap_text, type_text, scroll_down, scroll_up, back, home, done, ask_user.
Plan one safe, small action from the current screen, then inspect the next screen. Prefer exact visible labels. BOOTSTRAP RULE: if visible screen text is empty, unreadable, or shows only JARVIS while the task explicitly names an app, your first action MUST be {"action":"open_app","text":"<the exact app name from the task>","reason":"The requested app is not open; launch it first"}. Never assume a fixed target app. If the user says only "my app" or "my editing app" without naming an installed app, ask which app they mean. If opening fails, use the action result to report the exact failure; do not silently claim the app is open. Use open_app only when the requested app is not already open. Never reopen an app just because a tap failed.
Use scroll_down/up when the required control is off-screen. If the screen is an unexpected Google Play Store page or an app-install listing and the task was not to use Play Store, press Back ONCE, inspect the screen again, and do not tap the same link/ad again. If Play Store or the same unwanted page appears again, stop with ask_user and briefly state which screen is blocking progress. Never loop repeatedly between an app and Play Store.
If the target screen is already open and the requested action is complete, return done. If the same action fails twice or the screen does not change after two attempts, stop and ask for the specific help needed; do not keep tapping randomly.
Use type_text only when a text field is visibly focused and only for ordinary task text, never passwords, API keys, OTPs, payment details, or private information. Do not make purchases, accept subscriptions, send messages, delete content, or confirm sensitive actions. Never claim success unless the action result or next screen confirms it. Never output a normal conversational answer; the user-facing UI should show action steps only.
For done use {"action":"done","text":"short completion status","reason":"confirmed by screen"}. For ask_user use {"action":"ask_user","text":"specific screen or choice needed","reason":"why progress is paused"}.
User task: ${userTask}
Current plan: ${currentPlan()}. Step number: ${step}.
Plan rules: Free allows 500 successful actions/day; Pro allows 5,000/day; Max has a theoretical ceiling of 2 billion/day subject to fair-use and infrastructure safeguards; verified Admin accounts are unlimited. Do not stop just because the step number is high. Stop when the task is confirmed complete, the user must decide something, a safety pause is required, or a real technical/API error prevents continuing.
Visible screen text:
${if (mode == 0 && !shareScreenText) "[OMITTED: screen-text sharing is disabled. Do not guess visible controls; ask the user to enable screen sharing or choose a local mode.]" else screen.take(9000)}
Previous action result: ${lastResult.take(500)}
""".trimIndent()
                    val raw = when (mode) {
                        1 -> {
                            // Use the actual MediaPipe runtime for local inference; fall back to explicit
                            // local rules only when the selected model cannot load or produce a response.
                            try {
                                logCommunication("LOCAL AI", "Running the imported .task model on-device.")
                                generateLocalAi(instruction)
                            } catch (localError: Exception) {
                                logCommunication("LOCAL AI FALLBACK", "On-device inference failed: " +
                                    (localError.message ?: localError.javaClass.simpleName) +
                                    ". Falling back to local rules; no cloud request was made.")
                                localRulePlan(userTask, screen, lastResult)
                            }
                        }
                        2 -> localRulePlan(userTask, screen, lastResult)
                        else -> requestGeminiText(key, chosenModel, instruction)
                    }
                    val match = Regex("\\{[\\s\\S]*?\\}").find(raw)?.value
                    val command = try {
                        if (match != null) JSONObject(match)
                        else throw IllegalStateException("Planner did not return action JSON.")
                    } catch (parseError: Exception) {
                        if (mode == 1) {
                            logCommunication("LOCAL AI FALLBACK", "Local model response was not valid action JSON. Trying safe local rules for this step.")
                            JSONObject(localRulePlan(userTask, screen, lastResult))
                        } else throw IllegalStateException("Planner did not return valid action JSON.")
                    }
                    val action = command.optString("action", "ask_user")
                    val textValue = command.optString("text", "")
                    val reason = command.optString("reason", "")
                    prefs.edit().putString("current_agent_status", "● Step " + step + " · " + action).apply()
                    logCommunication("AGENT STEP " + step, "Action: " + action + " " + textValue + "\\nReason: " + reason)
                    runOnUiThread { answerView.append("\\n" + step + ". " + actionLabel(action, textValue)); setOrbState(LiquidOrbView.State.AUTOMATING, "WORKING · STEP " + step) }
                    speakThroughVoiceService(actionLabel(action, textValue))
                    when (action) {
                        "done" -> {
                            prefs.edit().putString("current_agent_status", "● JARVIS · Completed").apply()
                            runOnUiThread { answerView.append("\\n✓ Task completed"); setOrbState(LiquidOrbView.State.SPEAKING, "TASK COMPLETE") }
                            break
                        }
                        "ask_user" -> {
                            prefs.edit().putBoolean("agent_paused", true).putString("current_agent_status", "● JARVIS · Needs your input").apply()
                            speakThroughVoiceService(textValue)
                            runOnUiThread { answerView.append("\\n⏸ Needs your input: " + textValue); setOrbState(LiquidOrbView.State.PAUSED, "WAITING FOR YOUR INSTRUCTION") }
                            break
                        }
                        "open_app", "tap_text", "type_text", "scroll_down", "scroll_up", "back", "home" -> {
                            // Reset the previous result so a timeout cannot be mistaken for a successful action.
                            prefs.edit().putString("last_action_result", "")
                                .putString("pending_action", JSONObject().put("action", action).put("text", textValue).toString()).apply()
                            var waited = 0
                            var actionResult = ""
                            while (agentRunning && prefs.getBoolean("session_active", false) && waited < 6000) {
                                actionResult = prefs.getString("last_action_result", "").orEmpty()
                                if (actionResult.isNotBlank() && !actionResult.startsWith("Tap gesture queued:", true)) break
                                Thread.sleep(200)
                                waited += 200
                            }
                            lastResult = if (actionResult.startsWith("Tap gesture queued:", true)) {
                                "Tap gesture confirmation timed out. The screen will be rechecked before another attempt."
                            } else actionResult.ifBlank {
                                "No confirmation received. Inspect the current screen and choose a different safe action if needed."
                            }
                            recordSuccessfulStep(lastResult)
                            runOnUiThread { answerView.append("\\n   ↳ " + lastResult) }
                            Thread.sleep(700)
                        }
                        else -> { runOnUiThread { answerView.text = "Unsupported action: " + action; status.text = "Agent paused for safety" }; break }
                    }
                    step++
                }
                if (agentRunning && dailyStepsUsed() >= dailyLimit) {
                    runOnUiThread {
                        answerView.append("\\n\\nDaily action allowance reached for the " + currentPlan() + " plan.")
                        status.text = currentPlan() + " · daily action allowance reached"
                    }
                }
            } catch (e: Exception) {
                val message = "Agent error: " + (e.message ?: "unknown error")
                logCommunication("AGENT ERROR", message)
                runOnUiThread { answerView.append("\\n⚠ Agent stopped: " + (e.message ?: "request failed")); status.text = "Agent stopped · check connection or model settings" }
            } finally {
                agentRunning = false
                val finalStatus = prefs.getString("current_agent_status", "").orEmpty()
                prefs.edit().putBoolean("session_active", false).putBoolean("agent_paused", false)
                    .putString("current_agent_status", if (finalStatus.contains("Completed", true)) "● JARVIS · Completed" else "● JARVIS · Stopped")
                    .putString("pending_action", "").apply()
                runOnUiThread {
                    agentButton.text = "▶ Start JARVIS"
                    if (prefs.getBoolean("voice_session_requested", false)) setOrbState(LiquidOrbView.State.LISTENING, "LISTENING · READY FOR YOUR NEXT COMMAND")
                    else setOrbState(LiquidOrbView.State.IDLE, "TASK ENDED · TAP THE ORB TO TALK")
                }
                speakThroughVoiceService(if (finalStatus.contains("Completed", true)) "The task is complete." else "The automation has stopped.")
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
        val mode = modeSpinner.selectedItemPosition
        val chosenModel = model.selectedItem.toString().substringAfter("—").trim()
        val isOpenRouter = chosenModel.startsWith("openrouter/")
        val key = if (isOpenRouter) openRouterKey.text.toString().trim().ifBlank { securePrefs.getString("openrouter_api_key", "").orEmpty() }
            else apiKey.text.toString().trim().ifBlank { securePrefs.getString("gemini_api_key", "").orEmpty() }
        val userTask = task.text.toString().trim()
        if (mode == 0 && key.isEmpty()) {
            status.text = "Missing API key"
            answerView.text = if (isOpenRouter) "Paste an OpenRouter API key in the Connection section and save it." else "Paste your Gemini API key in the Connection section first. You can save it on this phone using the secure-save button."
            return
        }
        if (userTask.isEmpty()) {
            status.text = "Missing task"
            answerView.text = "Type what you want Gemini to help with, then tap Ask JARVIS."
            return
        }

        val screen = prefs.getString("latest_screen_text", "").orEmpty()
        prefs.edit().putString("last_task", userTask).apply()
        val prompt = buildString {
            append("You are JARVIS, a unified Android AI agent. Give clear, concise, actionable guidance based on the user's task. Be explicit that you cannot perform actions on the phone. Never claim you performed actions. Never request passwords, payment details, or secret codes.\n\n")
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
                    val localResponse = if (mode == 1) {
                        try {
                            generateLocalAi(prompt)
                        } catch (localError: Exception) {
                            logCommunication("LOCAL AI FALLBACK", "Model inference failed: " +
                                (localError.message ?: localError.javaClass.simpleName))
                            "On-device model could not run: " + (localError.message ?: "unknown model/runtime error") +
                                "\\n\\nTry a compatible MediaPipe .task model, or switch to Local Rules mode. No cloud request was sent."
                        }
                    } else localRulePlan(userTask, screen, "")
                    runOnUiThread {
                        answerView.text = localResponse
                        status.text = if (mode == 1) "On-device model attempt finished · no API used" else "Local rules response · no API used"
                        saveHistory(userTask, modeSpinner.selectedItem.toString(), localResponse)
                    }
                    return@Thread
                }
                val answer = requestGeminiText(key, chosenModel, prompt)
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
