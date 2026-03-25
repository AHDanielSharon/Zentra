package com.zentra

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class ZentraVoiceService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var wakeWordDetected = false
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Listening for wake word"))
        tts = TextToSpeech(this, this)
        setupSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, ZentraVoiceService::class.java)
        val restartPendingIntent = PendingIntent.getService(
            this,
            44,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(AlarmManager::class.java)
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1500, restartPendingIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            VoiceStateStore.updateStatus("Speech recognition not available")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(this@ZentraVoiceService)
        }
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: return
        if (isListening) return
        VoiceStateStore.updateStatus(if (wakeWordDetected) "Listening for command" else "Listening for wake word")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            recognizer.startListening(intent)
            isListening = true
        } catch (_: Exception) {
            isListening = false
            scheduleRestart()
        }
    }

    override fun onResults(results: android.os.Bundle?) {
        isListening = false
        val matches = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
        handleRecognized(matches)
        scheduleRestart()
    }

    override fun onPartialResults(partialResults: android.os.Bundle?) {
        val matches = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            .orEmpty()
        if (matches.isNotEmpty()) {
            VoiceStateStore.updateHeard(matches.first())
        }
    }

    private fun handleRecognized(matches: List<String>) {
        if (matches.isEmpty()) return

        val text = matches.first().lowercase(Locale.US).trim()
        VoiceStateStore.updateHeard(text)

        if (!wakeWordDetected && text.startsWith("open ")) {
            val outcome = executeCommand(text)
            VoiceStateStore.updateMessage(outcome)
            updateNotification("Listening for wake word")
            return
        }

        if (!wakeWordDetected) {
            if (text.contains("hey zentra")) {
                wakeWordDetected = true
                VoiceStateStore.updateStatus("Wake word detected")
                VoiceStateStore.updateMessage("Yes, I'm listening")
                speak("I'm listening")
                updateNotification("Wake word detected. Waiting for command")

                val immediateCommand = text.substringAfter("hey zentra", "").trim()
                if (immediateCommand.isNotEmpty()) {
                    val outcome = executeCommand(immediateCommand)
                    VoiceStateStore.updateMessage(outcome)
                    wakeWordDetected = false
                    updateNotification("Listening for wake word")
                }
            }
            return
        }

        val outcome = executeCommand(text)
        VoiceStateStore.updateMessage(outcome)
        wakeWordDetected = false
        updateNotification("Listening for wake word")
    }

    private fun executeCommand(text: String): String {
        val normalized = normalizeOpenCommand(text)

        return when {
            normalized.contains("open google") || normalized.contains("open chrome") -> {
                openPackage("com.android.chrome", "Google Chrome")
            }
            normalized.contains("open whatsapp") -> {
                openPackage("com.whatsapp", "WhatsApp")
            }
            normalized.contains("open youtube") -> {
                openPackage("com.google.android.youtube", "YouTube")
            }
            normalized.contains("open camera") -> {
                openCamera()
            }
            normalized.contains("open settings") -> {
                openSettings()
                "Opening Settings"
            }
            normalized.startsWith("open ") -> {
                val appName = normalized.removePrefix("open ").trim()
                openAnyAppByName(appName)
            }
            else -> {
                speak("Sorry, command not recognized")
                "Command not recognized"
            }
        }
    }

    private fun openCamera(): String {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            val msg = "Opening Camera"
            speak(msg)
            msg
        } catch (_: Exception) {
            openAnyAppByName("camera")
        }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        speak("Opening Settings")
    }

    private fun openPackage(packageName: String, appLabel: String): String {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                val msg = "Opening $appLabel"
                speak(msg)
                msg
            } else {
                val msg = "$appLabel is installed, but cannot be opened"
                speak(msg)
                msg
            }
        } catch (_: PackageManager.NameNotFoundException) {
            val msg = "$appLabel is not installed"
            speak(msg)
            msg
        } catch (_: ActivityNotFoundException) {
            val msg = "Unable to open $appLabel"
            speak(msg)
            msg
        }
    }

    private fun openAnyAppByName(query: String): String {
        if (query.isBlank()) {
            return "Please say an app name"
        }

        val launchables = getLaunchableApps()
        val normalizedQuery = query.replace(" app", "").trim()

        val exact = launchables.firstOrNull { (_, label) -> label.equals(normalizedQuery, ignoreCase = true) }
        val partial = launchables.firstOrNull { (_, label) -> label.contains(normalizedQuery, ignoreCase = true) }
        val fuzzy = launchables
            .map { it to tokenMatchScore(normalizedQuery, it.second) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first

        val match = exact ?: partial ?: fuzzy

        return if (match != null) {
            val packageName = match.first
            val label = match.second
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                val msg = "Opening $label"
                speak(msg)
                msg
            } else {
                val msg = "$label is installed but cannot be opened"
                speak(msg)
                msg
            }
        } else {
            val msg = "I could not find app $normalizedQuery"
            speak(msg)
            msg
        }
    }

    private fun normalizeOpenCommand(text: String): String {
        return text
            .replace("please", "")
            .replace("could you", "")
            .replace("for me", "")
            .replace("the ", "")
            .trim()
    }

    private fun tokenMatchScore(query: String, label: String): Int {
        val queryTokens = query.lowercase(Locale.US).split(" ").filter { it.isNotBlank() }
        val labelTokens = label.lowercase(Locale.US).split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty() || labelTokens.isEmpty()) return 0
        return queryTokens.count { q -> labelTokens.any { it.startsWith(q) || it.contains(q) } }
    }

    private fun getLaunchableApps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val matches: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        return matches.map {
            it.activityInfo.packageName to it.loadLabel(packageManager).toString()
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zentra_tts")
    }

    private fun scheduleRestart(delayMs: Long = 350) {
        mainHandler.postDelayed(
            {
                isListening = false
                speechRecognizer?.cancel()
                startListening()
            },
            delayMs
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZENTRA Listener",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZENTRA is active")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_zentra)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    override fun onError(error: Int) {
        isListening = false
        VoiceStateStore.updateStatus("Listening...")
        scheduleRestart(550)
    }

    override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        isListening = false
    }
    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

    companion object {
        private const val CHANNEL_ID = "zentra_voice_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
