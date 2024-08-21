package xyz.neruxov.prodcontestbot.util.osm

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import xyz.neruxov.prodcontestbot.util.osm.data.City
import xyz.neruxov.prodcontestbot.util.osm.data.Coordinates
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
class NominatimUtil {

    companion object {

        private const val NOMINATIM_REVERSE_URL =
            "https://nominatim.openstreetmap.org/reverse?format=json&accept-language=ru&lat=%s&lon=%s"
        private const val NOMINATIM_SEARCH_URL =
            "https://nominatim.openstreetmap.org/search.php?q=%s&format=jsonv2&limit=1&accept-language=ru"

        private val gson = Gson()
        private val httpClient = HttpClient.newHttpClient()

        fun reverseGeocode(coordinates: Coordinates): CompletableFuture<City> {
            val url = NOMINATIM_REVERSE_URL.format(
                coordinates.latitude.toString().replace(",", "."),
                coordinates.longitude.toString().replace(",", ".")
            )

            return makeRequest(url).thenApply {
                if (it.statusCode() != 200) {
                    return@thenApply City.UNKNOWN
                }

                val body = it.body()
                val json = gson.fromJson(body, Map::class.java)
                val address = json["address"] as Map<*, *>

                if (!address.containsKey("ISO3166-2-lvl6")) {
                    if (address.containsKey("ISO3166-2-lvl4")) {
                        return@thenApply City.getFromIso3166(address["ISO3166-2-lvl4"] as String)
                    }

                    if (address.containsKey("city")) {
                        return@thenApply City.getFromDisplayName(address["city"] as String)
                    }

                    if (address.containsKey("state")) {
                        return@thenApply City.getFromDisplayName(address["state"] as String)
                    }

                    return@thenApply City.UNKNOWN
                }

                val isoCode = address["ISO3166-2-lvl6"] as String
                val city = City.getFromIso3166(isoCode)

                return@thenApply city
            }
        }

        fun getCityByName(city: String): CompletableFuture<City> {
            val url = NOMINATIM_SEARCH_URL.format(city)

            return makeRequest(url).thenApply {
                if (it.statusCode() != 200) {
                    return@thenApply City.UNKNOWN
                }

                val json = gson.fromJson(it.body(), List::class.java)
                if (json.isEmpty()) {
                    return@thenApply City.UNKNOWN
                }

                val first = json.first() as Map<*, *>

                val lat = first["lat"] as String
                val lon = first["lon"] as String

                return@thenApply reverseGeocode(Coordinates(lat.toDouble(), lon.toDouble())).get()
            }
        }

        fun getCoordinates(city: City): CompletableFuture<Coordinates> {
            return getCoordinates(city.displayName)
        }

        fun getCoordinates(name: String): CompletableFuture<Coordinates> {
            val url = NOMINATIM_SEARCH_URL.format(name)

            return makeRequest(url).thenApply {
                if (it.statusCode() != 200) {
                    return@thenApply Coordinates(0.0, 0.0)
                }

                val json = gson.fromJson(it.body(), List::class.java)
                if (json.isEmpty()) {
                    return@thenApply Coordinates(0.0, 0.0)
                }

                val first = json.first() as Map<*, *>

                val lat = first["lat"] as String
                val lon = first["lon"] as String

                return@thenApply Coordinates(lat.toDouble(), lon.toDouble())
            }
        }

        private fun makeRequest(url: String): CompletableFuture<HttpResponse<String>> {
            val request = httpClient.sendAsync(
                HttpRequest.newBuilder()
                    .uri(url.toHttpUrl().toUri())
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )

            return request
        }

    }

}