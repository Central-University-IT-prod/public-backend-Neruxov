package xyz.neruxov.prodcontestbot.util.logic

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
class EmojiUtil {

    companion object {

        fun getCountEmoji(amount: Int): String {
            return when (amount) {
                0 -> "0️⃣"
                1 -> "1️⃣"
                2 -> "2️⃣"
                3 -> "3️⃣"
                4 -> "4️⃣"
                5 -> "5️⃣"
                6 -> "6️⃣"
                7 -> "7️⃣"
                8 -> "8️⃣"
                9 -> "9️⃣"
                10 -> "🔟"
                else -> {
                    val amountString = amount.toString()
                    val emojis = amountString.map { getCountEmoji(it.toString().toInt()) }
                    emojis.joinToString(separator = "")
                }
            }
        }

    }

}