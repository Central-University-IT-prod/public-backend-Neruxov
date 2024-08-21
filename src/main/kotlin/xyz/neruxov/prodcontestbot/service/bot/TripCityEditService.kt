package xyz.neruxov.prodcontestbot.service.bot;

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.*
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import xyz.neruxov.prodcontestbot.data.trip.model.Trip
import xyz.neruxov.prodcontestbot.data.trip.model.TripCity
import xyz.neruxov.prodcontestbot.data.trip.repo.TripRepository
import xyz.neruxov.prodcontestbot.data.user.repo.UserRepository
import xyz.neruxov.prodcontestbot.state.TripCityModificationState
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage
import xyz.neruxov.prodcontestbot.util.osm.NominatimUtil
import xyz.neruxov.prodcontestbot.util.osm.data.City
import xyz.neruxov.prodcontestbot.util.osm.data.Coordinates
import xyz.neruxov.prodcontestbot.util.other.DateUtil
import xyz.neruxov.prodcontestbot.util.other.ThreadUtil
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrElse

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class TripCityEditService(
    val userRepository: UserRepository,
    val tripRepository: TripRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    private final val stateMap = mutableMapOf<Long, Pair<Long, TripCityModificationState>>()
    private final val dateMap = mutableMapOf<Long, Pair<Long, Date>>()
    private final val cityMap = mutableMapOf<Long, Long>()
    private final val cityToAddMap = mutableMapOf<Long, Pair<Long, City>>()

    fun handleQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val parts = query.data().split("_")
        if (parts.size > 2 && parts[parts.size - 2].matches(Regex("\\d+"))) {
            val trip =
                tripRepository.findById(parts[parts.size - 2].toLong()).getOrElse { return EmptyMessage(user.id()) }
            val city = trip.cities.find { it.id == parts[parts.size - 1].toLong() }!!
            return when (parts.subList(0, parts.size - 2).joinToString("_")) {
                "trip_city" -> {
                    return when (checkState(user.id()).second) {
                        TripCityModificationState.SELECT_ADD_LEAVING_DATE -> addLeavingDate(
                            trip,
                            city,
                            query.message(),
                            user,
                            bot
                        )

                        TripCityModificationState.SELECT_CITY_TO_ADD_AFTER -> addCity(trip, city, user, bot)
                        TripCityModificationState.SELECT_CITY_TO_REMOVE -> removeCity(
                            trip,
                            city.id,
                            query.message(),
                            user,
                            bot
                        )

                        else -> EmptyMessage(user.id())
                    }
                }

                else -> EmptyMessage(user.id())
            }
        }

        if (!parts[parts.size - 1].matches(Regex("\\d+")))
            return EmptyMessage(user.id())

        val trip = tripRepository.findById(parts[parts.size - 1].toLong()).getOrElse { return EmptyMessage(user.id()) }
        return when (parts.subList(0, parts.size - 1).joinToString("_")) {
            "trip_edit_leaving_date" -> selectAddLeavingDate(trip, user, query, bot)
            "trip_edit_remove_city" -> removeCitySelect(trip, user, query.message(), bot)
            "trip_edit_add_city" -> selectCityToAddAfter(trip, user, query, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun handleConfirmQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(AnswerCallbackQuery(query.id()))

        val state = checkState(user.id())
        if (state.second == TripCityModificationState.NONE)
            return EmptyMessage(user.id())

        val trip = tripRepository.findById(state.first).getOrElse { return EmptyMessage(user.id()) }

        return when (state.second) {
            TripCityModificationState.CONFIRM_LEAVING_DATE -> confirmLeavingDate(trip, query, user, bot)
            TripCityModificationState.CONFIRM_REMOVE_CITY -> confirmRemoveCity(trip, query, user, bot)
            TripCityModificationState.CONFIRM_ADD_CITY -> confirmAddCity(trip, query, user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun handleMessage(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val check = checkState(user.id())
        if (check.second == TripCityModificationState.NONE)
            return EmptyMessage(user.id())

        val trip = tripRepository.findById(check.first).getOrElse { return EmptyMessage(user.id()) }
        return when (check.second) {
            TripCityModificationState.ADD_LEAVING_DATE -> getLeavingDate(trip, message, user, bot)
            TripCityModificationState.ADD_CITY -> getCityToAdd(trip, message, user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun handleCancelQuery(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(AnswerCallbackQuery(query.id()))

        val state = checkState(user.id())
        if (state.second == TripCityModificationState.NONE)
            return EmptyMessage(user.id())

        return when (state.second) {
            TripCityModificationState.SELECT_ADD_LEAVING_DATE,
            TripCityModificationState.ADD_LEAVING_DATE -> cancelLeavingDate(query.message(), user, bot)

            TripCityModificationState.SELECT_CITY_TO_REMOVE -> cancelRemoveCity(query.message(), user, bot)
            TripCityModificationState.ADD_CITY -> cancelAddCity(query.message(), user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun selectAddLeavingDate(trip: Trip, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = Pair(trip.id, TripCityModificationState.SELECT_ADD_LEAVING_DATE)

        bot.execute(
            DeleteMessage(
                user.id(),
                query.message().messageId()
            )
        )

        val cities = trip.getCityInfo(trip.cities)

        return SendMessage(
            user.id(),
            "Выбери город, для которого хочешь уточнить, когда ты планируешь его покинуть 👇\n\n"
                    + cities
        ).replyMarkup(
            BotKeyboard.getSelectCity(
                trip.id,
                trip.cities.dropLast(1)
            )
        )
    }

    fun addLeavingDate(
        trip: Trip,
        tripCity: TripCity,
        message: Message,
        user: User,
        bot: TelegramBot
    ): BaseRequest<*, *> {
        stateMap[user.id()] = Pair(trip.id, TripCityModificationState.ADD_LEAVING_DATE)
        dateMap[user.id()] = Pair(tripCity.id, Date())

        return EditMessageText(
            user.id(),
            message.messageId(),
            """
                Когда ты планируешь покинуть город ${tripCity.city.displayName}, ${tripCity.city.getCountry().displayName}? 📅
                
                Напиши дату в формате: ДД.ММ.ГГГГ
            """.trimIndent()
        ).replyMarkup(BotKeyboard.CANCEL.keyboard as InlineKeyboardMarkup)
    }

    fun getLeavingDate(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (message.text() == null)
            return EmptyMessage(user.id())

        val cityId = dateMap[user.id()]?.first ?: return EmptyMessage(user.id())

        val date = DateUtil.parseDate(message.text())
            ?: return SendMessage(
                user.id(),
                """
                    Не могу распознать дату 😔
                    
                    Попробуй еще раз.
                """.trimIndent()
            ).replyMarkup(BotKeyboard.CANCEL.keyboard)

        val tripCity = trip.cities.find { it.id == cityId } ?: return EmptyMessage(user.id())
        if (!trip.isValidDate(tripCity, date))
            return SendMessage(
                user.id(),
                """
                    Похоже, что ты так увлёкся путешествием, что решил путешествовать во времени 😅
                    
                    Попробуй еще раз.
                """.trimIndent()
            ).replyMarkup(BotKeyboard.CANCEL.keyboard)

        val city = trip.cities.find { it.id == cityId }!!.city
        dateMap[user.id()] = Pair(cityId, date)
        stateMap[user.id()] = Pair(trip.id, TripCityModificationState.CONFIRM_LEAVING_DATE)

        return SendMessage(
            user.id(),
            """
                Ставлю дату ${DateUtil.RU_DD_MMMM_YYYY.format(date)} для города ${city.displayName}, ${city.getCountry().displayName}? 📅
                
                Всё верно?
            """.trimIndent()
        ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
    }

    fun cancelLeavingDate(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val tripId = stateMap[user.id()]?.first ?: return EmptyMessage(user.id())

        stateMap.remove(user.id())
        dateMap.remove(user.id())

        return EditMessageText(
            user.id(),
            message.messageId(),
            """
                Хорошо, отменил! 👌
            """.trimIndent()
        ).replyMarkup(BotKeyboard.getToEditTrip(tripId))
    }

    fun cancelAddCity(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val tripId = stateMap[user.id()]?.first ?: return EmptyMessage(user.id())

        stateMap.remove(user.id())
        cityToAddMap.remove(user.id())

        return EditMessageText(
            user.id(),
            message.messageId(),
            """
                Хорошо, отменил! 👌
            """.trimIndent()
        ).replyMarkup(BotKeyboard.getToEditTrip(tripId))
    }

    fun cancelRemoveCity(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val tripId = stateMap[user.id()]?.first ?: return EmptyMessage(user.id())

        stateMap.remove(user.id())
        cityMap.remove(user.id())

        return EditMessageText(
            user.id(),
            message.messageId(),
            """
                Хорошо, отменил! 👌
            """.trimIndent()
        ).replyMarkup(BotKeyboard.getToEditTrip(tripId))
    }

    fun confirmLeavingDate(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val data = dateMap[user.id()] ?: return EmptyMessage(user.id())

        dateMap.remove(user.id())
        stateMap.remove(user.id())

        val cityId = data.first
        val date = data.second

        if (query.data() == "yes") {
            trip.cities.find { it.id == cityId }?.leavingDate = date
            tripRepository.save(trip)

            return SendMessage(
                user.id(),
                """
                    Хорошо, изменил! 👌
                """.trimIndent()
            ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
        } else {
            return SendMessage(
                user.id(),
                """
                    Хорошо, не буду менять! 👌
                """.trimIndent()
            ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
        }
    }

    fun removeCitySelect(trip: Trip, user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(
            DeleteMessage(
                user.id(),
                message.messageId()
            )
        )

        if (trip.cities.size == 2) {
            return SendMessage(
                user.id(),
                """
                    Нельзя удалить единственный город из маршрута 😔
                """.trimIndent()
            ).replyMarkup(BotKeyboard.getToEditTrip(trip.id))
        }

        stateMap[user.id()] = Pair(trip.id, TripCityModificationState.SELECT_CITY_TO_REMOVE)
        val cities = trip.getCityInfo(trip.cities.drop(1), false)

        return SendMessage(
            user.id(),
            "Выбери город, который хочешь удалить из маршрута 👇\n\n"
                    + cities
        ).replyMarkup(
            BotKeyboard.getSelectCity(
                trip.id,
                trip.cities.drop(1)
            )
        )
    }

    fun removeCity(trip: Trip, cityId: Long, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val tripCity = trip.cities.find { it.id == cityId } ?: return EmptyMessage(user.id())
        val city = tripCity.city

        cityMap[user.id()] = cityId
        stateMap[user.id()] = Pair(trip.id, TripCityModificationState.CONFIRM_REMOVE_CITY)

        return EditMessageText(
            user.id(),
            message.messageId(),
            """
                Ты уверен, что хочешь удалить город ${city.displayName}, ${city.getCountry().displayName} из маршрута? 🤔
            """.trimIndent()
        ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard as InlineKeyboardMarkup)
    }

    fun confirmRemoveCity(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val cityId = cityMap[user.id()] ?: return EmptyMessage(user.id())
        val tripCity = trip.cities.find { it.id == cityId } ?: return EmptyMessage(user.id())

        cityMap.remove(user.id())
        stateMap.remove(user.id())

        if (query.data() == "yes") {
            trip.removeCity(tripCity)
            tripRepository.save(trip)

            return EditMessageText(
                user.id(),
                query.message().messageId(),
                """
                    Хорошо, удалил! 👌
                """.trimIndent()
            ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
        } else {
            return EditMessageText(
                user.id(),
                query.message().messageId(),
                """
                    Хорошо, оставляем! 👌
                """.trimIndent()
            ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
        }
    }

    fun selectCityToAddAfter(trip: Trip, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(
            DeleteMessage(
                user.id(),
                query.message().messageId()
            )
        )

        val cities = trip.getCityInfo(trip.cities, false)
        stateMap[user.id()] = Pair(trip.id, TripCityModificationState.SELECT_CITY_TO_ADD_AFTER)

        return SendMessage(
            user.id(),
            "Выбери город, после которого хочешь добавить новый 👇\n\n"
                    + cities
        ).replyMarkup(
            BotKeyboard.getSelectCity(
                trip.id,
                trip.cities
            )
        )
    }

    fun addCity(trip: Trip, tripCity: TripCity, user: User, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = Pair(trip.id, TripCityModificationState.ADD_CITY)
        cityToAddMap[user.id()] = Pair(tripCity.id, City.UNKNOWN)

        return SendMessage(
            user.id(),
            """
                Какой город хочешь добавить после ${tripCity.city.displayName}, ${tripCity.city.getCountry().displayName}? 🏙️
                
                Напиши название города или отправь локацию 📍
            """.trimIndent()
        ).replyMarkup(BotKeyboard.CANCEL.keyboard as InlineKeyboardMarkup)
    }

    fun getCityToAdd(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val cityId = cityToAddMap[user.id()]?.first ?: return EmptyMessage(user.id())
        val tripCity = trip.cities.find { it.id == cityId } ?: return EmptyMessage(user.id())

        val cityFuture: CompletableFuture<City>
        if (message.text() != null && City.getFromDisplayName(message.text()) != City.UNKNOWN) {
            val cityObject = City.getFromDisplayName(message.text())
            cityFuture = CompletableFuture.completedFuture(cityObject)
        } else {
            if (message.location() != null) {
                val lat = message.location().latitude()
                val lon = message.location().longitude()
                cityFuture = NominatimUtil.reverseGeocode(Coordinates(lat.toDouble(), lon.toDouble()))
            } else {
                if (message.text() == null) return EmptyMessage(user.id())
                cityFuture = NominatimUtil.getCityByName(message.text())
            }
        }

        cityFuture.thenAccept { city ->
            if (city == City.UNKNOWN) {
                bot.execute(
                    SendMessage(
                        user.id(),
                        """
                            Извини, но я не смог определить город :(
                             
                            Попробуй еще раз.
                        """.trimIndent()
                    )
                )

                return@thenAccept
            }

            if (city == tripCity.city) {
                bot.execute(
                    SendMessage(
                        user.id(),
                        """
                        Полететь из ${city.displayName} в ${city.displayName} звучит, конечно, интересно, но странно 😅
                        
                        Попробуй еще раз.
                    """.trimIndent()
                    )
                )
            }

            stateMap[user.id()] = Pair(trip.id, TripCityModificationState.CONFIRM_ADD_CITY)
            cityToAddMap[user.id()] = Pair(tripCity.id, city)

            bot.execute(
                SendMessage(
                    user.id(),
                    """
                        Ты хочешь добавить город ${city.displayName}, ${city.getCountry().displayName} после ${tripCity.city.displayName}, ${tripCity.city.getCountry().displayName}? 🏙️
                        
                        Всё верно?
                    """.trimIndent()
                ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
            )
        }

        ThreadUtil.execute { cityFuture.join() }
        return SendMessage(user.id(), "Определяю город... 🔎")
            .replyMarkup(BotKeyboard.REMOVE_KEYBOARD.keyboard)
    }

    fun confirmAddCity(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val cityData = cityToAddMap[user.id()] ?: return EmptyMessage(user.id())
        val tripCity = trip.cities.find { it.id == cityData.first } ?: return EmptyMessage(user.id())
        val city = cityData.second

        cityToAddMap.remove(user.id())
        stateMap.remove(user.id())

        if (query.data() == "yes") {
            trip.addCityAfter(TripCity(trip, city, null), tripCity)
            tripRepository.save(trip)

            return SendMessage(
                user.id(),
                """
                    Хорошо, добавил! 👌
                """.trimIndent()
            ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
        } else {
            return SendMessage(
                user.id(),
                """
                    Хорошо, не буду добавлять! 👌
                """.trimIndent()
            ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
        }
    }

    fun checkState(userId: Long): Pair<Long, TripCityModificationState> {
        return stateMap[userId] ?: Pair(0, TripCityModificationState.NONE)
    }

}
