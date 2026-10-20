package cl.appbyte.tv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppEntry(val pkg: String, val label: String, val icon: Drawable, val intent: Intent)

object Apps {
    /** Apps the family actually uses come first, in this order; everything else follows alphabetically. */
    private val PREFERRED = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "org.jellyfin.androidtv", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.spotify.tv.android", "com.android.vending")

    /** The home row: the family's apps, in order, if installed (the rest is under "Todas las apps"). */
    fun favorites(ctx: Context): List<AppEntry> {
        val all = launchable(ctx); val base = PREFERRED.mapNotNull { p -> all.firstOrNull { it.pkg == p } }
        val manual = Prefs.order(ctx)
        if (manual.isNotEmpty()) return base.sortedBy { manual.indexOf(it.pkg).let { i -> if (i < 0) 999 else i } }
        return base.sortedByDescending { Prefs.launches(ctx, it.pkg) }                      // stable sort: ties keep the family's preferred order
    }

    /** Jellyfin's own start screen understands «ItemId»: opens that movie / series page directly. */
    fun jellyfinItem(id: String) = Intent().setComponent(android.content.ComponentName("org.jellyfin.androidtv", "org.jellyfin.androidtv.ui.startup.StartupActivity")).putExtra("ItemId", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, PackageManager.GET_META_DATA)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || !seen.add(pkg)) continue
                val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: continue
                val icon = try { ri.activityInfo.loadBanner(pm) ?: ri.loadIcon(pm) } catch (e: Exception) { ri.loadIcon(pm) }
                out += AppEntry(pkg, ri.loadLabel(pm).toString(), icon, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        return out.sortedWith(compareBy({ PREFERRED.indexOf(it.pkg).let { i -> if (i < 0) 99 else i } }, { it.label.lowercase() }))
    }
}

/** Small settings of the launcher: the family's manual order of the home apps (empty = automatic, most used first) and how many times each app was opened from here. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun order(c: Context): List<String> = sp(c).getString("order", "")!!.split(",").filter { it.isNotEmpty() }
    fun setOrder(c: Context, l: List<String>) = sp(c).edit().putString("order", l.joinToString(",")).apply()
    fun clean(c: Context) = sp(c).getBoolean("clean", false)
    fun setClean(c: Context, v: Boolean) = sp(c).edit().putBoolean("clean", v).apply()
    fun launches(c: Context, pkg: String) = sp(c).getInt("n_$pkg", 0)
    fun bump(c: Context, pkg: String) = sp(c).edit().putInt("n_$pkg", launches(c, pkg) + 1).apply()
}
