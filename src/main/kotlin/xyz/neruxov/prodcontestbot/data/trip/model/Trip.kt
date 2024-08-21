package xyz.neruxov.prodcontestbot.data.trip.model

import jakarta.persistence.*
import xyz.neruxov.prodcontestbot.data.notes.model.TripNote
import xyz.neruxov.prodcontestbot.data.user.model.TripUser
import xyz.neruxov.prodcontestbot.util.logic.EmojiUtil
import xyz.neruxov.prodcontestbot.util.other.DateUtil
import java.util.*

@Entity
@Table(name = "trips")
data class Trip(

    @Id
    @GeneratedValue
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", referencedColumnName = "telegram_id")
    val owner: TripUser,

    @Column(length = 100)
    var name: String,

    @OrderColumn(name = "cities_order")
    @OneToMany(mappedBy = "tripId", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var cities: List<TripCity>,

    val image: String, // storing telegram file id

    var isArchived: Boolean = false,

    var leavingDate: Date = cities.first().leavingDate!!,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "trip_companions",
        joinColumns = [JoinColumn(name = "trip_id")],
        inverseJoinColumns = [JoinColumn(name = "companion_id")]
    )
    var companions: List<TripUser> = mutableListOf(),

    @OneToMany(mappedBy = "tripId", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val notes: MutableList<TripNote> = mutableListOf()

) {

    fun getAllMembers(): List<TripUser> {
        return mutableListOf(owner).plus(companions)
    }

    fun getAllMembersExcept(user: TripUser): List<TripUser> {
        return getAllMembers().filter { it != user }
    }

    fun getAllMembersExcept(telegramId: Long): List<TripUser> {
        return getAllMembers().filter { it.telegramId != telegramId }
    }

    fun getInfo(): String {
        val date = DateUtil.RU_DD_MMMM_YYYY.format(leavingDate)

        val cities = getCityInfo()

        val companions = companions.joinToString("\n") { comp ->
            "👤 ${comp.username}"
        }

        return "📅 $date\n" +
                "\n" +
                "Маршрут:\n" +
                cities + "\n\n" +
                if (companions.isNotEmpty()) "Попутчики:\n$companions" else ""
    }

    fun getCityInfo(cities: List<TripCity> = this.cities, displayDates: Boolean = true): String {
        var count = 0
        val citiesText = cities.joinToString("\n") { tripCity ->
            count++
            val city = tripCity.city

            var leaving = ""
            if (tripCity.leavingDate != null) {
                leaving = "\n🔽 ${DateUtil.RU_DD_MMMM_YYYY.format(tripCity.leavingDate)}"
            }

            "${EmojiUtil.getCountEmoji(count)} ${city.displayName}, ${city.getCountry().displayName}" +
                    if (displayDates) leaving else ""
        }

        return citiesText
    }

    fun isValidDate(city: TripCity, date: Date): Boolean {
        val cityIndex = cities.indexOf(city)
        val citiesAfter = cities.subList(cityIndex + 1, cities.size)
        val citiesBefore = cities.subList(0, cityIndex)

        return citiesAfter.all { it.leavingDate == null || it.leavingDate!!.after(date) } &&
                citiesBefore.all { it.leavingDate == null || it.leavingDate!!.before(date) }
    }

    fun addCity(tripCity: TripCity) {
        cities = cities.plus(TripCity(this, tripCity.city, tripCity.leavingDate))
    }

    fun addCities(tripCities: List<TripCity>) {
        tripCities.forEach { addCity(it) }
    }

    fun addCityAfter(tripCity: TripCity, after: TripCity) {
        val index = cities.indexOf(after)
        cities =
            cities.toMutableList().apply { add(index + 1, TripCity(this@Trip, tripCity.city, tripCity.leavingDate)) }
    }

    fun removeCity(tripCity: TripCity) {
        cities = cities.filter { it != tripCity }
    }

}
