package xyz.neruxov.prodcontestbot.service.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.request.*
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.data.trip.model.Trip
import xyz.neruxov.prodcontestbot.data.trip.repo.TripRepository
import xyz.neruxov.prodcontestbot.service.api.WeatherService
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage
import xyz.neruxov.prodcontestbot.util.other.DateUtil
import xyz.neruxov.prodcontestbot.util.other.ThreadUtil
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrElse

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class BotWeatherService(
    val weatherService: WeatherService,
    val tripRepository: TripRepository
) {

    companion object {

        private const val PAGE_SIZE = 3

    }

    private final val pageMap = mutableMapOf<Long, Int>()

    fun handleQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val parts = query.data().split("_")
        if (!parts[parts.size - 1].matches(Regex("\\d+")))
            return EmptyMessage(user.id())

        if ((query.data().startsWith("trip_weather_prev") || query.data()
                .startsWith("trip_weather_next")) && !pageMap.containsKey(user.id())
        )
            pageMap[user.id()] = 0

        val trip = tripRepository.findById(parts[parts.size - 1].toLong()).getOrElse { return EmptyMessage(user.id()) }
        return when (parts.subList(0, parts.size - 1).joinToString("_")) {
            "trip_weather" -> showWeather(trip, 0, query, user, bot)
            "trip_weather_prev" -> showWeather(trip, pageMap[user.id()]!! - 1, query, user, bot)
            "trip_weather_next" -> showWeather(trip, pageMap[user.id()]!! + 1, query, user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun showWeather(trip: Trip, page: Int, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (page < 0)
            return AnswerCallbackQuery(query.id())
                .text("Данная страница не существует!")

        val count = trip.cities.size

        if (page * PAGE_SIZE >= count)
            return AnswerCallbackQuery(query.id())
                .text("Данная страница не существует!")

        pageMap[user.id()] = page

        var previousCity = trip.cities[0]
        var previousNonNullDate = trip.cities[0].leavingDate!!

        val cities = trip.cities.map {
            val date = previousCity.leavingDate ?: previousNonNullDate

            var note = if (previousCity.leavingDate != null) null else "дата не указана, используется дата предыдущего города"
            if (date.after(weatherService.getLatestDate())) note = "информация о погоде на ближайшую доступную дату"

            previousCity = it
            previousNonNullDate = date

            Triple(it.city, date, note)
        }.subList(page * PAGE_SIZE, ((page + 1) * PAGE_SIZE).coerceAtMost(count))

        val forecasts = cities.map { city ->
            Pair(city.third, weatherService.getWeatherForecast(city.first, city.second))
        }

        val future = CompletableFuture.allOf(*forecasts.map { it.second }.toTypedArray())

        val msg: Message
        if (query.message().caption() != null) {
            bot.execute(DeleteMessage(user.id(), query.message().messageId()))
            msg = bot.execute(
                SendMessage(
                    user.id(),
                    "☁️ Получаю информацию о погоде..."
                )
            ).message()
        } else {
            EditMessageText(
                user.id(),
                query.message().messageId(),
                "☁️ Получаю информацию о погоде..."
            )

            msg = query.message()
        }

        future.thenApply {
            val message = forecasts.joinToString("\n\n") { data ->
                val forecast = data.second
                val note = data.first

                val weather = forecast.get()
                val date = weather.date
                val weatherCode = weather.weatherCode
                val temperatureMin = weather.temperatureMin
                val temperatureMax = weather.temperatureMax
                val apparentTemperatureMin = weather.apparentTemperatureMin
                val apparentTemperatureMax = weather.apparentTemperatureMax
                val precipitationProbability = weather.precipitationChanceMax
                val humidity = weather.averageHumidity

                """
                    ${weather.city.displayName}, ${weather.city.getCountry().displayName} (${
                    DateUtil.RU_DD_MMMM_YYYY.format(
                        date
                    )
                }) ${if (note != null) "($note)" else ""}
                    ${weatherCode.emoji} ${weatherCode.description}
                    🌡️ $temperatureMin °C - $temperatureMax °C (ощущается: $apparentTemperatureMin °C - $apparentTemperatureMax °C)
                    💧 Вероятность осадков: $precipitationProbability%
                    💦 Влажность: ${humidity.toInt()}%
                """.trimIndent()
            }

            val text =
                """
                Погода в городах путешествия (страница ${page + 1}/${(count + PAGE_SIZE - 1) / PAGE_SIZE}):
            """.trimIndent() + "\n\n" + message

            bot.execute(
                EditMessageText(
                    user.id(),
                    msg.messageId(),
                    text
                ).replyMarkup(BotKeyboard.getWeatherKeyboard(trip.id))
            )
        }.exceptionally { e ->
            e.printStackTrace()
            bot.execute(
                EditMessageText(
                    user.id(),
                    msg.messageId(),
                    "Произошла ошибка при получении информации о погоде."
                ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
            )
        }

        ThreadUtil.execute(Thread { future.join() })
        return AnswerCallbackQuery(query.id())
    }

}