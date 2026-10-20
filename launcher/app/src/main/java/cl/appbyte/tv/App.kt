package cl.appbyte.tv

import android.app.Application

/** Reads the chosen look once for the whole process (every screen uses it). */
class App : Application() { override fun onCreate() { super.onCreate(); Look.hasPhotos = assets.list("")?.any { it.startsWith("bg_") } == true; Look.clean = Prefs.clean(this) || !Look.hasPhotos } }
