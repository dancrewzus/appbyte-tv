package cl.appbyte.tv

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Locale
import kotlin.concurrent.thread

/** AppByte TV screensaver: the wallpaper of the moment (same day / sunset / night choice as the home) drifting slowly (Ken Burns), with a big clock, the date and the outside temperature.
 *  The clock block hops to a new corner every minute so no pixel stays lit for hours. Any key press wakes the TV. */
class AppByteDream : DreamService() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var temp: TextView
    private lateinit var block: LinearLayout
    private var running = false
    private val spots = listOf(Gravity.START or Gravity.BOTTOM, Gravity.END or Gravity.BOTTOM, Gravity.END or Gravity.TOP, Gravity.START or Gravity.TOP)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false; isFullscreen = true; isScreenBright = true
        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; scaleX = 1.08f; scaleY = 1.08f }
        root.addView(img, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply { setBackgroundColor(0x55000000) }, FrameLayout.LayoutParams(-1, -1))
        val date = TextClock(this).apply { format24Hour = "EEEE, d 'de' MMMM"; format12Hour = "EEEE, d 'de' MMMM"; textLocale = Locale("es", "CL"); sizePx(24f); setTextColor(0xCCF4F7FB.toInt()) }
        val time = TextClock(this).apply { format24Hour = "HH:mm"; format12Hour = "HH:mm"; sizePx(96f); setTextColor(TEXT); setTypeface(typeface, Typeface.BOLD); includeFontPadding = false }
        temp = TextView(this).apply { sizePx(24f); setTextColor(0xCCF4F7FB.toInt()) }
        block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(110), px(90), px(110), px(90)); addView(time); addView(date); addView(temp) }
        root.addView(block, FrameLayout.LayoutParams(-2, -2, spots[0]))
        root.addView(TextView(this).apply { text = "POWERED BY APPBYTE TV"; setTextColor(0x66F4F7FB); sizePx(11f); letterSpacing = 0.1f; gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(14) })
        setContentView(root)
        for (p in listOf(View.SCALE_X, View.SCALE_Y)) ObjectAnimator.ofFloat(img, p, 1.08f, 1.0f).apply { duration = 60000; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator() }.start()
        thread {
            val prefix = "bg_${Sky.period().name.lowercase()}_"
            val f = (assets.list("") ?: emptyArray()).filter { it.startsWith(prefix) }.sorted().firstOrNull()
            val bmp = f?.let { try { assets.open(it).use { s -> BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) } } catch (e: Exception) { null } }
            ui.post { if (bmp != null) img.setImageBitmap(bmp) }
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted(); running = true
        var n = 0
        val hop = object : Runnable { override fun run() { if (!running) return; (block.layoutParams as FrameLayout.LayoutParams).gravity = spots[++n % spots.size]; block.requestLayout(); ui.postDelayed(this, 60_000) } }
        ui.postDelayed(hop, 60_000)
        thread { while (running) { val s = Hub(HUB).state(); ui.post { temp.text = s?.outsideTemp?.let { "%.0f°C  ·  %s".format(it, esWeather(s.weatherState)) } ?: "" }; Thread.sleep(300_000) } }
    }
    override fun onDreamingStopped() { running = false; ui.removeCallbacksAndMessages(null); super.onDreamingStopped() }
}
