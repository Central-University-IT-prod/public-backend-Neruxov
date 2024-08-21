package xyz.neruxov.prodcontestbot.service.bot;

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.EditMessageText
import org.springframework.stereotype.Service;
import xyz.neruxov.prodcontestbot.data.trip.model.Trip
import xyz.neruxov.prodcontestbot.data.trip.model.TripCity
import xyz.neruxov.prodcontestbot.data.trip.repo.TripRepository
import xyz.neruxov.prodcontestbot.data.user.repo.UserRepository
import xyz.neruxov.prodcontestbot.service.api.OpenTripMapService;
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage
import xyz.neruxov.prodcontestbot.util.other.ThreadUtil
import kotlin.jvm.optionals.getOrElse

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class GuideService(
    val openTripMapService: OpenTripMapService,
    val tripRepository: TripRepository,
    val userRepository: UserRepository
) {

    private val cityMap = mutableMapOf<Long, TripCity>()

    fun handleQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val parts = query.data().split("_")
        if (!parts[parts.size - 1].matches(Regex("\\d+")))
            return EmptyMessage(user.id())

        if (query.data().matches(Regex("trip_city_\\d+_\\d+"))) {
            val trip =
                tripRepository.findById(parts[parts.size - 2].toLong()).getOrElse { return EmptyMessage(user.id()) }
            val city = trip.cities.find { it.id == parts[parts.size - 1].toLong() } ?: return EmptyMessage(user.id())
            return getLandmarksMenu(trip, city, query, user, bot)
        }

        val trip = tripRepository.findById(parts[parts.size - 1].toLong()).getOrElse { return EmptyMessage(user.id()) }
        return when (parts.subList(0, parts.size - 1).joinToString("_")) {
            "trip_city_guide" -> selectCity(trip, query, user, bot)
            "trip_cultural" -> showCultural(trip, query, user, bot)
            "trip_amusements" -> showAmusements(trip, query, user, bot)
            "trip_accomodations" -> showAccomodations(trip, query, user, bot)
            "trip_foods" -> showFoods(trip, query, user, bot)
            "trip_shops" -> showShops(trip, query, user, bot)
            "trip_adult" -> showAdult(trip, query, user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun selectCity(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val cities = trip.cities.distinctBy { it.city }.drop(1)
        val citiesInfo = trip.getCityInfo(cities, false)

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            "Выбери город, для которого ты хочешь увидеть достопримечательности 👇\n\n$citiesInfo"
        ).replyMarkup(BotKeyboard.getSelectCity(trip.id, cities, "trip_guide"))
    }

    fun getLandmarksMenu(
        trip: Trip,
        tripCity: TripCity,
        query: CallbackQuery,
        user: User,
        bot: TelegramBot
    ): BaseRequest<*, *> {
        val city = tripCity.city
        cityMap[user.id()] = tripCity

        val tripUser = userRepository.findById(user.id()).get()
        val age = tripUser.age ?: 0

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            "Хорошо, буду показывать для города ${city.displayName}, ${city.getCountry().displayName}. Что ты хочешь увидеть?"
        ).replyMarkup(BotKeyboard.getLandmarksMenu(trip.id, age >= 18))
    }

    fun showCultural(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return showPlaces(
            trip,
            listOf(
                OpenTripMapService.PlaceType.CULTURAL,
                OpenTripMapService.PlaceType.HISTORIC,
                OpenTripMapService.PlaceType.RELIGION,
                OpenTripMapService.PlaceType.ARCHITECTURE
            ),
            "3h",
            query,
            user,
            bot
        )
    }

    fun showAmusements(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return showPlaces(trip, listOf(OpenTripMapService.PlaceType.AMUSEMENTS), "3", query, user, bot)
    }

    fun showAccomodations(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return showPlaces(trip, listOf(OpenTripMapService.PlaceType.ACCOMODATIONS), "3", query, user, bot)
    }

    fun showFoods(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return showPlaces(
            trip,
            listOf(
                OpenTripMapService.PlaceType.CAFES,
                OpenTripMapService.PlaceType.FAST_FOOD,
                OpenTripMapService.PlaceType.RESTAURANTS
            ),
            "3",
            query,
            user,
            bot
        )
    }

    fun showShops(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return showPlaces(
            trip,
            listOf(
                OpenTripMapService.PlaceType.MALLS,
                OpenTripMapService.PlaceType.MARKETPLACES,
                OpenTripMapService.PlaceType.SUPERMARKETS
            ),
            "3",
            query,
            user,
            bot
        )
    }

    fun showAdult(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return showPlaces(trip, listOf(OpenTripMapService.PlaceType.ADULT), "3", query, user, bot)
    }

    fun showPlaces(
        trip: Trip,
        types: List<OpenTripMapService.PlaceType>,
        rate: String,
        query: CallbackQuery,
        user: User,
        bot: TelegramBot
    ): BaseRequest<*, *> {
        val tripCity = cityMap[user.id()] ?: return EmptyMessage(user.id())
        val city = tripCity.city

        bot.execute(
            EditMessageText(
                user.id(),
                query.message().messageId(),
                "Ищу интересные места... 🔎\n\nЭто может занять некоторое время, пожалуйста, подожди."
            )
        )

        val places = openTripMapService.getPlaces(
            city,
            types,
            rate,
        ).thenAccept { places ->
            var placesString = places.joinToString("\n\n") {
                val placeTypes = it.types.joinToString(", ") { type -> "${type.emoji} ${type.displayName}" }

                "${it.name} ⭐⭐⭐\n" + placeTypes + if (it.url != null) "\n${it.url}" else ""
            }

            if (placesString.isEmpty()) {
                placesString = "К сожалению, я не нашел ничего интересного в этом городе 😔"
            }

            bot.execute(
                EditMessageText(
                    user.id(),
                    query.message().messageId(),
                    "Интересные места в ${city.displayName}, ${city.getCountry().displayName}:\n\n$placesString"
                ).replyMarkup(BotKeyboard.getBackToLandmarksMenuForCity(trip.id, cityMap[user.id()]!!.id))
                    .disableWebPagePreview(true)
            )
        }.exceptionally {
            it.printStackTrace()
            bot.execute(
                EditMessageText(
                    user.id(),
                    query.message().messageId(),
                    "Произошла ошибка при поиске интересных мест 😔"
                ).replyMarkup(BotKeyboard.getBackToLandmarksMenuForCity(trip.id, cityMap[user.id()]!!.id))
            )
            null
        }

        ThreadUtil.execute(Thread { places.join() })
        return AnswerCallbackQuery(query.id())
    }

}
