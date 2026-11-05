package com.example.data.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class WeatherRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {

    @SuppressLint("MissingPermission")
    suspend fun resolveDeviceGpsLocation(context: Context): CityLocation? = withContext(Dispatchers.IO) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val loc = withTimeoutOrNull(6000L) {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).await()
            } ?: withTimeoutOrNull(3000L) {
                client.lastLocation.await()
            } ?: return@withContext null

            var cityName = "Local Weather"
            var countryName = "GPS"
            try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.getDefault())
                    .getFromLocation(loc.latitude, loc.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    cityName = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Local Weather"
                    countryName = addr.adminArea ?: addr.countryName ?: "GPS"
                }
            } catch (ignored: Exception) {
            }

            CityLocation(
                name = cityName,
                regionOrCountry = countryName,
                latitude = loc.latitude,
                longitude = loc.longitude,
                isCurrentGps = true
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchCities(query: String): Result<List<CityLocation>> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@runCatching emptyList()
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=6&language=en&format=json"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("City search failed (HTTP ${response.code})")
                }
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val resultsArray = root.optJSONArray("results") ?: return@use emptyList()
                buildList {
                    for (i in 0 until resultsArray.length()) {
                        val obj = resultsArray.getJSONObject(i)
                        val name = obj.optString("name", "")
                        val admin1 = obj.optString("admin1", "")
                        val country = obj.optString("country", "")
                        val regionLabel = listOf(admin1, country).filter { it.isNotBlank() }.joinToString(", ")
                        val lat = obj.optDouble("latitude", 0.0)
                        val lon = obj.optDouble("longitude", 0.0)
                        if (name.isNotBlank()) {
                            add(CityLocation(name = name, regionOrCountry = regionLabel, latitude = lat, longitude = lon))
                        }
                    }
                }
            }
        }
    }

    suspend fun fetchWeatherForecast(location: CityLocation): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}" +
                "&longitude=${location.longitude}" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,precipitation_probability_max,wind_speed_10m_max" +
                "&timezone=auto"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Weather service returned HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val current = root.getJSONObject("current")
                val currentTemp = current.optDouble("temperature_2m", 18.0)
                val feelsLike = current.optDouble("apparent_temperature", currentTemp)
                val humidity = current.optInt("relative_humidity_2m", 55)
                val windSpeed = current.optDouble("wind_speed_10m", 10.0)
                val currentCode = current.optInt("weather_code", 0)

                val daily = root.getJSONObject("daily")
                val times = daily.getJSONArray("time")
                val codes = daily.getJSONArray("weather_code")
                val maxTemps = daily.getJSONArray("temperature_2m_max")
                val minTemps = daily.getJSONArray("temperature_2m_min")
                val apparentMax = daily.optJSONArray("apparent_temperature_max")
                val precipProbs = daily.optJSONArray("precipitation_probability_max")
                val maxWinds = daily.optJSONArray("wind_speed_10m_max")

                val forecasts = buildList {
                    val count = minOf(times.length(), 7)
                    for (i in 0 until count) {
                        val dateIso = times.getString(i)
                        val maxT = maxTemps.optDouble(i, currentTemp)
                        val minT = minTemps.optDouble(i, currentTemp - 5.0)
                        val midT = if (i == 0) currentTemp else ((maxT + minT) / 2.0)
                        val dayFeelsLike = if (i == 0) feelsLike else (apparentMax?.optDouble(i, maxT) ?: maxT)
                        val code = if (i == 0) currentCode else codes.optInt(i, 0)
                        val precip = precipProbs?.optInt(i, if (isRainyCode(code)) 70 else 10) ?: 10
                        val wind = if (i == 0) windSpeed else (maxWinds?.optDouble(i, 12.0) ?: 12.0)

                        val dayLabel = formatDayLabel(dateIso, i)
                        val condition = mapWmoConditionLabel(code)
                        val rainy = isRainyCode(code) || precip >= 45
                        val windy = wind >= 24.0
                        val targetWarmth = recommendedWarmthForTemp(dayFeelsLike, windy)
                        val advisory = buildStylingAdvisory(dayFeelsLike, rainy, windy, condition)

                        add(
                            DailyWeatherForecast(
                                dateIso = dateIso,
                                dayLabel = dayLabel,
                                tempCurrentOrMidC = (midT * 10.0).roundToInt() / 10.0,
                                tempMaxC = (maxT * 10.0).roundToInt() / 10.0,
                                tempMinC = (minT * 10.0).roundToInt() / 10.0,
                                feelsLikeC = (dayFeelsLike * 10.0).roundToInt() / 10.0,
                                precipitationProbability = precip,
                                windSpeedKmh = (wind * 10.0).roundToInt() / 10.0,
                                weatherCode = code,
                                conditionLabel = condition,
                                isRainy = rainy,
                                isWindy = windy,
                                recommendedWarmthLevel = targetWarmth,
                                stylingAdvisory = advisory
                            )
                        )
                    }
                }

                WeatherSnapshot(
                    location = location,
                    currentTempC = (currentTemp * 10.0).roundToInt() / 10.0,
                    feelsLikeC = (feelsLike * 10.0).roundToInt() / 10.0,
                    humidityPercent = humidity,
                    windSpeedKmh = (windSpeed * 10.0).roundToInt() / 10.0,
                    weatherCode = currentCode,
                    conditionLabel = mapWmoConditionLabel(currentCode),
                    dailyForecasts = forecasts
                )
            }
        }
    }

    private fun formatDayLabel(dateIso: String, index: Int): String {
        if (index == 0) return "Today"
        if (index == 1) return "Tomorrow"
        return try {
            val date = LocalDate.parse(dateIso)
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        } catch (e: Exception) {
            dateIso.takeLast(5)
        }
    }

    companion object {
        fun mapWmoConditionLabel(code: Int): String = when (code) {
            0 -> "Clear Sky"
            1 -> "Mostly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Light Drizzle"
            61, 63, 65, 66, 67, 80, 81, 82 -> "Rain Showers"
            71, 73, 75, 77, 85, 86 -> "Snowfall"
            95, 96, 99 -> "Thunderstorm"
            else -> "Partly Cloudy"
        }

        fun isRainyCode(code: Int): Boolean = code in setOf(
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99
        )

        fun recommendedWarmthForTemp(feelsLikeC: Double, isWindy: Boolean): Int {
            val effectiveTemp = if (isWindy) feelsLikeC - 2.5 else feelsLikeC
            return when {
                effectiveTemp >= 25.0 -> 1 // Ultralight breathable linen/cotton
                effectiveTemp >= 19.0 -> 2 // Light shirt / light layering
                effectiveTemp >= 13.0 -> 3 // Mid-weight knit / light jacket
                effectiveTemp >= 6.0 -> 4  // Trench / structured coat + sweater
                else -> 5                  // Heavy wool coat + scarf + thermal layers
            }
        }

        fun buildStylingAdvisory(
            feelsLikeC: Double,
            isRainy: Boolean,
            isWindy: Boolean,
            condition: String
        ): String {
            val base = when {
                feelsLikeC >= 25.0 -> "Opt for breathable natural fibers like linen, poplin, or silk."
                feelsLikeC >= 18.0 -> "Pleasant mild air — a single crisp layer or light overshirt works effortlessly."
                feelsLikeC >= 11.0 -> "Transitional chill — pair a mid-weight knit or shirt with a tailored outer layer."
                feelsLikeC >= 4.0 -> "Crisp cold conditions — anchor your silhouette with a structured coat and knitwear."
                else -> "Deep winter chill — prioritize high-insulation outerwear, wool trousers, and a warm scarf."
            }
            val rainNote = if (isRainy) " Rain is in the forecast; select water-repellent outerwear and treated footwear." else ""
            val windNote = if (!isRainy && isWindy) " Breezy conditions; include a wind-blocking outer shell." else ""
            return "$condition · $base$rainNote$windNote"
        }
    }
}
