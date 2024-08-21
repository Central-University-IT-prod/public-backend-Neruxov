package xyz.neruxov.prodcontestbot.data.trip.repo

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import xyz.neruxov.prodcontestbot.data.notes.model.TripNote
import xyz.neruxov.prodcontestbot.data.trip.model.Trip
import xyz.neruxov.prodcontestbot.data.user.model.TripUser


interface TripRepository : JpaRepository<Trip, Long> {

    @Query(
        "SELECT t FROM Trip t " +
                "WHERE (t.owner = :tripUser " +
                "OR :tripUser MEMBER OF t.companions) " +
                "AND t.isArchived = :archived " +
                "ORDER BY t.leavingDate ASC"
    )
    fun findByMembersContainsOrderByLeavingDateAscPaged(
        @Param("tripUser") tripUser: TripUser,
        @Param("archived") archived: Boolean,
        pageable: Pageable
    ): Page<Trip>

    @Query("SELECT COUNT(t) FROM Trip t WHERE t.owner = :tripUser OR :tripUser MEMBER OF t.companions AND t.isArchived = :archived")
    fun countByAllMembersContains(tripUser: TripUser, archived: Boolean = false): Int

    @Query("SELECT t FROM Trip t WHERE :note MEMBER OF t.notes")
    fun findByNote(note: TripNote): Trip

}
