package com.example.taycancolorproxy

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.media.MediaBrowserServiceCompat

class ColorProxyService : MediaBrowserServiceCompat() {

    companion object {
        var instance: ColorProxyService? = null
    }

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        instance = this

        mediaSession = MediaSessionCompat(baseContext, "TaycanColorProxy").apply {
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .build()
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    sendMediaButtonToDeezer(KeyEvent.KEYCODE_MEDIA_PLAY)
                }

                override fun onPause() {
                    sendMediaButtonToDeezer(KeyEvent.KEYCODE_MEDIA_PAUSE)
                }

                override fun onSkipToNext() {
                    sendMediaButtonToDeezer(KeyEvent.KEYCODE_MEDIA_NEXT)
                }

                override fun onSkipToPrevious() {
                    sendMediaButtonToDeezer(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            })

            isActive = true
        }

        sessionToken = mediaSession.sessionToken
    }

    fun updateMetadata(title: String?, artist: String?, bitmap: Bitmap?) {
        if (bitmap != null) {
            processAmbientColor(bitmap)
        }

        val cleanedMetadata = MediaMetadataCompat.Builder().apply {
            if (title != null) putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            if (artist != null) putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
        }.build()

        mediaSession.setMetadata(cleanedMetadata)
    }

    private fun processAmbientColor(bitmap: Bitmap) {
        // Traitement couleur LEDs ici
    }

    private fun sendMediaButtonToDeezer(keyCode: Int) {
        val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setPackage("com.deezer.android.app")
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        sendBroadcast(downIntent)

        val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setPackage("com.deezer.android.app")
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        sendBroadcast(upIntent)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("taycan_root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(mutableListOf())
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        mediaSession.release()
    }
}
