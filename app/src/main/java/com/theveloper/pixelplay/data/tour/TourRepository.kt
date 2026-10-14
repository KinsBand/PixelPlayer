package com.theveloper.pixelplay.data.tour

import android.content.Context
import com.google.gson.Gson
import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.data.network.ticketmaster.TicketmasterApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** One show on an artist's tour. */
data class ArtistShow(
    val id: String,
    /** ISO yyyy-MM-dd, local to the venue. */
    val date: String,
    val time: String?,
    val venue: String,
    val city: String,
    val region: String?,
    val country: String,
    val countryCode: String,
    val latitude: Double?,
    val longitude: Double?,
    val ticketUrl: String?,
    val cancelled: Boolean = false
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
}

/** An artist's tour, or the reason there's none to show. */
data class ArtistTour(
    val shows: List<ArtistShow>,
    val fetchedAt: Long
) {
    val upcoming: List<ArtistShow>
        get() {
            val today = LocalDate.now()
            return shows.filter { it.localDate?.isBefore(today) == false }
        }
    val countries: List<Pair<String, Int>>
        get() = upcoming.groupingBy { it.country }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key to it.value }
}

/**
 * Tour dates from the Ticketmaster Discovery API: find the artist by exact name (music only), then
 * its shows from 30 days ago onward. Cached in memory and on disk for [TTL_MS] so a page visit
 * costs at most two calls a day per artist.
 *
 * Returns an empty tour when no key is configured ([isConfigured]), the artist isn't found, or the
 * request fails with nothing cached, so the artist page never waits on or breaks because of it.
 */
@Singleton
class TourRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TicketmasterApiService
) {
    private val gson = Gson()
    private val memory = android.util.LruCache<String, ArtistTour>(40)
    private val dir: File get() = File(context.cacheDir, "artist_tours").apply { mkdirs() }

    val isConfigured: Boolean get() = BuildConfig.TICKETMASTER_API_KEY.isNotBlank()

    suspend fun getTour(artistName: String, forceRefresh: Boolean = false): ArtistTour = withContext(Dispatchers.IO) {
        val key = fold(artistName)
        val empty = ArtistTour(emptyList(), System.currentTimeMillis())
        if (!isConfigured || key.isBlank()) return@withContext empty

        val cached = memory.get(key) ?: readDisk(key)?.also { memory.put(key, it) }
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.fetchedAt < TTL_MS) {
            return@withContext cached
        }
        try {
            val tour = fetch(artistName)
            memory.put(key, tour)
            writeDisk(key, tour)
            tour
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.w(e, "Tour dates unavailable for %s", artistName)
            cached ?: empty
        }
    }

    private suspend fun fetch(artistName: String): ArtistTour {
        val apiKey = BuildConfig.TICKETMASTER_API_KEY
        val wanted = fold(artistName)
        // Exact name only: a near match ("The X Tribute Band") would show the wrong tour.
        val attraction = api.searchAttractions(artistName, apiKey).embedded?.attractions.orEmpty()
            .firstOrNull { a ->
                fold(a.name) == wanted &&
                    a.classifications.none { it.segment?.name?.equals("Music", true) == false }
            }
            ?: return ArtistTour(emptyList(), System.currentTimeMillis())

        val since = Instant.now().minus(RECENT_DAYS, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        val events = api.getEvents(
            attractionId = attraction.id,
            apiKey = apiKey,
            startDateTime = DateTimeFormatter.ISO_INSTANT.format(since.atOffset(ZoneOffset.UTC).toInstant())
        ).embedded?.events.orEmpty()

        val shows = events.mapNotNull { e ->
            val date = e.dates?.start?.localDate ?: return@mapNotNull null
            val venue = e.embedded?.venues?.firstOrNull() ?: return@mapNotNull null
            val city = venue.city?.name ?: return@mapNotNull null
            ArtistShow(
                id = e.id,
                date = date,
                time = e.dates.start.localTime?.take(5),
                venue = venue.name.orEmpty(),
                city = city,
                region = venue.state?.name,
                country = venue.country?.name ?: venue.country?.countryCode.orEmpty(),
                countryCode = venue.country?.countryCode.orEmpty(),
                latitude = venue.location?.latitude?.toDoubleOrNull(),
                longitude = venue.location?.longitude?.toDoubleOrNull(),
                ticketUrl = e.url,
                cancelled = e.dates.status?.code.equals("cancelled", true)
            )
        }
            .filterNot { it.cancelled }
            // The same night is often listed twice (VIP / general admission).
            .distinctBy { "${it.date}|${fold(it.venue)}" }
            .sortedBy { it.date }
        return ArtistTour(shows, System.currentTimeMillis())
    }

    private fun readDisk(key: String): ArtistTour? = runCatching {
        val file = File(dir, "$key.json")
        if (!file.exists()) null else gson.fromJson(file.readText(), ArtistTour::class.java)
    }.getOrNull()

    private fun writeDisk(key: String, tour: ArtistTour) {
        runCatching { File(dir, "$key.json").writeText(gson.toJson(tour)) }
    }

    private fun fold(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    companion object {
        const val TTL_MS = 12 * 60 * 60 * 1000L
        /** Shows this many days back are kept, so a just-finished leg still shows. */
        const val RECENT_DAYS = 30L
    }
}
