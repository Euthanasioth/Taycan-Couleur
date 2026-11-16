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
import android.service.media.MediaBrowserService

class Tone(val name: String, val c1: String, val c2: String)
class Family(val name: String, val tones: List<Tone>)

class ColorProxyService : MediaBrowserService() {

    private lateinit var session: MediaSession
    private var lastSourceMetadata: MediaMetadata? = null
    private var currentController: MediaController? = null
    private var currentCallback: MediaController.Callback? = null
    private var lastReassert = 0L
    private var cachedKey: String? = null
    private var cachedBitmap: Bitmap? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopHandler = Handler(Looper.getMainLooper())
    private val silent = SilentPlayer()

    private val antiDeezerRunnable = object : Runnable {
        override fun run() {
            reassertVisuals()
            handler.postDelayed(this, 1000L)
        }
    }

    companion object {
        private const val PAUSE_KEEP_MS = 10 * 60 * 1000L
        private const val STOP_KEEP_MS = 60 * 1000L
        private const val PRE_KEEP_MS = 3 * 60 * 1000L

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

    override fun onCreate() {
        super.onCreate()
        instance = this
        Logger.init(this)
        Logger.log("service créé")

        session = MediaSession(this, "TaycanColorProxy")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        sessionToken = session.sessionToken
        session.isActive = true

        session.setCallback(object : MediaSession.Callback() {
            override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                selectColor(mediaId)
            }
        })

        session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED, 0))

        pendingController?.let {
            attach(it)
            pendingController = null
        }
        findDeezerNow()

        silent.start()
        armSilentSafety()

        handler.postDelayed(antiDeezerRunnable, 1000L)
    }

    private fun armSilentSafety() {
        stopHandler.postDelayed({
            val st = currentController?.playbackState?.state
            if (st != PlaybackState.STATE_PLAYING) silent.stop()
        }, PRE_KEEP_MS)
    }

    private fun findDeezerNow() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, NotificationListener::class.java)
            val deezer = manager.getActiveSessions(component)
                .firstOrNull { it.packageName == "deezer.android.app" }
            if (deezer != null) attach(deezer)
        } catch (e: Exception) {
        }
    }

    private fun reassert() {
        val now = System.currentTimeMillis()
        if (now - lastReassert < 3000) return
        lastReassert = now
        reassertVisuals()
    }

    private fun reassertVisuals() {
        if (currentController == null) findDeezerNow()
        val c = currentController
        val st = c?.playbackState
        val s = st?.state ?: PlaybackState.STATE_PAUSED
        var p: Long = st?.position ?: 0L
        if (st != null && s == PlaybackState.STATE_PLAYING) {
            p += ((android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        
        session.setPlaybackState(buildState(s, p))
        updateMetadata(c?.metadata ?: lastSourceMetadata)
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
        if (currentController?.sessionToken == sourceController.sessionToken) return
        Logger.log("rattaché à Deezer")
        currentCallback?.let { currentController?.unregisterCallback(it) }
        currentController = sourceController

        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                Logger.log("commande voiture : lecture")
                silent.start()
                sourceController.transportControls.play()
            }
            override fun onPause() {
                Logger.log("commande voiture : pause")
                sourceController.transportControls.pause()
                updateMetadata(currentController?.metadata)
            }
            override fun onSkipToNext() {
                Logger.log("commande voiture : suivant")
                silent.start()
                sourceController.transportControls.skipToNext()
            }
            override fun onSkipToPrevious() {
                Logger.log("commande voiture : précédent")
                silent.start()
                sourceController.transportControls.skipToPrevious()
            }
            override fun onStop() { sourceController.transportControls.stop() }
            override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                selectColor(mediaId)
            }
        })

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateMetadata(currentController?.metadata)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val s = state?.state ?: PlaybackState.STATE_PAUSED
                val p: Long = state?.position ?: 0L
                Logger.log("Deezer : état=$s")
                
                session.setPlaybackState(buildState(s, p))
                updateMetadata(currentController?.metadata)

                when (s) {
                    PlaybackState.STATE_PLAYING -> {
                        stopHandler.removeCallbacksAndMessages(null)
                        silent.start()
                    }
                    PlaybackState.STATE_PAUSED -> {
                        stopHandler.removeCallbacksAndMessages(null)
                        updateMetadata(currentController?.metadata)
                        handler.postDelayed({ updateMetadata(currentController?.metadata) }, 200L)
                        stopHandler.postDelayed({ silent.stop() }, PAUSE_KEEP_MS)
                    }
                    PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE -> {
                        stopHandler.removeCallbacksAndMessages(null)
                        stopHandler.postDelayed({ silent.stop() }, STOP_KEEP_MS)
                    }
                    else -> {
                        stopHandler.removeCallbacksAndMessages(null)
                    }
                }
            }
        }
        sourceController.registerCallback(callback)
        currentCallback = callback

        updateMetadata(sourceController.metadata)
        val currentState = sourceController.playbackState
        val s0 = currentState?.state ?: PlaybackState.STATE_PAUSED
        val p0: Long = currentState?.position ?: 0L
        session.setPlaybackState(buildState(s0, p0))

        silent.start()
        armSilentSafety()
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
        if (source != null) {
            lastSourceMetadata = source
        }
        val fallbackSource = lastSourceMetadata

        val artwork = makeColorArtwork(1000, 1000)

        val builder = MediaMetadata.Builder()
        builder.putString(MediaMetadata.METADATA_KEY_TITLE, fallbackSource?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Ambiance Taycan")
        builder.putString(MediaMetadata.METADATA_KEY_ARTIST, fallbackSource?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Contrôle Couleur")
        builder.putString(MediaMetadata.METADATA_KEY_ALBUM, fallbackSource?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "Personnalisation")
        builder.putLong(MediaMetadata.METADATA_KEY_DURATION, fallbackSource?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L)
        builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
        builder.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
        builder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork)

        session.setMetadata(builder.build())
    }

    private fun makeColorArtwork(width: Int, height: Int): Bitmap {
        val prefs = getSharedPreferences("taycan_couleur", MODE_PRIVATE)
        val c1 = prefs.getString("color1", "#FF3DBB")!!
        val c2 = prefs.getString("color2", "#8A2BE2")!!
        val key = "$c1|$c2|$width|$height"
        val cached = cachedBitmap
        if (cached != null && cachedKey == key) return cached
        val b = makeGradientBitmap(width, height, c1, c2)
        cachedBitmap = b
        cachedKey = key
        return b
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
        Logger.log("onGetRoot depuis $clientPackageName")
        reassert()
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

    override fun onDestroy() {
        super.onDestroy()
        Logger.log("service détruit")
        handler.removeCallbacksAndMessages(null)
        stopHandler.removeCallbacksAndMessages(null)
        silent.stop()
        currentCallback?.let { currentController?.unregisterCallback(it) }
        session.release()
        cachedBitmap = null
        instance = null
    }
}
