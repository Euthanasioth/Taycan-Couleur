package com.example.taycancolorproxy

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DeezerNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        // On écoute uniquement les notifications émises par Deezer
        if (sbn?.packageName == "com.deezer.android.app") {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bitmap = extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)

            // On transmet les données au service proxy maître
            ColorProxyService.instance?.updateMetadata(title, artist, bitmap)
        }
    }
}
