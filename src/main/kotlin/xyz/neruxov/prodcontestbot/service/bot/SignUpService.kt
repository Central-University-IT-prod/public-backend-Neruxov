package xyz.neruxov.prodcontestbot.service.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.SendMessage
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.data.user.model.TripUser
import xyz.neruxov.prodcontestbot.data.user.repo.UserRepository
import xyz.neruxov.prodcontestbot.state.SignUpState
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage
import xyz.neruxov.prodcontestbot.util.osm.NominatimUtil
import xyz.neruxov.prodcontestbot.util.osm.data.City
import xyz.neruxov.prodcontestbot.util.osm.data.Coordinates
import xyz.neruxov.prodcontestbot.util.other.ThreadUtil
import java.util.concurrent.CompletableFuture


/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class SignUpService(
    val userRepository: UserRepository,
) {

    companion object {

        private const val USERNAME_REGEX = "^[a-zA-Z0-9]{5,32}$"

    }

    val stateMap = mutableMapOf<Long, SignUpState>()
    val dataMap = mutableMapOf<Long, MutableMap<String, Any>>()

    fun handleStartCommand(user: User, bot: TelegramBot): SendMessage {
        if (userRepository.existsByTelegramId(user.id()))
            return EmptyMessage(user.id())

        if (checkState(user) != SignUpState.NONE)
            return keepState(user, bot)

        return nextState(user, bot)
    }

    fun handleMessage(message: Message, user: User, bot: TelegramBot): SendMessage {
        return when (checkState(user)) {
            SignUpState.USERNAME -> getUsername(message, user, bot)
            SignUpState.LOCATION -> getLocation(message, user, bot)
            SignUpState.AGE -> getAge(message, user, bot)
            SignUpState.BIO -> getBio(message, user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun handleConfirmQuery(query: CallbackQuery, user: User, bot: TelegramBot): SendMessage {
        bot.execute(AnswerCallbackQuery(query.id()))

        return when (checkState(user)) {
            SignUpState.CONFIRM_USERNAME -> confirmUsername(query, user, bot)
            SignUpState.CONFIRM_LOCATION -> confirmLocation(query, user, bot)
            SignUpState.CONFIRM_AGE -> confirmAge(query, user, bot)
            SignUpState.CONFIRM_BIO -> confirmBio(query, user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun getUsername(message: Message, user: User, bot: TelegramBot): SendMessage {
        if (message.text() == null)
            return EmptyMessage(user.id())

        if (message.text().length > 32)
            return SendMessage(
                user.id(),
                """
                    Никнейм слишком длинный :( Попробуй уместить его в 32 символа.
                """.trimIndent()
            )

        if (message.text().length < 5)
            return SendMessage(
                user.id(),
                """
                    Никнейм слишком короткий :( Попробуй уместить его в 5 символов.
                """.trimIndent()
            )

        if (!message.text().matches(Regex(USERNAME_REGEX)))
            return SendMessage(
                user.id(),
                """
                    Никнейм может содержать только латинские буквы и цифры :( Попробуй придумать другой.
                """.trimIndent()
            )

        if (userRepository.existsByUsernameIgnoreCase(message.text()))
            return SendMessage(
                user.id(),
                """
                    Такой никнейм уже занят :( Попробуй придумать другой.
                """.trimIndent()
            )

        dataMap[user.id()] = mutableMapOf("username" to message.text())
        return nextState(user, bot)
    }

    fun confirmUsername(query: CallbackQuery, user: User, bot: TelegramBot): SendMessage {
        if (query.data() == "yes") {
            return nextState(user, bot)
        } else {
            dataMap.remove(user.id())
            previousState(user, bot)
            return SendMessage(
                user.id(),
                """
                    Хорошо, давай попробуем еще раз. Укажи свой никнейм.
                """.trimIndent()
            )
        }
    }

    fun getLocation(message: Message, user: User, bot: TelegramBot): SendMessage {
        val city: CompletableFuture<City>

        if (message.text() != null && City.getFromDisplayName(message.text()) != City.UNKNOWN) {
            dataMap[user.id()]?.put("city", City.getFromDisplayName(message.text()))
            return nextState(user, bot)
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

            dataMap[user.id()]?.put("city", it)
            bot.execute(nextState(user, bot))
        }

        ThreadUtil.execute { city.join() }
        return SendMessage(user.id(), "Определяю ваш город... 🔎")
            .replyMarkup(BotKeyboard.REMOVE_KEYBOARD.keyboard)
    }

    fun confirmLocation(query: CallbackQuery, user: User, bot: TelegramBot): SendMessage {
        if (query.data() == "yes") {
            return nextState(user, bot)
        } else {
            dataMap[user.id()]?.remove("city")
            previousState(user, bot)
            return SendMessage(
                user.id(),
                """
                    Хорошо, давай попробуем еще раз. Поделись локацией или напиши название города 👇
                """.trimIndent()
            ).replyMarkup(BotKeyboard.SHARE_LOCATION.keyboard)
        }
    }

    fun getAge(message: Message, user: User, bot: TelegramBot): SendMessage {
        if (message.text() == null)
            return EmptyMessage(user.id())

        if (message.text() == "Пропустить")
            return skipState(user, bot)

        val age = message.text().toIntOrNull()
        if (age == null || age < 0) {
            return SendMessage(
                user.id(),
                """
                    Это не очень похоже на возраст. Попробуй еще раз.
                """.trimIndent()
            )
        }

        dataMap[user.id()]?.put("age", age)
        return nextState(user, bot)
    }

    fun confirmAge(query: CallbackQuery, user: User, bot: TelegramBot): SendMessage {
        return when (query.data()) {
            "yes" -> nextState(user, bot)
            "no" -> {
                dataMap[user.id()]?.remove("age")
                previousState(user, bot)
                SendMessage(
                    user.id(),
                    """
                        Хорошо, давай попробуем еще раз. Укажи свой возраст.
                    """.trimIndent()
                )
            }

            else -> EmptyMessage(user.id())
        }
    }

    fun getBio(message: Message, user: User, bot: TelegramBot): SendMessage {
        if (message.text() == "Пропустить")
            return skipState(user, bot)

        if (message.text().length > 255) {
            return SendMessage(
                user.id(),
                """
                    Чересчур подробно, попробуй уместиться в 255 символов.
                """.trimIndent()
            )
        }

        dataMap[user.id()]?.put("bio", message.text())
        return nextState(user, bot)
    }

    fun confirmBio(query: CallbackQuery, user: User, bot: TelegramBot): SendMessage {
        if (query.data() == "yes") {
            return nextState(user, bot)
        } else {
            dataMap[user.id()]?.remove("bio")
            previousState(user, bot)
            return SendMessage(
                user.id(),
                """
                    Хорошо, давай попробуем еще раз. Расскажи немного о себе.
                """.trimIndent()
            )
        }
    }

    private fun checkState(user: User): SignUpState {
        return stateMap[user.id()] ?: SignUpState.NONE
    }

    private fun previousState(user: User, bot: TelegramBot): SendMessage {
        return changeState(checkState(user).previous(), user, bot)
    }

    private fun keepState(user: User, bot: TelegramBot): SendMessage {
        return changeState(checkState(user), user, bot)
    }

    private fun nextState(user: User, bot: TelegramBot): SendMessage {
        return changeState(checkState(user).next(), user, bot)
    }

    private fun skipState(user: User, bot: TelegramBot): SendMessage {
        return changeState(checkState(user).skip(), user, bot)
    }

    private fun changeState(signUpState: SignUpState, user: User, bot: TelegramBot): SendMessage {
        stateMap[user.id()] = signUpState
        when (signUpState) {
            SignUpState.USERNAME -> {
                return SendMessage(
                    user.id(),
                    """
                        Привет, ${user.firstName()}! 👋
        
                        🤖 Я помогу тебе спланировать любое твое путешествие - можешь убедиться в этом сам!
        
                        Сперва придумай себе никнейм - он будет использоваться для идентификации тебя в боте.
                    """.trimIndent()
                )
            }

            SignUpState.CONFIRM_USERNAME -> {
                val username = dataMap[user.id()]?.get("username") as String

                return SendMessage(
                    user.id(),
                    """
                        Твой никнейм: $username
                        
                        ⚠️ Никнейм нельзя будет изменить в будущем. 
                        
                        Уверен?
                    """.trimIndent()
                ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
            }

            SignUpState.LOCATION -> {
                return SendMessage(
                    user.id(),
                    """
                        Отлично! Теперь укажи свой город, из которого ты планируешь путешествовать.
                        
                        Напиши название города или поделись локацией 📍
                    """.trimIndent()
                ).replyMarkup(BotKeyboard.SHARE_LOCATION.keyboard)
            }

            SignUpState.CONFIRM_LOCATION -> {
                val city = dataMap[user.id()]?.get("city") as City

                return SendMessage(
                    user.id(),
                    """
                        Ты из города ${city.displayName}, ${city.getCountry().displayName}?
                    """.trimIndent()
                ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
            }

            SignUpState.AGE -> {
                return SendMessage(
                    user.id(),
                    """
                        Отлично! Теперь укажите свой возраст.
                        
                        Возраст будет использоваться для подбора попутчиков для ваших путешествий.
                        
                        Если ты хочешь пропустить этот шаг и заполнить возраст позже, нажми "Пропустить" 👇
                    """.trimIndent()
                ).replyMarkup(BotKeyboard.SKIP_REPLY.keyboard)
            }

            SignUpState.CONFIRM_AGE -> {
                val age = dataMap[user.id()]?.get("age") as Int

                return SendMessage(
                    user.id(),
                    """
                            Твой возраст: $age
                            
                            Все верно?
                        """.trimIndent()
                ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
            }

            SignUpState.BIO -> {
                return SendMessage(
                    user.id(),
                    """
                        Отлично! Теперь расскажи немного о себе. Это поможет другим пользователям узнать тебя получше.
                        
                        Если ты хотите пропустить этот шаг и заполнить биографию позже, нажми "Пропустить" 👇
                    """.trimIndent(),
                ).replyMarkup(BotKeyboard.SKIP_REPLY.keyboard)
            }

            SignUpState.CONFIRM_BIO -> {
                val bio = dataMap[user.id()]?.get("bio") as String
                return SendMessage(
                    user.id(),
                    """
                        Твоя биография:
                        
                        $bio
                        
                        Все верно?
                    """.trimIndent()
                ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
            }

            SignUpState.DONE -> {
                val city = dataMap[user.id()]?.get("city") as City
                val age = dataMap[user.id()]?.get("age") as Int?
                val bio = dataMap[user.id()]?.get("bio") as String?
                val username = dataMap[user.id()]?.get("username") as String

                userRepository.save(
                    TripUser(
                        user.id(),
                        username,
                        city,
                        age,
                        bio
                    )
                )

                dataMap.remove(user.id())
                stateMap.remove(user.id())

                BotKeyboard.removeKeyboard(user.id(), bot)

                return SendMessage(
                    user.id(),
                    """
                        Отлично! Твой профиль успешно создан. Теперь ты можешь начать пользоваться ботом.
                    """.trimIndent()
                ).replyMarkup(BotKeyboard.MAIN_MENU.keyboard)
            }

            else -> return EmptyMessage(user.id())
        }
    }

}