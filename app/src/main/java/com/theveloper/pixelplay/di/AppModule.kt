package com.theveloper.pixelplay.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.PixelPlayApplication
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.database.EngagementDao
import com.theveloper.pixelplay.data.database.EnrichmentDao
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.GDriveDao
import com.theveloper.pixelplay.data.database.LyricsDao
import com.theveloper.pixelplay.data.database.AiCacheDao
import com.theveloper.pixelplay.data.database.AiUsageDao
import com.theveloper.pixelplay.data.database.LocalPlaylistDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.PixelPlayDatabase
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import io.ktor.serialization.kotlinx.json.json
import com.theveloper.pixelplay.data.database.TransitionDao
import com.theveloper.pixelplay.data.database.CloudSongDao
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import com.theveloper.pixelplay.data.network.lastfm.LastFmApiService
import com.theveloper.pixelplay.data.network.musicbrainz.CoverArtArchiveApiService
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzApiService
import com.theveloper.pixelplay.data.network.lyrics.LrcLibApiService
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.LyricsRepositoryImpl
import com.theveloper.pixelplay.data.repository.MediaStoreSongRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.MusicRepositoryImpl
import com.theveloper.pixelplay.data.repository.SongRepository
import com.theveloper.pixelplay.data.repository.TransitionRepository
import com.theveloper.pixelplay.data.repository.TransitionRepositoryImpl
import com.theveloper.pixelplay.data.repository.FolderTreeBuilder
import dagger.Module
import dagger.Provides
import dagger.Lazy
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // MusicBrainz requires a descriptive User-Agent with contact info;
    // other APIs are fine with the generic one.
    private const val DEFAULT_USER_AGENT = "PixelPlayer/1.0 (Android; Music Player)"
    private const val MUSICBRAINZ_USER_AGENT = "PixelPlay/1.0 ( https://github.com/KinsBand/PixelPlayer )"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    // Hosts routed to NetworkPurpose.Enrichment by the policy interceptors.
    private val enrichmentHostMarkers = listOf(
        "musicbrainz.org",
        "coverartarchive.org",
        "ws.audioscrobbler.com",
        "last.fm",
        "lastfm"
    )

    private fun isLyricsBrowserHost(host: String): Boolean =
        host.endsWith("music.163.com", ignoreCase = true) ||
            host.endsWith("y.qq.com", ignoreCase = true) ||
            host.endsWith("kugou.com", ignoreCase = true) ||
            host.endsWith("musixmatch.com", ignoreCase = true)

    /** Lyrics hosts: offline mode and the lyrics switch (NetworkPurpose.Lyrics) apply to them. */
    private fun isLyricsRequest(host: String, url: String): Boolean =
        url.contains("lrclib.net", ignoreCase = true) ||
            isLyricsBrowserHost(host) ||
            url.contains("/amll-ttml-db/", ignoreCase = true)

    private fun userAgentForHost(host: String): String =
        if (host.contains("musicbrainz.org", ignoreCase = true) ||
            host.contains("coverartarchive.org", ignoreCase = true)
        ) {
            MUSICBRAINZ_USER_AGENT
        } else if (isLyricsBrowserHost(host)) {
            // NetEase / QQ / Kugou / Musixmatch lyrics endpoints reject obviously non-browser clients.
            BROWSER_USER_AGENT
        } else {
            DEFAULT_USER_AGENT
        }

    @Singleton
    @Provides
    fun provideApplication(@ApplicationContext app: Context): PixelPlayApplication {
        return app as PixelPlayApplication
    }

    @Singleton
    @Provides
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.Gson()
    }

    @OptIn(UnstableApi::class)
    @Singleton
    @Provides
    fun provideSessionToken(@ApplicationContext context: Context): androidx.media3.session.SessionToken {
        return androidx.media3.session.SessionToken(
            context,
            android.content.ComponentName(context, com.theveloper.pixelplay.data.service.MusicService::class.java)
        )
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore

    @Singleton
    @Provides
    fun provideJson(): Json { // Proveer Json
        return Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Singleton
    @Provides
    fun provideKtorHttpClient(json: Json): io.ktor.client.HttpClient {
        return io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
    }

    @Singleton
    @Provides
    @AppScope
    fun provideAppCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Singleton
    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Singleton
    @Provides
    fun providePixelPlayDatabase(@ApplicationContext context: Context): PixelPlayDatabase {
        val builder = Room.databaseBuilder(
            context.applicationContext,
            PixelPlayDatabase::class.java,
            "pixelplay_database"
        ).addMigrations(
            PixelPlayDatabase.MIGRATION_3_4,
            PixelPlayDatabase.MIGRATION_4_5,
            PixelPlayDatabase.MIGRATION_5_6,
            PixelPlayDatabase.MIGRATION_6_7,
            PixelPlayDatabase.MIGRATION_7_8,
            PixelPlayDatabase.MIGRATION_8_9,
            PixelPlayDatabase.MIGRATION_9_10,
            PixelPlayDatabase.MIGRATION_10_11,
            PixelPlayDatabase.MIGRATION_11_12,
            PixelPlayDatabase.MIGRATION_12_13,
            PixelPlayDatabase.MIGRATION_13_14,
            PixelPlayDatabase.MIGRATION_14_15,
            PixelPlayDatabase.MIGRATION_15_16,
            PixelPlayDatabase.MIGRATION_16_17,
            PixelPlayDatabase.MIGRATION_17_18,
            PixelPlayDatabase.MIGRATION_18_19,
            PixelPlayDatabase.MIGRATION_19_20,
            PixelPlayDatabase.MIGRATION_20_21,
            PixelPlayDatabase.MIGRATION_21_22,
            PixelPlayDatabase.MIGRATION_22_23,
            PixelPlayDatabase.MIGRATION_23_24,
            PixelPlayDatabase.MIGRATION_24_25,
            PixelPlayDatabase.MIGRATION_25_26,
            PixelPlayDatabase.MIGRATION_26_27,
            PixelPlayDatabase.MIGRATION_27_28,
            PixelPlayDatabase.MIGRATION_28_29,
            PixelPlayDatabase.MIGRATION_29_30,
            PixelPlayDatabase.MIGRATION_30_31,
            PixelPlayDatabase.MIGRATION_31_32,
            PixelPlayDatabase.MIGRATION_32_33,
            PixelPlayDatabase.MIGRATION_33_34,
            PixelPlayDatabase.MIGRATION_34_35,
            PixelPlayDatabase.MIGRATION_35_36,
            PixelPlayDatabase.MIGRATION_36_37,
            PixelPlayDatabase.MIGRATION_37_38,
            PixelPlayDatabase.MIGRATION_38_39,
            PixelPlayDatabase.MIGRATION_39_40,
            PixelPlayDatabase.MIGRATION_40_41,
            PixelPlayDatabase.MIGRATION_41_42,
            PixelPlayDatabase.MIGRATION_42_43,
            PixelPlayDatabase.MIGRATION_43_44,
            PixelPlayDatabase.MIGRATION_44_45,
            PixelPlayDatabase.MIGRATION_45_46,
            PixelPlayDatabase.MIGRATION_46_47,
            PixelPlayDatabase.MIGRATION_47_48,
            PixelPlayDatabase.MIGRATION_48_49,
            PixelPlayDatabase.MIGRATION_49_50,
            PixelPlayDatabase.MIGRATION_50_51,
            PixelPlayDatabase.MIGRATION_51_52,
            PixelPlayDatabase.MIGRATION_52_53
        )
            .addCallback(PixelPlayDatabase.createRuntimeArtifactsCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)

        // P2-4: Only allow destructive migration in debug builds.
        // In release, a migration bug will crash the app (revealing the problem)
        // rather than silently wiping user data (playlists, favorites, statistics).
        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration(dropAllTables = true)
        }

        return builder.build()
    }

    @Singleton
    @Provides
    fun provideAlbumArtThemeDao(database: PixelPlayDatabase): AlbumArtThemeDao {
        return database.albumArtThemeDao()
    }

    @Singleton
    @Provides
    fun provideSearchHistoryDao(database: PixelPlayDatabase): SearchHistoryDao { // NUEVO MÉTODO
        return database.searchHistoryDao()
    }

    @Singleton
    @Provides
    fun provideMusicDao(database: PixelPlayDatabase): MusicDao { // Proveer MusicDao
        return database.musicDao()
    }

    @Singleton
    @Provides
    fun provideEnrichmentDao(database: PixelPlayDatabase): EnrichmentDao {
        return database.enrichmentDao()
    }

    @Singleton
    @Provides
    fun provideTransitionDao(database: PixelPlayDatabase): TransitionDao {
        return database.transitionDao()
    }

    @Singleton
    @Provides
    fun provideEngagementDao(database: PixelPlayDatabase): EngagementDao {
        return database.engagementDao()
    }

    @Singleton
    @Provides
    fun provideFavoritesDao(database: PixelPlayDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Singleton
    @Provides
    fun provideLyricsDao(database: PixelPlayDatabase): LyricsDao {
        return database.lyricsDao()
    }

    @Singleton
    @Provides
    fun provideTrackMappingDao(database: PixelPlayDatabase): com.theveloper.pixelplay.data.database.TrackMappingDao {
        return database.trackMappingDao()
    }

    @Provides
    fun provideCloudSongDao(database: PixelPlayDatabase): CloudSongDao {
        return database.cloudSongDao()
    }

    @Singleton
    @Provides
    fun provideGDriveDao(database: PixelPlayDatabase): GDriveDao {
        return database.gdriveDao()
    }

    @Singleton
    @Provides
    fun provideStreamCollectionDao(database: PixelPlayDatabase): com.theveloper.pixelplay.data.database.StreamCollectionDao {
        return database.streamCollectionDao()
    }

    @Singleton
    @Provides
    fun provideLocalPlaylistDao(database: PixelPlayDatabase): LocalPlaylistDao {
        return database.localPlaylistDao()
    }

    @Singleton
    @Provides
    fun provideAiCacheDao(database: PixelPlayDatabase): AiCacheDao {
        return database.aiCacheDao()
    }

    @Provides
    fun provideAiUsageDao(database: PixelPlayDatabase): AiUsageDao {
        return database.aiUsageDao()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        base: OkHttpClient
    ): ImageLoader {
        // Share the app's connection pool and dispatcher so covers reuse warm HTTP/2
        // connections. Interceptors are dropped to keep image loading behaviour unchanged.
        val okHttpClient = base.newBuilder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(com.theveloper.pixelplay.data.image.DisplayArtworkMapper.Strings())
                add(com.theveloper.pixelplay.data.image.DisplayArtworkMapper())
            }
            .okHttpClient(okHttpClient)
            .dispatcher(Dispatchers.Default) // Use CPU-bound dispatcher for decoding
            .allowHardware(true) // Re-enable hardware bitmaps for better performance
            .memoryCache {
                MemoryCache.Builder(context)
                    // Budget relative to the process heap as well as the device-independent cap.
                    .maxSizeBytes((Runtime.getRuntime().maxMemory() / 10)
                        .coerceIn(8L * 1024 * 1024, 40L * 1024 * 1024).toInt())
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Ignore server cache headers, always cache
            .build()
    }

    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
        lrcLibApiService: LrcLibApiService,
        lyricsDao: LyricsDao,
        okHttpClient: OkHttpClient
    ): LyricsRepository {
        return LyricsRepositoryImpl(
            context = context,
            lrcLibApiService = lrcLibApiService,
            lyricsDao = lyricsDao,
            okHttpClient = okHttpClient
        )
    }

    @Provides
    @Singleton
    fun provideSongRepository(
        @ApplicationContext context: Context,
        mediaStoreObserver: com.theveloper.pixelplay.data.observer.MediaStoreObserver,
        favoritesDao: FavoritesDao,
        userPreferencesRepository: UserPreferencesRepository,
        musicDao: MusicDao
    ): SongRepository {
        return MediaStoreSongRepository(
            context = context,
            mediaStoreObserver = mediaStoreObserver,
            favoritesDao = favoritesDao,
            userPreferencesRepository = userPreferencesRepository,
            musicDao = musicDao
        )
    }

    @Provides
    @Singleton
    fun provideFolderTreeBuilder(): FolderTreeBuilder {
        return FolderTreeBuilder()
    }

    @Provides
    @Singleton
    fun provideMusicRepository(
        @ApplicationContext context: Context,
        userPreferencesRepository: UserPreferencesRepository,
        playlistPreferencesRepository: PlaylistPreferencesRepository,
        searchHistoryDao: SearchHistoryDao,
        musicDao: MusicDao,
        lyricsRepository: LyricsRepository,
        songRepository: SongRepository,
        favoritesDao: FavoritesDao,
        artistImageRepository: ArtistImageRepository,
        folderTreeBuilder: FolderTreeBuilder,
        youTubeRepository: com.theveloper.pixelplay.data.youtube.YouTubeRepository,
        cloudSongDao: com.theveloper.pixelplay.data.database.CloudSongDao,
        metadataGatherer: com.theveloper.pixelplay.data.metadata.SongMetadataGatherer,
        streamCollectionDao: com.theveloper.pixelplay.data.database.StreamCollectionDao
    ): MusicRepository {
        return MusicRepositoryImpl(
            context = context,
            userPreferencesRepository = userPreferencesRepository,
            playlistPreferencesRepository = playlistPreferencesRepository,
            searchHistoryDao = searchHistoryDao,
            musicDao = musicDao,
            lyricsRepository = lyricsRepository,
            songRepository = songRepository,
            favoritesDao = favoritesDao,
            artistImageRepository = artistImageRepository,
            folderTreeBuilder = folderTreeBuilder,
            youTubeRepository = youTubeRepository,
            cloudSongDao = cloudSongDao,
            metadataGatherer = metadataGatherer,
            streamCollectionDao = streamCollectionDao
        )

    }

    @Provides
    @Singleton
    fun provideTransitionRepository(
        transitionRepositoryImpl: TransitionRepositoryImpl
    ): TransitionRepository {
        return transitionRepositoryImpl
    }

    @Singleton
    @Provides
    fun provideSongMetadataEditor(
        @ApplicationContext context: Context,
        musicDao: MusicDao,
        userPreferencesRepository: UserPreferencesRepository
    ): SongMetadataEditor {
        return SongMetadataEditor(context, musicDao, userPreferencesRepository)
    }

    /**
     * Provee una instancia singleton de OkHttpClient con logging e interceptor de User-Agent.
     * Retry logic with backoff is handled in coroutine-based callers.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        networkAccessPolicy: com.theveloper.pixelplay.data.network.NetworkAccessPolicy
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // HEADERS (not BODY) so we never print response bodies that may contain
            // cookies, tokens, or third-party API payloads. Headers are still useful
            // for debugging request paths and status codes.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Redact every header that can carry a credential or session token.
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("x-goog-api-key")
            redactHeader("X-Emby-Token")
            redactHeader("X-Emby-Authorization")
            redactHeader("X-MediaBrowser-Token")
        }
        
        // Connection pool with optimized connections for better performance
        // Shared by the YouTube and image clients. A warm TLS/HTTP2 connection is what lets a
        // prewarmed search result (or the next song) start without a new handshake, so keep
        // idle connections for OkHttp's default five minutes instead of 30 seconds.
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 8,
            keepAliveDuration = 5,
            timeUnit = java.util.concurrent.TimeUnit.MINUTES
        )

        val networkPolicyInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            val urlStr = request.url.toString()
            val host = request.url.host

            val isLocal = host.equals("localhost", ignoreCase = true) ||
                    host.equals("127.0.0.1", ignoreCase = true) ||
                    host.startsWith("192.168.") ||
                    host.startsWith("10.") ||
                    host.startsWith("172.16.") ||
                    host.equals("::1") ||
                    host.endsWith(".local")

            if (!isLocal) {
                val purpose = when {
                    isLyricsRequest(request.url.host, urlStr) -> com.theveloper.pixelplay.data.network.NetworkPurpose.Lyrics
                    urlStr.contains("deezer.com", ignoreCase = true) -> com.theveloper.pixelplay.data.network.NetworkPurpose.Artwork
                    enrichmentHostMarkers.any { urlStr.contains(it, ignoreCase = true) } -> com.theveloper.pixelplay.data.network.NetworkPurpose.Enrichment
                    else -> com.theveloper.pixelplay.data.network.NetworkPurpose.Update
                }
                // Mirrored preferences avoid blocking an OkHttp thread on DataStore per request.
                val decision = networkAccessPolicy.decisionNow(purpose)
                    ?: kotlinx.coroutines.runBlocking { networkAccessPolicy.getDecision(purpose) }

                if (decision != com.theveloper.pixelplay.data.network.NetworkDecision.Allowed) {
                    throw java.io.IOException("Network request blocked by policy: $decision")
                }

                if (request.url.scheme == "http") {
                    val secureUrl = request.url.newBuilder().scheme("https").build()
                    val secureRequest = request.newBuilder().url(secureUrl).build()
                    return@Interceptor chain.proceed(secureRequest)
                }
            }

            chain.proceed(request)
        }
        
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Add User-Agent header (required by some APIs; MusicBrainz needs a contact UA)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", userAgentForHost(originalRequest.url.host))
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor(networkPolicyInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia de OkHttpClient con timeouts para búsquedas de lyrics.
     * Includes DNS resolver, modern TLS, connection pool, and connection retry.
     */
    @Provides
    @Singleton
    @FastOkHttpClient
    fun provideFastOkHttpClient(
        networkAccessPolicy: com.theveloper.pixelplay.data.network.NetworkAccessPolicy
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        
        // Connection pool to reuse connections for better performance
        // Shared by the YouTube and image clients. A warm TLS/HTTP2 connection is what lets a
        // prewarmed search result (or the next song) start without a new handshake, so keep
        // idle connections for OkHttp's default five minutes instead of 30 seconds.
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 8,
            keepAliveDuration = 5,
            timeUnit = java.util.concurrent.TimeUnit.MINUTES
        )
        
        // Use Cloudflare and Google DNS to avoid potential DNS issues
        val dns = okhttp3.Dns { hostname ->
            try {
                // First try system DNS
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // Fallback to manual resolution if system DNS fails
                java.net.InetAddress.getAllByName(hostname).toList()
            }
        }

        val networkPolicyInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            val urlStr = request.url.toString()
            val host = request.url.host

            val isLocal = host.equals("localhost", ignoreCase = true) ||
                    host.equals("127.0.0.1", ignoreCase = true) ||
                    host.startsWith("192.168.") ||
                    host.startsWith("10.") ||
                    host.startsWith("172.16.") ||
                    host.equals("::1") ||
                    host.endsWith(".local")

            if (!isLocal) {
                val purpose = when {
                    isLyricsRequest(request.url.host, urlStr) -> com.theveloper.pixelplay.data.network.NetworkPurpose.Lyrics
                    urlStr.contains("deezer.com", ignoreCase = true) -> com.theveloper.pixelplay.data.network.NetworkPurpose.Artwork
                    enrichmentHostMarkers.any { urlStr.contains(it, ignoreCase = true) } -> com.theveloper.pixelplay.data.network.NetworkPurpose.Enrichment
                    else -> com.theveloper.pixelplay.data.network.NetworkPurpose.Update
                }
                // Mirrored preferences avoid blocking an OkHttp thread on DataStore per request.
                val decision = networkAccessPolicy.decisionNow(purpose)
                    ?: kotlinx.coroutines.runBlocking { networkAccessPolicy.getDecision(purpose) }

                if (decision != com.theveloper.pixelplay.data.network.NetworkDecision.Allowed) {
                    throw java.io.IOException("Network request blocked by policy: $decision")
                }

                if (request.url.scheme == "http") {
                    val secureUrl = request.url.newBuilder().scheme("https").build()
                    val secureRequest = request.newBuilder().url(secureUrl).build()
                    return@Interceptor chain.proceed(secureRequest)
                }
            }

            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .dns(dns)
            .connectionPool(connectionPool)
            // Use HTTP/1.1 to avoid HTTP/2 stream issues with some servers
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // Use modern TLS connection spec
            .connectionSpecs(listOf(
                okhttp3.ConnectionSpec.MODERN_TLS,
                okhttp3.ConnectionSpec.COMPATIBLE_TLS
            ))
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            // Enable built-in retry on connection failure
            .retryOnConnectionFailure(true)
            // Add headers
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", userAgentForHost(originalRequest.url.host))
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .addInterceptor(networkPolicyInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia singleton de Retrofit para la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideRetrofit(@FastOkHttpClient okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee una instancia singleton del servicio de la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideLrcLibApiService(retrofit: Retrofit): LrcLibApiService {
        return retrofit.create(LrcLibApiService::class.java)
    }

    /**
     * Retrofit + service for ListenBrainz (scrobbling and similar recordings).
     */
    @Provides
    @Singleton
    @javax.inject.Named("listenbrainz")
    fun provideListenBrainzRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.listenbrainz.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideListenBrainzService(
        @javax.inject.Named("listenbrainz") retrofit: Retrofit
    ): com.theveloper.pixelplay.data.network.listenbrainz.ListenBrainzService {
        return retrofit.create(com.theveloper.pixelplay.data.network.listenbrainz.ListenBrainzService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de Deezer.
     */
    @Provides
    @Singleton
    @DeezerRetrofit
    fun provideDeezerRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee el servicio de la API de Deezer.
     */
    @Provides
    @Singleton
    fun provideDeezerApiService(@DeezerRetrofit retrofit: Retrofit): DeezerApiService {
        return retrofit.create(DeezerApiService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de Apple iTunes.
     */
    @Provides
    @Singleton
    @ITunesRetrofit
    fun provideITunesRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee el servicio de la API de Apple iTunes.
     */
    @Provides
    @Singleton
    fun provideITunesApiService(@ITunesRetrofit retrofit: Retrofit): com.theveloper.pixelplay.data.network.itunes.ITunesApiService {
        return retrofit.create(com.theveloper.pixelplay.data.network.itunes.ITunesApiService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de MusicBrainz.
     */
    @Provides
    @Singleton
    @MusicBrainzRetrofit
    fun provideMusicBrainzRetrofit(okHttpClient: OkHttpClient, gson: com.google.gson.Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Provee el servicio de la API de MusicBrainz.
     */
    @Provides
    @Singleton
    fun provideMusicBrainzApiService(@MusicBrainzRetrofit retrofit: Retrofit): MusicBrainzApiService {
        return retrofit.create(MusicBrainzApiService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de Cover Art Archive.
     */
    @Provides
    @Singleton
    @CoverArtArchiveRetrofit
    fun provideCoverArtArchiveRetrofit(okHttpClient: OkHttpClient, gson: com.google.gson.Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://coverartarchive.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Provee el servicio de la API de Cover Art Archive.
     */
    @Provides
    @Singleton
    fun provideCoverArtArchiveApiService(@CoverArtArchiveRetrofit retrofit: Retrofit): CoverArtArchiveApiService {
        return retrofit.create(CoverArtArchiveApiService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de Last.fm.
     */
    @Provides
    @Singleton
    @LastFmRetrofit
    fun provideLastFmRetrofit(okHttpClient: OkHttpClient, gson: com.google.gson.Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Provee el servicio de la API de Last.fm.
     */
    @Provides
    @Singleton
    fun provideLastFmApiService(@LastFmRetrofit retrofit: Retrofit): LastFmApiService {
        return retrofit.create(LastFmApiService::class.java)
    }



    @Provides
    @Singleton
    fun provideCastTokenStore(): com.theveloper.pixelplay.data.service.cast.CastTokenStore {
        return com.theveloper.pixelplay.data.service.cast.CastTokenStoreImpl()
    }

    @Provides
    @Singleton
    fun provideYouTubeRepository(
        impl: com.theveloper.pixelplay.data.youtube.YouTubeRepositoryImpl
    ): com.theveloper.pixelplay.data.youtube.YouTubeRepository {
        return impl
    }

    @Provides
    @Singleton
    fun provideCastMediaResolver(
        @ApplicationContext context: Context,
        musicRepository: MusicRepository
    ): com.theveloper.pixelplay.data.service.cast.CastMediaResolver {
        return com.theveloper.pixelplay.data.service.cast.CastMediaResolverImpl(context, musicRepository)
    }
}
