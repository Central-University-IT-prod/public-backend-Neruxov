package xyz.neruxov.prodcontestbot.util.logic

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.*
import com.pengrad.telegrambot.request.DeleteMessage
import com.pengrad.telegrambot.request.SendMessage
import xyz.neruxov.prodcontestbot.data.notes.enums.NoteVisibility
import xyz.neruxov.prodcontestbot.data.trip.model.TripCity

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
enum class BotKeyboard(
    open val keyboard: Keyboard
) {
    MAIN_MENU(
        InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✈️ Новая поездка")
                    .callbackData("new_trip"),
            ),
            arrayOf(
                InlineKeyboardButton("📅 Мои поездки")
                    .callbackData("my_trips")
            ),
            arrayOf(
                InlineKeyboardButton("👤 Профиль")
                    .callbackData("profile"),
            ),
        )
    ),
    TRIP_CONFIRM_LOCATIONS(
        InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✅")
                    .callbackData("yes"),
                InlineKeyboardButton("❌")
                    .callbackData("remove_last")
            )
        )
    ),
    TO_MAIN_MENU(
        InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("⬅️")
                    .callbackData("main_menu")
            )
        )
    ),
    TO_MY_TRIPS(
        InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("⬅️")
                    .callbackData("my_trips")
            )
        )
    ),
    TO_PROFILE(
        InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("⬅️")
                    .callbackData("profile")
            )
        )
    ),
    PROFILE(
        InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("🌆 Изменить город")
                    .callbackData("profile_change_city"),
            ),
            arrayOf(
                InlineKeyboardButton("👶 Изменить возраст")
                    .callbackData("profile_change_age"),
            ),
            arrayOf(
                InlineKeyboardButton("📃 Изменить биографию")
                    .callbackData("profile_change_bio"),
            ),
            arrayOf(
                InlineKeyboardButton("⬅️")
                    .callbackData("main_menu"),
            ),
        )
    ),
    CONFIRM_INLINE(
        InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✅")
                    .callbackData("yes"),
                InlineKeyboardButton("❌")
                    .callbackData("no")
            )
        )
    ),
    SKIP_REPLY(
        ReplyKeyboardMarkup(
            arrayOf(
                KeyboardButton("Пропустить")
            )
        )
    ),
    SHARE_LOCATION(
        ReplyKeyboardMarkup(
            arrayOf(
                KeyboardButton("Поделиться локацией 📍")
                    .requestLocation(true)
            )
        )
    ),
    CANCEL(
        InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("❌")
                    .callbackData("cancel")
            )
        )
    ),
    REMOVE_KEYBOARD(
        ReplyKeyboardRemove()
    );

    companion object {

        fun getMyTripsKeyboard(tripIds: List<Int>, archive: Boolean): InlineKeyboardMarkup {
            val buttons = mutableListOf<InlineKeyboardButton>()
            for ((i, trip) in tripIds.withIndex()) {
                buttons.add(
                    InlineKeyboardButton(EmojiUtil.getCountEmoji(i + 1))
                        .callbackData("trip_$trip")
                )
            }

            return InlineKeyboardMarkup(
                buttons.toTypedArray(),
                arrayOf(
                    InlineKeyboardButton("◀️")
                        .callbackData("trip" + if (archive) "_archive_" else "_" + "prev"),
                    InlineKeyboardButton("▶️")
                        .callbackData("trip" + if (archive) "_archive_" else "_" + "next")
                ),
                if (archive) emptyArray() else arrayOf(
                    InlineKeyboardButton("✈️ Новая поездка")
                        .callbackData("new_trip"),
                ),
                if (archive) emptyArray() else arrayOf(
                    InlineKeyboardButton("📚 Архив поездок")
                        .callbackData("trip_archive")
                ),
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData(if (archive) "my_trips" else "main_menu")
                )
            )
        }

        fun getShareUser(requestId: Int): ReplyKeyboardMarkup {
            return ReplyKeyboardMarkup(
                arrayOf(
                    KeyboardButton("Поделиться контактом 📞")
                        .requestUser(KeyboardButtonRequestUser(requestId))
                )
            )
        }

        fun getBackToCompanionMenu(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("◀️")
                        .callbackData("trip_$tripId"),
                ),
            )
        }

        fun getManageCompanions(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("👤 Пригласить попутчика")
                        .callbackData("trip_companion_invite_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("❌ Удалить попутчика")
                        .callbackData("trip_companion_remove_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("◀️")
                        .callbackData("trip_$tripId"),
                ),
            )
        }

        fun getManageTrip(tripId: Long, creator: Boolean, archive: Boolean): InlineKeyboardMarkup {
            if (!creator)
                return InlineKeyboardMarkup(
                    arrayOf(
                        InlineKeyboardButton("📒 Заметки")
                            .callbackData("trip_notes_$tripId"),
                    ),
                    arrayOf(
                        InlineKeyboardButton("☁️ Прогноз погоды")
                            .callbackData("trip_weather_$tripId")
                    ),
                    arrayOf(
                        InlineKeyboardButton("⬅️")
                            .callbackData("my_trips")
                    )
                )

            return InlineKeyboardMarkup(
                if (archive) emptyArray() else arrayOf(
                    InlineKeyboardButton("👥 Попутчики")
                        .callbackData("trip_companions_$tripId"),
                ),
                if (archive) emptyArray() else arrayOf(
                    InlineKeyboardButton("☁️ Прогноз погоды")
                        .callbackData("trip_weather_$tripId")
                ),
                if (archive) emptyArray() else arrayOf(
                    InlineKeyboardButton("🦮 Гид в поездке")
                        .callbackData("trip_guide_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("📒 Заметки")
                        .callbackData("trip_notes_$tripId"),
                ),
                if (archive) emptyArray() else arrayOf(
                    InlineKeyboardButton("✏️")
                        .callbackData("trip_edit_$tripId"),
                    InlineKeyboardButton("🗑️")
                        .callbackData("trip_archive_$tripId")
                ),
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("my_trips")
                )
            )
        }

        fun removeKeyboard(userId: Long, bot: TelegramBot) {
            val msg = bot.execute(
                SendMessage(userId, "🤪")
                    .replyMarkup(REMOVE_KEYBOARD.keyboard)
            ).message()

            bot.execute(DeleteMessage(userId, msg.messageId()))
        }

        fun getToManageTrip(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_$tripId"),
                ),
            )
        }

        fun getToEditTrip(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_edit_$tripId"),
                ),
            )
        }

        fun getSelectCity(tripId: Long, cities: List<TripCity>, back: String = "trip_edit"): InlineKeyboardMarkup {
            val cityRows = mutableListOf<List<InlineKeyboardButton>>()
            val citiesPerRow = 3

            cities.chunked(citiesPerRow).forEachIndexed { chunkIndex, chunk ->
                val buttons = chunk.mapIndexed { index, city ->
                    val emoji = EmojiUtil.getCountEmoji(chunkIndex * citiesPerRow + index + 1)
                    InlineKeyboardButton(emoji).callbackData("trip_city_${tripId}_${city.id}")
                }

                cityRows.add(buttons)
            }

            val inlineKeyboardMarkup = InlineKeyboardMarkup(
                *cityRows.map { it.toTypedArray() }.toTypedArray(),
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("${back}_$tripId"),
                ),
            )

            return inlineKeyboardMarkup
        }

        fun getEditMenu(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("📅 Указать дату")
                        .callbackData("trip_edit_leaving_date_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("🏙️ Добавить город")
                        .callbackData("trip_edit_add_city_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("🗑️ Удалить город")
                        .callbackData("trip_edit_remove_city_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_$tripId"),
                ),
            )
        }

        fun getNotesMenu(tripId: Long, noteIds: List<Long>, archive: Boolean): InlineKeyboardMarkup {
            val buttons = mutableListOf<InlineKeyboardButton>()
            for ((i, trip) in noteIds.withIndex()) {
                buttons.add(
                    InlineKeyboardButton(EmojiUtil.getCountEmoji(i + 1))
                        .callbackData("trip_note_$trip")
                )
            }

            return InlineKeyboardMarkup(
                buttons.toTypedArray(),
                arrayOf(
                    InlineKeyboardButton("◀️")
                        .callbackData("trip_notes_prev"),
                    InlineKeyboardButton("▶️")
                        .callbackData("trip_notes_next")
                ),
                if (archive) emptyArray() else arrayOf(
                    InlineKeyboardButton("📝 Добавить заметку")
                        .callbackData("trip_notes_new_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_$tripId"),
                ),
            )
        }

        fun getNoteMenu(
            noteId: Long,
            tripId: Long,
            visibility: NoteVisibility,
            isOwner: Boolean,
            archive: Boolean
        ): InlineKeyboardMarkup {
            if (isOwner && !archive)
                return InlineKeyboardMarkup(
                    arrayOf(
                        InlineKeyboardButton("${visibility.emoji} ${visibility.displayName}")
                            .callbackData("trip_note_visibility_$noteId"),
                    ),
                    arrayOf(
                        InlineKeyboardButton("✏️ Редактировать")
                            .callbackData("trip_note_edit_$noteId"),
                    ),
                    arrayOf(
                        InlineKeyboardButton("🗑️ Удалить")
                            .callbackData("trip_note_remove_$noteId"),
                    ),
                    arrayOf(
                        InlineKeyboardButton("⬅️")
                            .callbackData("trip_notes_$tripId"),
                    ),
                )

            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_notes_$tripId"),
                ),
            )
        }

        fun getToNotes(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_notes_$tripId"),
                ),
            )
        }

        fun getWeatherKeyboard(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("◀️")
                        .callbackData("trip_weather_prev_$tripId"),
                    InlineKeyboardButton("▶️")
                        .callbackData("trip_weather_next_$tripId")
                ),
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_$tripId"),
                ),
            )
        }

        fun getLandmarksMenu(tripId: Long, isAbove18: Boolean): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("🏛️ Культурные объекты")
                        .callbackData("trip_cultural_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("🎢 Развлечения")
                        .callbackData("trip_amusements_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("🏨 Жилье")
                        .callbackData("trip_accomodations_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("🍽️ Еда")
                        .callbackData("trip_foods_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("🛒 Магазины")
                        .callbackData("trip_shops_$tripId"),
                ),
                if (isAbove18) arrayOf(
                    InlineKeyboardButton("🔞 Для взрослых")
                        .callbackData("trip_adult_$tripId"),
                ) else emptyArray(),
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_city_guide_$tripId"),
                ),
            )
        }

        fun getBackToLandmarksMenu(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_city_guide_$tripId"),
                ),
            )
        }

        fun getBackToLandmarksMenuForCity(tripId: Long, cityId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_city_${tripId}_$cityId"),
                ),
            )
        }

        fun getGuideMenu(tripId: Long): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(
                arrayOf(
                    InlineKeyboardButton("🔎 Что посетить?")
                        .callbackData("trip_city_guide_$tripId"),
                ),
                arrayOf(
                    InlineKeyboardButton("⬅️")
                        .callbackData("trip_$tripId"),
                ),
            )
        }

    }

}