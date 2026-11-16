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

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        super.onActiveSessionsChanged(controllers)
        findAndAttachDeezer(controllers)
    }

    private fun findAndAttachDeezer(existingControllers: List<MediaController>? = null) {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            // On récupère les sessions actives filtrées par notre composant
            val controllers = existingControllers ?: manager.getActiveSessions(ComponentName(this, NotificationListener::class.java))
            
            // On cherche spécifiquement Deezer
            val deezerController = controllers.firstOrNull { it.packageName == "deezer.android.app" }
            
            if (deezerController != null) {
                Logger.log("Deezer trouvé et attaché au proxy de couleur")
                // On transmet le contrôleur au service pour que les boutons pilotent Deezer
                ColorProxyService.attachSourceController(deezerController)
            }
        } catch (e: Exception) {
            Logger.log("Erreur lors de la recherche de Deezer : ${e.message}")
        }
    }
}
