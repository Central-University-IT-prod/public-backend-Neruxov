package xyz.neruxov.prodcontestbot.data.notes.enums

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
enum class NoteVisibility(
    val displayName: String,
    val emoji: String
) {
    PUBLIC("Публичная", "🌐"),
    PRIVATE("Приватная", "🔒")
}