package com.example.taycancolorproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.media.MediaBrowserService

class Tone(val name: String, val c1: String, val c2: String)
class Family(val name: String, val tones: List<Tone>)

class ColorProxyService : MediaBrowserService() {

    private lateinit var session: MediaSession
    private var lastSourceMetadata: MediaMetadata? = null
    private var currentController: MediaController? = null
    private var currentCallback: MediaController.Callback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tickCount = 0

    /**
     * Rafraîchit périodiquement le PlaybackState pour réinitialiser
     * le timer interne ACTIVE_BUFFER d'Android (30 secondes).
     */
    private val refreshSessionRunnable = object : Runnable {
        override fun run() {
            try {
                val currentState = currentController?.playbackState?.state
                    ?: PlaybackState.STATE_PAUSED
                val position = currentController?.playbackState?.position ?: 0L
                session.setPlaybackState(buildState(currentState, position))
                session.isActive = true
            } catch (_: Exception) {
            }
            // Une ligne de journal par minute (toutes les 6 impulsions)
            tickCount++
            if (tickCount % 6 == 0) {
                Logger.log("TICK ordre des sessions : ${sessionsSnapshot()}")
            }
            // Re-programme dans 10 secondes
            mainHandler.postDelayed(this, 10_000L)
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "taycan_proxy"

        private var instance: ColorProxyService? = null
        private var pendingController: MediaController? = null

        fun attachSourceController(sourceController: MediaController) {
            val s = instance
            if (s != null) s.attach(sourceController) else pendingController = sourceController
        }

        private fun hsv(h: Float, s: Float, v: Float): String {
            val hue = ((h % 360f) + 360f) % 360f
            val c = Color.HSVToColor(floatArrayOf(hue, s, v))
            return String.format("#%06X", 0xFFFFFF and c)
        }

        private fun makeFamily(name: String, hue: Float): Family {
            val defs = listOf(
                Triple("Très clair", 0.30f, 1.00f),
                Triple("Clair", 0.50f, 1.00f),
                Triple("Doux", 0.70f, 1.00f),
                Triple("Vif", 1.00f, 1.00f),
                Triple("Intense", 1.00f, 0.85f),
                Triple("Profond", 1.00f, 0.65f),
                Triple("Foncé", 0.90f, 0.45f),
                Triple("Très foncé", 0.85f, 0.28f)
            )
            val tones = defs.map { (label, s, v) ->
                Tone("$name - $label", hsv(hue - 8f, s, v), hsv(hue + 8f, s, v))
            }
            return Family(name, tones)
        }

        val families: List<Family> = listOf(
            makeFamily("Rose", 330f),
            makeFamily("Rouge", 0f),
            makeFamily("Orange", 25f),
            makeFamily("Jaune", 50f),
            makeFamily("Vert", 130f),
            makeFamily("Turquoise", 175f),
            makeFamily("Bleu", 215f),
            makeFamily("Mauve", 275f),
            Family("Neutres", listOf(
                Tone("Neutres - Blanc", "#FFFFFF", "#E6E6E6"),
                Tone("Neutres - Gris clair", "#E0E0E0", "#BDBDBD"),
                Tone("Neutres - Argent", "#B0B0B0", "#E8E8E8"),
                Tone("Neutres - Gris", "#8A8A8A", "#5E5E5E"),
                Tone("Neutres - Anthracite", "#434343", "#2B2B2B"),
                Tone("Neutres - Noir", "#1A1A1A", "#000000")
            )),
            Family("Multicolores", listOf(
                Tone("Multicolores - Rose et violet", "#FF3DBB", "#8A2BE2"),
                Tone("Multicolores - Aurore", "#00C9A7", "#845EC2"),
                Tone("Multicolores - Sunset", "#FF512F", "#F09819"),
                Tone("Multicolores - Océan", "#2193B0", "#6DD5ED"),
                Tone("Multicolores - Néon", "#00FFFF", "#FF00CC"),
                Tone("Multicolores - Lave", "#FF4E00", "#EC9F05"),
                Tone("Multicolores - Forêt", "#134E5E", "#71B280"),
                Tone("Multicolores - Bonbon", "#FF9A9E", "#A18CD1")
            ))
        )
    }

    // ---------- Outils de journal ----------

    private fun shortPkg(pkg: String): String = when {
        pkg == packageName -> "PROXY"
        pkg.contains("deezer", ignoreCase = true) -> "DEEZER"
        else -> pkg.substringAfterLast('.')
    }

    /** Ordre actuel des sessions média, de la plus prioritaire à la moins prioritaire. */
    private fun sessionsSnapshot(): String {
        return try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = manager.getActiveSessions(ComponentName(this, NotificationListener::class.java))
            if (list.isEmpty()) "(aucune)"
            else list.joinToString(" > ") { "${shortPkg(it.packageName)}(${it.playbackState?.state})" }
        } catch (e: Exception) {
            "? (${e.message})"
        }
    }

    /** Écrit l'ordre des sessions 1 s plus tard, pour voir qui est devant après un événement. */
    private fun logOrderLater(tag: String) {
        mainHandler.postDelayed({
            Logger.log("ORDRE +1s après $tag : ${sessionsSnapshot()}")
        }, 1000L)
    }

    // ---------- Cycle de vie ----------

    override fun onCreate() {
        super.onCreate()
        instance = this
        Logger.init(applicationContext)
        Logger.log("SVC onCreate")

        // La session est créée AVANT startAsForeground(),
        // car la notification média utilise session.sessionToken.
        session = MediaSession(this, "TaycanColorProxy")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
            Media
