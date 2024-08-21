package xyz.neruxov.prodcontestbot

import com.github.kshashov.telegram.api.MessageType
import com.github.kshashov.telegram.api.TelegramMvcController
import com.github.kshashov.telegram.api.bind.annotation.BotController
import com.github.kshashov.telegram.api.bind.annotation.BotRequest
import com.github.kshashov.telegram.api.bind.annotation.request.CallbackQueryRequest
import com.github.kshashov.telegram.api.bind.annotation.request.MessageRequest
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.*
import org.springframework.beans.factory.annotation.Value
import xyz.neruxov.prodcontestbot.service.bot.*
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@BotController
class TelegramBotController(
    @Value("\${telegram.bot.token}") private val token: String,
    val signUpService: SignUpService,
    val profileService: ProfileService,
    private val tripCreationService: TripCreationService,
    private val tripService: TripService,
    private val tripCityEditService: TripCityEditService,
    private val tripNoteService: TripNoteService,
    private val weatherService: BotWeatherService,
    private val guideService: GuideService,
) : TelegramMvcController {

    private final val mainMenuText = "Привет! Чем могу помочь? 🛫"

    @MessageRequest(value = ["/start"])
    fun start(user: User, bot: TelegramBot): SendMessage {
        return getNonEmpty(
            listOf(
                signUpService.handleStartCommand(user, bot),
                SendMessage(user.id(), mainMenuText)
                    .replyMarkup(BotKeyboard.MAIN_MENU.keyboard)
            ), user
        ) as SendMessage
    }

    @BotRequest(type = [MessageType.MESSAGE])
    fun handleMessage(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return getNonEmpty(
            listOf(
                signUpService.handleMessage(message, user, bot),
                tripCreationService.handleMessage(message, user, bot),
                tripService.handleMessage(message, user, bot),
                tripCityEditService.handleMessage(message, user, bot),
                tripNoteService.handleMessage(message, user, bot),
                profileService.handleMessage(message, user, bot)
            ), user
        )
    }

    @CallbackQueryRequest(value = ["main_menu"])
    fun mainMenu(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(AnswerCallbackQuery(query.id()))
        if (query.message().caption() != null) {
            bot.execute(
                DeleteMessage(
                    user.id(),
                    query.message().messageId()
                )
            )

            return SendMessage(user.id(), mainMenuText)
                .replyMarkup(BotKeyboard.MAIN_MENU.keyboard)
        }

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            mainMenuText,
        ).replyMarkup(BotKeyboard.MAIN_MENU.keyboard as InlineKeyboardMarkup)
    }

    @CallbackQueryRequest(value = ["yes", "no"])
    fun handleConfirmQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        return getNonEmpty(
            listOf(
                signUpService.handleConfirmQuery(query, user, bot),
                tripCreationService.handleConfirmQuery(query, user, bot),
                tripService.handleConfirmQuery(query, user, bot),
                tripCityEditService.handleConfirmQuery(query, user, bot),
                tripNoteService.handleConfirmQuery(query, user, bot),
                profileService.handleConfirmQuery(query, user, bot)
            ), user
        )
    }

    @CallbackQueryRequest(value = ["remove_last"])
    fun removeLastLocation(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        return tripCreationService.removeLastLocation(user, query, bot)
    }

    @CallbackQueryRequest(value = ["new_trip", "add_location"])
    fun newTrip(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        return tripCreationService.addLocation(user, query, bot)
    }

    @CallbackQueryRequest(value = ["cancel"])
    fun cancelQuery(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        return getNonEmpty(
            listOf(
                tripService.handleCancel(user, query, bot),
                tripCityEditService.handleCancelQuery(user, query, bot),
                tripNoteService.handleCancelQuery(user, query, bot)
            ), user
        )
    }

    @CallbackQueryRequest(value = ["*"])
    fun defaultQueryHandler(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(
            getNonEmpty(
                listOf(
                    tripService.handleQuery(query, user, bot),
                    tripCreationService.handleQuery(query, user, bot),
                    tripCityEditService.handleQuery(query, user, bot),
                    tripNoteService.handleQuery(query, user, bot),
                    profileService.handleQuery(query, user, bot),
                    weatherService.handleQuery(query, user, bot),
                    guideService.handleQuery(query, user, bot)
                ), user
            )
        )

        return AnswerCallbackQuery(query.id())
    }

    fun getNonEmpty(messages: List<BaseRequest<*, *>>, user: User): BaseRequest<*, *> {
        return messages.plus(
            SendMessage(user.id(), "Неизвестная команда 😭")
        ).first { it !is EmptyMessage }
    }

    override fun getToken() = token

}