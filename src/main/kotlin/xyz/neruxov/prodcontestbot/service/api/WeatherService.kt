package xyz.neruxov.prodcontestbot.service.api

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.util.osm.data.City
import xyz.neruxov.prodcontestbot.util.other.DateUtil
import xyz.neruxov.prodcontestbot.util.weather.WeatherCode
import xyz.neruxov.prodcontestbot.util.weather.WeatherForecast
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class WeatherService {

    private final val httpClient = HttpClient.newHttpClient()
    private final val gson = Gson()

    private final val forecastUrl =
        "https://api.open-meteo.com/v1/forecast?start_date=%s&end_date=%s&latitude=%s&longitude=%s&hourly=relative_humidity_2m&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,precipitation_probability_max&timezone=Europe/Moscow"

    private final val dateFormat = SimpleDateFormat("yyyy-MM-dd")
    private final val timeFormat = SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm")

    private final val cache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.DAYS)
        .build<Pair<City, Date>, WeatherForecast>()

    fun getLatestDate(): Date {
        return Date(System.currentTimeMillis() + 15 * 24 * 60 * 60 * 1000)
    }

    fun getTomorrowDate(): Date {
        return Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)
    }

    fun getLatestDateFormatted(): String {
        return DateUtil.RU_DD_MMMM_YYYY.format(getLatestDate())
    }

    fun getWeatherForecast(city: City, date: Date): CompletableFuture<WeatherForecast> {
        val finalDate = if (date.before(getLatestDate())) date else getLatestDate()

        val cached = cache.getIfPresent(Pair(city, finalDate))
        if (cached != null) {
            return CompletableFuture.completedFuture(cached)
        }

        val formattedDate = dateFormat.format(finalDate)
        val url =
            forecastUrl.format(formattedDate, formattedDate, city.coordinates.latitude, city.coordinates.longitude)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build()

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply { response ->
            val forecast = gson.fromJson(response.body(), JsonObject::class.java)

            val hourly = forecast.getAsJsonObject("hourly")
            val relativeHumidity = hourly.getAsJsonArray("relative_humidity_2m")

            val averageHumidity = relativeHumidity.map {
                try {
                    it.asDouble
                } catch (e: Exception) {
                    50.0
                }
            }.average()

            val daily = forecast.getAsJsonObject("daily")

            val weatherCodeInt = try {
                daily.getAsJsonArray("weather_code").get(0).asInt
            } catch (e: Exception) {
                100
            }

            val weatherCode = WeatherCode.fromCode(weatherCodeInt)

            val temperatureMax = try {
                daily.getAsJsonArray("temperature_2m_max").get(0).asDouble
            } catch (e: Exception) {
                0.0
            }

            val temperatureMin = try {
                daily.getAsJsonArray("temperature_2m_min").get(0).asDouble
            } catch (e: Exception) {
                0.0
            }

            val apparentTemperatureMax = try {
                daily.getAsJsonArray("apparent_temperature_max").get(0).asDouble
            } catch (e: Exception) {
                temperatureMax
            }

            val apparentTemperatureMin = try {
                daily.getAsJsonArray("apparent_temperature_min").get(0).asDouble
            } catch (e: Exception) {
                temperatureMin
            }

            val precipitationChanceMax = try {
                daily.getAsJsonArray("precipitation_probability_max").get(0).asInt
            } catch (e: Exception) {
                0
            }

            val result = WeatherForecast(
                finalDate,
                city,
                weatherCode,
                temperatureMax,
                temperatureMin,
                apparentTemperatureMax,
                apparentTemperatureMin,
                precipitationChanceMax,
                averageHumidity
            )

            cache.put(Pair(city, finalDate), result)
            return@thenApply result
        }
    }

}