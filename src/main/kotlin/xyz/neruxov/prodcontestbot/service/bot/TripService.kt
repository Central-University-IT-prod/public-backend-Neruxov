package xyz.neruxov.prodcontestbot.service.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.*
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.data.trip.model.Trip
import xyz.neruxov.prodcontestbot.data.trip.repo.TripRepository
import xyz.neruxov.prodcontestbot.data.user.model.TripUser
import xyz.neruxov.prodcontestbot.data.user.repo.UserRepository
import xyz.neruxov.prodcontestbot.state.TripModificationState
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage
import xyz.neruxov.prodcontestbot.util.other.DateUtil
import xyz.neruxov.prodcontestbot.util.other.WordUtil
import kotlin.jvm.optionals.getOrElse

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class TripService(
    val userRepository: UserRepository,
    val tripRepository: TripRepository
) {

    companion object {

        const val PAGE_SIZE = 3

    }

    private final val pageMap = mutableMapOf<Long, Int>()
    private final val archivePageMap = mutableMapOf<Long, Int>()

    private final val invitedMap = mutableMapOf<Long, List<Long>>() // <userId, list<tripId>>
    private final val nameMap = mutableMapOf<Long, String>()

    private final val stateMap = mutableMapOf<Long, Pair<Long, TripModificationState>>()

    fun handleQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if ((query.data() == "trip_prev" || query.data() == "trip_next") && !pageMap.containsKey(user.id()))
            pageMap[user.id()] = 0

        if ((query.data() == "trip_archive_prev" || query.data() == "trip_archive_next") && !archivePageMap.containsKey(
                user.id()
            )
        )
            archivePageMap[user.id()] = 0

        return when (query.data()) {
            "my_trips" -> myTrips(0, query, user, bot)
            "trip_prev" -> myTrips(pageMap[user.id()]!! - 1, query, user, bot)
            "trip_next" -> myTrips(pageMap[user.id()]!! + 1, query, user, bot)
            "trip_archive" -> myTrips(0, query, user, bot, true)
            "trip_archive_prev" -> myTrips(archivePageMap[user.id()]!! - 1, query, user, bot, true)
            "trip_archive_next" -> myTrips(archivePageMap[user.id()]!! + 1, query, user, bot, true)
            else -> {
                val parts = query.data().split("_")
                if (!parts[parts.size - 1].matches(Regex("\\d+")))
                    return EmptyMessage(user.id())

                val trip =
                    tripRepository.findById(parts[parts.size - 1].toLong()).getOrElse { return EmptyMessage(user.id()) }
                return when (parts.subList(0, parts.size - 1).joinToString("_")) {
                    "trip" -> showTrip(trip, query.message(), user, bot)
                    "trip_companions" -> companionMenu(trip, query.message(), user, bot)
                    "trip_companion_invite" -> inviteCompanion(trip, query.message(), user, bot)
                    "trip_companion_remove" -> removeCompanion(trip, query.message(), user, bot)
                    "trip_edit" -> editTrip(trip, query.message(), user, bot)
                    "trip_rename" -> renameTrip(trip, query.message(), user, bot)
                    "trip_archive" -> archiveTrip(trip, query.message(), user, bot)
                    "trip_guide" -> guideTrip(trip, query.message(), user, bot)
                    else -> EmptyMessage(user.id())
                }
            }
        }
    }

    fun handleMessage(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val check = checkState(user)
        if (check.second == TripModificationState.NONE)
            return EmptyMessage(user.id())

        val trip = tripRepository.findById(check.first).get()
        return when (check.second) {
            TripModificationState.COMPANION_INVITE -> getInvitedCompanion(trip, message, user, bot)
            TripModificationState.COMPANION_REMOVE -> getRemovedCompanion(trip, message, user, bot)
            TripModificationState.RENAME_TRIP -> getRenameTrip(trip, message, user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun handleConfirmQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (invitedMap.containsKey(user.id()))
            return handleInviteConfirm(query, user, bot)

        val check = checkState(user)
        if (check.second == TripModificationState.NONE)
            return EmptyMessage(user.id())

        val trip = tripRepository.findById(check.first).get()

        return when (check.second) {
            TripModificationState.ARCHIVE_TRIP -> confirmArchiveTrip(trip, query, user, bot)
            TripModificationState.CONFIRM_RENAME_TRIP -> confirmRenameTrip(trip, query, user, bot)
            else -> EmptyMessage(user.id())
        }
    }

    fun guideTrip(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(DeleteMessage(user.id(), message.messageId()))

        return SendMessage(
            user.id(),
            """
                С чем тебе помочь? 🔎
            """.trimIndent(),
        ).replyMarkup(BotKeyboard.getGuideMenu(trip.id))
    }

    fun myTrips(
        page: Int,
        query: CallbackQuery,
        user: User,
        bot: TelegramBot,
        archived: Boolean = false
    ): BaseRequest<*, *> {
        if (page < 0)
            return AnswerCallbackQuery(query.id())
                .text("Данная страница не существует!")

        val tripUser = userRepository.findByTelegramId(user.id()).get()
        val count = tripRepository.countByAllMembersContains(tripUser, archived)

        if (page * PAGE_SIZE >= count && !(count == 0 && page == 0))
            return AnswerCallbackQuery(query.id())
                .text("Данная страница не существует!")

        pageMap[user.id()] = page
        val trips =
            tripRepository.findByMembersContainsOrderByLeavingDateAscPaged(
                tripUser,
                archived,
                Pageable.ofSize(PAGE_SIZE).withPage(page)
            ).get().toList()

        val tripsMessage: String = if (trips.isNotEmpty()) {
            trips.joinToString("\n") { trip ->
                val date = DateUtil.RU_DD_MMMM_YYYY.format(trip.leavingDate)
                val name = trip.name
                val cityCount = trip.cities.size

                "📅 $date\n" +
                        "📃 $name\n" +
                        "✈️ $cityCount ${WordUtil.getCityWord(cityCount)}\n"
            }
        } else if (archived) {
            "Архив пуст!️"
        } else {
            "Ты пока не создал ни одну поездку 😭"
        }

        val pageName = if (archived) "Архив" else "Твои поездки"

        if (query.message().caption() != null) {
            bot.execute(
                DeleteMessage(
                    user.id(),
                    query.message().messageId()
                )
            )

            return SendMessage(
                user.id(),
                """
                    $pageName (страница ${page + 1} из ${((count + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)}):
                """.trimIndent() + "\n\n" + tripsMessage,
            ).replyMarkup(BotKeyboard.getMyTripsKeyboard(trips.map { it.id.toInt() }.toList(), archived))
        }

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            """
                $pageName (страница ${page + 1} из ${((count + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)}):
            """.trimIndent() + "\n\n" + tripsMessage,
        ).replyMarkup(BotKeyboard.getMyTripsKeyboard(trips.map { it.id.toInt() }.toList(), archived))
    }

    fun showTrip(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(DeleteMessage(user.id(), message.messageId()))

        return SendPhoto(
            user.id(),
            trip.image
        ).caption(
            "${trip.name}\n" +
                    trip.getInfo()
        ).replyMarkup(BotKeyboard.getManageTrip(trip.id, trip.owner.telegramId == user.id(), trip.isArchived))
    }

    fun companionMenu(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        var companions = trip.companions.joinToString("\n") { companion ->
            "👤 ${companion.username}"
        }

        if (companions.isEmpty())
            companions = "Список попутчиков пуст :("

        bot.execute(DeleteMessage(user.id(), message.messageId()))

        return SendMessage(
            user.id(),
            """
                Попутчики:
            """.trimIndent() + "\n\n" + companions,
        ).replyMarkup(BotKeyboard.getManageCompanions(trip.id))
    }

    fun removeCompanion(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = Pair(trip.id, TripModificationState.COMPANION_REMOVE)
        return EditMessageText(
            user.id(),
            message.messageId(),
            """
                Отправь мне юзернейм пользователя, которого хочешь удалить из поездки
            """.trimIndent() + "\n\n" +
                    trip.companions.joinToString("\n") { companion ->
                        "👤 ${companion.username}"
                    },
        ).replyMarkup(BotKeyboard.CANCEL.keyboard as InlineKeyboardMarkup)
    }

    fun getRemovedCompanion(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val companion: TripUser

        val companionOptional = userRepository.findByUsernameIgnoreCase(message.text())
        if (companionOptional.isEmpty)
            return SendMessage(
                user.id(),
                """
                    Пользователь не зарегистрирован в боте! Попробуй ещё раз.
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.CANCEL.keyboard)

        companion = companionOptional.get()

        if (!trip.companions.contains(companion))
            return SendMessage(
                user.id(),
                """
                    Пользователь ${companion.username} не является попутчиком! Попробуй ещё раз.
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.CANCEL.keyboard)

        val companions = trip.companions.toMutableList()
        companions.remove(companion)
        trip.companions = companions
        tripRepository.save(trip)

        return SendMessage(
            user.id(),
            """
                Пользователь ${companion.username} успешно удалён из поездки!
            """.trimIndent(),
        ).replyMarkup(BotKeyboard.getManageCompanions(trip.id))
    }

    fun inviteCompanion(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = Pair(trip.id, TripModificationState.COMPANION_INVITE)
        return SendMessage(
            user.id(),
            """
                Отправь мне юзернейм пользователя, которого хочешь пригласить в поездку или поделись его контактом 👇
            """.trimIndent(),
        ).replyMarkup(BotKeyboard.getShareUser(trip.id.toInt()))
    }

    fun getInvitedCompanion(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val companion: TripUser
        if (message.userShared() != null) {
            val companionOptional = userRepository.findByTelegramId(message.userShared().userId())
            if (companionOptional.isEmpty)
                return SendMessage(
                    user.id(),
                    """
                        Пользователь не зарегистрирован в боте!
                    """.trimIndent(),
                ).replyMarkup(BotKeyboard.CANCEL.keyboard)

            companion = companionOptional.get()
        } else if (message.text() != null) {
            val companionOptional = userRepository.findByUsernameIgnoreCase(message.text())
            if (companionOptional.isEmpty)
                return SendMessage(
                    user.id(),
                    """
                        Пользователь не зарегистрирован в боте!
                    """.trimIndent(),
                ).replyMarkup(BotKeyboard.CANCEL.keyboard)

            companion = companionOptional.get()
        } else {
            return SendMessage(
                user.id(),
                """
                    Не удалось определить пользователя!
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.CANCEL.keyboard)
        }

        if (user.id() == companion.telegramId)
            return SendMessage(
                user.id(),
                """
                    Нельзя пригласить самого себя в поездку!
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.getManageCompanions(trip.id))

        if (trip.companions.contains(companion))
            return SendMessage(
                user.id(),
                """
                    Пользователь ${companion.username} уже является попутчиком!
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.getManageCompanions(trip.id))

        invitedMap[companion.telegramId] = invitedMap.getOrDefault(companion.telegramId, listOf()) + trip.id

        bot.execute(
            SendPhoto(
                companion.telegramId,
                trip.image
            ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard as InlineKeyboardMarkup)
                .caption(
                    "Пользователь ${trip.owner.username} (${user.firstName()}) пригласил тебя в поездку!\n\n" +
                            "Информация о поездке \"${trip.name}\"\n" +
                            trip.getInfo()
                )
        )

        stateMap.remove(user.id())

        BotKeyboard.removeKeyboard(user.id(), bot)
        return SendMessage(
            user.id(),
            """
                Пользователь ${companion.username} успешно приглашён в поездку!
            """.trimIndent(),
        ).replyMarkup(BotKeyboard.getBackToCompanionMenu(trip.id))
    }

    fun handleInviteConfirm(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val tripId = invitedMap[user.id()]!!.last()
        val trip = tripRepository.findById(tripId).get()
        val tripUser = userRepository.findByTelegramId(query.from().id()).get()

        stateMap.remove(user.id())
        invitedMap.remove(user.id())

        if (query.data() == "yes") {
            val companions = trip.companions.toMutableList()

            trip.getAllMembersExcept(user.id()).forEach {
                bot.execute(
                    SendMessage(
                        it.telegramId,
                        "Пользователь ${tripUser.username} присоединился к поездке \"${trip.name}\" 🥳"
                    ).replyMarkup(BotKeyboard.getBackToCompanionMenu(trip.id))
                )
            }

            companions.add(tripUser)
            trip.companions = companions
            tripRepository.save(trip)

            return EditMessageCaption(
                user.id(),
                query.message().messageId(),
            ).replyMarkup(BotKeyboard.TO_MY_TRIPS.keyboard as InlineKeyboardMarkup)
                .caption(
                    query.message().caption() + "\n\n" +
                            "Приглашение принято 🎉"
                )
        }

        bot.execute(
            SendMessage(
                trip.owner.telegramId,
                "Пользователь ${tripUser.username} отклонил приглашение в поездку \"${trip.name}\" 😭"
            ).replyMarkup(BotKeyboard.getBackToCompanionMenu(trip.id))
        )

        return EditMessageCaption(
            tripUser.telegramId,
            query.message().messageId(),
        ).caption(
            query.message().caption() + "\n\n" +
                    "Приглашение отклонено 😭"
        )
    }

    fun archiveTrip(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = Pair(trip.id, TripModificationState.ARCHIVE_TRIP)

        bot.execute(DeleteMessage(user.id(), message.messageId()))

        return SendMessage(
            user.id(),
            """
                Ты уверен, что хочешь архивировать поездку "${trip.name}"?
                
                Данное действие нельзя будет отменить! ⚠️
            """.trimIndent(),
        ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard as InlineKeyboardMarkup)
    }

    fun confirmArchiveTrip(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        stateMap.remove(user.id())
        if (query.data() == "yes") {
            trip.isArchived = true
            tripRepository.save(trip)

            return EditMessageText(
                user.id(),
                query.message().messageId(),
                """
                    Поездка "${trip.name}" архивирована! 🗑️
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.TO_MY_TRIPS.keyboard as InlineKeyboardMarkup)
        }

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            """
                Архивирование поездки "${trip.name}" отменено! 👌
            """.trimIndent(),
        ).replyMarkup(BotKeyboard.TO_MY_TRIPS.keyboard as InlineKeyboardMarkup)
    }

    fun renameTrip(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        stateMap[user.id()] = Pair(trip.id, TripModificationState.RENAME_TRIP)

        bot.execute(DeleteMessage(user.id(), message.messageId()))

        return SendMessage(
            user.id(),
            """
                Отправь мне новое название для поездки "${trip.name}"
            """.trimIndent(),
        ).replyMarkup(BotKeyboard.CANCEL.keyboard)
    }

    fun getRenameTrip(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (message.text() == null) return EmptyMessage(user.id())

        val newName = message.text()
        if (newName.length > 100)
            return SendMessage(
                user.id(),
                """
                    Название поездки не должно превышать 100 символов! Попробуй ещё раз.
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.CANCEL.keyboard)

        nameMap[user.id()] = newName
        stateMap[user.id()] = Pair(trip.id, TripModificationState.CONFIRM_RENAME_TRIP)

        return SendMessage(
            user.id(),
            """
                Меняю название поездки на "$newName"? 🤔
            """.trimIndent(),
        ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard as InlineKeyboardMarkup)
    }

    fun confirmRenameTrip(trip: Trip, query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        trip.name = nameMap[user.id()]!!
        tripRepository.save(trip)

        stateMap.remove(user.id())
        nameMap.remove(user.id())

        if (query.data() == "yes") {
            return EditMessageText(
                user.id(),
                query.message().messageId(),
                """
                    Изменил! 👌
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
        } else {
            tripRepository.save(trip)
            return EditMessageText(
                user.id(),
                query.message().messageId(),
                """
                    Хорошо, не меняю! 👌
                """.trimIndent(),
            ).replyMarkup(BotKeyboard.getToManageTrip(trip.id))
        }
    }

    fun handleCancel(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        val state = checkState(user)
        if (state.second == TripModificationState.NONE)
            return EmptyMessage(user.id())

        bot.execute(AnswerCallbackQuery(query.id()))
        val previousMenu = when (state.second) {
            TripModificationState.ARCHIVE_TRIP -> BotKeyboard.TO_MY_TRIPS.keyboard
            TripModificationState.RENAME_TRIP -> BotKeyboard.TO_MY_TRIPS.keyboard
            TripModificationState.COMPANION_REMOVE -> BotKeyboard.getManageCompanions(state.first)
            else -> BotKeyboard.MAIN_MENU.keyboard
        }

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            """
                Хорошо, отменил! 👌
            """.trimIndent(),
        ).replyMarkup(previousMenu as InlineKeyboardMarkup)
    }

    fun editTrip(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (message.caption() == null) {
            bot.execute(DeleteMessage(user.id(), message.messageId()))

            return SendPhoto(
                user.id(),
                trip.image
            ).replyMarkup(BotKeyboard.getEditMenu(trip.id))
                .caption(trip.name + "\n" + trip.getInfo())
        }

        return EditMessageCaption(
            user.id(),
            message.messageId(),
        ).replyMarkup(BotKeyboard.getEditMenu(trip.id))
            .caption(message.caption())
    }

    private fun checkState(user: User): Pair<Long, TripModificationState> {
        return stateMap.getOrDefault(user.id(), Pair(0, TripModificationState.NONE))
    }

}