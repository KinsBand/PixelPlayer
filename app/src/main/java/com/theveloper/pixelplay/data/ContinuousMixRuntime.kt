package com.theveloper.pixelplay.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.spotify.SpotifyToYouTubeResolver
import com.theveloper.pixelplay.utils.MediaItemBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** Service-owned live planning. No Activity, ViewModel or controller reference is retained. */
@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class ContinuousMixRuntime @Inject constructor(
    private val adaptive: AdaptiveMix,
    private val music: MusicRepository,
    private val spotify: SpotifyToYouTubeResolver,
    private val engine: DualPlayerEngine,
    private val gatherer: com.theveloper.pixelplay.data.metadata.SongMetadataGatherer
) {
    private val _flavor = MutableStateFlow<MixFlavor?>(null)
    val flavor = _flavor.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    /**
     * True while the mix is actively building the queue (planning + adding songs). Drives the
     * queue button outline and the "adding songs" strip in the queue sheet.
     */
    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()
    private var playerProvider: (() -> Player)? = null
    private var job: Job? = null
    private var generation = 0L
    private var contextSeeds = emptyList<Song>()
    private val seen = linkedSetOf<String>()
    private var resumeAfterRefill = false
    private var retryAfter = 0L
    /** Wakes the refill loop right away after the user tunes the mix, instead of waiting 1.5 s. */
    private val kick = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    /** Upcoming songs the user queued (not added by the mix). null = not snapshotted yet. */
    private var knownUserPicks: Set<String>? = null
    /** Keep refilling on every tick until the mix is back at [MixRefillPolicy.MAX_QUEUE] songs. */
    private var topUp = false
    private var lastCurrentId: String? = null
    private var lastExpectedNextId: String? = null
    private var lastUpcomingIds: Set<String> = emptySet()
    private var lastPickRefreshAt = 0L
    /** Songs the user queued while this mix was running. */
    private val queuedDuringMix = linkedSetOf<String>()
    /** Songs just removed from the queue; upcoming mix songs like them get swapped out next tick. */
    private val pendingOffVibe = java.util.concurrent.ConcurrentLinkedQueue<Song>()

    // ── Skip handling ────────────────────────────────────────────────────────────────
    /** When this mix session started (epoch ms); older attempts are ignored. */
    private var sessionStartedAt = 0L
    /** Songs skipped early in this session; they no longer count as seeds. */
    private val sessionSkippedIds = linkedSetOf<String>()
    /** Early skips in a row (reset by a song played past half way). */
    private var skipStreak = 0
    /** Early skips among the last few finished songs (true = skipped). */
    private val recentOutcomes = ArrayDeque<Boolean>()
    /** A skip is waiting to re-plan the upcoming songs (coalesces quick successive skips). */
    private var skipRefreshPending = false
    private var lastSkipRefreshAt = 0L
    /** A song was listened to: re-order the mix's upcoming songs around it on the next tick. */
    private var rerankPending = false

    // ── Live re-plans ────────────────────────────────────────────────────────────────
    /**
     * A re-plan of the upcoming mix songs is waiting. The old songs stay in the queue until the
     * first new song is ready, then they are swapped out, so the queue never empties and a
     * re-plan that finds nothing keeps what was there.
     */
    private data class Swap(val keepAutomatic: Int)
    @Volatile private var pendingSwap: Swap? = null
    /** Just started or switched: plan a small first batch so songs land within a second or so. */
    private var fastStart = false
    /** Passes in a row that added nothing (backs off the retry instead of giving up). */
    private var emptyPasses = 0

    fun attach(scope: CoroutineScope, player: () -> Player, remote: () -> Boolean) {
        playerProvider = player
        job?.cancel()
        job = scope.launch {
            // Finished plays arrive here as they happen, so a skip is acted on right away.
            launch {
                adaptive.finishedAttempts.collect { attempt ->
                    try { onAttemptFinished(attempt) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { }
                }
            }
            while (isActive) {
                // Re-check quickly while a top-up is still in progress; otherwise idle.
                withTimeoutOrNull(if ((topUp || resumeAfterRefill) && android.os.SystemClock.elapsedRealtime() >= retryAfter) 150L else 1_000L) { kick.receive() }
                if (remote() || _flavor.value == null) continue
                try {
                    followUserPicks()
                    pruneOffVibe()
                    applySkipRefresh()
                    refill()
                    rerankTail()
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    // Keep the mix alive and try again shortly; the queue is left as it is.
                    _flavor.value?.let { _status.value = adaptive.statusTitle(it) + " · retrying" }
                    retryAfter = android.os.SystemClock.elapsedRealtime() + 5_000
                    _working.value = false
                }
            }
        }
    }

    fun detach() { job?.cancel(); job = null; playerProvider = null; stop() }

    /**
     * Starts a mix session over the current queue. [replaceLeftovers] swaps out upcoming songs a
     * previous mix left behind (they're replaced as soon as the new mix's first song is ready).
     */
    fun start(flavor: MixFlavor, seeds: List<Song>, replaceLeftovers: Boolean = false) {
        generation++
        contextSeeds = seeds.takeLast(6)
        seen.clear()
        seeds.forEach { seen.addAll(adaptive.recordingKeys(it)) }
        retryAfter = 0
        resumeAfterRefill = false
        knownUserPicks = null
        lastCurrentId = null
        queuedDuringMix.clear()
        pendingOffVibe.clear()
        resetSkipState()
        sessionStartedAt = System.currentTimeMillis()
        topUp = true
        fastStart = true
        emptyPasses = 0
        pendingSwap = if (replaceLeftovers) Swap(keepAutomatic = 0) else null
        _flavor.value = flavor
        _status.value = adaptive.statusTitle(flavor)
        _working.value = true
        kick.trySend(Unit)
    }

    /**
     * Normal ↔ Smart while a mix is playing, without restarting it: the session (skips,
     * removals, steers, locked vibe) carries on and the upcoming mix songs are re-planned for
     * the new flavour straight away. Returns false when no mix is running (start one instead).
     */
    fun switchFlavor(flavor: MixFlavor): Boolean {
        val running = _flavor.value ?: return false
        if (playerProvider == null) return false
        if (running == flavor) return true
        adaptive.switchFlavor(flavor.name.lowercase(java.util.Locale.ROOT))
        _flavor.value = flavor
        _status.value = adaptive.statusTitle(flavor) + " · switching"
        fastStart = true
        emptyPasses = 0
        return refreshFuture(keepAutomatic = 0)
    }

    /**
     * Ends the mix. With [clearUpcoming] ("No mix" on the mix button) the mix's own upcoming
     * songs leave the queue too, so what's left is what the user queued; the next song stays
     * when a transition into it may already be armed.
     */
    fun stop(clearUpcoming: Boolean = false) {
        if (clearUpcoming && _flavor.value != null) {
            playerProvider?.invoke()?.let { player ->
                for (index in player.mediaItemCount - 1 downTo firstReplaceableIndex(player).coerceAtLeast(0)) {
                    if (MixQueueMetadata.automatic(player.getMediaItemAt(index))) player.removeMediaItem(index)
                }
            }
        }
        generation++
        pendingSwap = null
        fastStart = false
        _flavor.value = null
        _status.value = null
        contextSeeds = emptyList()
        seen.clear()
        resumeAfterRefill = false
        knownUserPicks = null
        lastCurrentId = null
        pendingOffVibe.clear()
        resetSkipState()
        topUp = false
        _working.value = false
        adaptive.deactivate()
    }

    /** Media ids of upcoming songs the user queued themselves (anything the mix didn't tag). */
    private fun userPickIds(player: Player): List<String> =
        ((player.currentMediaItemIndex + 1).coerceAtLeast(0) until player.mediaItemCount)
            .map(player::getMediaItemAt)
            .filter(MixQueueMetadata::userPick)
            .map { it.mediaId }

    /**
     * When the user queues a song during a mix, it becomes part of the mix's flavour: the
     * automatic songs after it are re-planned around it straight away. Songs already in the
     * queue when the mix started don't count as new picks.
     */
    private fun followUserPicks() {
        val player = playerProvider?.invoke() ?: return
        val index = player.currentMediaItemIndex
        val currentItem = player.currentMediaItem
        val currentId = currentItem?.mediaId
        val picks = userPickIds(player).toSet()
        val known = knownUserPicks
        knownUserPicks = picks

        // What the user chose to *play*: a song they queued starting, or jumping ahead to a song
        // they queued (tapping it) rather than reaching it naturally / with Next. Jumping to one
        // of the mix's own songs (or a mix-button song) is just a skip: the mix carries on from
        // there as planned, and the skipped songs count through the normal listening signals.
        val changed = lastCurrentId != null && currentId != null && currentId != lastCurrentId
        val jumpedToPick = changed && currentId in lastUpcomingIds && currentId != lastExpectedNextId &&
            currentItem != null && MixQueueMetadata.userPick(currentItem)
        val userSongStarted = changed && currentId in queuedDuringMix &&
            currentItem != null && MixQueueMetadata.userPick(currentItem)
        lastCurrentId = currentId
        lastExpectedNextId = if (index + 1 in 0 until player.mediaItemCount) player.getMediaItemAt(index + 1).mediaId else null
        lastUpcomingIds = ((index + 1).coerceAtLeast(0) until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()

        if (known == null) {
            // Mix just started on top of an existing queue: keep at most MAX_QUEUE songs.
            enforceCap(player, includeUserSongs = true)
            return
        }
        val newPicks = picks - known
        queuedDuringMix.addAll(newPicks)
        while (queuedDuringMix.size > 200) queuedDuringMix.remove(queuedDuringMix.first())
        val newlyQueued = newPicks.isNotEmpty()
        val now = android.os.SystemClock.elapsedRealtime()
        val playedPick = (jumpedToPick || userSongStarted) && now - lastPickRefreshAt > PICK_REFRESH_COOLDOWN_MS
        if (newlyQueued || playedPick) {
            lastPickRefreshAt = now
            _status.value = "${_flavor.value?.title ?: "Mix"} · blending in your picks"
            // Keep the next couple of mix songs so the change is a gradual drift, never a restart.
            refreshFuture(keepAutomatic = PICK_KEEP_AUTOMATIC)
        }
    }

    /**
     * The user removed [song] from the queue while this mix was running: treat it as "doesn't
     * fit the vibe". The mix steers away from similar songs for the rest of the session, and
     * upcoming mix songs that closely resemble it are swapped out. Songs the user queued are
     * never touched. No-op when no mix is running.
     */
    fun noteRemovedFromQueue(song: Song) {
        // No mix running: still a hint about the vibe (friends-in-the-room picks and the next
        // mix use it), just not stored for later sessions.
        if (_flavor.value == null) { adaptive.noteRemovedOutsideMix(song); return }
        adaptive.markOffVibe(song)
        pendingOffVibe.add(song)
        _status.value = "${_flavor.value?.title ?: "Mix"} · steering away from that"
        kick.trySend(Unit)
    }

    /**
     * The user queued [song] by hand (Add to queue / Play next / Play soon): "more of this
     * vibe". Similar-feeling songs rise in the next plans, mix or not; a running mix re-plans
     * through [followUserPicks] as before.
     */
    fun noteQueuedByUser(song: Song) {
        adaptive.markPicked(song)
        if (_flavor.value != null) kick.trySend(Unit)
    }

    /** Undo of a queue removal: forget the signal and don't mistake the restored song for a new pick. */
    fun noteRestoredToQueue(song: Song) {
        pendingOffVibe.removeAll { it.id == song.id }
        adaptive.clearOffVibe(song)
        knownUserPicks = knownUserPicks?.plus(song.id)
    }

    private fun resetSkipState() {
        rerankPending = false
        sessionSkippedIds.clear()
        skipStreak = 0
        recentOutcomes.clear()
        skipRefreshPending = false
    }

    /**
     * A play in this mix session finished. An early skip of a song the mix (or a mix button)
     * chose is a "not this": similar songs are lowered for the session, the skipped song stops
     * counting as a seed, and the upcoming mix songs are re-planned. Skipping a song the user
     * queued themselves says nothing about the mix and is ignored. A song played past half way
     * ends a skip streak; a song played to the end clears an earlier skip of it.
     */
    private suspend fun onAttemptFinished(attempt: MixAttempt) {
        if (_flavor.value == null || attempt.startedAt < sessionStartedAt) return
        val player = playerProvider?.invoke() ?: return
        // Same labels as the learning side (MixRewards): an early skip is a rejection, anything
        // rewarded ≥ +0.4 (listened past half way, finished, replayed) ends a skip streak.
        val earlySkip = MixRewards.isEarlySkip(attempt)
        val listened = (MixRewards.reward(attempt) ?: 0.0) >= 0.4
        if (!earlySkip) {
            if (listened) {
                skipStreak = 0
                recordOutcome(false)
                rerankPending = true
                if (sessionSkippedIds.remove(attempt.songId)) {
                    withContext(Dispatchers.IO) { music.getSongsByIds(listOf(attempt.songId)).first() }
                        .firstOrNull()?.let(adaptive::clearSkipped)
                }
            }
            return
        }
        // Was it one of the mix's picks (or a mix button's)? Look at the queue occurrence.
        val item = (0 until player.mediaItemCount).asSequence().map(player::getMediaItemAt)
            .lastOrNull { it.mediaId == attempt.songId }
        if (item != null && MixQueueMetadata.userPick(item)) return
        val song = withContext(Dispatchers.IO) { music.getSongsByIds(listOf(attempt.songId)).first() }
            .firstOrNull() ?: return
        if (_flavor.value == null) return
        sessionSkippedIds.add(song.id)
        while (sessionSkippedIds.size > 64) sessionSkippedIds.remove(sessionSkippedIds.first())
        skipStreak++
        recordOutcome(true)
        adaptive.markSkipped(song)
        skipRefreshPending = true
        kick.trySend(Unit)
    }

    private fun recordOutcome(skipped: Boolean) {
        recentOutcomes.addLast(skipped)
        while (recentOutcomes.size > PIVOT_WINDOW) recentOutcomes.removeFirst()
    }

    /**
     * Re-plans the upcoming mix songs after skips, at most once per [SKIP_REFRESH_COOLDOWN_MS]
     * so skipping through several songs quickly causes one re-plan, not one per skip.
     * One skip keeps the next mix song (a small correction); two in a row replace it too; three
     * of the last five is a pivot: the seeds are now only what was actually listened to.
     */
    private fun applySkipRefresh() {
        if (!skipRefreshPending) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSkipRefreshAt < SKIP_REFRESH_COOLDOWN_MS) return
        skipRefreshPending = false
        lastSkipRefreshAt = now
        val pivot = recentOutcomes.count { it } >= PIVOT_SKIPS
        val title = _flavor.value?.title ?: "Mix"
        _status.value = when {
            pivot -> "$title · changing direction"
            skipStreak >= 2 -> "$title · adjusting after your skips"
            else -> "$title · adjusting after that skip"
        }
        refreshFuture(keepAutomatic = if (skipStreak >= 2 || pivot) 0 else 1)
    }

    /**
     * Rolling queue: after each song that was listened to, the mix's own upcoming songs are
     * re-ordered for what is playing now (nothing is fetched or replaced, so it is cheap). The
     * next [ROLLING_FIXED] songs never move, so a prepared crossfade and "up next" stay put, and
     * only the last unbroken run of mix songs moves, so songs the user queued keep their place.
     */
    private suspend fun rerankTail() {
        if (!rerankPending || topUp || skipRefreshPending || _flavor.value == null) return
        rerankPending = false
        val player = playerProvider?.invoke() ?: return
        val requestGeneration = generation
        val start = player.currentMediaItemIndex + 1 + ROLLING_FIXED
        val end = player.mediaItemCount - 1
        if (end - start + 1 < ROLLING_MIN) return
        // The trailing run of automatic items (stop at the last item the user or a button added).
        var blockStart = end + 1
        while (blockStart - 1 >= start && MixQueueMetadata.automatic(player.getMediaItemAt(blockStart - 1))) blockStart--
        if (end - blockStart + 1 < ROLLING_MIN) return
        val ids = (blockStart..end).map { player.getMediaItemAt(it).mediaId }
        val songs = withContext(Dispatchers.IO) { music.getSongsByIds(ids.distinct()).first() }.associateBy { it.id }
        val ordered = ids.mapNotNull(songs::get)
        if (ordered.size < ROLLING_MIN) return
        val order = adaptive.rerank(ordered, contextSeeds)
        // The queue may have changed while ranking; only apply to the same, unchanged block.
        if (generation != requestGeneration || playerProvider?.invoke() !== player || _flavor.value == null) return
        if (player.mediaItemCount - 1 != end || (blockStart..end).map { player.getMediaItemAt(it).mediaId } != ids) return
        val wanted = order.filter { it in ids } + ids.filterNot { it in order }
        for ((offset, id) in wanted.withIndex()) {
            val target = blockStart + offset
            val current = (target..end).firstOrNull { player.getMediaItemAt(it).mediaId == id } ?: continue
            if (current != target) player.moveMediaItem(current, target)
        }
    }

    /** Swaps out upcoming mix songs that closely resemble songs just removed from the queue. */
    private suspend fun pruneOffVibe() {
        if (pendingOffVibe.isEmpty()) return
        val removed = generateSequence { pendingOffVibe.poll() }.toList()
        if (_flavor.value == null) return
        val player = playerProvider?.invoke() ?: return
        val firstReplaceable = firstReplaceableIndex(player)
        val candidates = (firstReplaceable.coerceAtLeast(0) until player.mediaItemCount)
            .map(player::getMediaItemAt).filter(MixQueueMetadata::automatic)
        if (candidates.isEmpty()) return
        val songs = withContext(Dispatchers.IO) { music.getSongsByIds(candidates.map { it.mediaId }.distinct()).first() }
            .associateBy { it.id }
        val drop = candidates.filter { item ->
            songs[item.mediaId]?.let { adaptive.offVibePenalty(it, removed) >= OFF_VIBE_PRUNE || adaptive.feelsLike(it, removed) } == true
        }.map { it.mediaId }.toSet()
        if (drop.isEmpty() || playerProvider?.invoke() !== player || _flavor.value == null) return
        // The queue may have moved during the lookup; re-scan by id from the current position.
        for (index in player.mediaItemCount - 1 downTo firstReplaceableIndex(player).coerceAtLeast(0)) {
            val item = player.getMediaItemAt(index)
            if (MixQueueMetadata.automatic(item) && item.mediaId in drop) player.removeMediaItem(index)
        }
        generation++
        topUp = true
        _working.value = true
    }

    /**
     * Keeps the queue at or under [MixRefillPolicy.MAX_QUEUE]: trims old history first, then the
     * mix's own songs from the end. Songs the user queued are only trimmed when a mix first
     * starts on top of a long existing queue ([includeUserSongs]). Current + next are never touched.
     */
    private fun enforceCap(player: Player, includeUserSongs: Boolean = false) {
        val max = MixRefillPolicy.MAX_QUEUE
        if (player.mediaItemCount <= max) return
        val index = player.currentMediaItemIndex
        val history = (index - MixRefillPolicy.KEEP_HISTORY).coerceAtLeast(0)
        if (history > 0) player.removeMediaItems(0, minOf(history, player.mediaItemCount - max))
        var i = player.mediaItemCount - 1
        val protectedUntil = player.currentMediaItemIndex + 1
        while (player.mediaItemCount > max && i > protectedUntil) {
            val item = player.getMediaItemAt(i)
            if ((includeUserSongs && item.mediaMetadata.extras?.getBoolean(com.theveloper.pixelplay.data.model.QueueEntryMetadata.PINNED, false) != true) ||
                MixQueueMetadata.automatic(item)) player.removeMediaItem(i)
            i--
        }
    }

    /**
     * Re-plans the upcoming automatic songs after mix feedback so changes are heard straight away.
     * Only tagged automatic occurrences are replaced — songs the user queued are never touched.
     * The next song is kept only when the current one is about to end (a crossfade/gapless
     * transition may already be armed). Returns false when no mix is running.
     */
    fun refreshFuture(keepAutomatic: Int = 0): Boolean {
        generation++
        retryAfter = 0
        emptyPasses = 0
        if (_flavor.value == null) return false
        playerProvider?.invoke() ?: return false
        // Nothing is removed yet: the swap happens when the first new song is ready.
        pendingSwap = Swap(keepAutomatic)
        topUp = true
        _working.value = true
        kick.trySend(Unit)
        return true
    }

    /** Queue positions a re-plan replaces: upcoming mix songs after the first [keepAutomatic]. */
    private fun replaceableIndices(player: Player, keepAutomatic: Int): List<Int> {
        var firstReplaceable = firstReplaceableIndex(player)
        // Optionally leave the first few upcoming mix songs in place (gradual re-plans).
        var kept = 0
        while (kept < keepAutomatic && firstReplaceable < player.mediaItemCount) {
            if (MixQueueMetadata.automatic(player.getMediaItemAt(firstReplaceable))) kept++
            firstReplaceable++
        }
        return (firstReplaceable.coerceAtLeast(0) until player.mediaItemCount)
            .filter { MixQueueMetadata.automatic(player.getMediaItemAt(it)) }
    }

    /** Removes the songs a pending re-plan replaces (positions re-read now; playback may have moved on). */
    private fun applySwap(player: Player, swap: Swap) {
        replaceableIndices(player, swap.keepAutomatic).asReversed().forEach(player::removeMediaItem)
    }

    /** Frees a slot by trimming played songs beyond [MixRefillPolicy.KEEP_HISTORY]; false when full. */
    private fun makeRoom(player: Player): Boolean {
        while (player.mediaItemCount >= MixRefillPolicy.MAX_QUEUE &&
            player.currentMediaItemIndex > MixRefillPolicy.KEEP_HISTORY) player.removeMediaItem(0)
        return player.mediaItemCount < MixRefillPolicy.MAX_QUEUE
    }

    /** First queue index a re-plan may change: the next song is kept when a transition may be armed. */
    private fun firstReplaceableIndex(player: Player): Int {
        val duration = player.duration
        val remaining = if (duration == androidx.media3.common.C.TIME_UNSET) Long.MAX_VALUE else duration - player.currentPosition
        val keepNext = remaining in 0..TRANSITION_GUARD_MS
        return player.currentMediaItemIndex + if (keepNext) 2 else 1
    }

    private companion object {
        const val TRANSITION_GUARD_MS = 15_000L
        const val PICK_REFRESH_COOLDOWN_MS = 30_000L
        /** Upcoming mix songs kept when the user queues something, so the mix drifts toward it. */
        const val PICK_KEEP_AUTOMATIC = 2
        /** [AdaptiveMix.offVibePenalty] at which an upcoming mix song is swapped out (≈ same artist). */
        const val OFF_VIBE_PRUNE = 3.0
        const val SKIP_REFRESH_COOLDOWN_MS = 8_000L
        /** Pivot when [PIVOT_SKIPS] of the last [PIVOT_WINDOW] finished songs were early skips. */
        const val PIVOT_WINDOW = 5
        const val PIVOT_SKIPS = 3
        const val RESOLVE_PARALLELISM = 8
        /** Upcoming songs that never move when the tail is re-ordered. */
        const val ROLLING_FIXED = 3
        /** Don't bother re-ordering fewer songs than this. */
        const val ROLLING_MIN = 4
        /** Extra planned songs resolved alongside, so a song that fails to resolve is covered. */
        const val RESOLVE_SPARE = 4
        const val RESOLVE_GATHER_TIMEOUT_MS = 800L
        /** Shorter metadata wait for the first songs after a start / switch. */
        const val FAST_GATHER_TIMEOUT_MS = 300L
        /** Songs planned in the first pass after a start / switch (the rest follow next tick). */
        const val FIRST_BATCH = 10
        /** Songs played this recently aren't repeated by the relaxed fallback plan. */
        const val RECENT_REPEAT_GUARD = 25
    }

    fun resumeWhenRefilled(wasPlaying: Boolean) {
        resumeAfterRefill = wasPlaying && _flavor.value != null
        retryAfter = 0
        generation++
    }

    internal suspend fun refill() {
        try {
            refillPass()
        } finally {
            // Busy only while there is still work queued for the next tick.
            _working.value = _flavor.value != null && (topUp || resumeAfterRefill) &&
                android.os.SystemClock.elapsedRealtime() >= retryAfter
        }
    }

    private suspend fun refillPass() {
        val flavor = _flavor.value ?: return
        val player = playerProvider?.invoke() ?: return
        if (android.os.SystemClock.elapsedRealtime() < retryAfter) return
        enforceCap(player)
        val swap = pendingSwap
        val index = player.currentMediaItemIndex
        if (MixRefillPolicy.shouldRefill(player.mediaItemCount, index)) topUp = true
        if (!resumeAfterRefill && !topUp && swap == null) return
        // Songs a pending re-plan will replace count as free room.
        val replacing = if (swap != null) replaceableIndices(player, swap.keepAutomatic).toSet() else emptySet()
        val room = MixRefillPolicy.room(player.mediaItemCount - replacing.size, index)
        if (room <= 0) {
            topUp = false
            if (pendingSwap === swap) pendingSwap = null
            _status.value = adaptive.statusTitle(flavor)
            return
        }
        _working.value = true
        val first = fastStart
        val want = minOf(room, if (first) FIRST_BATCH else MixRefillPolicy.BATCH)
        val requestGeneration = generation
        val feedbackRevision = adaptive.revision
        fun current() = generation == requestGeneration && adaptive.revision == feedbackRevision &&
            _flavor.value == flavor && playerProvider?.invoke() === player
        val mediaIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val queue = withContext(Dispatchers.IO) { music.getSongsByIds(mediaIds.distinct()).first() }.associateBy { it.id }
        val replacingIds = replacing.map { mediaIds[it] }.toSet()
        val keptIds = mediaIds.filterIndexed { position, _ -> position !in replacing }
        // Songs about to be replaced may be picked again by the new plan.
        replacingIds.forEach { id -> queue[id]?.let { seen.removeAll(adaptive.recordingKeys(it)) } }
        // Songs the user queued themselves steer the mix, but gently: they join the seeds and set
        // the direction, while what's playing now stays the newest seed (the planner weights the
        // last seeds most), so the mix blends toward a pick instead of jumping to its vibe.
        // Songs added by a queue mix button are neither: they apply once, when pressed.
        val userPicks = userPickIds(player).mapNotNull(queue::get).takeLast(4)
        val playedItems = (0..index.coerceAtLeast(0)).filter { it < player.mediaItemCount }.map(player::getMediaItemAt)
        // Songs skipped early this session aren't what the listener wants more of.
        val played = playedItems.mapNotNull { queue[it.mediaId] }.filterNot { it.id in sessionSkippedIds }
        val playedUserPicks = playedItems.filter(MixQueueMetadata::userPick).mapNotNull { queue[it.mediaId] }.takeLast(3)
        contextSeeds = (playedUserPicks + userPicks.takeLast(2) + played.takeLast(if (userPicks.isEmpty() && playedUserPicks.isEmpty()) 6 else 3))
            .asReversed().distinctBy(::mixIdentity).asReversed().ifEmpty { contextSeeds }
        val steering = (playedUserPicks + userPicks).distinctBy(::mixIdentity)
        val upcomingIds = keptIds.drop(index.coerceAtLeast(0)).toSet()
        val upcoming = upcomingIds.mapNotNull(queue::get).flatMapTo(HashSet(), adaptive::recordingKeys)
        keptIds.mapNotNull(queue::get).forEach { seen.addAll(adaptive.recordingKeys(it)) }
        val recentlyPlayed = played.takeLast(RECENT_REPEAT_GUARD).flatMapTo(HashSet(), adaptive::recordingKeys)

        // Plans get broader until something is found, so a mix never just stops: new songs
        // first; then songs from earlier in the session (not the last few); then anything not
        // already coming up; and a Normal Mix with nothing left in the library goes online.
        suspend fun plan(online: Boolean): List<Song> {
            val tiers = listOf(seen, upcoming + recentlyPlayed, upcoming)
            for (excluded in tiers) {
                if (!current()) return emptyList()
                val batch = adaptive.next(if (online) MixFlavor.SMART else flavor, contextSeeds, excluded, steering, limit = want)
                    .filterNot { it.id in upcomingIds || adaptive.isDisliked(it) }
                if (batch.isNotEmpty()) return batch
            }
            return emptyList()
        }

        var added = 0
        var aborted = false
        var full = false
        var swapApplied = false
        // Resolve several songs at once (metadata lookup, Spotify → YouTube, cloud URIs) but
        // add them strictly in plan order, so the first song lands as soon as it is ready
        // instead of after the whole batch.
        suspend fun addInOrder(batch: List<Song>) {
            val picks = batch.take(want + maxOf(RESOLVE_SPARE, want / 2))
            if (picks.isEmpty()) return
            val gate = kotlinx.coroutines.sync.Semaphore(RESOLVE_PARALLELISM)
            val gatherTimeout = if (first) FAST_GATHER_TIMEOUT_MS else RESOLVE_GATHER_TIMEOUT_MS
            coroutineScope {
                val resolving = picks.map { song ->
                    async {
                        gate.withPermit {
                            try { resolve(song, gatherTimeout) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        }
                    }
                }
                for ((position, song) in picks.withIndex()) {
                    if (!current()) { aborted = true; break }
                    if (added >= want) break
                    val item = resolving[position].await() ?: continue
                    val attributed = adaptive.attribute(song, item, requestGeneration)
                    if (!current() || adaptive.isDisliked(song)) { aborted = true; break }
                    // The first new song is ready: now the replaced ones go.
                    if (swap != null && !swapApplied) {
                        applySwap(player, swap)
                        swapApplied = true
                        if (pendingSwap === swap) pendingSwap = null
                    }
                    if ((player.currentMediaItemIndex.coerceAtLeast(0) until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == song.id }) continue
                    if (!makeRoom(player)) { full = true; break }
                    val shouldResume = resumeAfterRefill || (player.playWhenReady && player.playbackState == Player.STATE_ENDED)
                    player.addMediaItem(attributed)
                    if (shouldResume) {
                        resumeAfterRefill = false
                        player.seekTo(player.mediaItemCount - 1, 0L)
                        player.prepare()
                        player.play()
                    }
                    seen.addAll(adaptive.recordingKeys(song))
                    added++
                }
                resolving.forEach { it.cancel() }
            }
        }

        addInOrder(plan(online = false))
        // Nothing could be played (offline, lookups failing, an empty library): fall back.
        if (added == 0 && !aborted && !full && current()) {
            if (flavor == MixFlavor.SMART) {
                // Online songs didn't resolve: carry on from the library for now.
                addInOrder(adaptive.next(MixFlavor.NORMAL, contextSeeds, upcoming, steering, limit = want)
                    .filterNot { it.id in upcomingIds || adaptive.isDisliked(it) })
            } else {
                addInOrder(plan(online = true))
            }
        }
        if (aborted) return
        while (seen.size > 1024) seen.remove(seen.first())
        enforceCap(player)
        if (pendingSwap === swap) pendingSwap = null
        val roomLeft = MixRefillPolicy.room(player.mediaItemCount, player.currentMediaItemIndex) > 0
        if (added > 0) {
            fastStart = false
            emptyPasses = 0
            topUp = roomLeft
            _status.value = adaptive.statusTitle(flavor)
        } else {
            // Full, or nothing playable right now: keep the queue and quietly try again later
            // (5 s, 10 s, 20 s … up to a minute). The mix itself never gives up.
            topUp = !full && roomLeft
            _status.value = adaptive.statusTitle(flavor)
            if (topUp) {
                emptyPasses++
                retryAfter = android.os.SystemClock.elapsedRealtime() +
                    minOf(60_000L, 5_000L shl (emptyPasses - 1).coerceIn(0, 4))
            }
        }
    }

    private suspend fun resolve(song: Song, gatherTimeoutMs: Long = RESOLVE_GATHER_TIMEOUT_MS): MediaItem? {
        // Genre, duration, album, artist, BPM and mood travel with the song into the queue
        // (waits at most ~3.5 s; a slower lookup finishes in the background for next time).
        // Cached catalogue metadata applies instantly; a lookup gets a short wait here and
        // finishes in the background (it is on disk for next time).
        var playable = gatherer.gather(song, timeoutMs = gatherTimeoutMs)
        // Online results that arrive with only a video id.
        if (playable.contentUriString.isBlank()) {
            val videoId = playable.youtubeId?.takeIf { it.isNotBlank() } ?: return null
            playable = playable.copy(contentUriString = "youtube://$videoId")
        }
        if (song.contentUriString.startsWith("spotify:")) {
            val id = song.youtubeId ?: withContext(Dispatchers.IO) {
                spotify.resolveSpotifyTrackToVideoId(song.id.removePrefix("spotify_"), song.title,
                    song.artist, song.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), song.creditsAndRelease.isrc)
            } ?: return null
            playable = song.copy(youtubeId = id, contentUriString = "youtube://$id")
        }
        music.saveCloudSong(playable)
        val item = MediaItemBuilder.build(playable)
        val uri = item.localConfiguration?.uri ?: return null
        return if (uri.scheme == "gdrive") item.buildUpon().setUri(engine.resolveCloudUri(uri)).build() else item
    }
}
