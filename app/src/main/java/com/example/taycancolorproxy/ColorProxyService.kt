package com.example.taycancolorproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.media.MediaBrowserService

class ColorProxyService : MediaBrowserService() {

    private lateinit var session: MediaSession

    companion object {
        private var instance: ColorProxyService? = null

        fun attachSourceController(sourceController: MediaController) {
            instance?.attach(sourceController)
        }
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

        startForegroundIfNeeded()
    }

    private fun startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "proxy_channel", "Taycan Couleur",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)

            val notification = Notification.Builder(this, "proxy_channel")
                .setContentTitle("Taycan Couleur actif")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()

            startForeground(1, notification)
        }
    }

    private fun attach(sourceController: MediaController) {
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { sourceController.transportControls.play() }
            override fun onPause() { sourceController.transportControls.pause() }
            override fun onSkipToNext() { sourceController.transportControls.skipToNext() }
            override fun onSkipToPrevious() { sourceController.transportControls.skipToPrevious() }
            override fun onStop() { sourceController.transportControls.stop() }
        })

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateMetadata(metadata)
            }
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                if (state != null) session.setPlaybackState(state)
            }
        }
        sourceController.registerCallback(callback)

        updateMetadata(sourceController.metadata)
        sourceController.playbackState?.let { session.setPlaybackState(it) }
    }

    private fun updateMetadata(source: MediaMetadata?) {
        if (source == null) return

        val artwork = makeColorArtwork(1000, 1000)

        val builder = MediaMetadata.Builder(source)
        builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
        builder.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)

        session.setMetadata(builder.build())
    }

    private fun makeColorArtwork(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()

        val gradient = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor("#FF3DBB"), Color.parseColor("#8A2BE2"),
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
