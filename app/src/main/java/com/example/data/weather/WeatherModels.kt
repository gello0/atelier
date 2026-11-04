package com.example.data.weather

data class CityLocation(
    val name: String,
    val regionOrCountry: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentGps: Boolean = false
) {
    val displayTitle: String
        get() = if (regionOrCountry.isNotBlank()) "$name, $regionOrCountry" else name
}

data class DailyWeatherForecast(
    val dateIso: String,
    val dayLabel: String,
    val tempCurrentOrMidC: Double,
    val tempMaxC: Double,
    val tempMinC: Double,
    val feelsLikeC: Double,
    val precipitationProbability: Int,
    val windSpeedKmh: Double,
    val weatherCode: Int,
    val conditionLabel: String,
    val isRainy: Boolean,
    val isWindy: Boolean,
    val recommendedWarmthLevel: Int,
    val stylingAdvisory: String
)

data class WeatherSnapshot(
    val location: CityLocation,
    val currentTempC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val weatherCode: Int,
    val conditionLabel: String,
    val dailyForecasts: List<DailyWeatherForecast>
)

val PRESET_CITIES = listOf(
    CityLocation("New York", "United States", 40.7128, -74.0060),
    CityLocation("London", "United Kingdom", 51.5074, -0.1278),
    CityLocation("Paris", "France", 48.8566, 2.3522),
    CityLocation("Tokyo", "Japan", 35.6762, 139.6503),
    CityLocation("San Francisco", "United States", 37.7749, -122.4194),
    CityLocation("Milan", "Italy", 45.4642, 9.1900),
    CityLocation("Seoul", "South Korea", 37.5665, 126.9780),
    CityLocation("Sydney", "Australia", -33.8688, 151.2093)
)
