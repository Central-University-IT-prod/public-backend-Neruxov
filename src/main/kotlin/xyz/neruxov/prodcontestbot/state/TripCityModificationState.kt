package xyz.neruxov.prodcontestbot.state

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
enum class TripCityModificationState {
    NONE,
    SELECT_ADD_LEAVING_DATE,
    ADD_LEAVING_DATE,
    CONFIRM_LEAVING_DATE,
    SELECT_CITY_TO_ADD_AFTER,
    ADD_CITY,
    CONFIRM_ADD_CITY,
    SELECT_CITY_TO_REMOVE,
    CONFIRM_REMOVE_CITY,
}