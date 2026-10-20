package cl.appbyte.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlin.concurrent.thread

/** Every app on the TV in a plain grid (the home row only shows the family's favourites). Back returns to the home screen. */
class AllAppsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.setBackgroundColor(BG)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(96), px(56), px(96), px(40)) }
        col.addView(sectionTitle(this, "Todas las apps"))
        val grid = GridLayout(this).apply { columnCount = 9; clipChildren = false; clipToPadding = false }
        col.addView(grid); col.clipChildren = false; col.clipToPadding = false
        setContentView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col) })
        thread {
            val list = Apps.launchable(this)
            runOnUiThread {
                for (a in list + AppEntry("settings", "Ajustes", getDrawable(android.R.drawable.ic_menu_manage)!!, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                    grid.addTile(AppTile(this, a.label, a.icon, pkg = a.pkg).apply {
                        setOnClickListener { try { startActivity(a.intent, android.app.ActivityOptions.makeScaleUpAnimation(this, 0, 0, width, height).toBundle()) } catch (e: Exception) { Toast.makeText(this@AllAppsActivity, "No se pudo abrir ${a.label}", Toast.LENGTH_SHORT).show() } }
                    })
                }
                grid.getChildAt(0)?.requestFocus()
            }
        }
    }
}
