package cl.appbyte.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

/** Full-screen page inside the launcher (the house status dashboard from the hub, reachable on the LAN without Cloudflare's login). BACK goes back, then closes. */
class WebActivity : Activity() {
    private lateinit var web: WebView
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this).apply { setBackgroundColor(BG); settings.javaScriptEnabled = true; settings.domStorageEnabled = true; webViewClient = WebViewClient(); isFocusable = true; requestFocus() }
        setContentView(web)
        web.loadUrl(intent.getStringExtra("url") ?: HUB)
    }
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
