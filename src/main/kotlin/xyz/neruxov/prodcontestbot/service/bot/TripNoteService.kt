package xyz.neruxov.prodcontestbot.service.bot

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.*
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.data.notes.enums.NoteType
import xyz.neruxov.prodcontestbot.data.notes.enums.NoteVisibility
import xyz.neruxov.prodcontestbot.data.notes.model.TripNote
import xyz.neruxov.prodcontestbot.data.notes.repo.NoteRepository
import xyz.neruxov.prodcontestbot.data.trip.model.Trip
import xyz.neruxov.prodcontestbot.data.trip.repo.TripRepository
import xyz.neruxov.prodcontestbot.service.bot.TripService.Companion.PAGE_SIZE
import xyz.neruxov.prodcontestbot.util.logic.BotKeyboard
import xyz.neruxov.prodcontestbot.util.logic.EmptyMessage
import kotlin.jvm.optionals.getOrElse

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class TripNoteService(
    val tripRepository: TripRepository,
    val noteRepository: NoteRepository,
    val userRepository: TripRepository
) {

    companion object {

        private const val PER_PAGE = 3

    }

    private final val pageMap = mutableMapOf<Long, Int>()
    private final val noteMap =
        mutableMapOf<Long, Pair<Long, Long>>() // <userId, <tripId, noteId>>. noteId is nullable, present if overwriting
    private final val pendingRemoval = mutableMapOf<Long, Long>() // <userId, tripId>

    fun handleQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val parts = query.data().split("_")
        if (!parts[parts.size - 1].matches(Regex("\\d+")))
            return EmptyMessage(user.id())

        if ((query.data().startsWith("trip_notes_prev") || query.data()
                .startsWith("trip_notes_next")) && !pageMap.containsKey(user.id())
        )
            pageMap[user.id()] = 0

        if (query.data().startsWith("trip_note_")) {
            val note =
                noteRepository.findById(parts[parts.size - 1].toLong()).getOrElse { return EmptyMessage(user.id()) }
            val trip = tripRepository.findByNote(note)

            return when (parts.subList(0, parts.size - 1).joinToString("_")) {
                "trip_note" -> manageNote(trip, note, user, query, bot)
                "trip_note_remove" -> removeNote(trip, note, user, query, bot)
                "trip_note_edit" -> editNote(trip, note, user, query, bot)
                "trip_note_visibility" -> changeVisibility(trip, note, user, query, bot)
                else -> return EmptyMessage(user.id())
            }
        }

        val trip = tripRepository.findById(parts[parts.size - 1].toLong()).getOrElse { return EmptyMessage(user.id()) }
        return when (parts.subList(0, parts.size - 1).joinToString("_")) {
            "trip_notes" -> notesMenu(trip, 0, user, query, bot)
            "trip_notes_prev" -> notesMenu(trip, pageMap[user.id()]!! - 1, user, query, bot)
            "trip_notes_next" -> notesMenu(trip, pageMap[user.id()]!! + 1, user, query, bot)
            "trip_notes_new" -> addNote(trip, user, query, bot)
            else -> return EmptyMessage(user.id())
        }
    }

    fun handleConfirmQuery(query: CallbackQuery, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (!pendingRemoval.containsKey(user.id()))
            return EmptyMessage(user.id())

        val note = noteRepository.findById(pendingRemoval[user.id()]!!).getOrElse { return EmptyMessage(user.id()) }
        val trip = tripRepository.findByNote(note)

        return confirmRemove(trip, note, user, query, bot)
    }

    fun handleCancelQuery(user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        if (noteMap.containsKey(user.id()))
            return cancelAdd(
                tripRepository.findById(noteMap[user.id()]!!.first).getOrElse { return EmptyMessage(user.id()) },
                user,
                query,
                bot
            )

        if (pendingRemoval.containsKey(user.id())) {
            val note = noteRepository.findById(pendingRemoval[user.id()]!!).getOrElse { return EmptyMessage(user.id()) }
            val trip = tripRepository.findByNote(note)

            return cancelRemove(
                trip,
                note,
                user,
                query,
                bot
            )
        }

        return EmptyMessage(user.id())
    }

    fun handleMessage(message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        if (!noteMap.containsKey(user.id()))
            return EmptyMessage(user.id())

        val trip = tripRepository.findById(noteMap[user.id()]!!.first).getOrElse { return EmptyMessage(user.id()) }
        return getNote(trip, message, user, bot)
    }

    fun notesMenu(trip: Trip, page: Int = 0, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        if (page < 0)
            return AnswerCallbackQuery(query.id())
                .text("Данная страница не существует!")

        val tripUser = trip.getAllMembers().find { it.telegramId == user.id() } ?: return EmptyMessage(user.id())
        val count = noteRepository.countVisibleByTripId(trip.id, tripUser)

        if (page * PAGE_SIZE >= count && !(count == 0 && page == 0))
            return AnswerCallbackQuery(query.id())
                .text("Данная страница не существует!")

        pageMap[user.id()] = page
        val notes = noteRepository.findVisibleByTripIdPaged(trip.id, tripUser, Pageable.ofSize(PER_PAGE).withPage(page))
            .get().toList()

        val notesMessage = if (notes.isNotEmpty()) notes.joinToString("\n\n") {
            it.getInfo()
        } else "Заметок нет :("

        val totalPages = ((count + PER_PAGE - 1) / PER_PAGE).coerceAtLeast(1)
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
                    Заметки поездки (${page + 1}/$totalPages):
                """.trimIndent() + "\n\n" +
                        notesMessage
            ).replyMarkup(BotKeyboard.getNotesMenu(trip.id, notes.map { it.id }, trip.isArchived))
        }

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            """
                Заметки поездки (${page + 1}/$totalPages):
            """.trimIndent() + "\n\n" +
                    notesMessage
        ).replyMarkup(BotKeyboard.getNotesMenu(trip.id, notes.map { it.id }, trip.isArchived))
    }

    fun manageNote(trip: Trip, note: TripNote, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        bot.execute(note.type.consumer(user.id(), note.content))
        return SendMessage(
            user.id(),
            note.getInfo()
        ).replyMarkup(
            BotKeyboard.getNoteMenu(
                note.id,
                trip.id,
                note.visibility,
                user.id() == note.owner.telegramId,
                trip.isArchived
            )
        )
    }

    fun addNote(
        trip: Trip,
        user: User,
        query: CallbackQuery,
        bot: TelegramBot,
        noteId: Long? = null
    ): BaseRequest<*, *> {
        noteMap[user.id()] = Pair(trip.id, noteId ?: 0)

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            """
                Готов записывать! 👇
                 
                Поддерживаются следующие типы: текст, фото, видео, аудио, голосовое сообщение, видео сообщение (кружочек), файл.
            """.trimIndent()
        ).replyMarkup(BotKeyboard.CANCEL.keyboard as InlineKeyboardMarkup)
    }

    fun getNote(trip: Trip, message: Message, user: User, bot: TelegramBot): BaseRequest<*, *> {
        val type: NoteType
        val content: String
        var name: String? = null

        if (message.videoNote() != null) {
            type = NoteType.VIDEO_NOTE
            content = message.videoNote().fileId()
        } else if (message.voice() != null) {
            type = NoteType.VOICE
            content = message.voice().fileId()
        } else if (message.audio() != null) {
            type = NoteType.AUDIO
            content = message.audio().fileId()
            name = message.audio().fileName()
        } else if (message.document() != null) {
            type = NoteType.DOCUMENT
            content = message.document().fileId()
            name = message.document().fileName()
        } else if (message.video() != null) {
            type = NoteType.VIDEO
            content = message.video().fileId()
            name = message.video().fileName()
        } else if (message.photo() != null) {
            type = NoteType.PHOTO
            content = message.photo().last().fileId()
        } else if (message.text() != null) {
            type = NoteType.TEXT
            content = message.text()
            name = message.text().substring(0, 10.coerceAtMost(message.text().length)) + "..."
        } else {
            return SendMessage(
                user.id(),
                """
                   К сожалению, я не смог определить твоей заметки :( Попробуй еще раз.
                   
                   Список поддерживаемых типов: текст, фото, видео, аудио, голосовое сообщение, видео сообщение (кружочек), файл. Одновременно можно отправить только один файл.
                """.trimIndent()
            )
        }

        val tripUser = trip.getAllMembers().find { it.telegramId == user.id() } ?: return EmptyMessage(user.id())
        val data = noteMap.remove(user.id()) ?: return EmptyMessage(user.id())

        if (data.second != 0L) {
            val note = noteRepository.findById(data.second).getOrElse { return EmptyMessage(user.id()) }
            note.type = type
            note.content = content
            noteRepository.save(note)

            return SendMessage(
                user.id(),
                "Заметка обновлена! 👌"
            ).replyMarkup(BotKeyboard.getToNotes(trip.id))
        }

        trip.notes.add(
            TripNote(
                tripId = trip.id,
                type = type,
                content = content,
                name = name,
                owner = tripUser,
            )
        )

        tripRepository.save(trip)
        return SendMessage(
            user.id(),
            "Записал! 👌"
        ).replyMarkup(BotKeyboard.getToNotes(trip.id))
    }

    fun removeNote(trip: Trip, note: TripNote, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        pendingRemoval[user.id()] = note.id

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            "Ты уверен, что хочешь удалить эту заметку? ⚠️"
        ).replyMarkup(BotKeyboard.CONFIRM_INLINE.keyboard as InlineKeyboardMarkup)
    }

    fun confirmRemove(
        trip: Trip,
        note: TripNote,
        user: User,
        query: CallbackQuery,
        bot: TelegramBot
    ): BaseRequest<*, *> {
        if (query.data() == "yes") {
            trip.notes.remove(note)
            tripRepository.save(trip)

            return EditMessageText(
                user.id(),
                query.message().messageId(),
                "Хорошо, удалил! 👌"
            ).replyMarkup(BotKeyboard.getToNotes(trip.id))
        } else {
            return cancelRemove(trip, note, user, query, bot)
        }
    }

    fun cancelRemove(
        trip: Trip,
        note: TripNote,
        user: User,
        query: CallbackQuery,
        bot: TelegramBot
    ): BaseRequest<*, *> {
        pendingRemoval.remove(user.id())

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            "Хорошо, отменил! 👌"
        ).replyMarkup(
            BotKeyboard.getNoteMenu(
                note.id,
                trip.id,
                note.visibility,
                user.id() == note.owner.telegramId,
                trip.isArchived
            )
        )
    }

    fun cancelAdd(trip: Trip, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        noteMap.remove(user.id())

        return EditMessageText(
            user.id(),
            query.message().messageId(),
            "Хорошо, отменил! 👌"
        ).replyMarkup(BotKeyboard.getToNotes(trip.id))
    }

    fun changeVisibility(
        trip: Trip,
        note: TripNote,
        user: User,
        query: CallbackQuery,
        bot: TelegramBot
    ): BaseRequest<*, *> {
        note.visibility =
            if (note.visibility == NoteVisibility.PUBLIC) NoteVisibility.PRIVATE else NoteVisibility.PUBLIC
        noteRepository.save(note)

        return manageNote(trip, note, user, query, bot)
    }

    fun editNote(trip: Trip, note: TripNote, user: User, query: CallbackQuery, bot: TelegramBot): BaseRequest<*, *> {
        return addNote(trip, user, query, bot, note.id)
    }

}