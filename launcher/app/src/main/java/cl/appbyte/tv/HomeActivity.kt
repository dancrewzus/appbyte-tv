package cl.appbyte.tv

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.concurrent.thread

/** Optional home hub (docs/HUB_API.md). Empty = the launcher works on its own: no house cards, no Jellyfin row, weather from Open-Meteo. */
val HUB: String = BuildConfig.HUB_URL
val HUB_ON get() = HUB.isNotBlank() || Demo.on
private const val JELLYFIN = "org.jellyfin.androidtv"
private val PHRASES = listOf("Tu casa, conectada", "Hecho en casa, con cariño", "Hecho para tu sala", "Menos apps, más tele", "Tecnología que se queda en tu hogar", "Tus datos no salen de casa")
val SKY = Color.parseColor("#8EC8FF")
private const val SPOTIFY = "com.spotify.tv.android"

/** AppByte TV · inicio v0.4. Wallpaper by time of day (night / day / sunset-sunrise), square coloured app tiles that grow upwards, frosted-glass house cards, status cluster like the reference design. */
class HomeActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val hub = Hub(HUB)
    private lateinit var bgView: ImageView
    private lateinit var appsGrid: GridLayout
    private lateinit var houseRow: LinearLayout
    private lateinit var tempView: TextView; private lateinit var condView: TextView; private lateinit var wxIcon: ImageView
    private lateinit var avatarImg: ImageView; private lateinit var avatarInitial: TextView
    private lateinit var aire: HouseTile; private lateinit var dormir: HouseTile; private lateinit var cine: HouseTile; private lateinit var habitacion: HouseTile
    private lateinit var watchRow: LinearLayout; private lateinit var watchBox: LinearLayout; private lateinit var watchTitle: TextView
    private lateinit var estado: HouseTile; private lateinit var banner: TextView; private lateinit var dim: View
    private var lastTag: Any? = null; private var movieOn = false; private var online = true
    private val chipBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GREEN) }
    private var polling = false
    private val wxGlow = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x22FFFFFF) }
    private var wxAnim: android.animation.Animator? = null
    private lateinit var wifiIcon: ImageView; private lateinit var wifiName: TextView; private lateinit var wifiLine: TextView; private lateinit var footer: TextView
    private var shownPeriod: Period? = null
    private var forced: Period? = null                       // test aid: `am start ... --es period day|sunset|night`

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Demo.on = intent?.getBooleanExtra("demo", false) == true
        if (Demo.on) Look.clean = true else Look.clean = Prefs.clean(this) || !Look.hasPhotos          // docs screenshots always use the photo-free look
        introPending = !Look.skipIntro; Look.skipIntro = false
        window.decorView.setBackgroundColor(BG)
        forced = when (intent?.getStringExtra("period")) { "day" -> Period.DAY; "sunset" -> Period.SUNSET; "night" -> Period.NIGHT; else -> null }
        aire = HouseTile(this, R.drawable.ic_thermo, "Aire", BLUE, 324)
        dormir = HouseTile(this, R.drawable.ic_moon, "Dormir", VIOLET, 324)
        cine = HouseTile(this, R.drawable.ic_movie, "Modo cine", ROSE, 324)
        estado = HouseTile(this, R.drawable.ic_home, "Estado de la casa", GREEN, 324)
        habitacion = HouseTile(this, R.drawable.ic_bed, "Habitación", AMBER, 324)
        val root = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        bgView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        if (Look.clean) root.addView(blobs(), FrameLayout.LayoutParams(-1, -1)) else root.addView(bgView, FrameLayout.LayoutParams(-1, -1))
        fun scrim(top: Boolean, h: Int, strong: Int) = View(this).apply { background = GradientDrawable(if (top) GradientDrawable.Orientation.TOP_BOTTOM else GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(strong, 0x00070B10)) }.also { root.addView(it, FrameLayout.LayoutParams(-1, px(h), if (top) Gravity.TOP else Gravity.BOTTOM)) }
        if (!Look.clean) scrim(false, 700, 0xE6070B10.toInt())
        if (!Look.clean) View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF5000000.toInt(), 0xCC000000.toInt(), 0x66000000, 0x00000000)) }.also { root.addView(it, FrameLayout.LayoutParams(-1, px(380), Gravity.TOP)) }
        root.addView(rail(), FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = px(40) })
        root.addView(topBar(), FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(150), 0, px(80), px(46)); clipChildren = false; clipToPadding = false }
        watchBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; clipChildren = false; clipToPadding = false }
        watchTitle = sectionTitle(this, "Continuar viendo")
        watchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(px(10), px(6), 0, px(14)); clipChildren = false; clipToPadding = false }
        watchBox.addView(watchTitle); watchBox.addView(watchRow); col.addView(watchBox)
        appsTitle = sectionTitle(this, "Tus apps"); col.addView(appsTitle)
        appsGrid = GridLayout(this).apply { columnCount = 9; setPadding(px(10), px(14), px(10), px(8)); clipChildren = false; clipToPadding = false }
        col.addView(appsGrid)
        houseRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(px(10), px(6), 0, px(10)); clipChildren = false; clipToPadding = false }
        for (t in listOf(aire, dormir, cine, habitacion, estado)) houseRow.addView(t)
        if (!HUB_ON) houseRow.visibility = View.GONE
        col.addView(houseRow)
        root.addView(col, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        footer = TextView(this).apply { setTextColor(0x99F4F7FB.toInt()); sizePx(11f); gravity = Gravity.CENTER; letterSpacing = 0.08f; text = "POWERED BY APPBYTE TV   ·   ${PHRASES[(System.currentTimeMillis() / 86400000L % PHRASES.size).toInt()]}" }
        root.addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = px(14) })
        banner = TextView(this).apply { setTextColor(Color.WHITE); sizePx(17f); setTypeface(typeface, Typeface.BOLD); setPadding(px(30), px(14), px(30), px(14)); alpha = 0f; visibility = View.GONE; background = GradientDrawable().apply { cornerRadius = px(40).toFloat(); setColor(0xE61B2330.toInt()); setStroke(px(2), EDGE) } }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(70) })
        dim = View(this).apply { setBackgroundColor(0x33000000); visibility = View.GONE; isFocusable = false; isClickable = false }
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, n -> n?.tag?.let { lastTag = it } }
        buildIntro(root)
        registerReceiver(offReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF))
        setContentView(root)
        railEntry(aire)
        estado.setOnClickListener { fade(Intent(this, WebActivity::class.java).putExtra("url", "$HUB/")) }
        estado.set(line = "…"); estado.tag = "h:estado"
        aire.setOnClickListener { refresh() }
        dormir.setOnClickListener { send("dormir", "Buenas noches") }
        cine.setOnClickListener { if (movieOn) send("cine_off", "Modo cine desactivado") else send("cine_on", "Modo cine activado") }
        habitacion.setOnClickListener { send("toggle:habitacion", "Luz de la habitación") }
        aire.set(line = "…"); dormir.set(line = "Apaga todo"); cine.set(line = "atenúa la casa"); habitacion.set(line = "…")
        dormir.tag = "h:dormir"; cine.tag = "h:cine"; habitacion.tag = "h:hab"; aire.tag = "h:aire"
        loadAvatar()
    }

    override fun onResume() {
        super.onResume()
        thread { val list = Apps.favorites(this); ui.post { fillApps(list) } }
        updateBackground(); loadWatch()
        if (introPending) playIntro()
        hookMedia()
        polling = true; poll()
    }
    override fun onDestroy() { try { unregisterReceiver(offReceiver) } catch (e: Exception) { }; super.onDestroy() }
    override fun onPause() { super.onPause(); polling = false; focusedOnce = false; wxAnim?.cancel() }
    private var focusedOnce = false
    override fun onWindowFocusChanged(has: Boolean) { super.onWindowFocusChanged(has); if (has && !focusedOnce) { restoreFocus(); focusedOnce = true } }
    // ---- wallpaper by time of day ---------------------------------------------------------------------------------------------------------------
    private val night get() = (forced ?: Sky.period()) == Period.NIGHT
    private fun updateBackground() {
        val p = forced ?: Sky.period()
        if (Look.clean) { dim.visibility = if (p == Period.NIGHT) View.VISIBLE else View.GONE; recolorBlobs(p); return }
        dim.visibility = if (p == Period.NIGHT) View.VISIBLE else View.GONE                 // night mode: a quieter interface (dimmer, no moving weather icon)
        if (p == shownPeriod) return
        thread {
            val prefix = "bg_${p.name.lowercase()}_"
            val files = (assets.list("") ?: emptyArray()).filter { it.startsWith(prefix) }.sorted()
            val doy = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            val bmp = files.takeIf { it.isNotEmpty() }?.let { f -> try { assets.open(f[doy % f.size]).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) } } catch (e: Exception) { null } }
            if (bmp != null) ui.post { shownPeriod = p; bgView.animate().alpha(0f).setDuration(if (bgView.drawable == null) 0 else 500).withEndAction { bgView.setImageBitmap(bmp); bgView.animate().alpha(1f).setDuration(700).start() }.start() }
        }
    }

    // ---- avatar ----------------------------------------------------------------------------------------------------------------------------------
    private fun loadAvatar() {
        if (!HUB_ON || Demo.on) return
        Avatar.cached(this)?.let { setAvatar(it) }
        thread { val b = Avatar.fromHub(this); ui.post { if (b != null) setAvatar(b) else { avatarImg.visibility = View.GONE; avatarInitial.visibility = View.VISIBLE } } }
    }
    private fun setAvatar(b: Bitmap) { avatarImg.setImageBitmap(b); avatarImg.visibility = View.VISIBLE; avatarInitial.visibility = View.GONE }

    // ---- pieces ----------------------------------------------------------------------------------------------------------------------------------
    private fun open(pkg: String) { val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg); if (i != null) startActivity(i) else toast("No encuentro esa app") }
    private fun liveTv() { try { val (pkg, cls) = BuildConfig.LIVE_TV.split("/"); startActivity(Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast("No pude abrir la TV en vivo") } }
    private fun allApps() = fade(Intent(this, AllAppsActivity::class.java))
    private fun settings(action: String) { try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    private fun divider() = View(this).apply { setBackgroundColor(0x33FFFFFF) }

    private fun topBar(): View {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(px(60), px(30), px(50), 0); clipChildren = false; clipToPadding = false }
        bar.addView(ImageView(this).apply { setImageResource(R.drawable.appbyte_logo); scaleType = ImageView.ScaleType.FIT_START }, LinearLayout.LayoutParams(px(200), px(52)).apply { rightMargin = px(40) })
        val tabs = listOfNotNull(TopTab(this, "Inicio", true), TopTab(this, "Películas").apply { setOnClickListener { open(JELLYFIN) } }, TopTab(this, "Series").apply { setOnClickListener { open(JELLYFIN) } },
            if (BuildConfig.LIVE_TV.contains("/")) TopTab(this, "TV en vivo").apply { setOnClickListener { liveTv() } } else null, TopTab(this, "Apps").apply { setOnClickListener { allApps() } })
        for (t in tabs) bar.addView(t)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        // right cluster, like the reference: search | date + time | weather | wifi, bluetooth, input | avatar
        bar.addView(RoundIcon(this, R.drawable.ic_search, 62).apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = 0; setOnClickListener { fade(Intent(this@HomeActivity, SearchActivity::class.java)) } })
        bar.addView(divider(), LinearLayout.LayoutParams(px(2), px(48)).apply { leftMargin = px(12); rightMargin = px(24) })
        val clock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        clock.addView(TextClock(this).apply { format24Hour = "EEE, d MMM"; format12Hour = "EEE, d MMM"; sizePx(13f); setTextColor(DIM); textLocale = Locale("es", "CL"); gravity = Gravity.CENTER })
        clock.addView(TextClock(this).apply { format24Hour = "HH:mm"; format12Hour = "HH:mm"; sizePx(30f); setTextColor(TEXT); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER })
        bar.addView(clock, LinearLayout.LayoutParams(-2, -2))
        bar.addView(divider(), LinearLayout.LayoutParams(px(2), px(48)).apply { leftMargin = px(24); rightMargin = px(24) })
        wxIcon = ImageView(this).apply { tinted(R.drawable.ic_cloud, TEXT); setPadding(px(6), px(6), px(6), px(6)); background = wxGlow }
        bar.addView(wxIcon, LinearLayout.LayoutParams(px(58), px(58)).apply { rightMargin = px(12) })
        val wx = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tempView = TextView(this).apply { setTextColor(TEXT); sizePx(20f); setTypeface(typeface, Typeface.BOLD); text = "—" }
        condView = TextView(this).apply { setTextColor(DIM); sizePx(13f); maxLines = 1 }
        wx.addView(tempView); wx.addView(condView)
        bar.addView(wx, LinearLayout.LayoutParams(px(180), -2))
        bar.addView(divider(), LinearLayout.LayoutParams(px(2), px(48)).apply { leftMargin = px(8); rightMargin = px(16) })
        val wifi = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true; setPadding(px(12), px(6), px(16), px(6)); setOnClickListener { settings(Settings.ACTION_WIFI_SETTINGS) } }
        val wbg = GradientDrawable().apply { cornerRadius = px(32).toFloat(); setColor(Color.TRANSPARENT) }; wifi.background = wbg
        wifi.setOnFocusChangeListener { _, f -> wbg.setColor(if (f) GLASS_FOCUS else Color.TRANSPARENT); wbg.setStroke(if (f) px(3) else 0, Color.WHITE) }
        wifiIcon = ImageView(this).apply { tinted(R.drawable.ic_wifi, GREEN) }
        wifi.addView(wifiIcon, LinearLayout.LayoutParams(px(38), px(38)).apply { rightMargin = px(12) })
        val wcol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wifiName = TextView(this).apply { setTextColor(TEXT); sizePx(16f); setTypeface(typeface, Typeface.BOLD); isSingleLine = true; text = "Wi-Fi" }
        wifiLine = TextView(this).apply { setTextColor(DIM); sizePx(12f); isSingleLine = true; text = "…" }
        wcol.addView(wifiName); wcol.addView(wifiLine)
        wifi.addView(wcol, LinearLayout.LayoutParams(px(160), -2))
        bar.addView(wifi)
        bar.addView(divider(), LinearLayout.LayoutParams(px(2), px(48)).apply { leftMargin = px(8); rightMargin = px(18) })
        bar.addView(avatar(), LinearLayout.LayoutParams(px(64), px(64)))
        return bar
    }

    private fun avatar(): View {
        val f = FrameLayout(this).apply { isFocusable = true; isClickable = true; clipChildren = false; setOnClickListener { settings(Settings.ACTION_SETTINGS) } }
        val ring = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
        f.background = ring
        f.setOnFocusChangeListener { _, h -> ring.setStroke(if (h) px(4) else 0, Color.WHITE); f.animate().scaleX(if (h) 1.1f else 1f).scaleY(if (h) 1.1f else 1f).setDuration(100).start() }
        val oval = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) } }
        avatarInitial = TextView(this).apply { text = BuildConfig.USER_NAME.take(1).uppercase().ifEmpty { "·" }; setTextColor(Color.WHITE); sizePx(22f); setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER; background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) } }
        avatarImg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; outlineProvider = oval; clipToOutline = true; visibility = View.GONE }
        f.addView(avatarInitial, FrameLayout.LayoutParams(-1, -1)); f.addView(avatarImg, FrameLayout.LayoutParams(-1, -1))
        f.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GREEN); setStroke(px(3), Color.parseColor("#0B0F14")) } }, FrameLayout.LayoutParams(px(20), px(20), Gravity.BOTTOM or Gravity.END))
        return f
    }

    // The side panel (left edge) is NOT in the normal focus path: only LEFT from the first card of «Continuar viendo», «Tus apps» or «Aire» enters it; RIGHT or BACK leaves it.
    private lateinit var railCol: LinearLayout
    private var railReturn: View? = null
    private val railItems = ArrayList<View>()
    private lateinit var hideBtn: RoundIcon; private lateinit var lampBtn: RoundIcon; private lateinit var comedorBtn: RoundIcon; private lateinit var barBtn: RoundIcon; private lateinit var robotBtn: RoundIcon
    private lateinit var playerBox: LinearLayout; private lateinit var artView: ImageView; private lateinit var playBtn: RoundIcon; private lateinit var nextBtn: RoundIcon
    private lateinit var appsTitle: TextView
    private var contentHidden = false
    private val OFFC = 0x99F4F7FB.toInt()

    private fun rail(): View {
        railCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(px(14), px(14), px(14), px(6)); background = GradientDrawable().apply { cornerRadius = px(48).toFloat(); setColor(if (Look.clean) 0x26FFFFFF else 0xCC070B10.toInt()); if (!Look.clean) setStroke(px(2), EDGE) } }
        fun add(v: View, into: LinearLayout = railCol): View {
            v.isFocusable = false
            v.setOnKeyListener { _, code, ev -> if (ev.action == android.view.KeyEvent.ACTION_DOWN && (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || code == android.view.KeyEvent.KEYCODE_BACK)) { leaveRail(); true } else false }
            railItems += v; into.addView(v); return v
        }
        fun ic(res: Int, onClick: () -> Unit) = RoundIcon(this, res, 56).apply { flat = true; (layoutParams as LinearLayout.LayoutParams).apply { rightMargin = 0; bottomMargin = px(8) }; setOnClickListener { onClick() } }
        fun sep() = railCol.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) }, LinearLayout.LayoutParams(px(34), px(2)).apply { topMargin = px(4); bottomMargin = px(12) })
        add(ic(R.drawable.ic_home) { }.apply { selected(true) })
        hideBtn = ic(R.drawable.ic_hide) { toggleContent() }; add(hideBtn)
        add(ic(R.drawable.ic_play) { open(JELLYFIN) })
        add(ic(R.drawable.ic_settings) { settings(Settings.ACTION_SETTINGS) })
        add(ic(R.drawable.ic_palette) { switchLook() })
        if (HUB_ON) {
        sep()
        lampBtn = ic(R.drawable.ic_bulb) { send("toggle:lampara", "Lámpara") }; add(lampBtn)
        comedorBtn = ic(R.drawable.ic_dining) { send("toggle:comedor", "Comedor") }; add(comedorBtn)
        barBtn = ic(R.drawable.ic_bar) { send("toggle:bar", "Bar") }; add(barBtn)
        robotBtn = ic(R.drawable.ic_vacuum) { send("toggle:robot", "Aspiradora") }; add(robotBtn)
        }
        // mini music player: only while Spotify has a session
        playerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        playerBox.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) }, LinearLayout.LayoutParams(px(34), px(2)).apply { topMargin = px(4); bottomMargin = px(12) })
        artView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, px(12).toFloat()) } }; clipToOutline = true; setBackgroundColor(0xFF1B2330.toInt()) }
        playerBox.addView(artView, LinearLayout.LayoutParams(px(56), px(56)).apply { bottomMargin = px(8) })
        playBtn = ic(R.drawable.ic_play) { mc?.let { c -> if (c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play() } }; add(playBtn, playerBox)
        nextBtn = ic(R.drawable.ic_next) { mc?.transportControls?.skipToNext() }; add(nextBtn, playerBox)
        railCol.addView(playerBox)
        return railCol
    }
    private fun enterRail(from: View) { railReturn = from; railItems.forEach { it.isFocusable = true }; railItems[0].requestFocus() }
    private fun leaveRail() { railItems.forEach { it.isFocusable = false }; railReturn?.requestFocus() }

    /** «Ocultar»: hides «Continuar viendo» and «Tus apps», leaving the wallpaper, the top bar and the house row. */
    private fun toggleContent() {
        contentHidden = !contentHidden
        hideBtn.setIcon(if (contentHidden) R.drawable.ic_show else R.drawable.ic_hide)
        val views = listOf<View>(watchBox, appsTitle, appsGrid)
        views.forEach { v ->
            v.animate().cancel()
            if (contentHidden) v.animate().alpha(0f).translationY(px(20).toFloat()).setDuration(220).withEndAction { v.visibility = View.GONE }.start()
            else if (!(v === watchBox && watchRow.childCount == 0)) { v.visibility = View.VISIBLE; v.alpha = 0f; v.translationY = px(20).toFloat(); v.animate().alpha(1f).translationY(0f).setDuration(260).start() }
        }
    }

    // ---- look: «Paisaje» (photos) or «Abstracto» (Apple TV-like gradients) ---------------------------------------------------------------------
    private val blobViews = ArrayList<View>(); private val blobDrawables = ArrayList<GradientDrawable>()
    private fun switchLook() {
        if (!Look.hasPhotos) { toast("«Paisaje» necesita fotos en assets/ (ver README)", false); return }
        Prefs.setClean(this, !Look.clean); Look.clean = !Look.clean; Look.skipIntro = true
        toast(if (Look.clean) "Tema: Abstracto" else "Tema: Paisaje")
        ui.postDelayed({ recreate() }, 350)
    }
    /** Three huge soft colour spots drifting slowly behind everything: only translations of GPU layers, no per-frame drawing. */
    private fun blobs(): View {
        val f = FrameLayout(this).apply { setBackgroundColor(0xFF080A10.toInt()); clipChildren = true }
        val size = px(1700)
        val starts = listOf(Triple(-450, -650, 0), Triple(800, -200, 1), Triple(200, 250, 2))
        for ((x, y, i) in starts) {
            val gd = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0, 0)).apply { gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = size / 2f; setGradientCenter(0.5f, 0.5f) }
            val v = View(this).apply { background = gd; setLayerType(View.LAYER_TYPE_HARDWARE, null); translationX = px(x).toFloat(); translationY = px(y).toFloat() }
            f.addView(v, FrameLayout.LayoutParams(size, size))
            blobViews += v; blobDrawables += gd
            val dx = px(if (i == 1) -420 else 380).toFloat(); val dy = px(if (i == 2) -330 else 300).toFloat()
            android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_X, v.translationX, v.translationX + dx).apply { duration = 32000L + i * 7000; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = android.view.animation.AccelerateDecelerateInterpolator() }.start()
            android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, v.translationY, v.translationY + dy).apply { duration = 26000L + i * 9000; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = android.view.animation.AccelerateDecelerateInterpolator() }.start()
        }
        recolorBlobs(forced ?: Sky.period())
        return f
    }
    private fun recolorBlobs(p: Period) {
        val c = when (p) { Period.DAY -> listOf(0xFF3D8BFF, 0xFFFF9E6B, 0xFF21C7B5); Period.SUNSET -> listOf(0xFFFF5A36, 0xFFD42E8B, 0xFF5B3FE0); Period.NIGHT -> listOf(0xFF5B3FE0, 0xFF1E9BC4, 0xFFB03FA8) }
        blobDrawables.forEachIndexed { i, g -> val col = c[i].toInt(); g.colors = intArrayOf((col and 0x00FFFFFF) or 0xD0000000.toInt(), (col and 0x00FFFFFF) or 0x00000000) }
    }

    // ---- mini player (Spotify's media session) -------------------------------------------------------------------------------------------------
    private var mc: android.media.session.MediaController? = null
    private var mediaHooked = false
    private val mcCb = object : android.media.session.MediaController.Callback() {
        override fun onPlaybackStateChanged(st: android.media.session.PlaybackState?) { ui.post { renderPlayer() } }
        override fun onMetadataChanged(m: android.media.MediaMetadata?) { ui.post { renderPlayer() } }
        override fun onSessionDestroyed() { ui.post { mc = null; renderPlayer() } }
    }
    private fun pickSession(l: List<android.media.session.MediaController>?) { mc?.unregisterCallback(mcCb); mc = l?.firstOrNull { it.packageName == SPOTIFY }; mc?.registerCallback(mcCb); renderPlayer() }
    private fun hookMedia() {
        if (mediaHooked) return
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager; val cn = ComponentName(this, MediaListener::class.java)
            pickSession(msm.getActiveSessions(cn)); msm.addOnActiveSessionsChangedListener({ l -> ui.post { pickSession(l) } }, cn); mediaHooked = true
        } catch (e: SecurityException) { playerBox.visibility = View.GONE }          // notification access not granted: no mini player
    }
    private fun renderPlayer() {
        val c = mc; val st = c?.playbackState?.state
        if (c == null || st == null || st == android.media.session.PlaybackState.STATE_STOPPED || st == android.media.session.PlaybackState.STATE_NONE) { playerBox.visibility = View.GONE; return }
        playerBox.visibility = View.VISIBLE
        val m = c.metadata; val art = m?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
        if (art != null) artView.setImageBitmap(art) else artView.setImageDrawable(null)
        playBtn.setIcon(if (st == android.media.session.PlaybackState.STATE_PLAYING) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun railEntry(v: View) = v.setOnKeyListener { x, code, ev -> if (ev.action == android.view.KeyEvent.ACTION_DOWN && code == android.view.KeyEvent.KEYCODE_DPAD_LEFT && (x.parent as? ViewGroup)?.let { p -> p.indexOfChild(x) == 0 } == true) { enterRail(x); true } else false }
    override fun onBackPressed() { if (railItems[0].isFocusable) leaveRail() }

    private fun fillApps(list: List<AppEntry>, focusPkg: String? = null) {
        appsGrid.removeAllViews()
        for (a in list) appsGrid.addTile(AppTile(this, a.label, a.icon, pkg = a.pkg).apply {
            tag = a.pkg
            setOnClickListener { Prefs.bump(this@HomeActivity, a.pkg); try { launch(this, a.intent) } catch (e: Exception) { toast("No se pudo abrir ${a.label}", false) } }
            setOnLongClickListener { moveMenu(a, list); true }
        })
        appsGrid.addTile(AppTile(this, "Todas las apps", getDrawable(R.drawable.ic_apps)).apply { tag = "all"; solid = Color.parseColor("#2A2F3A"); setOnClickListener { allApps() }})
        appsGrid.getChildAt(0)?.let { railEntry(it) }
        if (!introRunning && focusPkg == null) staggerIn(appsGrid)
        appsGrid.post { if (focusPkg != null) appsGrid.findViewWithTag<View>(focusPkg)?.requestFocus() else if (currentFocus == null) restoreFocus() }
    }
    /** Back from an app the launcher puts the focus where the family left it, not on the first tile. */
    private fun restoreFocus() { (lastTag?.let { window.decorView.findViewWithTag<View>(it) }?.takeIf { it.isShown } ?: (if (contentHidden) aire else appsGrid.getChildAt(0)))?.requestFocus() }

    /** Long press (hold OK) on an app: move it along the row, or go back to the automatic order (most used first). */
    private fun moveMenu(a: AppEntry, list: List<AppEntry>) {
        val i = list.indexOfFirst { it.pkg == a.pkg }
        val items = listOf("Mover a la izquierda", "Mover a la derecha", "Orden automático (más usadas primero)")
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(a.label).setItems(items.toTypedArray()) { _, w ->
            val l = list.map { it.pkg }.toMutableList()
            when (w) { 0 -> if (i > 0) java.util.Collections.swap(l, i, i - 1); 1 -> if (i < l.size - 1) java.util.Collections.swap(l, i, i + 1); else -> { Prefs.setOrder(this, emptyList()); thread { val n = Apps.favorites(this); ui.post { fillApps(n, a.pkg) } }; return@setItems } }
            Prefs.setOrder(this, l); thread { val n = Apps.favorites(this); ui.post { fillApps(n, a.pkg) } }
        }.show()
    }

    private fun send(a: String, what: String) { thread { val ok = hub.action(a); ui.post { toast(if (ok) "$what ✓" else "No pude contactar la casa", ok) }; Thread.sleep(2500); refresh() } }
    private fun poll() { thread { while (polling) { refresh(); ui.post { updateBackground() }; Thread.sleep(20_000) } } }

    private fun refresh() {
        if (!HUB_ON) { val w = Weather.fetch(); ui.post { wifiInfo(); if (w == null) { tempView.text = "—"; condView.text = if (BuildConfig.LAT == 0.0) "sin ubicación" else "sin datos" } else { tempView.text = "%.0f°C".format(w.second); condView.text = esWeather(w.first); showWeather(w.first) } }; return }
        val s = if (Demo.on) Demo.state() else hub.state()
        ui.post {
            wifiInfo()
            online = s != null
            if (s == null) {
                tempView.text = "—"; condView.text = "sin conexión"; setChip(ROSE, "Sin conexión con la casa")
                aire.set(line = "sin conexión"); habitacion.set(line = "sin conexión"); return@post
            }
            movieOn = s.movie
            for ((k, b) in listOf("lampara" to lampBtn, "comedor" to comedorBtn, "bar" to barBtn)) b.tint(if (s.lights[k] == "on") AMBER else OFFC)
            robotBtn.tint(if (s.robot?.contains("clean", true) == true) GREEN else OFFC)
            cine.set("Modo cine", if (s.movie) "activado · salir" else "atenúa la casa", s.movie)
            val att = s.attention.size.let { "$it aviso${if (it > 1) "s" else ""} · ver detalle" }
            val mode = if (s.vacation) "Modo vacaciones" else if (s.away) "Modo ausente" else null
            when {
                s.attention.isNotEmpty() -> setChip(AMBER, att)
                mode != null -> setChip(BLUE, mode)
                else -> setChip(GREEN, "Todo en orden")
            }
            tempView.text = s.outsideTemp?.let { "%.0f°C".format(it) } ?: "—"
            condView.text = esWeather(s.weatherState)
            showWeather(s.weatherState)
            val air = listOfNotNull(s.inTemp?.let { "%.1f°".format(it) }, s.inHum?.let { "%.0f %%".format(it) }, s.co2?.let { "CO₂ %.0f".format(it) }).joinToString("  ·  ")
            if (air.isEmpty()) aire.set(line = "sin datos del sensor") else aire.set(line = air, on = true)
            val on = s.lights["habitacion"] == "on"
            habitacion.set("Habitación", if (on) "luz encendida · apagar" else "luz apagada · encender", on)
        }
    }
    /** Sun turns slowly, moon breathes, clouds drift side to side, rain bobs: cheap property animators on one 58 px view. */
    private fun animateWeather(state: String?) {
        wxAnim?.cancel(); wxIcon.rotation = 0f; wxIcon.translationX = 0f; wxIcon.translationY = 0f; wxIcon.scaleX = 1f; wxIcon.scaleY = 1f
        if (night) return
        val a: android.animation.Animator = when (state) {
            "sunny" -> android.animation.ObjectAnimator.ofFloat(wxIcon, View.ROTATION, 0f, 360f).apply { duration = 30000; repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator() }
            "clear-night" -> android.animation.ObjectAnimator.ofPropertyValuesHolder(wxIcon, android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f), android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f)).apply { duration = 2600; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE }
            "rainy", "pouring", "lightning" -> android.animation.ObjectAnimator.ofFloat(wxIcon, View.TRANSLATION_Y, -px(2).toFloat(), px(4).toFloat()).apply { duration = 700; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE }
            else -> android.animation.ObjectAnimator.ofFloat(wxIcon, View.TRANSLATION_X, -px(5).toFloat(), px(5).toFloat()).apply { duration = 3200; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE; interpolator = android.view.animation.AccelerateDecelerateInterpolator() }
        }
        wxAnim = a; a.start()
    }
    private fun showWeather(state: String?) {
        val (ic, col) = when (state) { "sunny" -> R.drawable.ic_sun to AMBER; "clear-night" -> R.drawable.ic_moon to VIOLET; "rainy", "pouring", "lightning" -> R.drawable.ic_cloud to BLUE; "partlycloudy" -> R.drawable.ic_cloud to SKY; else -> R.drawable.ic_cloud to Color.parseColor("#B8C4D6") }
        wxIcon.tinted(ic, col); wxGlow.setColor((col and 0x00FFFFFF) or 0x30000000)
        animateWeather(state)
    }
    private fun wifiInfo() {
        if (Demo.on) { wifiName.text = "MiRed-5G"; wifiLine.text = "5 GHz · 866 Mbps"; return }
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> { wifiName.text = "Sin red"; wifiLine.text = "revisa el router" }
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> { wifiName.text = "Cable de red"; wifiLine.text = "Ethernet · conectado" }
                else -> {
                    val w = (applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo
                    val ssid = w.ssid?.trim('"')?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" } ?: "Wi-Fi"
                    val band = if (w.frequency > 4900) "5 GHz" else "2,4 GHz"
                    val q = when { w.rssi >= -55 -> "excelente"; w.rssi >= -65 -> "buena"; w.rssi >= -75 -> "regular"; else -> "débil" }
                    wifiName.text = ssid; wifiLine.text = "$band · ${w.linkSpeed} Mbps"; wifiIcon.setColorFilter(when (q) { "excelente", "buena" -> GREEN; "regular" -> AMBER; else -> ROSE })
                }
            }
        } catch (e: Exception) { wifiName.text = "Wi-Fi"; wifiLine.text = "sin datos" }
    }
    private var watchSig = ""
    /** Hero row: Jellyfin (hub) + what Netflix, Disney+, YouTube... published as «continue watching» in Android TV's own database. */
    private fun loadWatch() = thread {
        val jf = if (HUB_ON && !Demo.on) hub.watch() else null; val tv = if (Demo.on) emptyList() else TvFeed.watchNext(this)
        class Entry(val card: Hub.Card, val img: () -> Bitmap?, val open: (View) -> Unit)
        val entries = ArrayList<Entry>()
        if (jf != null && jf.first == "Continuar viendo") jf.second.take(2).forEach { c -> entries += Entry(c, { hub.image(c.image) }, { v -> launch(v, Apps.jellyfinItem(c.id)) }) }
        val groups = tv.groupBy { it.pkg }.values.map { it.toMutableList() }; val mixed = ArrayList<TvFeed.Item>()          // one from each app in turn, so Netflix does not hide behind Prime
        while (groups.any { it.isNotEmpty() }) groups.forEach { if (it.isNotEmpty()) mixed += it.removeAt(0) }
        mixed.forEach { t -> if (entries.size < 7) entries += Entry(Hub.Card(t.pkg + t.title, t.title, t.sub, t.progress, ""), { TvFeed.image(this, t.image) }, { v -> if (!TvFeed.open(this, t, android.app.ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle())) toast("No pude abrir ${t.sub.substringBefore(" ·")}", false) }) }
        if (Demo.on) Demo.cards().forEachIndexed { i, c -> entries += Entry(c, { Demo.poster(i) }, { })}
        var title = "Continuar viendo"
        if (entries.isEmpty() && jf != null && jf.second.isNotEmpty()) { title = jf.first; jf.second.take(7).forEach { c -> entries += Entry(c, { hub.image(c.image) }, { v -> launch(v, Apps.jellyfinItem(c.id)) }) } }
        if (entries.isEmpty()) { ui.post { if (jf != null || tv.isEmpty()) watchBox.visibility = View.GONE }; return@thread }
        val sig = title + entries.joinToString { it.card.id + it.card.progress }
        if (sig == watchSig) return@thread
        val imgs = entries.map { it.img() }
        ui.post {
            watchSig = sig; watchTitle.text = "$title   ›"; watchRow.removeAllViews()
            entries.forEachIndexed { i, e -> watchRow.addView(WatchCard(this, e.card, 224, 126).apply { tag = "w:${e.card.id}"; imgs[i]?.let { setImage(it) }; setOnClickListener { try { e.open(this) } catch (x: Exception) { toast("No pude abrir eso", false) } } }) }
            watchRow.getChildAt(0)?.let { railEntry(it) }
            if (!contentHidden) { watchBox.visibility = View.VISIBLE; if (!introRunning) staggerIn(watchRow) }
        }
    }

    private fun setChip(color: Int, text: String) { estado.color = color; estado.set(line = text, on = true) }
    private var bannerHide: Runnable? = null
    /** In-app notice (replaces Android's toast): a dark pill above the footer, green tick for what worked, red text for what failed. */
    /** Opens an app growing out of the card it was launched from. */
    private fun launch(v: View, i: Intent) = startActivity(i, android.app.ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle())
    private fun fade(i: Intent) { startActivity(i); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) }

    // ---- intro: AppByte TV splash every time the TV wakes up or boots ------------------------------------------------------------------------------
    private lateinit var intro: FrameLayout; private lateinit var introLogo: View; private lateinit var introTv: View; private lateinit var introTag: View
    private var introPending = true; private var introRunning = false
    private val offReceiver = object : android.content.BroadcastReceiver() { override fun onReceive(c: android.content.Context, i: Intent) { introPending = true } }
    private fun buildIntro(root: FrameLayout) {
        intro = FrameLayout(this).apply { setBackgroundColor(BG); isClickable = false; isFocusable = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        introLogo = ImageView(this).apply { setImageResource(R.drawable.appbyte_logo); scaleType = ImageView.ScaleType.FIT_CENTER }
        row.addView(introLogo, LinearLayout.LayoutParams(px(460), px(120)))
        introTv = TextView(this).apply { text = "TV"; setTextColor(ACCENT); sizePx(46f); setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.12f; setPadding(px(18), px(30), 0, 0) }
        row.addView(introTv)
        intro.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        introTag = TextView(this).apply { text = "TU CASA, CONECTADA"; setTextColor(0x99F4F7FB.toInt()); sizePx(13f); letterSpacing = 0.3f }
        intro.addView(introTag, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { topMargin = px(130) })
        intro.visibility = View.GONE
        root.addView(intro, FrameLayout.LayoutParams(-1, -1))
    }
    private fun playIntro() {
        introPending = false; introRunning = true
        intro.animate().cancel(); intro.visibility = View.VISIBLE; intro.alpha = 1f
        introLogo.alpha = 0f; introLogo.scaleX = 0.8f; introLogo.scaleY = 0.8f; introTv.alpha = 0f; introTv.translationX = -px(40).toFloat(); introTag.alpha = 0f
        introLogo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(android.view.animation.OvershootInterpolator(1.3f)).start()
        introTv.animate().alpha(1f).translationX(0f).setStartDelay(380).setDuration(520).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        introTag.animate().alpha(1f).setStartDelay(800).setDuration(600).start()
        ui.postDelayed({
            intro.animate().alpha(0f).setDuration(650).withEndAction { intro.visibility = View.GONE; introRunning = false }.start()
            staggerIn(watchRow); staggerIn(appsGrid, 120); staggerIn(houseRow, 360)
        }, 2000)
    }

    private fun toast(t: String, ok: Boolean = true) {
        banner.text = t; banner.setTextColor(if (ok) Color.WHITE else 0xFFFFB4B4.toInt()); banner.visibility = View.VISIBLE
        banner.animate().cancel(); banner.animate().alpha(1f).setDuration(160).start()
        bannerHide?.let { ui.removeCallbacks(it) }
        bannerHide = Runnable { banner.animate().alpha(0f).setDuration(300).withEndAction { banner.visibility = View.GONE }.start() }.also { ui.postDelayed(it, 2600) }
    }
}
