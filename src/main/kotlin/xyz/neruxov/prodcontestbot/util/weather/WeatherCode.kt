package xyz.neruxov.prodcontestbot.util.weather

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
enum class WeatherCode(val code: Int, val description: String, val emoji: String) {
    CLEAR(0, "Ясно", "☀️"),
    MOSTLY_CLEAR(1, "В основном ясно", "🌤"),
    PARTLY_CLOUDY(2, "Переменная облачность", "⛅"),
    CLOUDY(3, "Облачно", "☁️"),
    HAZE(5, "Дымка", "🌫️"),
    MIST(10, "Туман", "🌫️"),
    FOG(45, "Туман", "🌫️"),
    FREEZING_FOG(48, "Замерзающий туман", "🌫️❄️"),
    LIGHT_DRIZZLE(51, "Легкая морось", "🌦️"),
    DRIZZLE(53, "Морось", "🌧️"),
    HEAVY_DRIZZLE(55, "Сильная морось", "🌧️"),
    LIGHT_FREEZING_DRIZZLE(56, "Легкая замерзающая морось", "🌧️❄️"),
    FREEZING_DRIZZLE(57, "Замерзающая морось", "🌧️❄️"),
    LIGHT_RAIN(61, "Легкий дождь", "🌦️"),
    RAIN(63, "Дождь", "🌧️"),
    HEAVY_RAIN(65, "Сильный дождь", "🌧️"),
    LIGHT_FREEZING_RAIN(66, "Легкий замерзающий дождь", "🌧️❄️"),
    FREEZING_RAIN(67, "Замерзающий дождь", "🌧️❄️"),
    LIGHT_SNOW(71, "Небольшой снег", "🌨️"),
    SNOW(73, "Снег", "❄️"),
    HEAVY_SNOW(75, "Сильный снег", "❄️"),
    SNOW_GRAINS(77, "Снежная крупа", "❄️"),
    ICE_PELLETS_SLEET(79, "Ледяные зёрна/Мокрый снег", "🌨️"),
    LIGHT_RAIN_SHOWER(80, "Легкий ливневый дождь", "🌦️"),
    RAIN_SHOWER(81, "Ливневый дождь", "🌧️"),
    HEAVY_RAIN_SHOWER(82, "Сильный ливневый дождь", "🌧️"),
    SNOW_SHOWER(85, "Снегопад", "🌨️"),
    HEAVY_SNOW_SHOWER(86, "Сильный снегопад", "❄️"),
    THUNDERSTORM(95, "Гроза", "⛈️"),
    HAILSTORM(96, "Град", "🌨️"),
    HEAVY_THUNDERSTORM(97, "Сильная гроза", "⛈️"),
    HEAVY_HAILSTORM(99, "Сильный град", "🌨️❄️"),
    UNKNOWN(100, "Неизвестно", "❓")
    ;

    companion object {

        fun fromCode(code: Int): WeatherCode = entries.find { it.code == code } ?: UNKNOWN

    }

}