
package com.example.taycancolorproxy

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService

class Tone(val name: String, val c1: String, val c2: String)
class Family(val name: String, val tones: List<Tone>)

class ColorProxyService : MediaBrowserService() {

    private lateinit var session: MediaSession
    private lateinit var sessionManager: MediaSessionManager
    private lateinit var notificationComponent: ComponentName
    private lateinit var sessionsListener:
        MediaSessionManager.OnActiveSessionsChangedListener

    private var lastSourceMetadata: MediaMetadata? = null
    private var currentController: MediaController? = null
    private var currentCallback: MediaController.Callback? = null

    companion object {
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
                Tone(
                    "$name - $label",
                    hsv(hue - 8f, s, v),
                    hsv(hue + 8f, s, v)
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

        // Création de la session permanente du proxy
        session = MediaSession(this, "TaycanColorProxy")

        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        sessionToken = session.sessionToken

        // Toutes les commandes passent désormais par le contrôleur courant
        session.setCallback(object : MediaSession.Callback() {

            override fun onPlay() {
                sendCommand { it.transportControls.play() }
            }

            override fun onPause() {
                sendCommand { it.transportControls.pause() }
            }

            override fun onStop() {
                sendCommand { it.transportControls.stop() }
            }

            override fun onSkipToNext() {
                sendCommand { it.transportControls.skipToNext() }
            }

            override fun onSkipToPrevious() {
                sendCommand { it.transportControls.skipToPrevious() }
            }

            override fun onSeekTo(pos: Long) {
                sendCommand { it.transportControls.seekTo(pos) }
            }

            override fun onPlayFromMediaId(
                mediaId: String,
                extras: Bundle?
            ) {
                selectColor(mediaId)
            }
        })

        // Activation initiale
        session.isActive = true
        session.setPlaybackState(
            buildState(null)
        )

        // Surveillance des sessions multimédias
        sessionManager =
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        notificationComponent =
            ComponentName(this, NotificationListener::class.java)

        sessionsListener =
            MediaSessionManager.OnActiveSessionsChangedListener { controllers ->

                val deezer = controllers
                    ?.filter { it.packageName == "deezer.android.app" }
                    ?.firstOrNull {
                        it.playbackState?.state ==
                            PlaybackState.STATE_PLAYING
                    }
                    ?: controllers?.firstOrNull {
                        it.packageName == "deezer.android.app"
                    }

                if (deezer != null) {
                    attach(deezer)
                }
            }

        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsListener,
                notificationComponent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        pendingController?.let {
            attach(it)
            pendingController = null
        }

        findDeezerNow()
    }

    // Transmission sécurisée des commandes à Deezer
    private inline fun sendCommand(
        command: (MediaController) -> Unit
    ) {
        val controller = currentController ?: return

        try {
            command(controller)
        } catch (e: Exception) {
            e.printStackTrace()
            findDeezerNow()
        }
    }

    // Recherche de la session Deezer
    private fun findDeezerNow() {
        try {
            val deezer = sessionManager
                .getActiveSessions(notificationComponent)
                .firstOrNull {
                    it.packageName == "deezer.android.app" &&
                        it.playbackState?.state ==
                        PlaybackState.STATE_PLAYING
                }
                ?: sessionManager
                    .getActiveSessions(notificationComponent)
                    .firstOrNull {
                        it.packageName == "deezer.android.app"
                    }

            if (deezer != null) {
                attach(deezer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Attachement et surveillance du contrôleur Deezer
    private fun attach(sourceController: MediaController) {

        if (currentController?.sessionToken ==
            sourceController.sessionToken
        ) {
            updateMetadata(sourceController.metadata)
            syncPlaybackState(sourceController.playbackState)
            return
        }

        // Suppression de l'ancien callback
        currentCallback?.let { callback ->
            try {
                currentController?.unregisterCallback(callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        currentController = sourceController

        val attachedToken = sourceController.sessionToken

        val callback = object : MediaController.Callback() {

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                if (currentController?.sessionToken != attachedToken) return
                updateMetadata(metadata)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (currentController?.sessionToken != attachedToken) return
                syncPlaybackState(state)
            }

            override fun onSessionDestroyed() {
                if (currentController?.sessionToken != attachedToken) return

                currentCallback = null
                currentController = null

                findDeezerNow()
            }
        }

        currentCallback = callback

        try {
            sourceController.registerCallback(callback)

            updateMetadata(sourceController.metadata)
            syncPlaybackState(sourceController.playbackState)

            // Maintien de la session proxy
            session.isActive = true

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Synchronisation de l'état sans imposer une fausse lecture
    private fun syncPlaybackState(source: PlaybackState?) {
        session.setPlaybackState(buildState(source))
        session.isActive = true
    }

    private fun buildState(source: PlaybackState?): PlaybackState {

        val state = source?.state
            ?: PlaybackState.STATE_PAUSED

        val position = source?.position ?: 0L

        val speed = if (
            state == PlaybackState.STATE_PLAYING
        ) {
            source?.playbackSpeed ?: 1f
        } else {
            0f
        }

        val actions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackState.ACTION_SEEK_TO

        return PlaybackState.Builder()
            .setActions(actions)
            .setState(state, position, speed)
            .build()
    }

    // Gestion des couleurs
    private fun applyColor(c1: String, c2: String) {

        getSharedPreferences("taycan_couleur", MODE_PRIVATE)
            .edit()
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

        val familyIndex =
            parts.getOrNull(1)?.toIntOrNull() ?: return

        val toneIndex =
            parts.getOrNull(2)?.toIntOrNull() ?: return

        val tone = families
            .getOrNull(familyIndex)
            ?.tones
            ?.getOrNull(toneIndex) ?: return

        applyColor(tone.c1, tone.c2)
    }

    // Publication des métadonnées transformées
    private fun updateMetadata(source: MediaMetadata?) {

        if (source == null) return

        lastSourceMetadata = source

        val builder = MediaMetadata.Builder()

        fun copyString(key: String) {
            val value = source.getString(key)
            if (value != null) {
                builder.putString(key, value)
            }
        }

        copyString(MediaMetadata.METADATA_KEY_TITLE)
        copyString(MediaMetadata.METADATA_KEY_ARTIST)
        copyString(MediaMetadata.METADATA_KEY_ALBUM)
        copyString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        copyString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        copyString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        copyString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)

        if (source.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
            builder.putLong(
                MediaMetadata.METADATA_KEY_DURATION,
                source.getLong(MediaMetadata.METADATA_KEY_DURATION)
            )
        }

        val artwork = makeColorArtwork(1000, 1000)

        builder.putBitmap(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            artwork
        )

        builder.putBitmap(
            MediaMetadata.METADATA_KEY_ART,
            artwork
        )

        session.setMetadata(builder.build())
        session.isActive = true
    }

    private fun makeColorArtwork(
        width: Int,
        height: Int
    ): Bitmap {

        val prefs = getSharedPreferences(
            "taycan_couleur",
            MODE_PRIVATE
        )

        val c1 = prefs.getString("color1", "#FF3DBB")!!
        val c2 = prefs.getString("color2", "#8A2BE2")!!

        return makeGradientBitmap(width, height, c1, c2)
    }

    private fun makeGradientBitmap(
        width: Int,
        height: Int,
        c1: String,
        c2: String
    ): Bitmap {

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        val paint = Paint()

        val gradient = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Color.parseColor(c1),
            Color.parseColor(c2),
            Shader.TileMode.CLAMP
        )

        paint.shader = gradient

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            paint
        )

        return bitmap
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {

        val extras = Bundle()

        extras.putBoolean(
            "android.media.browse.CONTENT_STYLE_SUPPORTED",
            true
        )

        extras.putInt(
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT",
            2
        )

        extras.putInt(
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT",
            2
        )

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

            items.add(
                MediaBrowser.MediaItem(
                    desc,
                    MediaBrowser.MediaItem.FLAG_BROWSABLE
                )
            )

        } else if (parentId == "tab_colors") {

            val randomIcon = makeGradientBitmap(
                200, 200, "#00FFFF", "#FF00CC"
            )

            val randomDesc = MediaDescription.Builder()
                .setMediaId("random")
                .setTitle("Aléatoire")
                .setIconBitmap(randomIcon)
                .build()

            items.add(
                MediaBrowser.MediaItem(
                    randomDesc,
                    MediaBrowser.MediaItem.FLAG_PLAYABLE
                )
            )

            families.forEachIndexed { index, family ->

                val sample = family.tones[
                    minOf(3, family.tones.size - 1)
                ]

                val icon = makeGradientBitmap(
                    200, 200, sample.c1, sample.c2
                )

                val desc = MediaDescription.Builder()
                    .setMediaId("fam_$index")
                    .setTitle(family.name)
                    .setIconBitmap(icon)
                    .build()

                items.add(
                    MediaBrowser.MediaItem(
                        desc,
                        MediaBrowser.MediaItem.FLAG_BROWSABLE
                    )
                )
            }

        } else if (parentId.startsWith("fam_")) {

            val familyIndex = parentId
                .removePrefix("fam_")
                .toIntOrNull()

            val family = if (familyIndex != null) {
                families.getOrNull(familyIndex)
            } else {
                null
            }

            if (family != null) {

                family.tones.forEachIndexed { toneIndex, tone ->

                    val icon = makeGradientBitmap(
                        200, 200, tone.c1, tone.c2
                    )

                    val desc = MediaDescription.Builder()
                        .setMediaId("col_${familyIndex}_$toneIndex")
                        .setTitle(tone.name)
                        .setIconBitmap(icon)
                        .build()

                    items.add(
                        MediaBrowser.MediaItem(
                            desc,
                            MediaBrowser.MediaItem.FLAG_PLAYABLE
                        )
                    )
                }
            }
        }

        result.sendResult(items)
    }

    override fun onDestroy() {

        try {
            sessionManager.removeOnActiveSessionsChangedListener(
                sessionsListener
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        currentCallback?.let {
            try {
                currentController?.unregisterCallback(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        currentCallback = null
        currentController = null

        session.isActive = false
        session.release()

        instance = null

        super.onDestroy()
    }
}
