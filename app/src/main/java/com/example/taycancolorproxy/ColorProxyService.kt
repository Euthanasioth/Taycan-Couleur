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
    private var reclaimToken = 0
    private val silentPlayer = SilentPlayer()

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
            tickCount++
            if (tickCount % 6 == 0) {
                Logger.log("TICK ordre des sessions : ${sessionsSnapshot()}")
            }
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

    // ---------- Reprise de priorité ----------

    /**
     * Android remonte une session en tête quand son état passe de "inactif" à "actif".
     * Quand Deezer passe en lecture, on met donc notre session en pause un court instant,
     * puis on la repasse en lecture : cette transition arrive APRÈS celle de Deezer.
     */
    private fun reclaimPriority(state: Int, position: Long, why: String) {
        val token = ++reclaimToken
        try {
            session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED, position))
        } catch (_: Exception) {
        }
        mainHandler.postDelayed({
            if (token == reclaimToken) {
                try {
                    session.setPlaybackState(buildState(state, position))
                    session.isActive = true
                } catch (_: Exception) {
                }
                Logger.log("RECLAIM ($why) : ordre ${sessionsSnapshot()}")
            }
        }, 40L)
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
            MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        sessionToken = session.sessionToken
        session.isActive = true

        startAsForeground()

        // Son silencieux continu : notre appli produit réellement de l'audio
        try {
            silentPlayer.start()
            Logger.log("SilentPlayer démarré")
        } catch (e: Exception) {
            Logger.log("SilentPlayer ÉCHEC : ${e.message}")
        }

        installSessionCallback()

        session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED, 0))

        pendingController?.let {
            attach(it)
            pendingController = null
        }
        findDeezerNow()
        updateNotification()

        Logger.log("SVC prêt, ordre des sessions : ${sessionsSnapshot()}")

        mainHandler.removeCallbacks(refreshSessionRunnable)
        mainHandler.postDelayed(refreshSessionRunnable, 10_000L)
    }

    override fun onDestroy() {
        Logger.log("SVC onDestroy")
        super.onDestroy()
        mainHandler.removeCallbacks(refreshSessionRunnable)
        silentPlayer.stop()
        currentCallback?.let { currentController?.unregisterCallback(it) }
        session.release()
        instance = null
    }

    private fun installSessionCallback() {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                Logger.log("CMD reçue : PLAY | ordre : ${sessionsSnapshot()}")
                ensureController()?.transportControls?.play()
                logOrderLater("CMD PLAY")
            }

            override fun onPause() {
                Logger.log("CMD reçue : PAUSE | ordre : ${sessionsSnapshot()}")
                ensureController()?.transportControls?.pause()
                logOrderLater("CMD PAUSE")
            }

            override fun onSkipToNext() {
                Logger.log("CMD reçue : SUIVANT | ordre : ${sessionsSnapshot()}")
                ensureController()?.transportControls?.skipToNext()
                logOrderLater("CMD SUIVANT")
            }

            override fun onSkipToPrevious() {
                Logger.log("CMD reçue : PRÉCÉDENT | ordre : ${sessionsSnapshot()}")
                ensureController()?.transportControls?.skipToPrevious()
                logOrderLater("CMD PRÉCÉDENT")
            }

            override fun onStop() {
                Logger.log("CMD reçue : STOP")
                ensureController()?.transportControls?.stop()
            }

            override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                Logger.log("CMD reçue : choix couleur $mediaId")
                selectColor(mediaId)
            }
        })
    }

    private fun ensureController(): MediaController? {
        val c = currentController
        if (c != null) return c
        Logger.log("ensureController : pas de contrôleur Deezer, recherche")
        findDeezerNow()
        return currentController
    }

    /**
     * startForeground() doit TOUJOURS être appelé, sinon Android tue toute l'appli.
     * Plan A : notification média. Plan B : notification simple. Plan C : arrêt propre.
     */
    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Taycan Couleur", NotificationManager.IMPORTANCE_LOW)
        )
        try {
            startForeground(
                NOTIF_ID,
                buildMediaNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            Logger.log("startForeground OK (notification média)")
        } catch (e: Exception) {
            Logger.log("startForeground ÉCHEC (média) : ${e::class.java.simpleName} ${e.message}")
            try {
                val simple = Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Taycan Couleur")
                    .setContentText("Proxy actif")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .build()
                startForeground(NOTIF_ID, simple)
                Logger.log("startForeground OK (notification simple)")
            } catch (e2: Exception) {
                Logger.log("startForeground ÉCHEC (simple) : ${e2::class.java.simpleName} ${e2.message}")
                stopSelf()
            }
        }
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildMediaNotification())
        } catch (e: Exception) {
            Logger.log("Erreur updateNotification : ${e.message}")
        }
    }

    private fun buildMediaNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Taycan Couleur")
            .setContentText("Proxy de couleur actif")
            .setSmallIcon(R.drawable.app_icon)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        val style = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
        builder.setStyle(style)

        builder.addAction(
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                "Précédent",
                mediaActionPending(PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            ).build()
        )

        val isPlaying = currentController?.playbackState?.state == PlaybackState.STATE_PLAYING
        builder.addAction(
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(
                    this,
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                ),
                if (isPlaying) "Pause" else "Lecture",
                mediaActionPending(if (isPlaying) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY)
            ).build()
        )

        builder.addAction(
            Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next),
                "Suivant",
                mediaActionPending(PlaybackState.ACTION_SKIP_TO_NEXT)
            ).build()
        )

        return builder.build()
    }

    private fun mediaActionPending(action: Long): PendingIntent {
        val intent = Intent(this, ColorProxyService::class.java).apply {
            this.action = ACTION_MEDIA_BUTTON
            putExtra(EXTRA_MEDIA_ACTION, action)
        }
        return PendingIntent.getService(
            this,
            action.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("onStartCommand appelé (action=${intent?.action})")
        if (intent?.action == ACTION_MEDIA_BUTTON) {
            val action = intent.getLongExtra(EXTRA_MEDIA_ACTION, -1L)
            when (action) {
                PlaybackState.ACTION_PLAY -> ensureController()?.transportControls?.play()
                PlaybackState.ACTION_PAUSE -> ensureController()?.transportControls?.pause()
                PlaybackState.ACTION_SKIP_TO_NEXT -> ensureController()?.transportControls?.skipToNext()
                PlaybackState.ACTION_SKIP_TO_PREVIOUS -> ensureController()?.transportControls?.skipToPrevious()
            }
        }
        // START_STICKY : Android relance le service s'il le tue
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.log("onTaskRemoved : l'utilisateur a fermé la tâche de l'app")
        super.onTaskRemoved(rootIntent)
        // On laisse le service tourner : ne rien faire ici
    }

    private fun findDeezerNow() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, NotificationListener::class.java)
            val deezer = manager.getActiveSessions(component)
                .firstOrNull { it.packageName == "deezer.android.app" }
            if (deezer != null) attach(deezer)
        } catch (_: Exception) {
        }
    }

    private fun applyColor(c1: String, c2: String) {
        getSharedPreferences("taycan_couleur", MODE_PRIVATE).edit()
            .putString("color1", c1)
            .putString("color2", c2)
            .apply()
        updateMetadata(lastSourceMetadata)
    }

    private fun selectColor(mediaId: String) {
        if (mediaId == "random") {
            val tone = families.random().tones.random()
            applyColor(tone.c1, tone.c2)
            return
        }
        if (!mediaId.startsWith("col_")) return
        val parts = mediaId.split("_")
        val familyIndex = parts.getOrNull(1)?.toIntOrNull() ?: return
        val toneIndex = parts.getOrNull(2)?.toIntOrNull() ?: return
        val tone = families.getOrNull(familyIndex)?.tones?.getOrNull(toneIndex) ?: return
        applyColor(tone.c1, tone.c2)
    }

    private fun attach(sourceController: MediaController) {
        if (currentController?.sessionToken == sourceController.sessionToken) {
            session.isActive = true
            return
        }

        Logger.log("attach : nouveau contrôleur ${shortPkg(sourceController.packageName)}")

        currentCallback?.let { currentController?.unregisterCallback(it) }
        currentController = sourceController

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                val titre = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                Logger.log("DEEZER titre changé : $titre | ordre : ${sessionsSnapshot()}")
                updateMetadata(metadata)
                logOrderLater("changement de titre")
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val deezerState = state?.state ?: PlaybackState.STATE_PAUSED
                val position = state?.position ?: 0L
                Logger.log(
                    "DEEZER état=$deezerState pos=$position vitesse=${state?.playbackSpeed} " +
                        "| ordre : ${sessionsSnapshot()}"
                )

                if (deezerState == PlaybackState.STATE_PLAYING ||
                    deezerState == PlaybackState.STATE_BUFFERING) {
                    // Deezer vient de passer en lecture : on reprend la tête de pile
                    reclaimPriority(deezerState, position, "état Deezer $deezerState")
                } else {
                    session.setPlaybackState(buildState(deezerState, position))
                    session.isActive = true
                }
                updateNotification()
                logOrderLater("état Deezer $deezerState")
            }

            override fun onSessionDestroyed() {
                Logger.log("DEEZER session détruite | ordre : ${sessionsSnapshot()}")
            }
        }
        sourceController.registerCallback(callback)
        currentCallback = callback

        updateMetadata(sourceController.metadata)
        val currentState = sourceController.playbackState
        session.setPlaybackState(
            buildState(
                currentState?.state ?: PlaybackState.STATE_PAUSED,
                currentState?.position ?: 0
            )
        )
        session.isActive = true
        updateNotification()
    }

    private fun buildState(state: Int, position: Long): PlaybackState {
        return PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
            )
            .setState(state, position, 1f)
            .build()
    }

    private fun updateMetadata(source: MediaMetadata?) {
        if (source == null) return
        lastSourceMetadata = source

        val artwork = makeColorArtwork(1000, 1000)

        val builder = MediaMetadata.Builder()
        builder.putString(MediaMetadata.METADATA_KEY_TITLE, source.getString(MediaMetadata.METADATA_KEY_TITLE))
        builder.putString(MediaMetadata.METADATA_KEY_ARTIST, source.getString(MediaMetadata.METADATA_KEY_ARTIST))
        builder.putString(MediaMetadata.METADATA_KEY_ALBUM, source.getString(MediaMetadata.METADATA_KEY_ALBUM))
        builder.putLong(MediaMetadata.METADATA_KEY_DURATION, source.getLong(MediaMetadata.METADATA_KEY_DURATION))
        builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
        builder.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)

        session.setMetadata(builder.build())
    }

    private fun makeColorArtwork(width: Int, height: Int): Bitmap {
        val prefs = getSharedPreferences("taycan_couleur", MODE_PRIVATE)
        val c1 = prefs.getString("color1", "#FF3DBB")!!
        val c2 = prefs.getString("color2", "#8A2BE2")!!
        return makeGradientBitmap(width, height, c1, c2)
    }

    private fun makeGradientBitmap(width: Int, height: Int, c1: String, c2: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()

        val gradient = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor(c1), Color.parseColor(c2),
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        return bitmap
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Logger.log("AA onGetRoot : client=$clientPackageName uid=$clientUid")
        val extras = Bundle()
        extras.putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
        extras.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
        extras.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
        return BrowserRoot("root", extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        Logger.log("AA onLoadChildren : parent=$parentId")
        val items = mutableListOf<MediaBrowser.MediaItem>()

        if (parentId == "root") {
            val sample = families[0].tones[3]
            val icon = makeGradientBitmap(200, 200, sample.c1, sample.c2)
            val desc = MediaDescription.Builder()
                .setMediaId("tab_colors")
                .setTitle("Couleurs")
                .setIconBitmap(icon)
                .build()
            items.add(MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_BROWSABLE))
        } else if (parentId == "tab_colors") {
            val randomIcon = makeGradientBitmap(200, 200, "#00FFFF", "#FF00CC")
            val randomDesc = MediaDescription.Builder()
                .setMediaId("random")
                .setTitle("Aléatoire")
                .setIconBitmap(randomIcon)
                .build()
            items.add(MediaBrowser.MediaItem(randomDesc, MediaBrowser.MediaItem.FLAG_PLAYABLE))

            families.forEachIndexed { index, family ->
                val sample = family.tones[minOf(3, family.tones.size - 1)]
                val icon = makeGradientBitmap(200, 200, sample.c1, sample.c2)
                val desc = MediaDescription.Builder()
                    .setMediaId("fam_$index")
                    .setTitle(family.name)
                    .setIconBitmap(icon)
                    .build()
                items.add(MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_BROWSABLE))
            }
        } else if (parentId.startsWith("fam_")) {
            val familyIndex = parentId.removePrefix("fam_").toIntOrNull()
            val family = if (familyIndex != null) families.getOrNull(familyIndex) else null
            if (family != null) {
                family.tones.forEachIndexed { toneIndex, tone ->
                    val icon = makeGradientBitmap(200, 200, tone.c1, tone.c2)
                    val desc = MediaDescription.Builder()
                        .setMediaId("col_${familyIndex}_$toneIndex")
                        .setTitle(tone.name)
                        .setIconBitmap(icon)
                        .build()
                    items.add(MediaBrowser.MediaItem(desc, MediaBrowser.MediaItem.FLAG_PLAYABLE))
                }
            }
        }

        result.sendResult(items)
    }
}

private const val ACTION_MEDIA_BUTTON = "com.example.taycancolorproxy.MEDIA_BUTTON"
private const val EXTRA_MEDIA_ACTION = "media_action"
