package com.theveloper.pixelplay.di

import javax.inject.Qualifier

/**
 * Qualifier for Deezer Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeezerRetrofit

/**
 * Qualifier for MusicBrainz Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MusicBrainzRetrofit

/**
 * Qualifier for Cover Art Archive Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CoverArtArchiveRetrofit

/**
 * Qualifier for Last.fm Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LastFmRetrofit

/**
 * Qualifier for Fast OkHttpClient (Short timeouts).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FastOkHttpClient

/**
 * Qualifier for Gson instance configured for backup serialization.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackupGson

/**
 * Qualifier for application-lifetime coroutine scope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/**
 * Qualifier for iTunes Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ITunesRetrofit

/**
 * Qualifier for the OkHttpClient used for NewPipe extraction and googlevideo audio transfers.
 * It must NOT carry the app-wide User-Agent interceptor: YouTube rejects foreign user agents.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YouTubeOkHttpClient
