package xyz.neruxov.prodcontestbot.util.osm.data.loader

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import xyz.neruxov.prodcontestbot.util.osm.data.City
import java.io.InputStreamReader


@Component
class CityLoader {

    @PostConstruct
    private fun loadCities() {
        val resource = ClassPathResource("cities.json")
        val inputStream = resource.inputStream
        val reader = InputStreamReader(inputStream, Charsets.UTF_8)

        val type = object : TypeToken<List<City>>() {}.type
        val cities: List<City> = Gson().fromJson(reader, type)

        City.addEntries(cities)
    }

}