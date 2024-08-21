package xyz.neruxov.prodcontestbot.service.api

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.util.osm.data.City
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class OpenTripMapService(
    @Value("\${opentripmap.key}")
    private val apiKey: String
) {

    private final val RADIUS_API_URL =
        "https://api.opentripmap.com/0.1/ru/places/radius?radius=100000&lon=%s&lat=%s&kinds=%s&rate=%s&limit=25&apikey=%s"

    private final val XID_API_URL = "https://api.opentripmap.com/0.1/ru/places/xid/%s?apikey=%s"

    private final val client = HttpClient.newHttpClient()
    private final val gson = Gson()

    private final val placesCache = Caffeine.newBuilder()
        .build<Pair<City, List<PlaceType>>, List<Place>>()

    private final val urlCache = Caffeine.newBuilder()
        .build<String, String>()

    fun getPlaces(city: City, types: List<PlaceType>, rate: String): CompletableFuture<List<Place>> {
        val cached = placesCache.getIfPresent(Pair(city, types))
        if (cached != null) {
            return CompletableFuture.completedFuture(cached)
        }

        val url = RADIUS_API_URL.format(
            city.coordinates.longitude,
            city.coordinates.latitude,
            types.joinToString(",") { it.name.toLowerCase() },
            rate,
            apiKey
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply { response ->
            val json = gson.fromJson(response.body(), JsonObject::class.java)
            val places = json.getAsJsonArray("features")

            val result = places.map { place ->
                val p = place as JsonObject
                val kinds = p.getAsJsonObject("properties").get("kinds").asString.split(",")
                val kindsEnum = kinds.mapNotNull { PlaceType.findByNameOrNull(it) }

                Place(
                    p.getAsJsonObject("properties").get("name").asString,
                    kindsEnum,
                    p.getAsJsonObject("properties").get("rate").asString,
                    p.getAsJsonObject("properties").get("xid").asString,
                    getUrl(p.getAsJsonObject("properties").get("xid").asString).get()
                )
            }.distinctBy { it.xid }.take(10)

            placesCache.put(Pair(city, types), result)
            result
        }
    }

    fun getUrl(xid: String): CompletableFuture<String?> {
        val cached = urlCache.getIfPresent(xid)
        if (cached != null)
            return CompletableFuture.completedFuture(cached)

        val url = XID_API_URL.format(xid, apiKey)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply { response ->
            if (response.statusCode() != 200)
                return@thenApply null

            val json = gson.fromJson(response.body(), JsonObject::class.java)
            if (!json.has("url"))
                return@thenApply null

            val objectUrl = json.get("url").asString

            urlCache.put(xid, objectUrl)
            objectUrl
        }
    }

    data class Place(
        val name: String,
        val types: List<PlaceType>,
        val rate: String,
        val xid: String,
        val url: String?
    )

    enum class PlaceType(val displayName: String, val emoji: String) {
        CULTURAL("Культура", "🏛️"),
        HISTORIC("История", "🏰"),
        RELIGION("Религия", "⛪"),
        ARCHITECTURE("Архитектура", "🏢"),
        AMUSEMENTS("Развлечения", "🎢"),
        BARS("Бар", "🍻"),
        PUBS("Паб", "🍺"),
        CAFES("Кафе", "🍽️"),
        FAST_FOOD("Фастфуд", "🍔"),
        RESTAURANTS("Ресторан", "🍽️"),
        ACCOMODATIONS("Жилье", "🏨"),
        HOTELS("Отель", "🏨"),
        MOTELS("Мотель", "🏨"),
        HOSTELS("Хостел", "🏨"),
        APARTMENTS("Апартаменты", "🏨"),
        RESORTS("Курорт", "🏨"),
        ADULT("Для взрослых", "🔞"),
        CASINO("Казино", "🎰"),
        HOOKAH("Кальян", "🌬️"),
        NIGHTCLUBS("Ночной клуб", "🕺"),
        STRIP_CLUBS("Стрип-клуб", "💃"),
        MALLS("Торговый центр", "🛍️"),
        MARKETPLACES("Рынок", "🛍️"),
        SUPERMARKETS("Супермаркет", "🛒"),
        ;

        companion object {

            fun findByNameOrNull(name: String): PlaceType? {
                return entries.find { it.name.equals(name, ignoreCase = true) }
            }

        }

    }

}