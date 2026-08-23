package com.example.taycancolorproxy

import android.graphics.Bitmap
import android.graphics.Color
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

        private const val ACTION_CHANGE_COLOR = "ACTION_CHANGE_COLOR"

        val palette = listOf(
            "#FF3DBB" to "#8A2BE2",
            "#00C6FF" to "#0072FF",
            "#00F260" to "#0575E6",
            "#FF8C00" to "#FF3D00",
            "#FF416C" to "#FF4B2B",
            "#FFE259" to "#FFA751",
            "#00FFA3" to "#00C2FF",
            "#7F00FF" to "#E100FF",
            "#FF9966" to "#FF5E62",
            "#0F2027" to "#2C5364",
            "#FFAFBD" to "#FFC3A0",
            "#A8E063" to "#56AB2F",
            "#FFD700" to "#FFA500",
            "#00FFFF" to "#0080FF",
            "#FF00CC" to "#333399",
            "#C09B6D" to "#8B5E3C",
            "#00B09B" to "#96C93D",
            "#C471ED" to "#F64F59",
            "#FF512F" to "#F09819",
            "#434343" to "#B0B0B0"
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
            override fun onCustomAction(action: String, extras: Bundle?) {
                if (action == ACTION_CHANGE_COLOR) cycleColor()
            }
        })

        session.setPlaybackState(buildState(PlaybackState.STATE_PAUSED, 0))
    }

    private fun cycleColor() {
        val prefs = getSharedPreferences("taycan_couleur", MODE_PRIVATE)
        val currentIndex = prefs.getInt("colorIndex", 0)
        val nextIndex = (currentIndex + 1) % palette.size
        val (c1, c2) = palette[nextIndex]
        prefs.edit()
            .putInt("colorIndex", nextIndex)
            .putString("color1", c1)
            .putString("color2", c2)
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
            override fun onCustomAction(action: String, extras: Bundle?) {
                if (action == ACTION_CHANGE_COLOR) cycleColor()
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
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .addCustomAction(
                ACTION_CHANGE_COLOR,
                "Couleur suivante",
                android.R.drawable.ic_menu_gallery
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
        result.sendResult(mutableListOf())
    }

    override fun onDestroy() {
        super.onDestroy()
        session.release()
        instance = null
    }
}
