package com.example.taycancolorproxy

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat

class ColorProxyService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    companion object {
        var instance: ColorProxyService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialisation de la MediaSession pour Android Auto
        mediaSession = MediaSessionCompat(this, "TaycanColorProxySession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
        
        // 2. On lie le token au service pour qu'Android Auto valide la source audio
        sessionToken = mediaSession.sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // Autoriser toutes les connexions entrantes (notamment Android Auto)
        return BrowserRoot("root_id", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        // Retourne une liste vide pour éviter les erreurs de navigation sur l'écran de la voiture
        result.sendResult(mutableListOf())
    }

    fun updateMetadata(title: String?, artist: String?, bitmap: Bitmap?) {
        // Méthode appelée par le NotificationListener pour synchroniser les métadonnées
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        mediaSession.release()
    }
}
