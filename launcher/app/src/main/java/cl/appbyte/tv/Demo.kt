package cl.appbyte.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

/** Demo mode (`am start -n cl.appbyte.tv/.HomeActivity --ez demo true`): made-up house, weather, Wi-Fi and posters, no network, no personal data. Used for the screenshots in docs/. */
object Demo {
    @Volatile var on = false
    fun state() = Hub.State("partlycloudy", 19.0, 21.4, 48.0, 612.0, 6.0, emptyList(), false, false, false, false,
        mapOf("lampara" to "on", "comedor" to "off", "bar" to "off", "habitacion" to "off"), emptyMap(), "docked", 100, 4, 4, null, false, emptyList())
    fun cards() = listOf(Hub.Card("d1", "Dune: Part Two", "Movies · 2024", 0.62f, ""), Hub.Card("d2", "The Bear", "Series · S2 E5", 0.3f, ""),
        Hub.Card("d3", "Planet Earth III", "Documentary · E2", 0.8f, ""), Hub.Card("d4", "Live concert", "Music video", 0.15f, ""))
    private val palette = listOf(0xFF3D8BFF to 0xFF5B3FE0, 0xFFFF9E6B to 0xFFD42E8B, 0xFF21C7B5 to 0xFF1E5BC4, 0xFFB03FA8 to 0xFF5B3FE0)
    fun poster(i: Int, w: Int = 448, h: Int = 252): Bitmap {
        val (a, b) = palette[i % palette.size]
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a.toInt(), b.toInt(), Shader.TileMode.CLAMP) }) }
    }
}
