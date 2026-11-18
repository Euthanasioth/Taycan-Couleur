package com.example.taycancolorproxy

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.graphics.Bitmap

class DeezerNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        // Vérifie si la notification vient bien de Deezer
        if (sbn?.packageName == "deezer.android.app") {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            // Récupère la pochette d'album si elle est présente dans la notification
            val largeIcon = extras.get(Notification.EXTRA_LARGE_ICON)
            val bitmap = if (largeIcon is Bitmap) largeIcon else null

            // Transmet les infos au service proxy principal
            ColorProxyService.instance?.updateMetadata(title, artist, bitmap)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
