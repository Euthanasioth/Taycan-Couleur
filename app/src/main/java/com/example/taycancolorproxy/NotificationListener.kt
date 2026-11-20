package com.example.taycancolorproxy

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    private val testHandler = Handler(Looper.getMainLooper())

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

    override fun onCreate() {
        super.onCreate()
        Logger.init(applicationContext)
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

        // TEST : masquer aussi une notification Deezer déjà présente
        try {
            activeNotifications
                .filter { it.packageName == "deezer.android.app" }
                .forEach { onNotificationPosted(it) }
        } catch (e: Exception) {
            Logger.log("TEST snooze : lecture des notifs existantes impossible (${e.message})")
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

    // --- TEST : masquer la notification média de Deezer ---
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "deezer.android.app") return

        val key = sbn.key
        Logger.log("TEST snooze : notif Deezer vue (ongoing=${sbn.isOngoing}, clé=$key)")

        try {
            snoozeNotification(key, 6 * 60 * 60 * 1000L)
            Logger.log("TEST snooze : appel envoyé sans erreur")
        } catch (e: Exception) {
            Logger.log("TEST snooze : erreur ${e.message}")
            return
        }

        testHandler.postDelayed({
            try {
                val encoreLa = activeNotifications.any { it.key == key }
                Logger.log("TEST snooze : notif Deezer encore présente = $encoreLa")
            } catch (e: Exception) {
                Logger.log("TEST snooze : vérification impossible (${e.message})")
            }
        }, 500L)
    }
    // --- FIN DU TEST ---

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
