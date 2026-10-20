package cl.appbyte.tv

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Which of the three wallpapers applies right now, from the Sun's height over the house (configured lat/lon): it does not depend on the TV's own timezone setting. */
/** Wallpapers live in assets/ as bg_<period>_<n>.jpg (day, sunset, night): drop more files in and they rotate by day of the year. */
enum class Period { DAY, SUNSET, NIGHT }

object Sky {
    private val LAT = BuildConfig.LAT; private val LON = BuildConfig.LON

    /** Solar elevation in degrees (NOAA-style approximation, good to a fraction of a degree). */
    fun elevation(utcMs: Long, lat: Double = LAT, lon: Double = LON): Double {
        val d = utcMs / 86400000.0 + 2440587.5 - 2451545.0
        val g = Math.toRadians((357.529 + 0.98560028 * d) % 360)
        val q = (280.459 + 0.98564736 * d) % 360
        val l = Math.toRadians((q + 1.915 * sin(g) + 0.020 * sin(2 * g)) % 360)
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = atan2(cos(e) * sin(l), cos(l)); val dec = asin(sin(e) * sin(l))
        val gmst = (18.697374558 + 24.06570982441908 * d) % 24
        val ha = Math.toRadians((((gmst * 15 + lon) % 360) + 360) % 360) - ra
        val la = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(la) * sin(dec) + cos(la) * cos(dec) * cos(ha)))
    }

    /** Night below -4° (after civil twilight), day above 12°, and the long golden hour between them (dusk and dawn both). */
    /** Without a configured location the local clock decides. */
    private fun byClock(): Period { val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return if (h in 8..17) Period.DAY else if (h in 6..7 || h in 18..19) Period.SUNSET else Period.NIGHT }
    fun period(utcMs: Long = System.currentTimeMillis()): Period { if (LAT == 0.0 && LON == 0.0) return byClock(); val h = elevation(utcMs); return if (h > 12) Period.DAY else if (h > -4) Period.SUNSET else Period.NIGHT }
}
