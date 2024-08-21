package xyz.neruxov.prodcontestbot.util.weather

import xyz.neruxov.prodcontestbot.util.osm.data.City
import java.util.*

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
data class WeatherForecast(
    val date: Date,
    val city: City,
    val weatherCode: WeatherCode,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val apparentTemperatureMax: Double,
    val apparentTemperatureMin: Double,
    val precipitationChanceMax: Int,
    val averageHumidity: Double,
)