package xyz.neruxov.prodcontestbot.util.other

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
class WordUtil {

    companion object {

        fun getCityWord(count: Int): String {
            return when {
                count % 10 == 1 && count % 100 != 11 -> "город"
                count % 10 in 2..4 && count % 100 !in 12..14 -> "города"
                else -> "городов"
            }
        }

    }

}