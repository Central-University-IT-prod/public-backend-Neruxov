package xyz.neruxov.prodcontestbot.state

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
enum class TripCreationState {
    NONE,
    ADD_LOCATION,
    CONFIRM_LOCATION,
    CONFIRM_LOCATIONS,
    ADD_LEAVING_DATE,
    CONFIRM_LEAVING_DATE,
    ADD_NAME,
    CONFIRM_NAME,
    DONE
    ;

    fun previous(): TripCreationState {
        return TripCreationState.entries.getOrNull(ordinal - 1) ?: NONE
    }

    fun next(): TripCreationState {
        return TripCreationState.entries.getOrNull(ordinal + 1) ?: NONE
    }

    fun skip(): TripCreationState {
        return TripCreationState.entries.getOrNull(ordinal + 2) ?: NONE
    }

}