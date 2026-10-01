package com.theveloper.pixelplay.data.library

import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collapses duplicate artists and albums already in the library tables.
 *
 * Older syncs keyed artists by their exact name and albums by exact title + album artist, so
 * "Drake", "drake", "Drake - Topic" and "DrakeVEVO" were four artists, and "Album" /
 * "Album (Deluxe)" / an album whose tracks had "X - Topic" as album artist were separate albums.
 * New syncs group correctly; this repairs what's stored. Cheap when there's nothing to do.
 */
@Singleton
class LibraryDuplicateRepair @Inject constructor(
    private val musicDao: MusicDao,
) {
    private val mutex = Mutex()

    /** Returns true when anything was merged or renamed. */
    suspend fun run(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val artists = mergeArtists()
            val albums = mergeAlbums()
            if (artists || albums) {
                musicDao.deleteOrphanedAlbums()
                musicDao.deleteOrphanedArtists()
                musicDao.refreshArtistTrackCounts()
                musicDao.refreshAlbumSongCounts()
                Timber.tag(TAG).i("Merged duplicate artists=%s albums=%s", artists, albums)
            }
            artists || albums
        }
    }

    private suspend fun mergeArtists(): Boolean {
        val all = musicDao.getAllArtistsListRaw()
        var changed = false
        all.groupBy { CollectionKeys.normalizeArtist(it.name) }
            .filterKeys { it.isNotEmpty() }
            .values
            .forEach { group ->
                val survivor = group.maxWith(
                    compareBy<ArtistEntity> { if (!it.customImageUri.isNullOrBlank()) 1 else 0 }
                        .thenBy { if (CollectionKeys.cleanArtistName(it.name) == it.name.trim()) 1 else 0 }
                        .thenBy { it.trackCount }
                        .thenByDescending { it.id }
                )
                group.filter { it.id != survivor.id }.forEach { twin ->
                    musicDao.mergeArtistInto(twin.id, survivor.id)
                    musicDao.fillArtistImageBlanks(survivor.id, twin.imageUrl, twin.customImageUri)
                    changed = true
                }
                val clean = CollectionKeys.cleanArtistName(survivor.name).trim()
                if (clean.isNotBlank() && clean != survivor.name) {
                    musicDao.renameArtist(survivor.id, clean)
                    changed = true
                }
            }
        return changed
    }

    private suspend fun mergeAlbums(): Boolean {
        val all = musicDao.getAllAlbumsList(emptyList(), false, 0)
        var changed = false
        all.groupBy(::albumKey)
            .filterKeys { it != null }
            .values
            .forEach { group ->
                if (group.size < 2) return@forEach
                // Keep the biggest album; on a tie prefer a library (MediaStore) album over a
                // downloads-only one.
                val survivor = group.maxWith(
                    compareBy<AlbumEntity> { it.songCount }
                        .thenBy { if (it.id < CollectionKeys.DOWNLOAD_ALBUM_BASE) 1 else 0 }
                        .thenByDescending { it.id }
                )
                group.filter { it.id != survivor.id }.forEach { twin ->
                    musicDao.mergeAlbumInto(twin.id, survivor.id)
                    musicDao.fillAlbumBlanks(survivor.id, twin.albumArtUriString, twin.year, twin.albumArtist)
                    changed = true
                }
            }
        return changed
    }

    /** (main album artist, title without edition qualifiers); null when either is unknown. */
    private fun albumKey(album: AlbumEntity): String? {
        val artistSource = album.albumArtist?.takeIf { it.isNotBlank() } ?: album.artistName
        val artist = CollectionKeys.normalizeArtist(RecordingKeys.primaryArtist(artistSource).ifBlank { artistSource })
        val title = CollectionKeys.normalizeAlbum(album.title)
        if (artist.isEmpty() || artist == "unknown artist" || title.isEmpty() || title == "unknown album") return null
        return "$artist|$title"
    }

    private companion object {
        const val TAG = "LibraryDuplicateRepair"
    }
}
