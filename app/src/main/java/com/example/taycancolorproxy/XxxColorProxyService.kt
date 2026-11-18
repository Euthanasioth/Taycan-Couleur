
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

    // Cache du dernier dégradé généré
    private var cachedArtworkKey: String? = null
    private var cachedArtwork: Bitmap? = null

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

        session = MediaSession(this, "TaycanColorProxy")

        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        sessionToken = session.sessionToken

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

        session.isActive = true
        session.setPlaybackState(buildState(null))

        sessionManager =
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        notificationComponent =
            ComponentName(this, NotificationListener::class.java)

        // Surveillance des changements de sessions
        sessionsListener =
            MediaSessionManager.OnActiveSessionsChangedListener { controllers ->

                val deezer = findDeezerController(controllers)

                if (deezer != null) {

                    if (!isSameSession(deezer)) {
                        Logger.log("Nouvelle session Deezer détectée")
                        attach(deezer)
                    } else {
                        refreshCurrentSession(deezer)
                    }

                } else {
                    Logger.log("Aucune session Deezer active détectée")
                    detachCurrentController()
                }
            }

        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsListener,
                notificationComponent
            )
        } catch (e: Exception) {
            Logger.log("Erreur listener : ${e.message}")
        }

        pendingController?.let {
            attach(it)
            pendingController = null
        }

        findDeezerNow()
    }

    // Recherche Deezer en excluant d'abord l'ancien token
    private fun findDeezerController(
        controllers: List<MediaController>?,
        excludedToken: MediaSession.Token? = null
    ): MediaController? {

        if (controllers == null) return null

        val candidates = controllers.filter {
            it.packageName == "deezer.android.app" &&
                it.sessionToken != excludedToken
        }

        return candidates.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: candidates.firstOrNull()
    }

    private fun isSameSession(
        controller: MediaController
    ): Boolean {
        return currentController?.sessionToken ==
            controller.sessionToken
    }

    // Actualisation sans réenregistrement du callback
    private fun refreshCurrentSession(
        controller: MediaController
    ) {
        if (!isSameSession(controller)) return

        updateMetadata(controller.metadata)
        syncPlaybackState(controller.playbackState)
    }

    // Commandes vers Deezer
    // Correction : après une recherche, récupérer le contrôleur nouvellement attaché.
    private fun sendCommand(command: (MediaController) -> Unit) {

        var controller = currentController

        if (controller == null) {
            Logger.log("Aucun contrôleur Deezer : nouvelle recherche")
            findDeezerNow()
            controller = currentController
        }

        if (controller == null) {
            Logger.log("Aucun contrôleur Deezer disponible")
            return
        }

        try {
            command(controller)
        } catch (e: Exception) {
            Logger.log("Erreur commande Deezer : ${e.message}")
            findDeezerNow(controller.sessionToken)
        }
    }

    // Recherche d'une session Deezer disponible
    private fun findDeezerNow(
        excludedToken: MediaSession.Token? = null
    ) {

        try {

            val controllers =
                sessionManager.getActiveSessions(notificationComponent)

            val deezer = findDeezerController(
                controllers,
                excludedToken
            )

            if (deezer != null) {

                if (!isSameSession(deezer)) {
                    attach(deezer)
                } else {
                    refreshCurrentSession(deezer)
                }

            } else if (excludedToken != null) {
                detachCurrentController()
            }

        } catch (e: Exception) {
            Logger.log("Erreur recherche Deezer : ${e.message}")
        }
    }

    // Attachement à une session Deezer
    private fun attach(sourceController: MediaController) {

        if (isSameSession(sourceController)) {
            refreshCurrentSession(sourceController)
            return
        }

        // Désenregistrement de l'ancien callback
        currentCallback?.let { callback ->
            try {
                currentController?.unregisterCallback(callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        currentCallback = null
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

                Logger.log("Session Deezer détruite")

                detachCurrentController()
                findDeezerNow(attachedToken)
            }
        }

        currentCallback = callback

        try {

            sourceController.registerCallback(callback)

            updateMetadata(sourceController.metadata)
            syncPlaybackState(sourceController.playbackState)

            Logger.log("Session Deezer attachée au proxy")

        } catch (e: Exception) {

            Logger.log("Erreur attachement : ${e.message}")

            try {
                sourceController.unregisterCallback(callback)
            } catch (_: Exception) {
            }

            if (currentController?.sessionToken == attachedToken) {
                currentController = null
                currentCallback = null
            }
        }
    }

    // Détachement propre d'une session disparue
    private fun detachCurrentController() {

        currentCallback?.let { callback ->
            try {
                currentController?.unregisterCallback(callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        currentCallback = null
        currentController = null

        lastSourceMetadata = null

        // Conserver le dégradé mais supprimer les informations périmées
        updateMetadata(null)

        session.setPlaybackState(buildState(null))

        Logger.log("Contrôleur Deezer détaché")
    }

    // Synchronisation de l'état de lecture et des actions
    private fun syncPlaybackState(source: PlaybackState?) {
        session.setPlaybackState(buildState(source))
    }

    private fun buildState(source: PlaybackState?): PlaybackState {

        val state = source?.state
            ?: PlaybackState.STATE_PAUSED

        val position = source?.position ?: 0L

        val speed = if (state == PlaybackState.STATE_PLAYING) {
            source?.playbackSpeed ?: 1f
        } else {
            0f
        }

        // Actions annoncées par Deezer.
        // PLAY_FROM_MEDIA_ID reste disponible pour sélectionner les couleurs.
        val actions = (source?.actions ?: 0L) or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID

        return PlaybackState.Builder()
            .setActions(actions)
            .setState(state, position, speed)
            .build()
    }

    // Sélection des couleurs
    private fun applyColor(c1: String, c2: String) {

        getSharedPreferences("taycan_couleur", MODE_PRIVATE)
            .edit()
            .putString("color1", c1)
            .putString("color2", c2)
            .apply()

        // Invalider le cache lorsque les couleurs changent
        cachedArtworkKey = null
        cachedArtwork = null

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

        // Mémoriser également null pour ne pas conserver
        // les informations d'un ancien morceau.
        lastSourceMetadata = source

        val builder = MediaMetadata.Builder()

        if (source != null) {

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
        }

        // Artwork du proxy toujours présent
        val artwork = makeColorArtwork(512, 512)

        builder.putBitmap(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            artwork
        )

        builder.putBitmap(
            MediaMetadata.METADATA_KEY_ART,
            artwork
        )

        session.setMetadata(builder.build())
    }

    // Dégradé mis en cache
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

        val key = "$width|$height|$c1|$c2"

        if (cachedArtworkKey == key && cachedArtwork != null) {
            return cachedArtwork!!
        }

        val bitmap = makeGradientBitmap(
            width,
            height,
            c1,
            c2
        )

        cachedArtworkKey = key
        cachedArtwork = bitmap

        return bitmap
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

        val items =
            mutableListOf<MediaBrowser.MediaItem>()

        if (parentId == "root") {

            val sample = families[0].tones[3]

            val icon = makeGradientBitmap(
                200,
                200,
                sample.c1,
                sample.c2
            )

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
                200,
                200,
                "#00FFFF",
                "#FF00CC"
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
                    200,
                    200,
                    sample.c1,
                    sample.c2
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
                        200,
                        200,
                        tone.c1,
                        tone.c2
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

        currentCallback?.let { callback ->
            try {
                currentController?.unregisterCallback(callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        currentCallback = null
        currentController = null

        cachedArtwork = null
        cachedArtworkKey = null

        session.isActive = false
        session.release()

        if (instance === this) {
            instance = null
        }

        super.onDestroy()
    }
}
