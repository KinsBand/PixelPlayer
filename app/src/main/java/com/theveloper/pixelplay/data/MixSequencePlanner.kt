package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import kotlin.math.pow
import kotlin.random.Random

/**
 * Bounded look-ahead planner with explicit, auditable score components.
 *
 * Each candidate gets named components (relevance, history, taste, co-listens, vibe), then a
 * beam search picks the order, adding transition scores ([MixVibe]), artist / album spacing
 * and the discovery balance. All weights come from [MixWeights]; plays are labelled by
 * [MixRewards]. An artist can't return within [MixWeights.minArtistGap] songs unless nothing
 * else is left.
 */
internal object MixSequencePlanner {
    /** Planner generation + weight set; logged with every recommendation. */
    val MODEL_VERSION = "session-vibe-beam-v7/" + MixWeights.DEFAULT.version
    private const val POOL_LIMIT = 160
    private const val BEAM_WIDTH = 5
    private const val HORIZON = 3
    private const val DAY = 86_400_000.0
    data class Decision(
        val song: Song, val source: String, val components: Map<String, Double>,
        val confidence: Double = 0.0, val selectionProbability: Double = 1.0,
        val explored: Boolean = false,
        /** The friend in the room this song came from, or null for your own pool. */
        val friendId: String? = null,
    ) {
        /** Computed once: the beam search compares scores thousands of times per plan. */
        val score: Double = components.values.sum()
    }
    private data class Path(val choices: List<Decision>, val score: Double)

    fun plan(
        candidates: List<Song>, seeds: List<Song>, favorites: Set<String>,
        libraryKeys: Set<String>, history: List<MixAttempt>, mixId: String,
        discovery: Double, now: Long, limit: Int = 12, penalties: Map<String, Double> = emptyMap(),
        direction: Song? = null, directionWeight: Double = 1.5, catalogue: List<Song> = candidates + seeds,
        random: Random? = null,
        /** Extra score per song id, e.g. how well it fits a locked vibe ("vibeFit"). */
        boosts: Map<String, Double> = emptyMap(),
        /**
         * Which plays count as the same recording. History is grouped by this, so a song's
         * stream and library copies share their skips, listens and fatigue.
         */
        identity: (Song) -> String = ::mixIdentity,
        weights: MixWeights = MixWeights.DEFAULT,
        /** The current mix session, for the session bandit in [MixTasteProfile]. */
        sessionId: String? = null,
        /** More named components per song id (e.g. "soundAlike"), added as they are. */
        extras: Map<String, Map<String, Double>> = emptyMap(),
        /** 0…1 energy the listener asked for (Calm ≈ 0.25, Hype ≈ 0.85), or null to follow. */
        energyTarget: Double? = null,
        microSkipCooldowns: List<MicroSkipCooldown> = emptyList(),
        /**
         * Energy wanted at each planned position (0 = the next slot), so a new Energy setting
         * eases in over a few songs and Follow tracks what's been played. Overrides [energyTarget].
         */
        energyAt: ((Int) -> Double?)? = null,
        /** Weight of [energyAt] (defaults to [MixWeights.energyTarget]). */
        energyWeight: Double? = null,
        /** Song id → friend id for songs that come from friends in the room. */
        friendOf: Map<String, String> = emptyMap(),
        /** Share of picks that should be friends' songs (0 = off). */
        friendShare: Double = 0.0,
        /** How far behind each friend is on their fair share (higher = their turn first). */
        friendDeficit: Map<String, Double> = emptyMap(),
        /** Artist (lower-case) → minimum songs before they may return ("less Drake"). */
        artistGaps: Map<String, Int> = emptyMap(),
    ): List<Decision> {
        val energyFor: ((Int) -> Double?)? = energyAt ?: energyTarget?.let { target -> { _: Int -> target } }
        val energyScale = energyWeight ?: weights.energyTarget
        val observations = history.filter { it.startedAt <= now }
        val keyById = HashMap<String, String>(catalogue.size + candidates.size)
        (catalogue + candidates).forEach { keyById.getOrPut(it.id) { identity(it) } }
        val bySong = observations.groupBy { keyById[it.songId] ?: ("id:" + it.songId) }
        val taste = MixTasteProfile(catalogue, observations, favorites, mixId, now, sessionId, weights, random)
        val coListen = MixCoListen(observations, keyById, now)
        val seedKeys = seeds.takeLast(4).map { keyById[it.id] ?: identity(it) }
        val seedArtists = seeds.mapTo(HashSet()) { it.artist.lowercase() }

        val unique = candidates.distinctBy(::mixIdentity)
        // Relevance first, so songs whose fit can't be judged get the batch's (discounted) average.
        val rawFit = unique.associate { it.id to MixVibe.sessionFit(it, seeds) }
        fun judgeable(song: Song) = seeds.isEmpty() || GenreTaxonomy.match(song.genre) != null ||
            song.artist.lowercase() in seedArtists
        val knownFits = unique.filter(::judgeable).mapNotNull { rawFit[it.id] }
        val fitPrior = if (knownFits.isEmpty()) 0.0 else knownFits.average() * weights.unknownFitShare

        val ranked = unique.map { song ->
            val key = keyById[song.id] ?: identity(song)
            val attempts = bySong[key].orEmpty()
            val rewards = attempts.mapNotNull { attempt -> MixRewards.reward(attempt)?.let { attempt to it } }
            // Familiar fades: a song really listened to more than [MixWeights.familiarDays] ago
            // counts as a discovery again (it used to stay familiar forever after one play).
            val familiarSince = now - (weights.familiarDays * DAY).toLong()
            val familiar = (observations.isEmpty() && mixIdentity(song) in libraryKeys) ||
                song.id in favorites || song.isFavorite ||
                attempts.any { it.uniqueMs >= 30_000 && it.startedAt >= familiarSince }
            val lastPositive = rewards.filter { it.second > 0 }.maxOfOrNull { it.first.startedAt }
            val rediscovered = lastPositive != null && now - lastPositive > 14L * 86_400_000
            val fatigue = attempts.filter { it.activeMs >= 5_000 }
                .sumOf { MixRewards.fade(now, it.startedAt, DAY) * weights.fatiguePerPlay }
            val rejections = rewards.filter { it.first.mixId == mixId && it.second < 0 }
                .sumOf { -it.second * MixRewards.fade(now, it.first.startedAt, 1_800_000.0) }
            val fit = if (judgeable(song)) rawFit[song.id] ?: 0.0 else fitPrior
            val isFavorite = song.id in favorites || song.isFavorite
            // A liked song that doesn't fit what's playing (or the locked vibe) gets a smaller lift.
            val lowFit = (seeds.isNotEmpty() && fit < weights.lowFitThreshold) ||
                (boosts.isNotEmpty() && (boosts[song.id] ?: 0.0) < weights.lowVibeBoost)
            // One map per song, filled in a fixed order (the order is part of the logged record).
            val components = LinkedHashMap<String, Double>(24)
            components["sessionFit"] = fit * weights.sessionFit
            components["explicitDirection"] = direction?.let { mixAffinity(song, listOf(it)) * directionWeight } ?: 0.0
            components["explicitFavorite"] = if (!isFavorite) 0.0 else if (lowFit) weights.favoriteLowFit else weights.favorite
            components["enjoyment"] = (rewards.filter { it.second > 0 }
                .sumOf { it.second * MixRewards.fade(now, it.first.startedAt, 30 * DAY) } * weights.enjoyment)
                .coerceAtMost(weights.enjoymentCap)
            components["rediscovery"] = if (rediscovered) weights.rediscovery else 0.0
            components["fatigue"] = -(fatigue + (penalties[song.id] ?: 0.0))
            components["recentRejection"] = -rejections.coerceAtMost(weights.recentRejectionCap)
            components["microSkipCooldown"] = -MicroSkipPolicy.penalty(song.artist, song.genre, microSkipCooldowns, sessionId, now)
            components["coListen"] = if (coListen.isEmpty) 0.0 else coListen.affinity(seedKeys, key) * weights.coListen
            components.putAll(taste.components(song))
            if (boosts.isNotEmpty()) components["vibeFit"] = boosts[song.id] ?: 0.0
            for ((name, values) in extras) components[name] = values[song.id] ?: 0.0
            Decision(song, if (!familiar) "discovery" else if (rediscovered) "rediscovery" else "familiar", components,
                confidence = taste.confidence(song), friendId = friendOf[song.id])
        }.sortedWith(order)
        // Reserve room for both sources; a large familiar library cannot crowd out discoveries,
        // and friends' songs keep a place when friends are in the room.
        // Each friend gets their own slice, so one friend with a huge library can't fill it alone.
        val friendReserve = if (friendShare <= 0.0) emptyList() else {
            val byFriend = ranked.filter { it.friendId != null }.groupBy { it.friendId!! }
            val each = (POOL_LIMIT / 4 / byFriend.size.coerceAtLeast(1)).coerceAtLeast(8)
            byFriend.values.flatMap { it.take(each) }
        }
        val pool = (ranked.take(POOL_LIMIT / 2) + ranked.filter { it.source == "discovery" }.take(POOL_LIMIT / 4) +
            ranked.filter { it.source != "discovery" }.take(POOL_LIMIT / 4) + friendReserve).distinctBy { it.song.id }.toMutableList()
        val share = Quotas(
            discovery = discovery.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.35,
            friends = friendShare.coerceIn(0.0, 0.9),
            // A random start, so small re-plans (2–3 songs) still give friends their share on average.
            friendOffset = random?.nextDouble() ?: 0.5,
            friendDeficit = friendDeficit,
            artistGaps = artistGaps,
            energyAt = energyFor,
            energyScale = energyScale,
        )
        val result = mutableListOf<Decision>()
        // Transition scores only depend on the (previous, next) pair, and the beam asks for the
        // same pairs over and over; each is computed once per plan.
        val transitions = TransitionCache(weights)
        var explorationUsed = false
        // Recent early skips in this mix lower exploration; the same for every pick of this plan.
        val recentSkips = observations.sortedByDescending { it.startedAt }.take(20).count { it.mixId == mixId && MixRewards.isEarlySkip(it) }
        while (pool.isNotEmpty() && result.size < limit.coerceIn(0, MixRefillPolicy.MAX_QUEUE)) {
            var beam = listOf(Path(emptyList(), 0.0))
            repeat(minOf(HORIZON, limit - result.size, pool.size)) {
                beam = beam.flatMap { path ->
                    val chosenIds = path.choices.map { it.song.id }.toSet()
                    val prefix = result + path.choices
                    options(pool.filterNot { it.song.id in chosenIds }, prefix, seeds, share, weights, transitions)
                        .take(BEAM_WIDTH).map { it.decision() }.map { next -> Path(path.choices + next, path.score + next.score * 0.75.pow(path.choices.size)) }
                }.sortedWith(compareByDescending<Path> { it.score }.thenBy { it.choices.joinToString("|") { item -> item.song.id } })
                    .take(BEAM_WIDTH)
            }
            var greedy = beam.firstOrNull()?.choices?.firstOrNull() ?: break
            val here = options(pool, result, seeds, share, weights, transitions)
            // A friend's turn: draw from that friend's best few songs, weighted by score, so the
            // same moment doesn't always give the same song ("random, but by feel").
            if (greedy.friendId != null && random != null) {
                val top = here.filter { it.item.friendId == greedy.friendId && it.score >= greedy.score - weights.friendTolerance }.take(5)
                if (top.size > 1) {
                    val best = top.first().score
                    val odds = top.map { kotlin.math.exp((it.score - best) / FRIEND_TEMPERATURE) }
                    var roll = random.nextDouble() * odds.sum()
                    val pick = top.indices.firstOrNull { index -> roll -= odds[index]; roll <= 0 } ?: 0
                    greedy = top[pick].decision()
                }
            }
            val eligible = here
                .filter { it.item.source == "discovery" && it.item.friendId == null && it.score >= greedy.score - 3.0 && (penalties[it.item.song.id] ?: 0.0) == 0.0 }
                .take(4).map { it.decision() }
            // At most one exploration branch per batch. Rejections lower epsilon; no random
            // choice can bypass eligibility, explicit exclusions, fatigue, or discovery balance.
            val epsilon = if (random != null && !explorationUsed && greedy.source == "discovery" && eligible.size > 1)
                0.12 / (1 + recentSkips) else 0.0
            val explore = epsilon > 0 && random!!.nextDouble() < epsilon
            val selected = if (explore) eligible[random!!.nextInt(eligible.size)] else greedy
            val probability = (if (selected.song.id == greedy.song.id) 1 - epsilon else 0.0) +
                (if (epsilon > 0 && eligible.any { it.song.id == selected.song.id }) epsilon / eligible.size else 0.0)
            result += selected.copy(selectionProbability = probability, explored = explore)
            if (explore) explorationUsed = true
            val selectedIdentity = mixIdentity(selected.song)
            pool.removeAll { mixIdentity(it.song) == selectedIdentity }
        }
        return result
    }

    private val order = compareByDescending<Decision> { it.score }.thenBy { it.song.id }

    /** How tightly a friend's turn sticks to their best-fitting song (higher = more random). */
    private const val FRIEND_TEMPERATURE = 1.2

    /** Everything that depends on how many picks of each kind came before a slot. */
    private class Quotas(
        val discovery: Double,
        val friends: Double,
        val friendOffset: Double,
        val friendDeficit: Map<String, Double>,
        val artistGaps: Map<String, Int>,
        val energyAt: ((Int) -> Double?)?,
        val energyScale: Double,
    ) {
        /** Guessed energy per song id (NaN = nothing to guess from), so each song is guessed once per plan. */
        val guessed = HashMap<String, Double>()
    }

    private fun artistKey(song: Song) = song.artist.trim().lowercase()

    /**
     * −weight × distance from the wanted energy. An unmeasured song uses a guess from its mood /
     * genre ([Feel.of]) at a discount, and costs an average 0.3 only when nothing is known.
     */
    private fun energyFit(song: Song, target: Double, scale: Double, guessed: HashMap<String, Double>): Double {
        val measured = song.mixIntelligence.energy?.takeIf { it.isFinite() && it in 0f..1f }?.toDouble()
        val wanted = target.coerceIn(0.0, 1.0)
        val gap = when {
            measured != null -> kotlin.math.abs(measured - wanted)
            else -> {
                val guess = guessed.getOrPut(song.id) { Feel.of(song).energy ?: Double.NaN }
                if (guess.isNaN()) 0.3 else 0.3 * 0.4 + kotlin.math.abs(guess - wanted) * 0.6
            }
        }
        return -gap * scale
    }

    /**
     * A candidate scored as the next song after a given prefix. The full [Decision] (with every
     * component) is only built for the few that are actually used; [score] is summed in the same
     * order as [Decision.score], so the ranking is identical to building them all.
     */
    private class Scored(
        val item: Decision,
        private val transition: Map<String, Double>,
        private val spacing: Double,
        private val album: Double,
        private val balance: Double,
        private val energy: Double?,
        private val friendBalance: Double,
    ) {
        val score: Double = run {
            var total = item.score
            for (value in transition.values) total += value
            total + spacing + album + balance + (energy ?: 0.0) + friendBalance
        }
        fun decision() = item.copy(components = item.components + transition +
            mapOf("artistSpacing" to spacing, "albumSpacing" to album, "discoveryBalance" to balance) +
            (if (energy != null) mapOf("energyTarget" to energy) else emptyMap()) +
            (if (friendBalance != 0.0) mapOf("friendShare" to friendBalance) else emptyMap()))
    }

    /** [MixVibe.components] per (previous, next) pair, for one plan. */
    private class TransitionCache(private val weights: MixWeights) {
        private val byPrevious = HashMap<String, HashMap<String, Map<String, Double>>>()
        fun get(previous: Song?, next: Song): Map<String, Double> {
            if (previous == null) return emptyMap()
            return byPrevious.getOrPut(previous.id) { HashMap() }
                .getOrPut(next.id) { MixVibe.components(previous, next, weights) }
        }
    }

    private val scoredOrder = compareByDescending<Scored> { it.score }.thenBy { it.item.song.id }

    private fun options(
        pool: List<Decision>, prefix: List<Decision>, seeds: List<Song>, quotas: Quotas,
        weights: MixWeights, transitions: TransitionCache
    ): List<Scored> {
        val history = seeds.map { it } + prefix.map { it.song }
        val recent = history.takeLast(2)
        // Hard artist gap: an artist heard in the last (gap − 1) songs can't come back yet,
        // unless every remaining candidate is one of those artists. "Less <artist>" sets a
        // longer gap for that artist, and nobody gets more than [MixWeights.artistCapPer20]
        // songs in any 20.
        val gapBlocked = history.takeLast((weights.minArtistGap - 1).coerceAtLeast(0))
            .map(::artistKey).filter { it.isNotBlank() }.toSet()
        val last20 = history.takeLast(19).map(::artistKey).filter { it.isNotBlank() }
        val counts = last20.groupingBy { it }.eachCount()
        val capped = counts.filterValues { it >= weights.artistCapPer20 }.keys
        val longGaps = quotas.artistGaps.filter { (artist, gap) -> artist in history.takeLast(gap.coerceAtLeast(1)).map(::artistKey) }.keys
        val blocked = gapBlocked + capped + longGaps
        val open = pool.filter { artistKey(it.song) !in blocked }.ifEmpty { pool.filter { artistKey(it.song) !in gapBlocked }.ifEmpty { pool } }
        val position = prefix.size
        val wantDiscovery = prefix.count { it.source == "discovery" } < ((prefix.size + 1) * quotas.discovery).toInt()
        // Friends' share: like the Discover balance, with a random start so short re-plans
        // still give friends their share on average.
        val friendsSoFar = prefix.count { it.friendId != null }
        val wantFriend = quotas.friends > 0.0 && friendsSoFar + 1 <= (position + 1) * quotas.friends + quotas.friendOffset
        // Whose turn: the friend furthest behind their fair share (counting this plan too).
        val plannedPerFriend = prefix.mapNotNull { it.friendId }.groupingBy { it }.eachCount()
        val friendOrder = if (!wantFriend) emptyList() else pool.mapNotNull { it.friendId }.distinct()
            .sortedByDescending { (quotas.friendDeficit[it] ?: 0.0) - (plannedPerFriend[it] ?: 0) }
        val energyWanted = quotas.energyAt?.invoke(position)
        val recentReversed = recent.asReversed()
        val previous = recent.lastOrNull()
        val scored = open.map { item ->
            var spacing = 0.0
            for ((index, earlier) in recentReversed.withIndex()) {
                spacing += if (earlier.artist.isNotBlank() && earlier.artist.equals(item.song.artist, true))
                    -weights.artistRepeat / (index + 1) else 0.0
            }
            val album = if (previous?.let { it.album.isNotBlank() && it.album == item.song.album && it.artist == item.song.artist } == true) -weights.albumRepeat else 0.0
            val isFriend = item.friendId != null
            Scored(item, transitions.get(previous, item.song), spacing, album,
                if (isFriend || (item.source == "discovery") == wantDiscovery) 1.0 else 0.0,
                energyWanted?.let { energyFit(item.song, it, quotas.energyScale, quotas.guessed) },
                if (quotas.friends > 0.0 && isFriend == wantFriend) 1.0 else 0.0)
        }.sortedWith(scoredOrder)
        val best = scored.firstOrNull()?.score ?: 0.0
        if (wantFriend) {
            // The friend whose turn it is, if one of their songs fits well enough; else the next
            // friend; a song that doesn't fit is never forced (the turn rolls over).
            for (friend in friendOrder) {
                val theirs = scored.filter { it.item.friendId == friend }
                if (theirs.isNotEmpty() && theirs.first().score >= best - weights.friendTolerance) return theirs
            }
        }
        val ownPool = if (quotas.friends > 0.0 && !wantFriend) scored.filter { it.item.friendId == null }.ifEmpty { scored } else scored
        val target = ownPool.filter { (it.item.source == "discovery") == wantDiscovery }
        // Do not force a discovery whose estimated quality is far below the available alternative.
        return if (target.isNotEmpty() && target.first().score >= (ownPool.firstOrNull()?.score ?: 0.0) - weights.discoveryTolerance) target else ownPool
    }
}
