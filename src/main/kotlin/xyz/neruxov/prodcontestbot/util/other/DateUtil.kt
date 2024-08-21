package xyz.neruxov.prodcontestbot.util.other

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
class DateUtil {

    companion object {

        val RU_DD_MMMM_YYYY = SimpleDateFormat("dd MMMM yyyy", Locale("ru", "RU")).apply { isLenient = false }
        val RU_DD_MM_YYYY = SimpleDateFormat("dd.MM.yyyy", Locale("ru", "RU")).apply { isLenient = false }
        val HH_MM = SimpleDateFormat("HH:mm", Locale("ru", "RU")).apply { isLenient = false }

        fun parseDate(date: String): Date? {
            return try {
                RU_DD_MM_YYYY.parse(date)
            } catch (e: ParseException) {
                null
            }
        }

        fun equalsIgnoreTime(date1: Date, date2: Date): Boolean {
            val cal1 = Calendar.getInstance().apply { time = date1 }
            val cal2 = Calendar.getInstance().apply { time = date2 }
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

    }

}