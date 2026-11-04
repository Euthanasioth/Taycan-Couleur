package com.example.taycancolorproxy

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            pickDeezer(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        component = ComponentName(this, NotificationListener::class.java)
        try {
            manager?.addOnActiveSessionsChangedListener(sessionsListener, component)
            pickDeezer(manager?.getActiveSessions(component))
        } catch (e: SecurityException) {
        }
    }

    override fun onListenerDisconnected() {
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        try {
            pickDeezer(manager?.getActiveSessions(component))
        } catch (e: SecurityException) {
        }
    }

    private fun pickDeezer(controllers: List<MediaController>?) {
        val deezer = controllers?.firstOrNull { it.packageName == "deezer.android.app" } ?: return
        ColorProxyService.attachSourceController(deezer)
    }
}
