package com.example.taycancolorproxy

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

class NotificationListener : NotificationListenerService() {

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            try {
                val deezer = controllers?.firstOrNull {
                    it.packageName == "deezer.android.app"
                }
                if (deezer != null) {
                    Logger.log("Deezer (ré)attaché au proxy (changement de session)")
                    ColorProxyService.attachSourceController(deezer)
                }
            } catch (e: Exception) {
                Logger.log("Erreur sessionsChangedListener : ${e.message}")
            }
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                ComponentName(this, NotificationListener::class.java)
            )
            Logger.log("Écoute des changements de sessions active")
            findAndAttachDeezer()
        } catch (e: Exception) {
            Logger.log("Erreur onListenerConnected : ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            manager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
            Logger.log("Erreur onListenerDisconnected : ${e.message}")
        }
    }

    private fun findAndAttachDeezer() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = manager.getActiveSessions(
                ComponentName(this, NotificationListener::class.java)
            )
            val deezerController = controllers.firstOrNull {
                it.packageName == "deezer.android.app"
            }
            if (deezerController != null) {
                Logger.log("Deezer trouvé et attaché au proxy de couleur")
                ColorProxyService.attachSourceController(deezerController)
            }
        } catch (e: Exception) {
            Logger.log("Erreur lors de la recherche de Deezer : ${e.message}")
        }
    }
}
