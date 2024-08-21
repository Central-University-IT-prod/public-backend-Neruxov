package xyz.neruxov.prodcontestbot.data.user.repo

import org.springframework.data.jpa.repository.JpaRepository
import xyz.neruxov.prodcontestbot.data.user.model.TripUser
import java.util.*

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
interface UserRepository : JpaRepository<TripUser, Long> {

    fun findByTelegramId(telegramId: Long): Optional<TripUser>
    fun existsByTelegramId(telegramId: Long): Boolean

    fun findByUsernameIgnoreCase(username: String): Optional<TripUser>
    fun existsByUsernameIgnoreCase(username: String): Boolean

}