package xyz.neruxov.prodcontestbot.data.user.model

import jakarta.persistence.*
import xyz.neruxov.prodcontestbot.converter.CityConverter
import xyz.neruxov.prodcontestbot.util.osm.data.City

@Entity
@Table(name = "users")
data class TripUser(

    @Id
    @Column(name = "telegram_id", unique = true)
    val telegramId: Long,

    @Column(unique = true, length = 32)
    val username: String,

    @Convert(converter = CityConverter::class)
    var city: City,

    var age: Int?,

    @Column(length = 255)
    var bio: String?

)
