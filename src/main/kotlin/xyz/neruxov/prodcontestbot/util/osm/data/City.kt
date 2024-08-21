package xyz.neruxov.prodcontestbot.util.osm.data

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
class City(
    val iso: String,
    val displayName: String,
    val coordinates: Coordinates
) {

    fun getCountry(): Country {
        return Country.getByCity(this)
    }

    companion object {

        private val entries: MutableList<City> = mutableListOf()

        fun addEntries(entries: List<City>) {
            Companion.entries.addAll(entries)
        }

        val UNKNOWN = City("XX-XX", "Неизвестно", Coordinates(0.0, 0.0))

        fun getFromIso3166WithUnderscore(iso: String): City {
            return entries.firstOrNull { it.iso.equals(iso, ignoreCase = true) }
                ?: UNKNOWN
        }

        fun getFromIso3166(code: String): City {
            return getFromIso3166WithUnderscore(code.replace("-", "_"))
        }

        fun getFromDisplayName(displayName: String): City {
            println("displayName: $displayName")
            println("result: ${entries.firstOrNull { it.displayName.equals(displayName, ignoreCase = true) } ?: UNKNOWN}")
            return entries.firstOrNull { it.displayName.equals(displayName, ignoreCase = true) }
                ?: UNKNOWN
        }

    }

}