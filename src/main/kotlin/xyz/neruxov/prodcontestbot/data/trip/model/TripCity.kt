package xyz.neruxov.prodcontestbot.data.trip.model

import jakarta.persistence.*
import xyz.neruxov.prodcontestbot.converter.CityConverter
import xyz.neruxov.prodcontestbot.util.osm.data.City
import java.util.*

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Entity
@Table(name = "trip_cities")
data class TripCity(

    @Id
    @GeneratedValue
    val id: Long = 0,

    @Column(name = "trip_id")
    val tripId: Long = 0,

    @Convert(converter = CityConverter::class)
    val city: City,

    var leavingDate: Date?

) {

    constructor(city: City, leavingDate: Date?) : this(0, 0, city, leavingDate)
    constructor(trip: Trip, city: City, leavingDate: Date?) : this(0, trip.id, city, leavingDate)

}