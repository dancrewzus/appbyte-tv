package cl.appbyte.tv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Weather without a hub: Open-Meteo (free, no key) for the latitude/longitude configured at build time. Returns a Home-Assistant-style state name and the temperature, or null. */
object Weather {
    private var cached: Pair<String, Double>? = null; private var at = 0L
    /** Cached for 10 minutes: the home polls every 20 s but the weather does not change that fast. */
    fun fetch(): Pair<String, Double>? {
        if (BuildConfig.LAT == 0.0 && BuildConfig.LON == 0.0) return null
        if (cached != null && System.currentTimeMillis() - at < 600_000) return cached
        return try {
            val c = URL("https://api.open-meteo.com/v1/forecast?latitude=${BuildConfig.LAT}&longitude=${BuildConfig.LON}&current=temperature_2m,weather_code,is_day").openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 6000
            val cur = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            val day = cur.optInt("is_day", 1) == 1
            val state = when (cur.getInt("weather_code")) {
                0 -> if (day) "sunny" else "clear-night"
                1, 2 -> "partlycloudy"
                45, 48 -> "fog"
                in 51..67, in 80..82 -> "rainy"
                in 71..77, 85, 86 -> "snowy"
                in 95..99 -> "lightning"
                else -> "cloudy"
            }
            (state to cur.getDouble("temperature_2m")).also { cached = it; at = System.currentTimeMillis() }
        } catch (e: Exception) { cached }
    }
}
