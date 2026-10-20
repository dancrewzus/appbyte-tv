package cl.appbyte.tv

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

val BG = Color.parseColor("#070B10"); val ACCENT = Color.parseColor("#8B84FF")
val TEXT = Color.parseColor("#F4F7FB"); val DIM = Color.parseColor("#B7C3D0")
val GLASS = Color.parseColor("#17FFFFFF"); val GLASS_FOCUS = Color.parseColor("#33FFFFFF"); val EDGE = Color.parseColor("#2AFFFFFF")
val AMBER = Color.parseColor("#FBBF24"); val BLUE = Color.parseColor("#60A5FA"); val VIOLET = Color.parseColor("#A78BFA"); val ROSE = Color.parseColor("#FB7185"); val GREEN = Color.parseColor("#34D399")

/** 1 unit = 1 px on a 1080p TV (density 320 => dp*2): every size below is written as it looks on the 1920x1080 screen. */
fun px(v: Int) = (v * Resources.getSystem().displayMetrics.density / 2f).toInt().coerceAtLeast(1)

/** Text size in real pixels of a 1080p TV (Android's sp doubles on a 320 dpi TV and the layout is written in 1080p pixels): 1 unit = 1.45 px. */
fun TextView.sizePx(v: Float) = setTextSize(TypedValue.COMPLEX_UNIT_PX, v * 1.45f * Resources.getSystem().displayMetrics.density / 2f)

fun ImageView.tinted(res: Int, color: Int) { setImageResource(res); setColorFilter(color) }

fun sectionTitle(a: Activity, t: String) = TextView(a).apply { text = "$t   ›"; setTextColor(TEXT); sizePx(20f); setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, px(8), 0, px(14)) }

/** Focus feel: grows with a small overshoot (springy), settles back smoothly. */
fun View.focusPop(f: Boolean, to: Float) { animate().cancel(); animate().scaleX(if (f) to else 1f).scaleY(if (f) to else 1f).setDuration(if (f) 190 else 140).setInterpolator(if (f) android.view.animation.OvershootInterpolator(2.2f) else android.view.animation.DecelerateInterpolator()).start() }

/** Cards enter one after another: fade in and rise 24 px, 45 ms apart. */
fun staggerIn(vg: android.view.ViewGroup, startDelay: Long = 0) {
    for (i in 0 until vg.childCount) vg.getChildAt(i).apply { alpha = 0f; translationY = px(24).toFloat(); animate().alpha(1f).translationY(0f).setStartDelay(startDelay + i * 45L).setDuration(320).setInterpolator(android.view.animation.DecelerateInterpolator()).start() }
}

/** Which look is on: false = «Paisaje» (photo wallpapers, glass cards, white focus ring), true = «Abstracto» (Apple TV-like: moving colour gradients, softer and rounder cards, focus lifts the card with a glow instead of a ring). */
object Look { @Volatile var hasPhotos = false; @Volatile var clean = false; @Volatile var skipIntro = false }

/** Base of every focusable card: frosted-glass look WITHOUT blur (blur is far too heavy for a 1.4 GB TV): translucent white fill, thin edge, lighter + accent ring + slight growth on focus. */
open class GlassTile(ctx: Activity, w: Int, h: Int, radius: Int = 22) : FrameLayout(ctx) {
    var solid: Int? = null; var tileW = 0; var tileH = 0
    private val r = if (Look.clean) radius + 12 else radius
    private val bg = GradientDrawable().apply { cornerRadius = px(r).toFloat(); setColor(GLASS); setStroke(px(2), EDGE) }
    var tint: Int? = null
        set(v) { field = v; paint(isFocused) }
    init {
        isFocusable = true; isClickable = true; background = bg
        layoutParams = LinearLayout.LayoutParams(px(w), px(h)).apply { rightMargin = px(16) }
        tileW = w; tileH = h
        if (Look.clean) outlineProvider = roundOutline(px(r))
        onFocusChangeListener = OnFocusChangeListener { _, f -> if (f) playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_DOWN); focusPop(f, if (Look.clean) 1.1f else 1.06f); if (Look.clean) lift(f); paint(f) }
    }
    private fun opaque() = solid.let { it != null && (Color.alpha(it) == 255 || it == Color.TRANSPARENT) }
    /** «Abstracto»: the focused card rises and throws a soft white glow. */
    private fun lift(f: Boolean) {
        if (!opaque()) return                                              // a translucent card would show its own shadow through itself
        animate().translationZ(if (f) px(28).toFloat() else 0f).setDuration(180).start()
        if (android.os.Build.VERSION.SDK_INT >= 28) { outlineSpotShadowColor = if (f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(); outlineAmbientShadowColor = if (f) 0x88FFFFFF.toInt() else 0xFF000000.toInt() }
    }
    protected fun paint(focused: Boolean) {
        val sd = solid
        if (Look.clean) {
            elevation = if (opaque()) px(3).toFloat() else 0f
            bg.setStroke(0, 0)
            bg.setColor(if (sd != null) (if (sd == Color.TRANSPARENT) Color.TRANSPARENT else if (focused) lighten(sd, 0.10f) else sd) else if (focused) 0x55FFFFFF else (tint?.let { (it and 0x00FFFFFF) or 0x33000000 } ?: 0x26FFFFFF))
            return
        }
        if (sd != null) { bg.setColor(if (sd == Color.TRANSPARENT) Color.TRANSPARENT else if (focused) lighten(sd, 0.12f) else sd); bg.setStroke(if (focused) px(4) else 0, Color.WHITE); return }
        val t = tint
        bg.setColor(if (focused) GLASS_FOCUS else if (t != null) (t and 0x00FFFFFF) or 0x2E000000 else GLASS)
        bg.setStroke(if (focused) px(4) else px(2), if (focused) Color.WHITE else if (t != null) (t and 0x00FFFFFF) or 0x99000000.toInt() else EDGE)
    }
}

class AppTile(ctx: Activity, title: String, icon: Drawable?, size: Int = 160, pkg: String? = null) : GlassTile(ctx, size, size, 26) {
    init {
        val logo = pkg?.let { try { ctx.assets.open("logos/$it.png").use { s -> android.graphics.BitmapFactory.decodeStream(s) } } catch (e: Exception) { null } }
        if (logo != null) {                                                            // a proper app icon we bundle (assets/logos/<package>.png) fills the whole tile
            solid = Color.TRANSPARENT
            addView(ImageView(ctx).apply { setImageBitmap(logo); scaleType = ImageView.ScaleType.FIT_XY }, LayoutParams(-1, -1))
        } else {
            val banner = icon != null && icon.intrinsicWidth > 0 && icon.intrinsicWidth.toFloat() / icon.intrinsicHeight.coerceAtLeast(1) > 1.4f
            if (icon != null) {
                solid = cornerColor(icon)                                               // the banner's own background: the logo sits on a square of the SAME colour, like the reference
                addView(ImageView(ctx).apply { setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER }, LayoutParams(-1, -1).apply { if (banner) setMargins(px(6), px(6), px(6), px(6)) else setMargins(px(34), px(22), px(34), px(52)) })
            } else solid = Color.parseColor("#2A2F3A")
            if (!banner) addView(TextView(ctx).apply { text = title; setTextColor(Color.WHITE); sizePx(13f); maxLines = 1; gravity = Gravity.CENTER; ellipsize = TextUtils.TruncateAt.END },
                LayoutParams(-1, -2, if (icon != null) Gravity.BOTTOM else Gravity.CENTER).apply { setMargins(px(8), 0, px(8), px(14)) })
        }
        contentDescription = title
        paint(false)
    }
    private fun cornerColor(d: Drawable): Int = try {
        val bmp = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp); val dd = d.constantState?.newDrawable()?.mutate() ?: d
        dd.setBounds(0, 0, 16, 16); dd.draw(c)
        val px = bmp.getPixel(1, 8); bmp.recycle()
        if (Color.alpha(px) < 200) Color.parseColor("#2A2F3A") else px
    } catch (e: Throwable) { Color.parseColor("#2A2F3A") }
}

private fun roundOutline(r: Int) = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, r.toFloat()) } }

/** House card: low, OPAQUE, icon on the left and the text beside it, everything centred. When "on" it takes a soft tint of its icon colour. */
class HouseTile(ctx: Activity, icon: Int, title: String, var color: Int, w: Int = 300) : GlassTile(ctx, w, 84, 22) {
    private val base = if (Look.clean) Color.parseColor("#551B2330") else Color.parseColor("#B31B2330")                    // quieter than the apps: translucent, low
    private val head = TextView(ctx).apply { text = title; setTextColor(TEXT); sizePx(16f); setTypeface(typeface, android.graphics.Typeface.BOLD); isSingleLine = true; gravity = Gravity.START }
    private val sub = TextView(ctx).apply { setTextColor(DIM); sizePx(14f); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.START }
    init {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(px(18), 0, px(18), 0) }
        row.addView(ImageView(ctx).apply { tinted(icon, color) }, LinearLayout.LayoutParams(px(44), px(44)).apply { rightMargin = px(16) })
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        col.addView(head, LinearLayout.LayoutParams(-1, -2)); col.addView(sub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(3) })
        row.addView(col, LinearLayout.LayoutParams(px(w - 96), -2))                          // fixed text column: wrap_content measured the title against the shorter line below it and clipped it
        addView(row, LayoutParams(-1, -1)); contentDescription = title
        solid = base; paint(false)
    }
    fun set(title: String? = null, line: String, on: Boolean = false) { title?.let { head.text = it }; sub.text = line; sub.setTextColor(if (on) color else DIM); solid = if (on) (mix(base, color, 0.22f) and 0x00FFFFFF) or 0xD9000000.toInt() else base; paint(isFocused) }
}

/** Hero card of the home: a Jellyfin poster/thumbnail with title, subtitle and, when it is half-watched, a progress bar. */
class WatchCard(ctx: Activity, c: Hub.Card, w: Int = 330, h: Int = 186) : GlassTile(ctx, w, h, 22) {
    private val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    init {
        solid = Color.parseColor("#1B2330")
        img.outlineProvider = roundOutline(px(22)); img.clipToOutline = true                  // only the picture is clipped: the focus ring stays whole
        addView(img, LayoutParams(-1, -1))
        addView(View(ctx).apply { outlineProvider = roundOutline(px(22)); clipToOutline = true; background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xEE000000.toInt(), 0x00000000)) }, LayoutParams(-1, px(h * 3 / 5), Gravity.BOTTOM))
        val small = w < 260
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(px(if (small) 11 else 16), 0, px(if (small) 11 else 16), px(if (c.progress > 0f) (if (small) 17 else 24) else 10)) }
        col.addView(TextView(ctx).apply { text = c.title; setTextColor(TEXT); sizePx(if (small) 13f else 16f); setTypeface(typeface, android.graphics.Typeface.BOLD); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        if (c.sub.isNotEmpty()) col.addView(TextView(ctx).apply { text = c.sub; setTextColor(DIM); sizePx(if (small) 11f else 13f); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        addView(col, LayoutParams(-1, -2, Gravity.BOTTOM))
        if (c.progress > 0f) {
            val bar = px(w) - px(if (small) 22 else 32)
            addView(View(ctx).apply { background = GradientDrawable().apply { cornerRadius = px(3).toFloat(); setColor(0x66FFFFFF) } }, LayoutParams(bar, px(if (small) 4 else 6), Gravity.BOTTOM or Gravity.START).apply { leftMargin = px(if (small) 11 else 16); bottomMargin = px(if (small) 7 else 10) })
            addView(View(ctx).apply { background = GradientDrawable().apply { cornerRadius = px(3).toFloat(); setColor(ACCENT) } }, LayoutParams((bar * c.progress.coerceIn(0.04f, 1f)).toInt(), px(if (small) 4 else 6), Gravity.BOTTOM or Gravity.START).apply { leftMargin = px(if (small) 11 else 16); bottomMargin = px(if (small) 7 else 10) })
        }
        contentDescription = c.title; paint(false)
    }
    fun setImage(b: android.graphics.Bitmap) = img.setImageBitmap(b)
}

/** Round icon button (side rail / top bar). */
class RoundIcon(ctx: Activity, icon: Int, size: Int = 64) : FrameLayout(ctx) {
    private val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
    /** flat = side panel style: no ring and no fill, only the icon changes colour when focused. */
    var flat = false
    private var base = TEXT
    init {
        isFocusable = true; isClickable = true; background = bg
        layoutParams = LinearLayout.LayoutParams(px(size), px(size)).apply { bottomMargin = px(14); rightMargin = px(10) }
        addView(ImageView(ctx).apply { tinted(icon, TEXT); alpha = 0.9f }, LayoutParams(px(size * 5 / 9), px(size * 5 / 9), Gravity.CENTER))
        onFocusChangeListener = OnFocusChangeListener { _, f -> if (f) playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_DOWN)
            if (flat) (getChildAt(0) as ImageView).apply { setColorFilter(if (f) ACCENT else base); alpha = 1f } else { bg.setColor(if (f) GLASS_FOCUS else Color.TRANSPARENT); bg.setStroke(if (f) px(3) else 0, Color.WHITE) }
            focusPop(f, 1.12f) }
    }
    fun selected(v: Boolean) { if (!isFocused) { bg.setColor(if (v) GLASS else Color.TRANSPARENT) } }
    fun tint(c: Int) { base = c; if (!(flat && isFocused)) (getChildAt(0) as ImageView).apply { setColorFilter(c); alpha = 1f } }
    fun setIcon(res: Int) { (getChildAt(0) as ImageView).setImageResource(res) }
}

/** Top navigation pill ("Inicio", "Apps"...). */
class TopTab(ctx: Activity, title: String, var current: Boolean = false) : TextView(ctx) {
    private val bg = GradientDrawable().apply { cornerRadius = px(32).toFloat(); setColor(Color.TRANSPARENT) }
    init {
        text = title; setTextColor(if (current) TEXT else DIM); sizePx(17f); gravity = Gravity.CENTER; setPadding(px(30), 0, px(30), 0); isFocusable = true; isClickable = true; background = bg
        layoutParams = LinearLayout.LayoutParams(-2, px(60)).apply { rightMargin = px(8) }
        paint(false)
        onFocusChangeListener = OnFocusChangeListener { _, f -> if (f) playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_DOWN); paint(f) }
    }
    private fun paint(f: Boolean) {
        if (Look.clean) {                                                   // Apple TV style: the focused tab is a white pill with dark text, the current one is just bolder
            bg.setColor(if (f) Color.WHITE else Color.TRANSPARENT); bg.setStroke(0, 0)
            setTextColor(if (f) 0xFF0B0F14.toInt() else if (current) TEXT else DIM); setTypeface(null, if (current || f) Typeface.BOLD else Typeface.NORMAL)
            return
        }
        bg.setColor(if (f) GLASS_FOCUS else if (current) GLASS else Color.TRANSPARENT)
        bg.setStroke(if (f) px(3) else if (current) px(2) else 0, if (f) Color.WHITE else EDGE)
        setTextColor(if (f || current) TEXT else DIM)
    }
}

/** Put a tile into a GridLayout (tiles are built with LinearLayout params). */
fun android.widget.GridLayout.addTile(t: GlassTile) {
    t.layoutParams = android.widget.GridLayout.LayoutParams().apply { width = px(t.tileW); height = px(t.tileH); setMargins(0, 0, px(14), px(14)) }
    addView(t)
}

private fun lift(v: Int, f: Float): Int = (v + (255 - v) * f).toInt().coerceIn(0, 255)
fun lighten(c: Int, f: Float): Int = Color.rgb(lift(Color.red(c), f), lift(Color.green(c), f), lift(Color.blue(c), f))
private fun blend(x: Int, y: Int, t: Float): Int = (x + (y - x) * t).toInt().coerceIn(0, 255)
fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(blend(Color.red(a), Color.red(b), t), blend(Color.green(a), Color.green(b), t), blend(Color.blue(a), Color.blue(b), t))
