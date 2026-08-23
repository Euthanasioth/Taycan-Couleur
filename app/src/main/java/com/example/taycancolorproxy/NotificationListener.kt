package com.example.taycancolorproxy

import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        scanForDeezerSession()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        scanForDeezerSession()
    }

    private fun scanForDeezerSession() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, NotificationListener::class.java)
            val controllers: List<MediaController> = manager.getActiveSessions(component)

            for (controller in controllers) {
                if (controller.packageName == "deezer.android.app") {
                    ColorProxyService.attachSourceController(controller)
                    return
                }
            }
        } catch (e: SecurityException) {
            // Permission pas encore accordée, on réessaiera au prochain événement.
        }
    }
}
