package cl.appbyte.tv

import android.service.notification.NotificationListenerService

/** Empty on purpose: being an enabled notification listener is what lets the launcher read the active media session (Spotify) for the mini player. */
class MediaListener : NotificationListenerService()
