package com.example.taycancolorproxy

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.palette.graphics.Palette

class ColorProxyService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    companion5: Companion? = null
    companion object {
        var instance: ColorProxyService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialisation de la MediaSession complète avec Callbacks
        mediaSession = MediaSessionCompat(this, "TaycanColorProxySession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            // Définition de l'état initial (En lecture / Prêt)
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                    .build()
            )

            // Interception des commandes envoyées par la voiture (commandes au volant ou écran central)
            setCallback(object : MediaSessionCompat.Callback() {
                onPlay() {
                    // Transfère la commande de lecture
                }
                override fun onPause() {
                    // Transfère la commande de pause
                }
                override fun onSkipToNext() {
                    // Passe à la piste suivante
                }
                override fun onSkipToPrevious() {
                    // Passe à la piste précédente
                }
            })

            isActive = true
        }

        sessionToken = mediaSession.sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        // Autorise la connexion d'Android Auto
        return BrowserRoot("root_id", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        // Fournit un élément visuel basique pour que l'app ne soit pas vide dans l'interface de la voiture
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()
        // On peut injecter un item de navigation si besoin
        result.sendResult(mediaItems)
    }

    /**
     * Méthode appelée par le NotificationListener dès que Deezer change de morceau
     */
    fun updateMetadata(title: String?, artist: String?, bitmap: Bitmap?) {
        // 1. Met à jour les métadonnées affichées sur l'écran de la voiture
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title ?: "Taycan Couleur")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist ?: "Deezer Proxy")

        if (bitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            
            // 2. Extraction automatique de la couleur dominante via Palette
            Palette.from(bitmap).generate { palette ->
                val dominantColor = palette?.getDominantColor(Color.BLUE) ?: Color.BLUE
                applyAmbientColor(dominantColor)
            }
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun applyAmbientColor(colorInt: Int) {
        val red = Color.red(colorInt)
        val green = Color.green(colorInt)
        val blue = Color.blue(colorInt)
        
        // C'est ici que tu envoies tes valeurs RGB vers ton bus / module d'éclairage d'ambiance
        // ex: Log.d("TaycanColor", "Couleur synchronisée : R=$red, G=$green, B=$blue")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        mediaSession.release()
    }
}
