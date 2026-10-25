package com.example.sheshield.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.sheshield.data.ContactStore
import com.example.sheshield.data.IncidentStore
import com.example.sheshield.data.SosPhase
import com.example.sheshield.data.SosPrefs
import com.example.sheshield.data.SosState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume

class SosService : Service() {

    companion object {
        const val ACTION_MONITOR = "com.example.sheshield.MONITOR"
        const val ACTION_TRIGGER = "com.example.sheshield.TRIGGER"
        const val ACTION_CANCEL = "com.example.sheshield.CANCEL"
        const val ACTION_SAFE = "com.example.sheshield.SAFE"

        private const val CHANNEL_ID = "sheshield_background"
        private const val NOTIF_ID = 1
        private const val COUNTDOWN_SECONDS = 10
        private const val UPDATE_INTERVAL_MS = 60_000L
        private const val CHUNK_MS = 30_000L

        fun start(context: Context, action: String) {
            val intent = Intent(context, SosService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null
    private var sosJob: Job? = null
    private var recordingJob: Job? = null
    private var incidentId: String? = null

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private val prefs by lazy { SosPrefs(this) }
    private var shakeDetector: ShakeDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitoring = false

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Background service", NotificationManager.IMPORTANCE_LOW
        ).apply { lockscreenVisibility = Notification.VISIBILITY_SECRET }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startInForeground(buildNotification("Running"))) {
            stopSelf()
            return START_NOT_STICKY
        }
        startMonitoring()
        when (intent?.action) {
            ACTION_TRIGGER -> startCountdown()
            ACTION_CANCEL -> cancelCountdown()
            ACTION_SAFE -> imSafe()
        }
        return START_STICKY
    }

    private fun hasMic(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Asks Android for a location + microphone foreground service when the microphone
     * permission is granted. If Android refuses (for example when started from the
     * background), it falls back to location only so SOS still works.
     */
    private fun startInForeground(n: Notification): Boolean {
        if (Build.VERSION.SDK_INT < 29) {
            return try {
                startForeground(NOTIF_ID, n)
                true
            } catch (e: Exception) {
                Log.e("SheShield", "Could not start foreground service", e)
                false
            }
        }
        val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        val withMic = if (Build.VERSION.SDK_INT >= 30 && hasMic()) {
            location or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            location
        }
        return try {
            startForeground(NOTIF_ID, n, withMic)
            true
        } catch (e: Exception) {
            Log.w("SheShield", "Foreground start with microphone failed, retrying without it", e)
            try {
                startForeground(NOTIF_ID, n, location)
                true
            } catch (e2: Exception) {
                Log.e("SheShield", "Could not start foreground service", e2)
                false
            }
        }
    }

    // ---------- Shake detection ----------

    @SuppressLint("WakelockTimeout")
    private fun startMonitoring() {
        if (monitoring) return
        monitoring = true
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SheShield:monitor")
            .apply { acquire() }
        val detector = ShakeDetector({ prefs.sensitivity }) { onShake() }
        shakeDetector = detector
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun onShake() {
        if (SosState.phase.value == SosPhase.IDLE) startCountdown()
    }

    // ---------- Countdown ----------

    private fun startCountdown() {
        if (SosState.phase.value != SosPhase.IDLE) return
        countdownJob = scope.launch {
            for (s in COUNTDOWN_SECONDS downTo 1) {
                SosState.set(SosPhase.COUNTDOWN, s)
                updateNotification(buildNotification("Alert in $s", ACTION_CANCEL, "Cancel"))
                vibrate(150)
                delay(1000)
            }
            startSos()
        }
    }

    private fun cancelCountdown() {
        if (SosState.phase.value != SosPhase.COUNTDOWN) return
        countdownJob?.cancel()
        SosState.set(SosPhase.IDLE)
        updateNotification(buildNotification("Running"))
    }

    // ---------- SOS ----------

    private fun startSos() {
        SosState.set(SosPhase.ACTIVE)
        val id = IncidentStore.create(this, "SOS")
        incidentId = id
        updateNotification(buildNotification("Alert active", ACTION_SAFE, "I'm safe"))
        vibrate(600)
        startRecording(id)
        sosJob = scope.launch {
            var first = true
            while (isActive) {
                val loc = getLocation()
                incidentId?.let {
                    IncidentStore.addPoint(this@SosService, it, loc?.latitude, loc?.longitude)
                }
                val link = loc?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }
                val msg = when {
                    link == null && first -> "EMERGENCY! I need help. My location is unavailable right now."
                    link == null -> "SOS update: location unavailable right now."
                    first -> "EMERGENCY! I need help. My live location: $link"
                    else -> "SOS update, my location now: $link"
                }
                val names = notifyContacts(msg)
                incidentId?.let { IncidentStore.addAlerted(this@SosService, it, names) }
                first = false
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun imSafe() {
        val phase = SosState.phase.value
        if (phase == SosPhase.IDLE) return
        countdownJob?.cancel()
        sosJob?.cancel()
        recordingJob?.cancel()   // the last chunk is still saved
        recordingJob = null
        SosState.set(SosPhase.IDLE)
        if (phase == SosPhase.ACTIVE) {
            notifyContacts("I'm safe now. Please ignore my earlier alert. Thank you.")
            incidentId?.let { IncidentStore.finish(this, it) }
            incidentId = null
        }
        updateNotification(buildNotification("Running"))
    }

    /** Sends the text to every trusted contact and returns the names alerted. */
    private fun notifyContacts(text: String): List<String> {
        val contacts = ContactStore(this).load()
        contacts.forEach { SmsHelper.send(this, it.phone, text) }
        return contacts.map { it.name }
    }

    // ---------- Audio recording (30-second chunks) ----------

    private fun startRecording(id: String) {
        if (!hasMic()) {
            Log.w("SheShield", "Microphone permission not granted, not recording")
            return
        }
        if (recordingJob != null) return
        recordingJob = scope.launch {
            var part = 1
            while (isActive) {
                val file = File(cacheDir, "chunk_${id}_$part.m4a")
                val ok = recordChunk(file)
                if (file.exists() && file.length() > 0) {
                    // Runs even if recording was just cancelled, so the last chunk is kept.
                    withContext(NonCancellable + Dispatchers.IO) {
                        IncidentStore.addAudio(this@SosService, id, file, "Audio part $part")
                    }
                } else {
                    file.delete()
                }
                if (!ok) break
                part++
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    /** Records one chunk. Returns true if it ran the full time, false if stopped or failed. */
    private suspend fun recordChunk(file: File): Boolean {
        val recorder = newRecorder()
        var started = false
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            started = true
            delay(CHUNK_MS)
            return true
        } catch (e: CancellationException) {
            return false
        } catch (e: Exception) {
            Log.e("SheShield", "Recording failed", e)
            return false
        } finally {
            if (started) {
                try { recorder.stop() } catch (e: Exception) { /* nothing was recorded */ }
            }
            try { recorder.release() } catch (e: Exception) { }
        }
    }

    // ---------- Location ----------

    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Location? {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val fresh = withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
        if (fresh != null) return fresh

        return suspendCancellableCoroutine { cont ->
            fused.lastLocation
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }

    // ---------- Notification and vibration ----------

    private fun buildNotification(
        text: String,
        actionName: String? = null,
        actionLabel: String? = null
    ): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val openApp = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (actionName != null) {
            val pi = PendingIntent.getForegroundService(
                this, 1,
                Intent(this, SosService::class.java).setAction(actionName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, actionLabel, pi)
        }
        return builder.build()
    }

    private fun updateNotification(n: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    private fun vibrate(ms: Long) {
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onDestroy() {
        scope.cancel()
        shakeDetector?.let { sensorManager.unregisterListener(it) }
        wakeLock?.let { if (it.isHeld) it.release() }
        SosState.set(SosPhase.IDLE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}