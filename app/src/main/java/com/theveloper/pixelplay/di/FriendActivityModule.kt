package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.social.FriendActivitySource
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the (possibly empty) set of friend-activity sources. A platform adds itself with:
 *
 * ```
 * @Binds @IntoSet abstract fun spotify(impl: SpotifyFriendActivitySource): FriendActivitySource
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FriendActivityModule {
    @Multibinds
    abstract fun friendActivitySources(): Set<FriendActivitySource>
}
