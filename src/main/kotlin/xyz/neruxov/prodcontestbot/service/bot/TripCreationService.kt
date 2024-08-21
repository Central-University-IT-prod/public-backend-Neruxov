package xyz.neruxov.prodcontestbot.service.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.*
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.data.trip.model.Trip
import xyz.neruxov.prodcontestbot.data.trip.model.TripCity
import xyz.neruxov.prodcontestbot.data.trip.repo.TripRepository
import xyz.neruxov.prodcontestbot.data.user.repo.UserRepository
import xyz.neruxov.prodcontestbot.service.api.StaticMapService
import xyz.neruxov.prodcontestbot.state.TripCreationState
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmojiUtil
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage
import xyz.neruxov.prodcontestbot.util.osm.NominatimUtil
import xyz.neruxov.prodcontestbot.util.osm.data.City
import xyz.neruxov.prodcontestbot.util.osm.data.Coordinates
import xyz.neruxov.prodcontestbot.util.other.DateUtil
import xyz.neruxov.prodcontestbot.util.other.ThreadUtil
import java.util.*
import java.util.concurrent.CompletableFuture


/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class TripCreationService(
    val userRepository: UserRepository,
    val tripRepository: TripRepository,
    val staticMapService: StaticMapService
) {

    val stateMap = mutableMapOf<Long, TripCreationState>()
    val dataMap = mutableMapOf<Long, MutableMap<String, Any>>()

    fun handleMessage(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return when (checkState(user)) {
            TripCreationState.ADD_LOCATION -> getLocation(user, message, bot)
            TripCreationState.ADD_LEAVING_DATE -> getLeavingDate(user, message, bot)
            TripCreationState.ADD_NAME -> getTripName(user, message, bot)
            else -> changeState(TripCreationState.NONE, user, message, bot)
        }
    }

    fun handleConfirmQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(AnswerCallbackQuery(query.id()))

        return when (checkState(user)) {
            TripCreationState.CONFIRM_LOCATION -> confirmLocation(query, user, bot)
            TripCreationState.ADD_LOCATION,
            TripCreationState.CONFIRM_LOCATIONS -> confirmLocations(query, user, bot)
            TripCreationState.CONFIRM_LEAVING_DATE -> confirmLeavingDate(query, user, bot)
            TripCreationState.CONFIRM_NAME -> confirmName(query, user, bot)
            else -> changeState(TripCreationState.NONE, user, query.message(), bot)
        }
    }

    fun handleQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        changeState(TripCreationState.NONE, user, query.message(), bot)
        return EmptyMessage(user.id())
    }

    fun confirmLocation(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (query.data() == "yes") {
            return nextState(user, query.message(), bot)
        } else {
            val cities = dataMap[user.id()]?.get("cities") as MutableList<City>
            cities.removeLast()
            dataMap[user.id()]?.put("cities", cities)

            previousState(user, query.message(), bot)
            return SendMessage(
                user.id(),
                """
                    Хорошо, давай попробуем еще раз. Отправь локацию или напиши название города 👇
                """.trimIndent()
            )
        }
    }

    fun removeLastLocation(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        val cities = dataMap[user.id()]?.get("cities") as MutableList<City>
        cities.removeLast()
        dataMap[user.id()]?.put("cities", cities)

        if (cities.size == 1) {
            return changeState(TripCreationState.ADD_LOCATION, user, query.message(), bot)
        }

        return changeState(TripCreationState.CONFIRM_LOCATIONS, user, query.message(), bot)
    }

    fun confirmLocations(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return changeState(TripCreationState.ADD_LEAVING_DATE, user, query.message(), bot)
    }

    fun confirmLeavingDate(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val cities = dataMap[user.id()]?.get("cities") as List<City>
        val tripCities =
            dataMap[user.id()]?.getOrDefault("trip_cities", mutableListOf<TripCity>()) as MutableList<TripCity>

        if (query.data() == "yes") {
            if (tripCities.size == cities.size - 1) {
                return changeState(TripCreationState.ADD_NAME, user, query.message(), bot)
            }

            return previousState(user, query.message(), bot)
        } else {
            tripCities.removeLast()
            dataMap[user.id()]?.put("trip_cities", tripCities)

            previousState(user, query.message(), bot)
            return SendMessage(
                user.id(),
                """
                    Хорошо, давай попробуем еще раз. Напиши дату в формате: ДД.ММ.ГГГГ
                """.trimIndent()
            )
        }
    }

    fun addLocation(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        if (!userRepository.existsByTelegramId(user.id()))
            return EmptyMessage(user.id())

        var data = dataMap[user.id()] ?: mutableMapOf()
        if (data.isNotEmpty() && checkState(user) == TripCreationState.NONE) {
            data = mutableMapOf()
            dataMap[user.id()] = data
        }

        return nextState(user, query.message(), bot)
    }

    fun getLocation(user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        if (checkState(user) != TripCreationState.ADD_LOCATION && checkState(user) != TripCreationState.CONFIRM_LOCATIONS)
            return keepState(user, message, bot)

        val city: CompletableFuture<City>

        if (message.text() != null && City.getFromDisplayName(message.text()) != City.UNKNOWN) {
            val data = dataMap[user.id()] ?: mutableMapOf()
            val cityObject = City.getFromDisplayName(message.text())

            val cities = data["cities"] as? MutableList<City> ?: mutableListOf(
                userRepository.findByTelegramId(user.id()).get().city
            )

            if (cityObject == cities.lastOrNull()) {
                return SendMessage(
                    user.id(),
                    """
                        Полететь из ${cityObject.displayName} в ${cityObject.displayName} звучит, конечно, интересно, но странно 😅
                        
                        Попробуй еще раз.
                    """.trimIndent()
                )
            }

            cities.add(cityObject)

            data["cities"] = cities
            dataMap[user.id()] = data
            return nextState(user, message, bot)
        }

        if (message.location() != null) {
            val lat = message.location().latitude()
            val lon = message.location().longitude()
            city = NominatimUtil.reverseGeocode(Coordinates(lat.toDouble(), lon.toDouble()))
        } else {
            if (message.text() == null) return EmptyMessage(user.id())
            city = NominatimUtil.getCityByName(message.text())
        }

        city.thenAccept {
            if (it == City.UNKNOWN) {
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

            val data = dataMap[user.id()] ?: mutableMapOf()

            val cities = data["cities"] as? MutableList<City> ?: mutableListOf(
                userRepository.findByTelegramId(user.id()).get().city
            )
            if (it == cities.lastOrNull()) {
                bot.execute(
                    SendMessage(
                        user.id(),
                        """
                            Полететь из ${it.displayName} в ${it.displayName} звучит, конечно, интересно, но странно 😅
                            
                            Попробуй еще раз.
                        """.trimIndent()
                    )
                )

                return@thenAccept
            }

            cities.add(it)

            data["cities"] = cities
            dataMap[user.id()] = data

            bot.execute(nextState(user, message, bot))
        }

        ThreadUtil.execute { city.join() }
        return SendMessage(user.id(), "Определяю город... 🔎")
            .replyMarkup(BotKeyboard.REMOVE_KEYBOARD.keyboard)
    }

    fun getLeavingDate(user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        if (checkState(user) != TripCreationState.ADD_LEAVING_DATE)
            return keepState(user, message, bot)

        if (message.text() == null) return EmptyMessage(user.id())

        val tripCities =
            dataMap[user.id()]?.getOrDefault("trip_cities", mutableListOf<TripCity>()) as MutableList<TripCity>
        val cities = dataMap[user.id()]?.get("cities") as List<City>
        val currentCity = cities[tripCities.size]

        if (message.text() == "Пропустить" && tripCities.size > 0) {
            return skipState(user, message, bot)
        }

        val date = DateUtil.parseDate(message.text())
            ?: return SendMessage(
                user.id(),
                """
                    Не могу распознать дату 😔
                    
                    Попробуй еще раз.
                """.trimIndent()
            )

        if (date.before(Date()) || (tripCities.size > 0 && tripCities.last().leavingDate != null && date.before(
                tripCities.last().leavingDate
            ))
        )
            return SendMessage(
                user.id(),
                """
                    Похоже, что ты так увлёкся путешествием, что решил путешествовать во времени 😅
                    
                    Попробуй еще раз.
                """.trimIndent()
            )

        tripCities.add(TripCity(currentCity, date))
        dataMap[user.id()]?.put("trip_cities", tripCities)

        return nextState(user, message, bot)
    }

    fun getTripName(user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        if (checkState(user) != TripCreationState.ADD_NAME)
            return keepState(user, message, bot)

        if (message.text() == null) return EmptyMessage(user.id())

        val tripName = message.text()
        if (tripName.length > 100) {
            return SendMessage(
                user.id(),
                """
                    Название поездки должно быть до 100 символов 😔
                    
                    Попробуй еще раз.
                """.trimIndent()
            )
        }

        dataMap[user.id()]?.put("trip_name", tripName)
        return nextState(user, message, bot)
    }

    fun confirmName(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (query.data() == "yes") {
            return nextState(user, query.message(), bot)
        } else {
            dataMap[user.id()]?.remove("trip_name")
            return previousState(user, query.message(), bot)
        }
    }

    private fun checkState(user: User): TripCreationState {
        return stateMap[user.id()] ?: TripCreationState.NONE
    }

    private fun previousState(user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        return changeState(checkState(user).previous(), user, message, bot)
    }

    private fun keepState(user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        return changeState(checkState(user), user, message, bot)
    }

    private fun skipState(user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        return changeState(checkState(user).skip(), user, message, bot)
    }

    private fun nextState(user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        return changeState(checkState(user).next(), user, message, bot)
    }

    private fun changeState(
        state: TripCreationState,
        user: User,
        message: Message,
        bot: TelegramBot
    ): BaseRequest<*, *> {
        try {
            stateMap[user.id()] = state
            when (state) {
                TripCreationState.ADD_LOCATION -> {
                    return EditMessageText(
                        user.id(),
                        message.messageId(),
                        """
                            Куда ты хочешь поехать? ✈️
                            
                            Напиши название города или отправь локацию 📍
                        """.trimIndent()
                    ).replyMarkup(BotKeyboard.TO_MAIN_MENU.keyboard as InlineKeyboardMarkup)
                }

                TripCreationState.CONFIRM_LOCATION -> {
                    val cities = dataMap[user.id()]?.get("cities") as List<City>
                    val city = cities.last()

                    return SendMessage(
                        user.id(),
                        """
                            Ты указал город: ${city.displayName}, ${city.getCountry().displayName}
                            
                            Верно?
                        """.trimIndent()
                    ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
                }

                TripCreationState.CONFIRM_LOCATIONS -> {
                    val cities = dataMap[user.id()]?.get("cities") as List<City>

                    var count = 0
                    val path = cities
                        .joinToString("\n") {
                            count++
                            "${EmojiUtil.getCountEmoji(count)} ${it.displayName}, ${it.getCountry().displayName}"
                        }.trimIndent()

                    changeState(TripCreationState.ADD_LOCATION, user, message, bot)
                    return EditMessageText(
                        user.id(),
                        message.messageId(),
                        """
                            Твой маршрут:
                        """.trimIndent() +
                                "\n\n${path}\n\n" +
                                """
                            Верно?
                            
                            Если хочешь удалить последний город из списка, нажми ❌
                            Если хочешь добавить еще один город, то напиши его название или отправь локацию 📍
                        """.trimIndent()
                    ).replyMarkup(BotKeyboard.TRIP_CONFIRM_LOCATIONS.keyboard as InlineKeyboardMarkup)
                }

                TripCreationState.ADD_LEAVING_DATE -> {
                    val cities = dataMap[user.id()]?.get("cities") as List<City>
                    val tripCities =
                        dataMap[user.id()]?.getOrDefault(
                            "trip_cities",
                            mutableListOf<TripCity>()
                        ) as MutableList<TripCity>
                    val currentCity = cities[tripCities.size]

                    var msg = SendMessage(
                        user.id(),
                        """
                            Когда ты планируешь покинуть город ${currentCity.displayName}, ${currentCity.getCountry().displayName}? 📅
                            
                            Напиши дату в формате: ДД.ММ.ГГГГ
                        """.trimIndent() +
                                if (tripCities.isNotEmpty())
                                    "\n\n" +
                                            """
                                Если хочешь пропустить этот шаг и заполнить данную информацию позже, нажми "Пропустить" 👇
                            """.trimIndent() else ""
                    )

                    if (tripCities.isNotEmpty()) {
                        msg = msg.replyMarkup(BotKeyboard.SKIP_REPLY.keyboard)
                    }

                    return msg
                }

                TripCreationState.CONFIRM_LEAVING_DATE -> {
                    val tripCities =
                        dataMap[user.id()]?.getOrDefault(
                            "trip_cities",
                            mutableListOf<TripCity>()
                        ) as MutableList<TripCity>
                    val date = tripCities.last().leavingDate
                    val formatted = DateUtil.RU_DD_MMMM_YYYY.format(date)

                    return SendMessage(
                        user.id(),
                        """
                            Ты указал дату: $formatted
                            
                            Верно?
                        """.trimIndent()
                    ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
                }

                TripCreationState.ADD_NAME -> {
                    return SendMessage(
                        user.id(),
                        """
                            Прекрасно! Как же ты назовешь эту поездку? ✍️
                        """.trimIndent()
                    ).replyMarkup(BotKeyboard.REMOVE_KEYBOARD.keyboard)
                }

                TripCreationState.CONFIRM_NAME -> {
                    val tripName = dataMap[user.id()]?.get("trip_name") as String

                    return SendMessage(
                        user.id(),
                        """
                            Фиксирую название "$tripName"? 📝
                        """.trimIndent()
                    ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
                }

                TripCreationState.DONE -> {
                    val cities = dataMap[user.id()]?.get("cities") as List<City>
                    val tripCities = dataMap[user.id()]?.get("trip_cities") as MutableList<TripCity>
                    val tripUser = userRepository.findByTelegramId(user.id()).get()
                    val tripName = dataMap[user.id()]?.get("trip_name") as String

                    for (i in tripCities.size until cities.size) {
                        tripCities.add(TripCity(cities[i], null))
                    }

                    staticMapService.getStaticMap(cities.map { it.coordinates }).thenAccept {
                        BotKeyboard.removeKeyboard(user.id(), bot)

                        val msg = bot.execute(
                            SendPhoto(
                                user.id(),
                                it
                            ).replyMarkup(BotKeyboard.TO_MY_TRIPS.keyboard)
                                .caption(
                                    """
                                     Отлично! Я все записал!
    
                                     Теперь ты помжешь посмотреть её в разделе "Мои поездки"!
                                  """.trimIndent()
                                )
                        ).message()

                        val photoId = msg.photo().last().fileId()

                        val trip = Trip(
                            owner = tripUser,
                            name = tripName,
                            cities = listOf(),
                            leavingDate = Date(),
                            image = photoId,
                        )

                        val savedTrip = tripRepository.save(trip)

                        savedTrip.leavingDate = tripCities.first().leavingDate!!
                        savedTrip.addCities(tripCities)
                        tripRepository.save(savedTrip)

                        dataMap.remove(user.id())
                        stateMap.remove(user.id())
                    }

                    return SendMessage(
                        user.id(),
                        """
                            Сохраняю твою поездку... 📝
                        """.trimIndent()
                    )
                }

                else -> return EmptyMessage(user.id())
            }
        } catch (e: Exception) {
            return EmptyMessage(user.id())
        }
    }

}