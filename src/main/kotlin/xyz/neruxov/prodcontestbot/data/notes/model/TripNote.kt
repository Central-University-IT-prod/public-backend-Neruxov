package xyz.neruxov.prodcontestbot.data.notes.model

import jakarta.persistence.*
import xyz.neruxov.prodcontestbot.data.notes.enums.NoteType
import xyz.neruxov.prodcontestbot.data.notes.enums.NoteVisibility
import xyz.neruxov.prodcontestbot.data.user.model.TripUser
import xyz.neruxov.prodcontestbot.util.other.DateUtil
import java.util.*

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Entity
@Table(name = "trip_notes")
data class TripNote(

    @Id
    @GeneratedValue
    val id: Long = 0,

    @Column(name = "trip_id")
    val tripId: Long,

    @Enumerated(EnumType.STRING)
    var type: NoteType,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", referencedColumnName = "telegram_id")
    val owner: TripUser,

    @Enumerated(EnumType.STRING)
    var visibility: NoteVisibility = NoteVisibility.PRIVATE,

    val name: String?,

    var content: String,

    val creationDate: Date = Date()

) {

    fun getInfo(): String {
        return (if (name != null) "$name" else "Без названия") + "\n" +
                """
            ${type.emoji} ${type.displayName}
            👤 ${owner.username}
            📅 ${DateUtil.RU_DD_MMMM_YYYY.format(creationDate)}
            ${visibility.emoji} ${visibility.displayName}
        """.trimIndent()
    }

}