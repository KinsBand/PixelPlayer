package com.theveloper.pixelplay.data.metadata

enum class MetadataPriority { NEED, WANT, HELPFUL, ALGORITHM, SESSION }
enum class MetadataScope { WORK, RECORDING, RELEASE, ASSET, USER, TRANSITION, SESSION }
data class MetadataField(val key: String, val label: String, val group: String, val priority: MetadataPriority,
    val scope: MetadataScope, val sources: List<String>, val songPath: String? = null, val unit: String? = null)

/** Extensible catalogue: unknown fields do not block import, playback or analysis. */
object SongMetadataCatalogue {
    val fields = buildList {
        fun group(name: String, prefix: String, priority: MetadataPriority, scope: MetadataScope, sources: String, names: String) {
            names.split('|').forEach { label ->
                val key = prefix + "." + label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                add(MetadataField(key, label, name, priority, scope, sources.split(',')))
            }
        }
        group("Identity", "identity", MetadataPriority.NEED, MetadataScope.RECORDING, "Embedded tags,MusicBrainz,AcoustID,Manual",
            "Work ID|Recording ID|Release ID|Asset ID|ISRC|ISWC|MusicBrainz recording ID|MusicBrainz work ID|MusicBrainz release ID|Provider IDs|Fingerprint|Version match confidence")
        group("Song", "song", MetadataPriority.NEED, MetadataScope.RECORDING, "Embedded tags,MusicBrainz,Artist submission,Manual",
            "Title|Original title|Sort title|Subtitle|Version name|Primary artists|Featured artists|Artist ordering|Display credit|Artist IDs|Studio or live|Original or cover|Remix source|Remaster|Radio edit|Instrumental status|Languages|Explicit status|Catalogue duration")
        group("Release", "release", MetadataPriority.NEED, MetadataScope.RELEASE, "Embedded tags,MusicBrainz,Discogs,Manual",
            "Album title|Album artist|Release type|Release status|Track number|Disc number|Total tracks|Total discs|Original release date|Edition release date|Date precision|Country|Label|Catalogue number|Barcode|Medium|Edition|Packaging|Matrix and runout")
        group("Audio asset", "asset", MetadataPriority.NEED, MetadataScope.ASSET, "File inspection,Local decoder,Manual",
            "Location|Filename|Availability|Duration|Playable start|Playable end|Container|Codec|Sample rate|Channels|Channel layout|Bitrate|Bit depth|File size|SHA256|Decode status|Corrupt file|Codec delay|Encoder padding|Market|Availability checked at|Territory restrictions")
        group("Provenance", "provenance", MetadataPriority.NEED, MetadataScope.ASSET, "Application,Manual",
            "Schema version|Created at|Updated at|Field sources|Retrieval dates|Verification status|Conflicting values|Manual locks|Analysis state|Analysis coverage|Artwork source|Artwork rights|Artwork attribution")
        group("Classification", "classification", MetadataPriority.WANT, MetadataScope.RECORDING, "MusicBrainz,Last.fm,Local model,Manual",
            "Genres|Subgenres|Styles|Era|Tags|Mood labels|Energy|Emotional tone|Tension|Brightness|Calmness|Acousticness|Danceability|Instrumentalness|Model and confidence")
        group("Performance", "performance", MetadataPriority.WANT, MetadataScope.RECORDING, "MusicBrainz,Artist submission,Local model,Manual",
            "Instruments|Dominant instruments|Ensemble|Lead vocals|Backing vocals|Spoken sections|Vocal range|Studio|Venue|Recording location|Recording date|Audience presence|Tempo|Key|Mode|Metre|Tuning reference")
        group("Credits", "credits", MetadataPriority.WANT, MetadataScope.RECORDING, "MusicBrainz,Discogs,Artist submission,Manual",
            "Composer|Songwriter|Lyricist|Performers|Producer|Mixing engineer|Mastering engineer|Recording engineer|Arranger|Remixer|Session musicians|Publisher|Copyright|Licensing|Rights holders|Ownership splits|Contributor identifiers")
        group("Lyrics", "lyrics", MetadataPriority.WANT, MetadataScope.ASSET, "Embedded lyrics,LRCLIB,User import,Validated alignment model,Manual",
            "Plain text|Line timing|Word timing|Syllable timing|Phoneme timing|Phoneme alphabet|Language|Translation|Romanization|Voice lanes|Note events|Asset hash|Text hash|Model version|Alignment quality|Review status|Recording offset|Output latency offset")
        group("Discovery", "discovery", MetadataPriority.WANT, MetadataScope.RECORDING, "MusicBrainz,Wikidata,Last.fm,ListenBrainz,Manual",
            "Related artists|Related recordings|Original recording|Alternate versions|Samples|Interpolations|Artist website|Release link|Provider links|Music video|Description|Front artwork|Back artwork|Booklet|Artist image|Popularity source|Popularity date|Popularity territory|Aliases|Transliteration")
        group("Personal library", "user", MetadataPriority.WANT, MetadataScope.USER, "Application,User import,Manual",
            "Favourite|Rating|Tags|Folders|Playlists|Date added|Last played|Play count|Completed plays|Skip count|Resume position|Notes|Colour label|Contextual likes|Recommendation feedback")
        group("Musicians", "musician", MetadataPriority.HELPFUL, MetadataScope.WORK, "Artist submission,MusicXML import,MIDI import,Manual",
            "Instrument tuning|Capo|Chord chart|Chord voicings|Tablature|Fingering|Playing techniques|Effects notes|Groove|Swing|Drum notation|Fills|Sticking|Count in|Click preferences|Performance key|Tessitura|Harmonies|Breathing cues|Pronunciation|Score|MIDI|Inversions|Harmonic analysis|Orchestration")
        group("Specialist catalogue", "specialist", MetadataPriority.HELPFUL, MetadataScope.WORK, "MusicBrainz,Wikidata,Artist submission,Manual",
            "Work hierarchy|Movement hierarchy|Opus|Conductor|Orchestra|Soloists|Movement order|Jazz personnel|Take number|Solo order|Standard relationship|Raga|Maqam|Tala|Rhythmic cycle|Microtonal tuning|Regional terminology|Source medium|Transfer equipment|Restoration history|Preservation master|Permissions|Territory|Permission expiry")
        group("Rehearsal and DJ", "practice", MetadataPriority.HELPFUL, MetadataScope.USER, "DJ library import,Artist submission,Manual",
            "Assigned parts|Rehearsal arrangement|Section notes|Readiness|Practice tempo|Difficult passages|Setlist position|Hot cues|Loops|Beatgrid corrections|Transition notes|Compatible tracks|Crowd tags|Clean edit|Pronunciation notes|Event suitability|Permission documents")
        group("Algorithm · timing", "timing", MetadataPriority.ALGORITHM, MetadataScope.ASSET, "Local DSP,Validated model,Manual",
            "BPM|Tempo candidates|Tempo confidence|Tempo curve|Beats|Downbeats|Bar boundaries|Time signature timeline|Beat unit|Onsets|Swing estimate|Timing irregularity|Pickup|Phrase boundaries|Beatgrid anchors|Unreliable regions|Sample rate|Timeline origin|Coverage start|Coverage end")
        group("Algorithm · harmony", "harmony", MetadataPriority.ALGORITHM, MetadataScope.ASSET, "Local DSP,Validated model,Manual",
            "Key candidates|Key confidence|Local key timeline|Mode|Scale|Chroma|Chord timeline|Chord confidence|Bass note timeline|Tuning cents|Pitch range|Tonal balance|Tonal ambiguity|Camelot|Pitch shift assessment")
        group("Algorithm · structure", "structure", MetadataPriority.ALGORITHM, MetadataScope.ASSET, "Local DSP,Validated model,Manual",
            "Sections|Section confidence|Repeated sections|Energy curve|Vocal activity|Vocal phrase boundaries|Instrumental regions|Drum activity|Bass activity|Drops|Fills|Builds|Start silence|End silence|Fade regions|Decay tails|Candidate loops|Loop seam quality|Entry points|Exit points")
        group("Algorithm · quality", "quality", MetadataPriority.ALGORITHM, MetadataScope.ASSET, "Local measurement,Validated model,Manual",
            "Integrated LUFS|Short term LUFS|Momentary LUFS|Loudness range|Sample peak|True peak|RMS|Crest factor|Band energy|Spectral centroid|Spectral flatness|Stereo width|Channel balance|Correlation|DC offset|Clipping indicators|Noise|Hum|Dropouts|Source limitations|Audio hash|Analyser|Analyser version|Model version|Settings|Measured at|Units|Confidence|Verification")
        group("Algorithm · transition", "transition", MetadataPriority.ALGORITHM, MetadataScope.TRANSITION, "Mix engine,Manual preview",
            "Source asset IDs|Source regions|Exit timestamp|Entry timestamp|Overlap duration|Phrase alignment|Local tempos|Target tempo|Stretch ratios|Tempo ramps|Harmonic fit|Pitch shift|Tuning correction|Vocal overlap|Vocal collisions|Energy direction|Bass conflict|Bass handover|Regional loudness|Gain envelopes|Predicted headroom|Technique|Fader curves|EQ automation|Stem levels|Effects|Beat drift|Stretch artefacts|Clipping risk|Tail truncation|Preview|Score breakdown|User decision|Engine version|Source hashes|Render settings")
        group("Recording session", "session", MetadataPriority.SESSION, MetadataScope.SESSION, "Recording device,DAW import,Artist submission,Manual",
            "Session ID|Session date|Song version|Sample rate|Recording format|Tempo map|Metre map|Take ID|Performer|Instrument|Start time|Duration|Take selection|Interface|Input channel|Microphone or DI|Preamp|Input gain|Pad|Phantom power|Mic position|Mic distance|Mic angle|Room notes|Reference photo|Clock source|Timecode|Start offset|Latency compensation|Clock drift|Peak history|Clipping events|Buffer underruns|Routing|Buses|Sends|Sidechain|Plugins|Plugin versions|Parameters|Automation|Bypass|Plugin latency|Edit boundaries|Fades|Comp selection|Timing corrections|Pitch corrections|Stem kind|Stem source|Stem alignment|Separation model|Monitoring path|Cue mix|Monitoring latency|Export format|Export bit depth|Dither|Export loudness|Export true peak|Tail length|Export checksum")
    }.map { field -> field.copy(songPath = paths[field.key]) }
    private val paths get() = mapOf(
        "song.title" to "title", "song.primary_artists" to "artist", "song.subtitle" to "songInformation.subtitle",
        "song.featured_artists" to "songInformation.featuredArtists", "song.languages" to "songInformation.language",
        "release.album_title" to "album", "release.album_artist" to "albumArtist", "release.track_number" to "trackNumber",
        "release.disc_number" to "discNumber", "release.country" to "songInformation.releaseCountry",
        "release.release_type" to "songInformation.releaseType", "release.label" to "creditsAndRelease.recordLabel",
        "release.catalogue_number" to "creditsAndRelease.catalogueNumber", "release.barcode" to "creditsAndRelease.upcEan",
        "asset.location" to "contentUriString", "asset.duration" to "duration", "asset.codec" to "audioTech.codec",
        "asset.sample_rate" to "sampleRate", "asset.bitrate" to "bitrate", "asset.bit_depth" to "audioTech.bitDepth",
        "asset.channels" to "audioTech.channels", "asset.file_size" to "audioTech.fileSize", "asset.sha256" to "audioTech.checksum",
        "identity.isrc" to "creditsAndRelease.isrc", "classification.genres" to "genre", "classification.subgenres" to "songInformation.subgenre",
        "classification.energy" to "mixIntelligence.energy", "classification.danceability" to "mixIntelligence.danceability",
        "performance.tempo" to "musicalFeatures.bpm", "performance.key" to "musicalFeatures.key", "performance.mode" to "musicalFeatures.mode",
        "performance.metre" to "musicalFeatures.timeSignature", "performance.tuning_reference" to "musicalFeatures.tuning",
        "credits.composer" to "creditsAndRelease.composer", "credits.songwriter" to "creditsAndRelease.songwriter",
        "credits.lyricist" to "musicalFeatures.lyricist", "credits.producer" to "creditsAndRelease.producer",
        "credits.mixing_engineer" to "creditsAndRelease.mixingEngineer", "credits.mastering_engineer" to "creditsAndRelease.masteringEngineer",
        "credits.recording_engineer" to "creditsAndRelease.recordingEngineer", "credits.arranger" to "creditsAndRelease.arranger",
        "credits.publisher" to "creditsAndRelease.publisher", "credits.copyright" to "creditsAndRelease.copyright",
        "credits.licensing" to "creditsAndRelease.licensing", "user.favourite" to "isFavorite", "user.date_added" to "dateAdded",
        "user.rating" to "userActivityStats.rating", "user.notes" to "userActivityStats.notes", "user.play_count" to "userActivityStats.playCount",
        "musician.capo" to "musicalFeatures.capo", "musician.instrument_tuning" to "musicalFeatures.tuning",
        "lyrics.plain_text" to "lyrics", "timing.bpm" to "musicalFeatures.bpm",
        "quality.integrated_lufs" to "audioTech.loudnessLufs", "quality.audio_hash" to "audioTech.checksum")
}

