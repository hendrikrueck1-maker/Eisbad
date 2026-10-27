package de.eisbad.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Läuft als Foreground-Service, damit Musik und Ansagen auch bei gesperrtem Bildschirm
 * weiterlaufen (Handy liegt am Beckenrand, Ton kommt über die Kopfhörer).
 */
class SessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var voiceJob: Job? = null
    private var queue = Channel<String>(Channel.UNLIMITED)
    private var music: MusicPlayer? = null
    private var voice: VoicePlayer? = null
    private var tone: ToneGenerator? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_STOP -> stopSession()
            ACTION_SKIP -> SessionBus.skip = true
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        if (job?.isActive == true) return
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        val pm = getSystemService(PowerManager::class.java)
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eisbad:session")
            .apply { acquire(90 * 60 * 1000L) }

        val s = AppSettings.load(this)
        SessionBus.skip = false
        queue = Channel(Channel.UNLIMITED)

        music = if (s.musicUri.isNotBlank())
            MusicPlayer(this, Uri.parse(s.musicUri), s.musicVol) else null
        voice = VoicePlayer(this, PhraseBook(this))
        tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 35)
        } catch (e: Exception) {
            null
        }

        val q = queue
        voiceJob = scope.launch {
            for (key in q) {
                music?.duck(true)
                voice?.play(key, s.lang, s.voiceVol)
                music?.duck(false)
            }
        }

        val runner = SessionRunner(
            s,
            say = { key -> q.trySend(key) },
            beep = { inhale ->
                tone?.startTone(
                    if (inhale) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2, 90
                )
            },
        )

        job = scope.launch {
            runner.run()
            delay(1500)
            q.close()
            voiceJob?.join()
            SessionBus.state.value = UiState(
                running = false, phase = Phase.DONE, title = "Geschafft", big = "✓",
                sub = "Wärme dich langsam auf.", progress = 1f,
            )
            cleanup()
            stopSelf()
        }
    }

    private fun stopSession() {
        job?.cancel()
        voiceJob?.cancel()
        queue.close()
        SessionBus.state.value = UiState()
        cleanup()
        stopSelf()
    }

    private fun cleanup() {
        music?.release(); music = null
        voice?.release(); voice = null
        tone?.release(); tone = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        scope.cancel()
        cleanup()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Eisbad-Session", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Eisbad läuft")
            .setContentText("Ansagen und Musik sind aktiv")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Beenden", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "de.eisbad.app.START"
        const val ACTION_STOP = "de.eisbad.app.STOP"
        const val ACTION_SKIP = "de.eisbad.app.SKIP"
        private const val CHANNEL = "session"
        private const val NOTIF_ID = 1
    }
}
