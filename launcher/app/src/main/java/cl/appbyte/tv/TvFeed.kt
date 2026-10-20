package cl.appbyte.tv

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.net.URL

/** What the other apps publish to Android TV's own database (the same one Google's launcher shows as «Continuar viendo» and recommendation rows): Netflix, Disney+, YouTube, Prime... Readable with READ_TV_LISTINGS, no account or API. */
object TvFeed {
    data class Item(val title: String, val sub: String, val progress: Float, val pkg: String, val image: String?, val intentUri: String?, val at: Long, val cont: Boolean = false)
    private const val BASE = "content://android.media.tv/"
    private val images = HashMap<String, Bitmap>()
    /** Netflix writes «Seguir viendo Episodio 7» (Spanish) / «Continue watching…» in the description of the cards to resume; add your language here. */
    private val RESUME_WORDS = listOf("Seguir viendo", "Continuar", "Continue", "Resume", "Reprendre", "Weiter")

    private fun rows(ctx: Context, table: String, max: Int = 400): List<Item> = try {
        val pm = ctx.packageManager
        ctx.contentResolver.query(Uri.parse(BASE + table), null, null, null, null)?.use { c ->           // the provider refuses a WHERE on these tables: everything is filtered here
            fun s(n: String) = c.getColumnIndex(n).let { i -> if (i >= 0 && !c.isNull(i)) c.getString(i) else null }
            fun l(n: String) = c.getColumnIndex(n).let { i -> if (i >= 0 && !c.isNull(i)) c.getLong(i) else 0L }
            val out = ArrayList<Item>()
            while (c.moveToNext() && out.size < max) {
                val pkg = s("package_name") ?: continue; val title = s("title") ?: continue
                val app = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                val ep = if (!s("season_display_number").isNullOrEmpty() && !s("episode_display_number").isNullOrEmpty()) "T${s("season_display_number")}E${s("episode_display_number")}" else null
                val dur = l("duration_millis"); val pos = l("last_playback_position_millis")
                val resume = (dur > 0 && pos > 0) || (s("short_description") ?: "").let { d -> RESUME_WORDS.any { d.startsWith(it, ignoreCase = true) } }     // Netflix labels its «continue watching» cards in the description
                out += Item(title, listOfNotNull(app, ep ?: (s("short_description")?.takeIf { d -> RESUME_WORDS.any { d.startsWith(it, ignoreCase = true) } })).joinToString(" · "), if (dur > 0 && pos > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f, pkg,
                    s("thumbnail_uri") ?: s("poster_art_uri"), s("intent_uri"), l("last_engagement_time_utc_millis"), resume)
            }
            out
        } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    /** «Continuar viendo» of every app: Android TV's watch-next list (Prime...) first, then the cards the apps mark as «keep watching» in their own channel (Netflix). Jellyfin is excluded: the hub gives a richer answer. */
    fun watchNext(ctx: Context): List<Item> =
        (rows(ctx, "watch_next_program").sortedByDescending { it.at } + rows(ctx, "preview_program").filter { it.cont })
            .filter { it.pkg != "org.jellyfin.androidtv" }.distinctBy { it.pkg + it.title }

    /** Titles the apps publish (continue watching + recommendations) that contain what was typed. */
    fun search(ctx: Context, q: String): List<Item> =
        (rows(ctx, "watch_next_program") + rows(ctx, "preview_program")).filter { it.pkg != "org.jellyfin.androidtv" && it.title.contains(q, ignoreCase = true) }
            .distinctBy { it.title.lowercase() + it.pkg }.take(12)

    fun image(ctx: Context, uri: String?): Bitmap? {
        if (uri.isNullOrEmpty()) return null
        images[uri]?.let { return it }
        return try {
            val u = Uri.parse(uri)
            fun open() = if (u.scheme == "http" || u.scheme == "https") URL(uri).openStream() else ctx.contentResolver.openInputStream(u)!!
            val bo = BitmapFactory.Options().apply { inJustDecodeBounds = true }; open().use { BitmapFactory.decodeStream(it, null, bo) }
            var ss = 1; while (bo.outWidth / ss > 700) ss *= 2
            open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = ss; inPreferredConfig = Bitmap.Config.RGB_565 }) }?.also { if (images.size > 30) images.clear(); images[uri] = it }
        } catch (e: Exception) { null }
    }

    /** Opens the item exactly where the app says (the app published its own deep link). */
    fun open(ctx: Context, it: Item, opts: android.os.Bundle? = null): Boolean {
        val uri = it.intentUri
        if (uri != null) try { ctx.startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts); return true } catch (e: Exception) { }
        return try { ctx.packageManager.getLeanbackLaunchIntentForPackage(it.pkg)?.let { i -> ctx.startActivity(i, opts); true } ?: false } catch (e: Exception) { false }
    }
}
