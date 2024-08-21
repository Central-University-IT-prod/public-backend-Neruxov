package xyz.neruxov.prodcontestbot.state

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 * Must be in order of the sign up process
 */
enum class SignUpState {
    NONE,
    USERNAME,
    CONFIRM_USERNAME,
    LOCATION,
    CONFIRM_LOCATION,
    AGE,
    CONFIRM_AGE,
    BIO,
    CONFIRM_BIO,
    DONE
    ;

    fun previous(): SignUpState {
        return entries.getOrNull(ordinal - 1) ?: NONE
    }

    fun next(): SignUpState {
        return entries.getOrNull(ordinal + 1) ?: NONE
    }

    fun skip(): SignUpState {
        return entries.getOrNull(ordinal + 2) ?: NONE
    }

}