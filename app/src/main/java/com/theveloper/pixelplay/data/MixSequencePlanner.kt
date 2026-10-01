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
    val MODEL_VERSION = "session-vibe-beam-v6/" + MixWeights.DEFAULT.version
    private const val POOL_LIMIT = 160
    private const val BEAM_WIDTH = 5
    private const val HORIZON = 3
    private const val DAY = 86_400_000.0
    data class Decision(
        val song: Song, val source: String, val components: Map<String, Double>,
        val confidence: Double = 0.0, val selectionProbability: Double = 1.0,
        val explored: Boolean = false
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
    ): List<Decision> {
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
            val familiar = (observations.isEmpty() && mixIdentity(song) in libraryKeys) ||
                song.id in favorites || song.isFavorite || attempts.any { it.uniqueMs >= 30_000 }
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
            if (energyTarget != null) components["energyTarget"] = energyFit(song, energyTarget, weights)
            Decision(song, if (!familiar) "discovery" else if (rediscovered) "rediscovery" else "familiar", components,
                confidence = taste.confidence(song))
        }.sortedWith(order)
        // Reserve room for both sources; a large familiar library cannot crowd out discoveries.
        val pool = (ranked.take(POOL_LIMIT / 2) + ranked.filter { it.source == "discovery" }.take(POOL_LIMIT / 4) +
            ranked.filter { it.source != "discovery" }.take(POOL_LIMIT / 4)).distinctBy { it.song.id }.toMutableList()
        val result = mutableListOf<Decision>()
        // Transition scores only depend on the (previous, next) pair, and the beam asks for the
        // same pairs over and over; each is computed once per plan.
        val transitions = TransitionCache(weights)
        val requestedDiscovery = discovery.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.35
        var explorationUsed = false
        // Recent early skips in this mix lower exploration; the same for every pick of this plan.
        val recentSkips = observations.sortedByDescending { it.startedAt }.take(20).count { it.mixId == mixId && MixRewards.isEarlySkip(it) }
        while (pool.isNotEmpty() && result.size < limit.coerceIn(0, MixRefillPolicy.MAX_QUEUE)) {
            var beam = listOf(Path(emptyList(), 0.0))
            repeat(minOf(HORIZON, limit - result.size, pool.size)) {
                beam = beam.flatMap { path ->
                    val chosenIds = path.choices.map { it.song.id }.toSet()
                    val prefix = result + path.choices
                    options(pool.filterNot { it.song.id in chosenIds }, prefix, seeds, requestedDiscovery, weights, transitions)
                        .take(BEAM_WIDTH).map { it.decision() }.map { next -> Path(path.choices + next, path.score + next.score * 0.75.pow(path.choices.size)) }
                }.sortedWith(compareByDescending<Path> { it.score }.thenBy { it.choices.joinToString("|") { item -> item.song.id } })
                    .take(BEAM_WIDTH)
            }
            val greedy = beam.firstOrNull()?.choices?.firstOrNull() ?: break
            val eligible = options(pool, result, seeds, requestedDiscovery, weights, transitions)
                .filter { it.item.source == "discovery" && it.score >= greedy.score - 3.0 && (penalties[it.item.song.id] ?: 0.0) == 0.0 }
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

    private fun artistKey(song: Song) = song.artist.trim().lowercase()

    /** −weight × distance from the wanted energy; an unmeasured song costs an average 0.3. */
    private fun energyFit(song: Song, target: Double, weights: MixWeights): Double {
        val energy = song.mixIntelligence.energy?.takeIf { it.isFinite() && it in 0f..1f }
        val gap = if (energy == null) 0.3 else kotlin.math.abs(energy - target.coerceIn(0.0, 1.0))
        return -gap * weights.energyTarget
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
        private val balance: Double
    ) {
        val score: Double = run {
            var total = item.score
            for (value in transition.values) total += value
            total + spacing + album + balance
        }
        fun decision() = item.copy(components = item.components + transition +
            mapOf("artistSpacing" to spacing, "albumSpacing" to album, "discoveryBalance" to balance))
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
        pool: List<Decision>, prefix: List<Decision>, seeds: List<Song>, discovery: Double,
        weights: MixWeights, transitions: TransitionCache
    ): List<Scored> {
        val history = seeds.map { it } + prefix.map { it.song }
        val recent = history.takeLast(2)
        // Hard artist gap: an artist heard in the last (gap − 1) songs can't come back yet,
        // unless every remaining candidate is one of those artists.
        val blocked = history.takeLast((weights.minArtistGap - 1).coerceAtLeast(0))
            .map(::artistKey).filter { it.isNotBlank() }.toSet()
        val open = pool.filter { artistKey(it.song) !in blocked }.ifEmpty { pool }
        val wantDiscovery = prefix.count { it.source == "discovery" } < ((prefix.size + 1) * discovery).toInt()
        val recentReversed = recent.asReversed()
        val previous = recent.lastOrNull()
        val scored = open.map { item ->
            var spacing = 0.0
            for ((index, earlier) in recentReversed.withIndex()) {
                spacing += if (earlier.artist.isNotBlank() && earlier.artist.equals(item.song.artist, true))
                    -weights.artistRepeat / (index + 1) else 0.0
            }
            val album = if (previous?.let { it.album.isNotBlank() && it.album == item.song.album && it.artist == item.song.artist } == true) -weights.albumRepeat else 0.0
            Scored(item, transitions.get(previous, item.song), spacing, album,
                if ((item.source == "discovery") == wantDiscovery) 1.0 else 0.0)
        }.sortedWith(scoredOrder)
        val target = scored.filter { (it.item.source == "discovery") == wantDiscovery }
        // Do not force a discovery whose estimated quality is far below the available alternative.
        return if (target.isNotEmpty() && target.first().score >= (scored.firstOrNull()?.score ?: 0.0) - 1.0) target else scored
    }
}
