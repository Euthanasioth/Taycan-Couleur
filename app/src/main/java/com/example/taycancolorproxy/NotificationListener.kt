package com.example.taycancolorproxy

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        findAndAttachDeezer()
    }

    private fun findAndAttachDeezer() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = manager.getActiveSessions(ComponentName(this, NotificationListener::class.java))
            
            val deezerController = controllers.firstOrNull { it.packageName == "deezer.android.app" }
            
            if (deezerController != null) {
                Logger.log("Deezer trouvé et attaché au proxy de couleur")
                ColorProxyService.attachSourceController(deezerController)
            }
        } catch (e: Exception) {
            Logger.log("Erreur lors de la recherche de Deezer : ${e.message}")
        }
    }
}
