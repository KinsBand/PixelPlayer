package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.social.FriendActivitySource
import com.theveloper.pixelplay.data.spotify.web.SpotifyFriendActivitySource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Adds Spotify's friend feed to the Friends section (see FriendActivityModule). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SpotifyFriendActivityModule {
    @Binds
    @IntoSet
    abstract fun spotify(impl: SpotifyFriendActivitySource): FriendActivitySource
}
