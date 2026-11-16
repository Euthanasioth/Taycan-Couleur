package com.example.taycancolorproxy

import android.content.ComponentName
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
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.util.Log

class Tone(val name: String, val c1: String, val c2: String)
class Family(val name: String, val tones: List<Tone>)

class ColorProxyService : MediaBrowserService() {

    private val tag = "TaycanColorProxy"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val silent = SilentPlayer()

    private lateinit var session: MediaSession
    private lateinit var mediaSessionManager: MediaSessionManager

    private var currentController: MediaController? = null
    private var currentControllerCallback: MediaController.Callback? = null
    private var lastSourceMetadata: MediaMetadata? = null
    private var lastReassertAt = 0L

    private val stopSilentRunnable = Runnable {
        silent.stop()
    }

    private val silentSafetyRunnable = Runnable {
        if (currentController?.playbackState?.state != PlaybackState.STATE_PLAYING) {
            silent.stop()
        }
    }

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            val deezer = controllers
                ?.firstOrNull { it.packageName == DEEZER_PACKAGE }

            if (deezer != null) {
                attach(deezer)
            }
        }

    companion object {
        private const val DEEZER_PACKAGE = "deezer.android.app"
        private const val PAUSE_KEEP_MS = 5 * 60 * 1000L
        private const val STOP_KEEP_MS = 60 * 1000L
        private const val PRE_KEEP_MS = 3 * 60 * 1000L

        private var instance: ColorProxyService? = null
        private var pendingController: MediaController? = null

        fun attachSourceController(sourceController: MediaController) {
            val service = instance
            if (service != null) {
                service.attach(sourceController)
            } else {
                pendingController = sourceController
            }
        }

        private fun hsv(hue: Float, saturation: Float, value: Float): String {
            val normalizedHue = ((hue % 360f) + 360f) % 360f
            val color = Color.HSVToColor(
                floatArrayOf(normalizedHue, saturation, value)
            )
            return String.format("#%06X", 0xFFFFFF and color)
        }

        private fun makeFamily(name: String, hue: Float): Family {
            val definitions = listOf(
                Triple("Très clair", 0.30f, 1.00f),
                Triple("Clair", 0.50f, 1.00f),
                Triple("Doux", 0.70f, 1.00f),
                Triple("Vif", 1.00f, 1.00f),
                Triple("Intense", 1.00f, 0.85f),
                Triple("Profond", 1.00f, 0.65f),
                Triple("Foncé", 0.90f, 0.45f),
                Triple("Très foncé", 0.85f, 0.28f)
            )

            val tones = definitions.map { (label, saturation, value) ->
                Tone(
                    "$name - $label",
                    hsv(hue - 8f, saturation, value),
                    hsv(hue + 8f, saturation, value)
                )
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
            Family(
                "Neutres",
                listOf(
                    Tone("Neutres - Blanc", "#FFFFFF", "#E6E6E6"),
                    Tone("Neutres - Gris clair", "#E0E0E0", "#BDBDBD"),
                    Tone("Neutres - Argent", "#B0B0B0", "#E8E8E8"),
                    Tone("Neutres - Gris", "#8A8A8A", "#5E5E5E"),
                    Tone("Neutres - Anthracite", "#434343", "#2B2B2B"),
                    Tone("Neutres - Noir", "#1A1A1A", "#000000")
                )
            ),
            Family(
                "Multicolores",
                listOf(
                    Tone("Multicolores - Rose et violet", "#FF3DBB", "#8A2BE2"),
                    Tone("Multicolores - Aurore", "#00C9A7", "#845EC2"),
                    Tone("Multicolores - Sunset", "#FF512F", "#F09819"),
                    Tone("Multicolores - Océan", "#2193B0", "#6DD5ED"),
                    Tone("Multicolores - Néon", "#00FFFF", "#FF00CC"),
                    Tone("Multicolores - Lave", "#FF4E00", "#EC9F05"),
                    Tone("Multicolores - Forêt", "#134E5E", "#71B280"),
                    Tone("Multicolores - Bonbon", "#FF9A9E", "#A18CD1")
                )
            )
        )
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        session = MediaSession(this, "TaycanColorProxy")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        sessionToken = session.sessionToken
        session.isActive = true

        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                Log.d(tag, "Proxy session onPlay()")
                currentController?.transportControls?.play()
                scheduleReassertBurst()
            }

            override fun onPause() {
                Log.d(tag, "Proxy session onPause()")
                currentController?.transportControls?.pause()
                scheduleReassertBurst()
            }

            override fun onSkipToNext() {
                Log.d(tag, "Proxy session onSkipToNext()")
                currentController?.transportControls?.skipToNext()
                scheduleReassertBurst()
            }

            override fun onSkipToPrevious() {
                Log.d(tag, "Proxy session onSkipToPrevious()")
                currentController?.transportControls?.skipToPrevious()
                scheduleReassertBurst()
            }

            override fun onStop() {
                Log.d(tag, "Proxy session onStop()")
                currentController?.transportControls?.stop()
                scheduleReassertBurst()
            }

            override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                Log.d(tag, "Proxy session onPlayFromMediaId($mediaId)")
                selectColor(mediaId)
            }
        })

        session.setPlaybackState(
            buildState(PlaybackState.STATE_PAUSED, 0L)
        )

        mediaSessionManager =
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        val notificationListenerComponent =
            ComponentName(this, NotificationListener::class.java)

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsListener,
                notificationListenerComponent,
                mainHandler
            )
        } catch (e: Exception) {
            Log.e(tag, "Could not register active-sessions listener", e)
        }

        pendingController?.let { controller ->
            attach(controller)
            pendingController = null
        }

        silent.start()
        scheduleSilentSafetyCheck()
        findDeezerNow()
    }

    private fun scheduleReassertBurst() {
    mainHandler.post { reassert() }
    mainHandler.postDelayed({ reassert() }, 250L)
    mainHandler.postDelayed({ reassert() }, 750L)
    mainHandler.postDelayed({ reassert() }, 1500L)
    mainHandler.postDelayed({ reassert() }, 2500L)
}


    private fun scheduleSilentSafetyCheck() {
        mainHandler.removeCallbacks(silentSafetyRunnable)
        mainHandler.postDelayed(silentSafetyRunnable, PRE_KEEP_MS)
    }

    private fun findDeezerNow() {
        try {
            val listenerComponent =
                ComponentName(this, NotificationListener::class.java)

            val deezer = mediaSessionManager
                .getActiveSessions(listenerComponent)
                .firstOrNull { it.packageName == DEEZER_PACKAGE }

            if (deezer != null) {
                attach(deezer)
            }
        } catch (e: Exception) {
            Log.e(tag, "Could not find Deezer session", e)
        }
    }

    private fun reassert() {
    val now = SystemClock.elapsedRealtime()

    if (now - lastReassertAt < 500L) return
    lastReassertAt = now

    val controller = currentController ?: return
    val sourceState = controller.playbackState
    val state = sourceState?.state ?: PlaybackState.STATE_PAUSED
    var position = sourceState?.position ?: 0L

    if (sourceState != null && state == PlaybackState.STATE_PLAYING) {
        val elapsed =
            SystemClock.elapsedRealtime() - sourceState.lastPositionUpdateTime
        position += (elapsed * sourceState.playbackSpeed).toLong()
    }

    session.setPlaybackState(buildState(state, position))
    updateMetadata(controller.metadata)
}


    private fun attach(sourceController: MediaController) {
        if (currentController?.sessionToken == sourceController.sessionToken) {
            updateMetadata(sourceController.metadata)
            return
        }

        currentControllerCallback?.let { callback ->
            currentController?.unregisterCallback(callback)
        }

        currentController = sourceController

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
    Log.d(tag, "onMetadataChanged: ${metadata?.description?.title}")
    updateMetadata(metadata)
    scheduleReassertBurst()
}


            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val currentState = state?.state ?: PlaybackState.STATE_PAUSED
                val position = state?.position ?:0L
                session.setPlaybackState(buildState(currentState, position))

updateMetadata(lastSourceMetadata)
scheduleReassertBurst()

when (currentState) {
    PlaybackState.STATE_PLAYING -> {
        mainHandler.removeCallbacks(stopSilentRunnable)
        mainHandler.removeCallbacks(silentSafetyRunnable)
        silent.start()
    }

    PlaybackState.STATE_PAUSED -> {
        mainHandler.removeCallbacks(stopSilentRunnable)
        mainHandler.postDelayed(
            stopSilentRunnable,
            PAUSE_KEEP_MS
        )
    }

    PlaybackState.STATE_STOPPED,
    PlaybackState.STATE_NONE -> {
        mainHandler.removeCallbacks(stopSilentRunnable)
        mainHandler.postDelayed(
            stopSilentRunnable,
            STOP_KEEP_MS
        )
    }
}
}


        sourceController.registerCallback(callback)
        currentControllerCallback = callback

        updateMetadata(sourceController.metadata)

        val sourceState = sourceController.playbackState
        val state = sourceState?.state ?: PlaybackState.STATE_PAUSED
        val position = sourceState?.position ?: 0L
        session.setPlaybackState(buildState(state, position))

        silent.start()
        scheduleSilentSafetyCheck()
    }

    private fun applyColor(color1: String, color2: String) {
        getSharedPreferences("taycan_couleur", MODE_PRIVATE)
            .edit()
            .putString("color1", color1)
            .putString("color2", color2)
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
        val tone = families
            .getOrNull(familyIndex)
            ?.tones
            ?.getOrNull(toneIndex)
            ?: return

        applyColor(tone.c1, tone.c2)
    }

    private fun buildState(state: Int, position: Long): PlaybackState {
        return PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
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
            .putString(
                MediaMetadata.METADATA_KEY_TITLE,
                source.getString(MediaMetadata.METADATA_KEY_TITLE)
            )
            .putString(
                MediaMetadata.METADATA_KEY_ARTIST,
                source.getString(MediaMetadata.METADATA_KEY_ARTIST)
            )
            .putString(
                MediaMetadata.METADATA_KEY_ALBUM,
                source.getString(MediaMetadata.METADATA_KEY_ALBUM)
            )
            .putLong(
                MediaMetadata.METADATA_KEY_DURATION,
                source.getLong(MediaMetadata.METADATA_KEY_DURATION)
            )
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            .putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork)

        session.setMetadata(builder.build())
    }

    private fun makeColorArtwork(width: Int, height: Int): Bitmap {
        val preferences = getSharedPreferences("taycan_couleur", MODE_PRIVATE)
        val color1 = preferences.getString("color1", "#FF3DBB") ?: "#FF3DBB"
        val color2 = preferences.getString("color2", "#8A2BE2") ?: "#8A2BE2"
        return makeGradientBitmap(width, height, color1, color2)
    }

    private fun makeGradientBitmap(
        width: Int,
        height: Int,
        color1: String,
        color2: String
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()

        paint.shader = android.graphics.LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Color.parseColor(color1),
            Color.parseColor(color2),
            android.graphics.Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        reassert()

        val extras = Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2)
        }

        return BrowserRoot("root", extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        val items = mutableListOf<MediaBrowser.MediaItem>()

        if (parentId == "root") {
            val sample = families[0].tones[3]
            val icon = makeGradientBitmap(200, 200, sample.c1, sample.c2)

            val description = MediaDescription.Builder()
                .setMediaId("tab_colors")
                .setTitle("Couleurs")
                .setIconBitmap(icon)
                .build()

            items.add(
                MediaBrowser.MediaItem(
                    description,
                    MediaBrowser.MediaItem.FLAG_BROWSABLE
                )
            )
        } else if (parentId == "tab_colors") {
            val randomIcon =
                makeGradientBitmap(200, 200, "#00FFFF", "#FF00CC")

            val randomDescription = MediaDescription.Builder()
                .setMediaId("random")
                .setTitle("Aléatoire")
                .setIconBitmap(randomIcon)
                .build()

            items.add(
                MediaBrowser.MediaItem(
                    randomDescription,
                    MediaBrowser.MediaItem.FLAG_PLAYABLE
                )
            )

            families.forEachIndexed { index, family ->
                val sample = family.tones[
                    minOf(3, family.tones.size - 1)
                ]
                val icon = makeGradientBitmap(
                    200,
                    200,
                    sample.c1,
                    sample.c2
                )

                val description = MediaDescription.Builder()
                    .setMediaId("fam_$index")
                    .setTitle(family.name)
                    .setIconBitmap(icon)
                    .build()

                items.add(
                    MediaBrowser.MediaItem(
                        description,
                        MediaBrowser.MediaItem.FLAG_BROWSABLE
                    )
                )
            }
        } else if (parentId.startsWith("fam_")) {
            val familyIndex =
                parentId.removePrefix("fam_").toIntOrNull()
            val family =
                familyIndex?.let { families.getOrNull(it) }

            if (family != null && familyIndex != null) {
                family.tones.forEachIndexed { toneIndex, tone ->
                    val icon = makeGradientBitmap(
                        200,
                        200,
                        tone.c1,
                        tone.c2
                    )

                    val description = MediaDescription.Builder()
                        .setMediaId("col_${familyIndex}_$toneIndex")
                        .setTitle(tone.name)
                        .setIconBitmap(icon)
                        .build()

                    items.add(
                        MediaBrowser.MediaItem(
                            description,
                            MediaBrowser.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
            }
        }

        result.sendResult(items)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        silent.stop()

        currentControllerCallback?.let { callback ->
            currentController?.unregisterCallback(callback)
        }

        if (::mediaSessionManager.isInitialized) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(
                    activeSessionsListener
                )
            } catch (e: Exception) {
                Log.w(tag, "Could not unregister active-sessions listener", e)
            }
        }

        session.release()
        instance = null

        super.onDestroy()
    }
}
