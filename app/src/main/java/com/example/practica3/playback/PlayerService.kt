package com.example.practica3.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import android.support.v4.media.session.MediaSessionCompat
import com.example.practica3.R
import com.example.practica3.audio.BeatDetector
import com.example.practica3.flash.TorchController
import com.example.practica3.sensors.ProximityPause
import com.example.practica3.sensors.ShakeSensor
import com.example.practica3.sensors.hapticClick

@UnstableApi
class PlayerService : Service() {

    companion object {
        const val ACTION_PLAY_URI   = "ACTION_PLAY_URI"
        const val ACTION_PLAY_URIS  = "ACTION_PLAY_URIS"
        const val ACTION_PLAY_INDEX = "ACTION_PLAY_INDEX"
        const val ACTION_TOGGLE     = "ACTION_TOGGLE"
        const val ACTION_NEXT       = "ACTION_NEXT"
        const val ACTION_PREV       = "ACTION_PREV"
        const val ACTION_STOP       = "ACTION_STOP"

        const val EXTRA_URI   = "EXTRA_URI"
        const val EXTRA_URIS  = "EXTRA_URIS"
        const val EXTRA_INDEX = "EXTRA_INDEX"

        private const val NOTI_ID = 1
        private const val CHANNEL = "player_channel"
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat

    // Gestos
    private var shake: ShakeSensor? = null
    private var proximity: ProximityPause? = null

    // Linterna al ritmo
    private var torch: TorchController? = null
    private var beat: BeatDetector? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build()

        // Atributos de audio con constantes de Media3 (C)
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(attrs, /* handleAudioFocus = */ true)

        mediaSession = MediaSessionCompat(this, "GuindaBeatsSession").apply { isActive = true }

        // Canal de notificación (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL, "Reproducción", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }

        // === Gestos ===
        shake = ShakeSensor(this) { player.seekToNext(); updateNotification() }
        proximity = ProximityPause(this) {
            if (player.isPlaying) player.pause() else player.play()
            applicationContext.hapticClick()
            updateNotification()
        }
        shake?.start()
        proximity?.start()

// === Linterna al ritmo ===
        torch = TorchController(this)
// ratio=1.6f → más sensible que antes, sube a 2.0 si parpadea demasiado
        beat  = BeatDetector(
            onBeat = { torch?.pulse(minIntervalMs = 100, onMs = 60) },
            minIntervalMs = 120,
            ratio = 1.4f
        )

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // Reenganchar el visualizer cuando cambie la sesión
                beat?.attachToSession(audioSessionId)
                if (audioSessionId != 0) beat?.start() else beat?.stop()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    // Pulso único para verificar que el flash funciona
                    torch?.pulse(minIntervalMs = 0, onMs = 80)
                    // Pequeño delay para asegurar sessionId y buffers listos
                    mainHandler.postDelayed({
                        beat?.attachToSession(player.audioSessionId)
                        beat?.start()
                    }, 300)
                } else {
                    beat?.stop()
                    torch?.off()
                }
                updateNotification()
            }
        })

        // Por si ya existía sessionId válido antes del callback
        if (player.audioSessionId != 0) {
            beat?.attachToSession(player.audioSessionId)
            beat?.start()
        }

        startForeground(NOTI_ID, buildNotification("Listo para reproducir"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY_URIS -> {
                val list = intent.getStringArrayListExtra(EXTRA_URIS)
                if (!list.isNullOrEmpty()) {
                    val items = list.map { MediaItem.fromUri(it) }
                    player.setMediaItems(items, true)
                    player.prepare()
                    player.play()
                    updateNotification()
                }
            }
            ACTION_PLAY_URI -> {
                val uri = intent.getStringExtra(EXTRA_URI) ?: return START_STICKY
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.play()
                updateNotification()
            }
            ACTION_PLAY_INDEX -> {
                val index = intent.getIntExtra(EXTRA_INDEX, -1)
                if (index in 0 until player.mediaItemCount) {
                    player.seekTo(index, 0L)
                    player.prepare()
                    player.play()
                    updateNotification()
                }
            }
            ACTION_TOGGLE -> {
                if (player.isPlaying) player.pause() else player.play()
                applicationContext.hapticClick()
                updateNotification()
            }
            ACTION_NEXT -> { player.seekToNext(); updateNotification() }
            ACTION_PREV -> { player.seekToPrevious(); updateNotification() }
            ACTION_STOP -> { stopSelf() }
        }
        return START_STICKY
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTI_ID,
            buildNotification(if (player.isPlaying) "Reproduciendo…" else "Pausado")
        )
    }

    private fun buildNotification(texto: String): Notification {
        val style = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Guinda Beats")
            .setContentText(texto)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", pi(ACTION_PREV))
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "Pausar" else "Reproducir",
                pi(ACTION_TOGGLE)
            )
            .addAction(android.R.drawable.ic_media_next, "Siguiente", pi(ACTION_NEXT))
            .setStyle(style)
            .setOngoing(player.isPlaying)
            .build()
    }

    private fun pi(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlayerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun onDestroy() {
        proximity?.stop(); shake?.stop()
        beat?.stop(); beat?.release(); beat = null
        torch?.off(); torch = null
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
