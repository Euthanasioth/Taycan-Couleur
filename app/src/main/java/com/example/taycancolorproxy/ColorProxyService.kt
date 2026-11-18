package com.example.taycancolorproxy

import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaSessionService
import android.view.KeyEvent

class ColorProxyService : MediaSessionService() {

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
                        PlaybackStateCompat.ACTION_SKIP_TO_PREV or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .build()
            )

            // Redirection des boutons de la voiture vers Deezer
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
        // 1. USAGE INTERNE : Extraction des couleurs pour tes LEDS d'ambiance
        if (bitmap != null) {
            processAmbientColor(bitmap)
        }

        // 2. USAGE VOITURE : On envoie le titre/artiste mais ON EXCLUT la pochette
        val cleanedMetadata = MediaMetadataCompat.Builder().apply {
            if (title != null) putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            if (artist != null) putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            // AUCUN artwork transmis à Android Auto -> Interface propre et neutre !
        }.build()

        mediaSession.setMetadata(cleanedMetadata)
    }

    private fun processAmbientColor(bitmap: Bitmap) {
        // Insère ici ton algorithme d'extraction de couleur pour tes LEDs
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

    override fun onGetSession(controllerInfo: MediaSessionInfo): MediaSessionCompat? {
        return mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        mediaSession.release()
    }
}
