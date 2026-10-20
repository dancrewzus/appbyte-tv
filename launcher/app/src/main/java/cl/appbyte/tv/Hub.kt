package cl.appbyte.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The status hub (the same one the entrance tablet uses): read the house state, push an action. Plain HttpURLConnection: no libraries on a TV with 1.4 GB of RAM. */
class Hub(private val base: String) {
    data class State(
        val weatherState: String?, val outsideTemp: Double?, val inTemp: Double?, val inHum: Double?, val co2: Double?, val pm25: Double?,
        val people: List<Pair<String, Boolean>>, val doorOpen: Boolean?, val away: Boolean, val vacation: Boolean, val movie: Boolean,
        val lights: Map<String, String>, val tv: Map<String, String>, val robot: String?, val robotBattery: Int?,
        val camsUp: Int, val camsTotal: Int, val officeEffective: String?, val officeLive: Boolean, val attention: List<String> = emptyList(),
    ) {
        val lightsOn get() = lights.values.count { it == "on" }
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply { requestMethod = method; connectTimeout = 3000; readTimeout = 5000 }

    fun state(): State? = try {
        val c = open("/api/tv/home", "GET")
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val mm = j.optJSONObject("mimir")
        val w = j.optJSONObject("weather"); val air = j.optJSONObject("air"); val ppl = j.optJSONArray("people"); val cams = j.optJSONObject("cams"); val off = j.optJSONObject("office")
        fun strMap(o: JSONObject?): Map<String, String> = o?.keys()?.asSequence()?.associateWith { o.optString(it) } ?: emptyMap()
        fun num(o: JSONObject?, k: String): Double? = o?.takeIf { it.has(k) && !it.isNull(k) }?.optDouble(k)?.takeIf { !it.isNaN() }
        State(
            w?.optString("state"), num(w, "temp"), num(air, "temp"), num(air, "hum"), num(air, "co2"), num(air, "pm25"),
            (0 until (ppl?.length() ?: 0)).map { ppl!!.getJSONObject(it).let { p -> p.optString("name") to p.optBoolean("present") } },
            j.optJSONObject("door")?.optBoolean("open"), mm?.optBoolean("away") ?: false, mm?.optBoolean("vacation") ?: false, mm?.optBoolean("movie") ?: false,
            strMap(mm?.optJSONObject("lights")), strMap(mm?.optJSONObject("tv")), mm?.optJSONObject("robot")?.optString("state"), mm?.optJSONObject("robot")?.optInt("battery"),
            cams?.optInt("up") ?: 0, cams?.optInt("total") ?: 0, off?.optString("effective"), off?.optBoolean("live") ?: false,
            (0 until (j.optJSONArray("attention")?.length() ?: 0)).map { j.getJSONArray("attention").getString(it) },
        )
    } catch (e: Exception) { null }

    data class Card(val id: String, val title: String, val sub: String, val progress: Float, val image: String)
    private fun cards(a: org.json.JSONArray?): List<Card> = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it).let { c -> Card(c.getString("id"), c.optString("title"), c.optString("sub"), c.optDouble("progress", 0.0).toFloat(), c.optString("image", c.getString("id"))) } }

    /** Hero row: what the family is in the middle of in Jellyfin (title says whether it is «Continuar viendo» or «Novedades»). */
    fun watch(): Pair<String, List<Card>>? = try {
        val j = JSONObject(open("/api/tv/watch", "GET").inputStream.bufferedReader().use { it.readText() }); j.optString("title") to cards(j.optJSONArray("items"))
    } catch (e: Exception) { null }

    fun search(q: String): List<Card>? = try {
        cards(JSONObject(open("/api/tv/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"), "GET").inputStream.bufferedReader().use { it.readText() }).optJSONArray("items"))
    } catch (e: Exception) { null }

    fun image(id: String): Bitmap? = try {
        open("/api/tv/watch/img/$id", "GET").inputStream.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) }
    } catch (e: Exception) { null }

    fun action(a: String): Boolean = try {
        val c = open("/api/entrada/action", "POST"); c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write("""{"a":"$a"}""".toByteArray()) }
        c.responseCode in 200..299
    } catch (e: Exception) { false }
}

fun esWeather(s: String?) = when (s) { "sunny" -> "Soleado"; "clear-night" -> "Despejado"; "partlycloudy" -> "Parcialmente nublado"; "cloudy" -> "Nublado"; "rainy" -> "Lluvia"; "pouring" -> "Lluvia fuerte"; "fog" -> "Niebla"; "snowy" -> "Nieve"; "windy" -> "Viento"; "lightning" -> "Tormenta"; null -> ""; else -> s }
