package xyz.neruxov.prodcontestbot.data.notes.repo

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import xyz.neruxov.prodcontestbot.data.notes.model.TripNote
import xyz.neruxov.prodcontestbot.data.user.model.TripUser

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
interface NoteRepository : JpaRepository<TripNote, Long> {

    @Query(
        "SELECT note FROM TripNote note " +
                "WHERE note.tripId = :tripId " +
                "AND (note.visibility = 'PUBLIC' OR note.owner = :user) " +
                "ORDER BY note.creationDate ASC"
    )
    fun findVisibleByTripIdPaged(tripId: Long, user: TripUser, pageable: Pageable): Page<TripNote>

    @Query(
        "SELECT COUNT(note) FROM TripNote note " +
                "WHERE note.tripId = :tripId " +
                "AND (note.visibility = 'PUBLIC' OR note.owner = :user)"
    )
    fun countVisibleByTripId(tripId: Long, user: TripUser): Int

}