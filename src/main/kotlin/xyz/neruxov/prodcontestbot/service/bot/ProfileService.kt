package xyz.neruxov.prodcontestbot.service.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.data.user.model.TripUser
import xyz.neruxov.prodcontestbot.data.user.repo.UserRepository
import xyz.neruxov.prodcontestbot.state.ProfileModificationState
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage
import xyz.neruxov.prodcontestbot.util.osm.NominatimUtil
import xyz.neruxov.prodcontestbot.util.osm.data.City
import xyz.neruxov.prodcontestbot.util.osm.data.Coordinates
import xyz.neruxov.prodcontestbot.util.other.ThreadUtil
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrElse

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class ProfileService(
    val userRepository: UserRepository
) {

    private final val stateMap = mutableMapOf<Long, ProfileModificationState>()
    private final val cityMap = mutableMapOf<Long, City>()

    fun handleQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val tripUser = userRepository.findById(user.id()).getOrElse { return EmptyMessage(user.id()) }
        return when (query.data()) {
            "profile" -> myProfile(tripUser, user, query, bot)
            "profile_change_city" -> changeCity(tripUser, user, query, bot)
            "profile_change_age" -> changeAge(tripUser, user, query, bot)
            "profile_change_bio" -> changeBio(tripUser, user, query, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun handleMessage(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val tripUser = userRepository.findById(user.id()).getOrElse { return EmptyMessage(user.id()) }
        return when (checkState(user)) {
            ProfileModificationState.CITY -> getCity(tripUser, user, message, bot)
            ProfileModificationState.AGE -> getAge(tripUser, user, message, bot)
            ProfileModificationState.BIO -> getBio(tripUser, user, message, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun handleConfirmQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val tripUser = userRepository.findById(user.id()).getOrElse { return EmptyMessage(user.id()) }
        return when (checkState(user)) {
            ProfileModificationState.CONFIRM_CITY -> confirmCity(tripUser, user, query, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun myProfile(tripUser: TripUser, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        return EditMessageText(
            query.message().chat().id(),
            query.message().messageId(),
            "Твой профиль:\n\n" +
                    "👤 Юзернейм: ${tripUser.username}\n" +
                    "🌆 Город: ${tripUser.city.displayName}, ${tripUser.city.getCountry().displayName}\n" +
                    "👶 Возраст: ${tripUser.age ?: "Не указан"}\n" +
                    "📃 Био: ${tripUser.bio ?: "Не указано"}"
        ).replyMarkup(BotKeyboard.PROFILE.keyboard as InlineKeyboardMarkup)
    }

    fun changeCity(tripUser: TripUser, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = ProfileModificationState.CITY
        return SendMessage(
            user.id(),
            """
                Напиши название города или отправь его геолокацию 📍 
            
                Из него будут планироваться твои путешествия.
            """.trimIndent()
        ).replyMarkup(BotKeyboard.SHARE_LOCATION.keyboard)
    }

    fun getCity(tripUser: TripUser, user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
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

            stateMap[user.id()] = ProfileModificationState.CONFIRM_CITY
            cityMap[user.id()] = city

            bot.execute(
                SendMessage(
                    user.id(),
                    """
                        Ты выбрал город ${city.displayName}, ${city.getCountry().displayName}.
                         
                        Всё верно?
                    """.trimIndent()
                ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard)
            )
        }

        ThreadUtil.execute { cityFuture.join() }
        return SendMessage(user.id(), "Определяю город... 🔎")
            .replyMarkup(BotKeyboard.REMOVE_KEYBOARD.keyboard)
    }

    fun confirmCity(tripUser: TripUser, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        if (query.data() == "yes") {
            tripUser.city = cityMap[user.id()]!!
            userRepository.save(tripUser)

            stateMap.remove(user.id())
            cityMap.remove(user.id())

            return SendMessage(
                user.id(),
                """
                    Город успешно изменен! 🌆
                     
                    Теперь ты будешь планировать путешествия из ${tripUser.city.displayName}, ${tripUser.city.getCountry().displayName}.
                """.trimIndent()
            ).replyMarkup(BotKeyboard.PROFILE.keyboard)
        } else {
            stateMap.remove(user.id())
            cityMap.remove(user.id())

            return SendMessage(user.id(), "Хорошо, отменил! 👌")
                .replyMarkup(BotKeyboard.TO_PROFILE.keyboard)
        }
    }

    fun changeAge(tripUser: TripUser, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = ProfileModificationState.AGE
        return SendMessage(
            user.id(),
            """
                Напиши свой возраст 👇
            
                Он будет использовать для подбора попутчиков.
            """.trimIndent()
        ).replyMarkup(BotKeyboard.REMOVE_KEYBOARD.keyboard)
    }

    fun getAge(tripUser: TripUser, user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        val age = message.text().toIntOrNull()
        if (age == null || age < 0 || age > 100) {
            return SendMessage(user.id(), "Напиши корректный возраст ??")
                .replyMarkup(BotKeyboard.REMOVE_KEYBOARD.keyboard)
        }

        tripUser.age = age
        userRepository.save(tripUser)
        stateMap.remove(user.id())

        return SendMessage(
            user.id(),
            """
                Возраст успешно изменен! 👶
                 
                Теперь тебе $age лет.
            """.trimIndent()
        ).replyMarkup(BotKeyboard.PROFILE.keyboard)
    }

    fun changeBio(tripUser: TripUser, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = ProfileModificationState.BIO
        return SendMessage(
            user.id(),
            """
                Напиши свою новую биографию 👇 
            
                Она будет отображаться в твоем профиле.
            """.trimIndent()
        ).replyMarkup(BotKeyboard.REMOVE_KEYBOARD.keyboard)
    }

    fun getBio(tripUser: TripUser, user: User, message: Message, bot: TelegramBot): BaseRequest<*, *> {
        tripUser.bio = message.text()
        userRepository.save(tripUser)
        stateMap.remove(user.id())

        return SendMessage(
            user.id(),
            """
                Биография успешно изменена! 📃
                 
                Теперь твоя биография: ${tripUser.bio}
            """.trimIndent()
        ).replyMarkup(BotKeyboard.PROFILE.keyboard)
    }

    fun checkState(user: User): ProfileModificationState {
        return stateMap.getOrDefault(user.id(), ProfileModificationState.NONE)
    }

}