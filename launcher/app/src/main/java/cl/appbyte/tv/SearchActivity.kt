package cl.appbyte.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

/** One search for the whole TV: what the family has in Jellyfin (movies, series, episodes: opens straight on its page), plus one click to search the same words in YouTube or to talk to the Google Assistant. */
class SearchActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val hub = Hub(HUB)
    private lateinit var edit: EditText
    private lateinit var status: TextView
    private lateinit var results: GridLayout
    private var pending: Runnable? = null
    private var seq = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.setBackgroundColor(BG)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(150), px(70), px(150), px(40)) }
        col.addView(TextView(this).apply { text = "Buscar"; setTextColor(TEXT); sizePx(26f); setTypeface(typeface, android.graphics.Typeface.BOLD) })
        edit = EditText(this).apply {
            hint = "Película, serie o video…"; setHintTextColor(DIM); setTextColor(TEXT); sizePx(24f); isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(px(28), px(14), px(28), px(14)); background = GradientDrawable().apply { cornerRadius = px(40).toFloat(); setColor(0xFF1B2330.toInt()); setStroke(px(2), EDGE) }
        }
        col.addView(edit, LinearLayout.LayoutParams(px(1100), -2).apply { topMargin = px(18); bottomMargin = px(18) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(TopTab(this, "🎙  Hablar con el Asistente").apply { setOnClickListener { try { startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { status.text = "Asistente no disponible" } } })
        actions.addView(TopTab(this, "Buscar en YouTube").apply { setOnClickListener { youtube() } })
        col.addView(actions)
        status = TextView(this).apply { setTextColor(DIM); sizePx(15f); setPadding(0, px(26), 0, px(14)); text = "Escribe para buscar en Jellyfin y en lo que publican tus apps (Netflix, Disney+, YouTube…)." }
        col.addView(status)
        results = GridLayout(this).apply { columnCount = 4; clipChildren = false; clipToPadding = false }
        col.addView(results)
        setContentView(col)
        edit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) { schedule() }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
        edit.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { run(); true } else false }
    }

    override fun onWindowFocusChanged(has: Boolean) {
        super.onWindowFocusChanged(has)
        if (has) edit.post { edit.requestFocus(); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(edit, 0) }
    }

    private fun schedule() { pending?.let { ui.removeCallbacks(it) }; pending = Runnable { run() }.also { ui.postDelayed(it, 450) } }

    private fun youtube() {
        val q = edit.text.toString().trim()
        try { startActivity(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv").putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { status.text = "YouTube no responde" }
    }

    private fun run() {
        val q = edit.text.toString().trim(); val mine = ++seq
        if (q.length < 2) { results.removeAllViews(); status.text = "Escribe para buscar en Jellyfin y en lo que publican tus apps (Netflix, Disney+, YouTube…)."; return }
        status.text = "Buscando…"
        thread {
            val jf = if (HUB_ON && !Demo.on) hub.search(q) else emptyList(); val apps = TvFeed.search(this, q)
            val jfImgs = if (HUB_ON) jf?.map { hub.image(it.image) } else null; val appImgs = apps.map { TvFeed.image(this, it.image) }
            ui.post {
                if (mine != seq) return@post
                results.removeAllViews()
                if ((jf == null || jf.isEmpty()) && apps.isEmpty()) status.text = if (jf == null) "Sin conexión con Jellyfin, y tus apps no tienen «$q». Prueba en YouTube." else "Nada con «$q». Prueba en YouTube o con el Asistente."
                else {
                    status.text = "Resultados" + (if (jf == null) "  ·  Jellyfin sin conexión" else "")
                    jf?.forEachIndexed { i, c -> results.addTile(WatchCard(this, c.copy(sub = "Jellyfin" + (if (c.sub.isNotEmpty()) " · ${c.sub}" else ""))).apply { jfImgs?.get(i)?.let { setImage(it) }; setOnClickListener { try { startActivity(Apps.jellyfinItem(c.id)) } catch (e: Exception) { status.text = "No pude abrir Jellyfin" } } }) }
                    apps.forEachIndexed { i, t -> results.addTile(WatchCard(this, Hub.Card(t.pkg + t.title, t.title, t.sub, 0f, "")).apply { appImgs[i]?.let { setImage(it) }; setOnClickListener { if (!TvFeed.open(this@SearchActivity, t)) status.text = "No pude abrir esa app" } }) }
                }
            }
        }
    }
}
