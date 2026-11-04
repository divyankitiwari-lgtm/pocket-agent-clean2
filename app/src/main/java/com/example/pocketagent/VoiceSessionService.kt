package com.example.pocketagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * User-visible voice session that remains available while JARVIS opens another app.
 * It starts only after an explicit user action and microphone permission.
 */
class VoiceSessionService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_START = "com.example.pocketagent.voice.START"
        const val ACTION_STOP = "com.example.pocketagent.voice.STOP"
        const val ACTION_SPEAK = "com.example.pocketagent.voice.SPEAK"
        const val ACTION_TASK = "com.example.pocketagent.voice.TASK"
        const val ACTION_FOLLOWUP = "com.example.pocketagent.voice.FOLLOWUP"
        const val ACTION_STATE = "com.example.pocketagent.voice.STATE"
        const val EXTRA_TEXT = "voice_text"
        private const val CHANNEL_ID = "jarvis_voice_session"
        private const val NOTIFICATION_ID = 731
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private var speaking = false
    private var restarting = false
    private var pendingSpeech = ""

    private val prefs by lazy { getSharedPreferences("pocket_agent", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVoiceSession()
                return START_NOT_STICKY
            }
            ACTION_SPEAK -> {
                speak(intent.getStringExtra(EXTRA_TEXT).orEmpty())
                return START_STICKY
            }
            ACTION_START -> startVoiceSession()
        }
        return START_STICKY
    }

    private fun startVoiceSession() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            speak("Microphone permission is required before I can listen.")
            stopSelf()
            return
        }
        val notification = buildNotification("Voice control is active. Say pause, continue, stop, or ask for progress.")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is not available on this device.")
            stopVoiceSession()
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        }
        if (!listening && !speaking) startListening()
        broadcastState("LISTENING")
    }

    private fun startListening() {
        if (restarting || speaking || recognizer == null) return
        restarting = true
        handler.post {
            restarting = false
            if (speaking || recognizer == null) return@post
            try {
                val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag("en-IN"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer?.startListening(request)
                listening = true
            } catch (_: Exception) {
                listening = false
                handler.postDelayed({ startListening() }, 900)
            }
        }
    }

    private fun handleTranscript(raw: String) {
        val text = raw.trim()
        if (text.isBlank()) return
        val active = prefs.getBoolean("session_active", false)
        when (val parsed = VoiceCommandParser.parse(text, active)) {
            is VoiceCommandParser.Parsed -> when (parsed.kind) {
                VoiceCommandParser.Kind.PAUSE -> {
                    prefs.edit().putBoolean("agent_paused", true).apply()
                    broadcastState("PAUSED")
                    speak("Paused. Say continue when you are ready.")
                }
                VoiceCommandParser.Kind.RESUME -> {
                    prefs.edit().putBoolean("agent_paused", false).apply()
                    broadcastState(if (active) "AUTOMATING" else "LISTENING")
                    speak("Continuing the task.")
                }
                VoiceCommandParser.Kind.STOP -> {
                    prefs.edit().putBoolean("session_active", false)
                        .putBoolean("agent_paused", false)
                        .putString("pending_action", "")
                        .apply()
                    broadcastState("LISTENING")
                    speak("Stopping the automation.")
                }
                VoiceCommandParser.Kind.STATUS -> speak(buildProgressAnswer())
                VoiceCommandParser.Kind.TASK -> {
                    if (active) {
                        val followup = Intent(ACTION_FOLLOWUP).setPackage(packageName)
                            .putExtra(EXTRA_TEXT, text)
                        sendBroadcast(followup)
                    } else {
                        prefs.edit().putString("pending_voice_task", text).apply()
                        val task = Intent(ACTION_TASK).setPackage(packageName).putExtra(EXTRA_TEXT, text)
                        sendBroadcast(task)
                    }
                }
            }
        }
        updateNotification(if (active) "Listening for voice controls while automation runs." else "Listening for your next JARVIS request.")
    }

    private fun buildProgressAnswer(): String {
        val task = prefs.getString("last_task", "").orEmpty().ifBlank { "your task" }
        val status = prefs.getString("current_agent_status", "").orEmpty()
            .ifBlank { if (prefs.getBoolean("agent_paused", false)) "Paused" else "Working" }
        val result = prefs.getString("last_action_result", "").orEmpty()
        val problem = when {
            result.contains("Could not find", true) || result.contains("failed", true) -> "The latest action reported a problem: ${result.take(180)}"
            prefs.getBoolean("agent_paused", false) -> "The task is paused and waiting for your instruction."
            else -> "No blocking problem has been reported by the last action."
        }
        return "I'm working on $task. Current status: $status. $problem"
    }

    private fun speak(message: String) {
        if (message.isBlank()) return
        val engine = tts
        if (!ttsReady || engine == null) {
            pendingSpeech = message
            updateNotification(message.take(100))
            return
        }
        try {
            listening = false
            speaking = true
            broadcastState("SPEAKING")
            try { recognizer?.cancel() } catch (_: Exception) {}
            engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
            updateNotification("JARVIS is speaking.")
        } catch (_: Exception) {
            speaking = false
            startListening()
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.forLanguageTag("en-IN")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    handler.post {
                        speaking = false
                        if (prefs.getBoolean("voice_session_requested", false)) {
                            broadcastState("LISTENING")
                            startListening()
                        }
                    }
                }
                override fun onError(utteranceId: String?) {
                    handler.post {
                        speaking = false
                        if (prefs.getBoolean("voice_session_requested", false)) startListening()
                    }
                }
            })
            if (pendingSpeech.isNotBlank()) {
                val queued = pendingSpeech
                pendingSpeech = ""
                speak(queued)
            }
            if (prefs.getBoolean("voice_session_requested", false)) startVoiceSession()
        }
    }

    override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
    override fun onBeginningOfSpeech() { listening = true }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { listening = false }
    override fun onError(error: Int) {
        listening = false
        if (!speaking && prefs.getBoolean("voice_session_requested", false)) {
            handler.postDelayed({ startListening() }, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 700 else 350)
        }
    }
    override fun onResults(results: android.os.Bundle?) {
        listening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        handleTranscript(matches?.firstOrNull().orEmpty())
        if (!speaking) handler.postDelayed({ startListening() }, 250)
    }
    override fun onPartialResults(partialResults: android.os.Bundle?) {}
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    private fun stopVoiceSession() {
        prefs.edit().putBoolean("voice_session_requested", false).apply()
        broadcastState("IDLE")
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        try { tts?.stop() } catch (_: Exception) {}
        listening = false
        speaking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        recognizer = null
        tts = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "JARVIS voice session", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS voice control")
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun broadcastState(value: String) {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra(EXTRA_TEXT, value))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
