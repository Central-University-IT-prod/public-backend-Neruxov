package xyz.neruxov.prodcontestbot.data.notes.enums

import com.pengrad.telegrambot.request.*

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
enum class NoteType(
    val displayName: String,
    val emoji: String,
    val consumer: (Long, String) -> BaseRequest<*, *>,
) {
    TEXT("Текст", "📝", { chatId, content -> SendMessage(chatId, content) }),
    PHOTO("Фото", "📷", { chatId, content -> SendPhoto(chatId, content) }),
    VIDEO("Видео", "🎥", { chatId, content -> SendVideo(chatId, content) }),
    VIDEO_NOTE("Видео сообщение (кружочек)", "📹", { chatId, content -> SendVideo(chatId, content) }),
    AUDIO("Аудио", "🔊", { chatId, content -> SendAudio(chatId, content) }),
    VOICE("Голосовое сообщение", "🎤", { chatId, content -> SendVoice(chatId, content) }),
    DOCUMENT("Файл", "📄", { chatId, content -> SendDocument(chatId, content) })
}