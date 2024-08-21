package xyz.neruxov.prodcontestbot.service.api

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import xyz.neruxov.prodcontestbot.util.osm.data.Coordinates
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Service
class StaticMapService(
    @Value("\${service.static-map.url}")
    private val staticMapUrl: String
) {

    private final val routeUrl = "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
    private final val client = HttpClient.newHttpClient()
    private final val gson = Gson()

    fun getRoute(coords: List<Coordinates>): CompletableFuture<List<Coordinates>> {
        val url = routeUrl + coords.joinToString(";") { "${it.longitude},${it.latitude}" } +
                "?geometries=geojson"
        val urlObject = URL(url)

        val request = HttpRequest.newBuilder()
            .uri(urlObject.toURI())
            .GET()
            .build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply { response ->
            val json = gson.fromJson(response.body(), JsonObject::class.java)
            val routes = json.getAsJsonArray("routes")
            if (routes.size() == 0) {
                return@thenApply emptyList<Coordinates>()
            }

            val route = routes[0] as JsonObject
            val geometry = route.getAsJsonObject("geometry")
            val coordinates = geometry.getAsJsonArray("coordinates")

            return@thenApply coordinates.map { coord ->
                val c = coord as JsonArray
                Coordinates(c.get(1).asDouble, c.get(0).asDouble)
            }
        }
    }

    fun getStaticMap(coords: List<Coordinates>): CompletableFuture<ByteArray> {
        val start = System.currentTimeMillis()
        val route = getRoute(coords)

        return route.thenApply { routeCoords ->
            val body = mapOf(
                "width" to 1280,
                "height" to 720,
                "route" to routeCoords.map { listOf(it.longitude, it.latitude) },
                "locations" to coords.map { listOf(it.longitude, it.latitude) }
            )

            val url = URL("$staticMapUrl/render")
            val request = HttpRequest.newBuilder()
                .uri(url.toURI())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            println("Request took ${System.currentTimeMillis() - start}ms")
            response.body()
        }
    }

}