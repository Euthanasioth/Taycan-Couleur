package com.example.taycancolorproxy

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.service.media.MediaBrowserService

class ColorProxyService : MediaBrowserService() {

    private lateinit var session: MediaSession
    private var lastSourceMetadata: MediaMetadata? = null

    companion object {
        private var instance: ColorProxyService? = null

        fun attachSourceController(sourceController: MediaController) {
            instance?.attach(sourceController)
        }

        val palette = listOf(
            "Rose / Violet" to Pair("#FF3DBB", "#8A2BE2"),
            "Bleu" to Pair("#00C6FF", "#0072FF"),
            "Vert" to Pair("#00F260", "#0575E6"),
            "Orange" to Pair("#FF8C00", "#FF3D00"),
            "Rouge" to Pair("#FF416C", "#FF4B2B"),
            "Jaune" to Pair("#FFE259", "#FFA751"),
            "Turquoise" to Pair("#00FFA3", "#00C2FF"),
            "Violet foncé" to Pair("#7F00FF", "#E100FF"),
            "Corail" to Pair("#FF9966", "#FF5E62"),
            "Bleu nuit" to Pair("#0F2027", "#2C5364"),
            "Rose pastel" to Pair("#FFAFBD", "#FFC3A0"),
            "Vert lime" to Pair("#A8E063", "#56AB2F"),
            "Or" to Pair("#FFD700", "#FFA500"),
            "Cyan" to Pair("#00FFFF", "#0080FF"),
            "Magenta" to Pair("#FF00CC", "#333399"),
            "Bronze" to Pair("#C09B6D", "#8B5E3C"),
            "Menthe" to Pair("#00B09B", "#96C93D"),
            "Lavande" to Pair("#C471ED", "#F64F59"),
            "Sunset" to Pair("#FF512F", "#F09819"),
            "Noir / Argent" to Pair("#434343", "#B0B0B0")
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
            override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                selectColor(mediaId)
            }
        })

        session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED, 0))
    }

    private fun selectColor(mediaId: String) {
        val index = mediaId.toIntOrNull() ?: return
        if (index < 0 || index >= palette.size) return
        val (_, colors) = palette[index]

        getSharedPreferences("taycan_couleur", MODE_PRIVATE).edit()
            .putInt("colorIndex", index)
            .putString("color1", colors.first)
            .putString("color2", colors.second)
            .apply()

        updateMetadata(lastSourceMetadata)
    }

    private fun attach(sourceController: MediaController) {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { sourceController.transportControls.play() }
            override fun onPause() { sourceController.transportControls.pause() }
            override fun onSkipToNext() { sourceController.transportControls.skipToNext() }
            override fun onSkipToPrevious() { sourceController.transportControls.skipToPrevious() }
            override fun onStop() { sourceController.transportControls.stop() }
            override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                selectColor(mediaId)
            }
        })

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateMetadata(metadata)
            }
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                session.setPlaybackState(buildState(
                    state?.state ?: PlaybackState.STATE_PAUSED,
                    state?.position ?: 0
                ))
            }
        }
        sourceController.registerCallback(callback)

        updateMetadata(sourceController.metadata)
        val currentState = sourceController.playbackState
        session.setPlaybackState(buildState(
            currentState?.state ?: PlaybackState.STATE_PAUSED,
            currentState?.position ?: 0
        ))
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
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        val items = mutableListOf<MediaBrowser.MediaItem>()

        palette.forEachIndexed { index, entry ->
            val (name, colors) = entry
            val icon = makeGradientBitmap(200, 200, colors.first, colors.second)

            val description = MediaDescription.Builder()
                .setMediaId(index.toString())
                .setTitle(name)
                .setIconBitmap(icon)
                .build()

            items.add(MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE))
        }

        result.sendResult(items)
    }

    override fun onDestroy() {
        super.onDestroy()
        session.release()
        instance = null
    }
}
