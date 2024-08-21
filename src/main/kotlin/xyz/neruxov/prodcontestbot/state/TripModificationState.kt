package xyz.neruxov.prodcontestbot.state

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
enum class TripModificationState {
    NONE,
    COMPANION_INVITE,
    COMPANION_REMOVE,
    ARCHIVE_TRIP,
    RENAME_TRIP,
    CONFIRM_RENAME_TRIP,
}