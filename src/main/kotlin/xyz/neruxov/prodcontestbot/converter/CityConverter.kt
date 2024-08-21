package xyz.neruxov.prodcontestbot.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import xyz.neruxov.prodcontestbot.util.osm.data.City

/**
 * @author <a href="https://github.com/Neruxov">Neruxov</a>
 */
@Converter(autoApply = true)
class CityConverter : AttributeConverter<City, String> {

    override fun convertToDatabaseColumn(p0: City?): String {
        return p0?.iso ?: "XX-XX"
    }

    override fun convertToEntityAttribute(p0: String?): City {
        return City.getFromIso3166WithUnderscore(p0 ?: "XX-XX")
    }

}