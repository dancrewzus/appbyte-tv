package cl.appbyte.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The avatar of the TV's owner: ONLY a picture the family chose (the hub serves data/avatars/<name>.jpg); otherwise the initial in a coloured circle.
 *  (Google's own account photo cannot be read by a normal app: "UnregisteredOnApiConsole"; Immich's face thumbnail was tried and rejected as ugly.) Cached on disk. */
object Avatar {
    private fun file(ctx: Context) = File(ctx.filesDir, "avatar-own.jpg")
    fun cached(ctx: Context): Bitmap? { File(ctx.filesDir, "avatar.jpg").delete(); return file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }

    /** null = the hub has no picture for this person (the cache is cleared); a Bitmap = the current one. */
    fun fromHub(ctx: Context, name: String = BuildConfig.AVATAR_NAME): Bitmap? = try {
        val c = URL("$HUB/api/tv/avatar?name=$name&own=1").openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 6000
        if (c.responseCode == 404) { file(ctx).delete(); null }
        else { val bytes = c.inputStream.use { it.readBytes() }; file(ctx).writeBytes(bytes); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    } catch (e: Exception) { null }
}
