# Song metadata and karaoke implementation

## What is implemented

Song information now opens a complete metadata catalogue with Needs, Wants, Helpful, Algorithm and Session filters. Existing song values are populated where the application has a matching field. Every field can hold a manual correction; custom namespaced fields are supported. Unknown values stay unknown. The catalogue is a schema and editor, not a promise that a public API supplies every field.

Claims retain source, retrieval date, status, optional confidence, units, method version and asset revision. Manual claims survive automated refreshes. Atomic private storage preserves changes without rewriting audio files. Asset-dependent claims become stale when the asset revision changes. The lightweight cache revision uses file metadata; a separately computed SHA256 identifies analysed file bytes.

Existing MusicBrainz enrichment records release claims. Local analysis records its version, hash, actual coverage, working sample rate and estimates. Partial decoding no longer produces purported whole-track loudness or outro timing, and a missing tempo no longer creates a fabricated 120 BPM beat grid. Analysis reuse checks content hash and analyser version. PCM memory is bounded; decoding observes cancellation and heavy work runs off the main thread.

Lyrics imports support LRC, TTML and structured timing JSON. TTML millisecond precision, word ends, line ends and voice identifiers survive import and caching. The native format can carry syllable IDs, phonemes, alphabet, character ranges, confidence and provenance. Highlighting follows playback position, including seek backwards and sustained intervals. Ordinary line-timed lyrics remain line-timed.

## Remaining acoustic work

Automatic phoneme/vowel alignment for arbitrary recordings is **not implemented**. No validated singing alignment model has been bundled or benchmarked. Imported phoneme evidence can be displayed, but adding a runtime does not create such evidence. [SOFA](https://github.com/qiuqiao/SOFA) is a research candidate designed for singing; compatibility, language coverage, model licensing, conversion and real-device quality must be evaluated before integration. [ONNX Runtime Mobile](https://onnxruntime.ai/docs/tutorials/mobile/) supplies deployment tools, not a universal singing model.

A production alignment worker should bind results to audio hash, text hash, language, model version and settings; use one cancellable job at a time; obey battery/thermal constraints; persist progress; reject poor alignment; and retain manual edits. Low-confidence regions must fall back to word, line or plain lyrics. Dense consonant-to-character mapping, overlapping singers and melisma need explicit modelling. Output-device latency correction is separate from recording alignment. These requirements are a follow-on model integration, not a claim about the current implementation.

## Sources and practical access

| Source | Useful data | Access and limits | Current use |
| --- | --- | --- | --- |
| Embedded tags and local file inspection | Titles, credits, artwork, technical format, checksum | No remote fee; accuracy depends on the file | Existing reader plus catalogue |
| [MusicBrainz](https://musicbrainz.org/doc/MusicBrainz_API) | Recording/release identifiers, credits, dates, relationships | Public reads; identify the client and limit to one request per second; check commercial terms | Existing enrichment, extended release claims |
| [AcoustID](https://acoustid.org/webservice) | Fingerprint-to-recording candidates | Client key; free noncommercial service; maximum three requests per second | Existing identification infrastructure; not a source of lyrics or stems |
| [LRCLIB](https://lrclib.net/docs) | Plain and line-synchronised lyrics where present | Public catalogue; recording matching still required | Existing lyrics lookup |
| [Wikidata](https://www.wikidata.org/wiki/Wikidata:Data_access) | Artist/work relationships and identifiers | Public structured data; observe endpoint constraints | Candidate source, no new adapter in this change |
| [Last.fm](https://www.last.fm/api/tos) | Community tags and popularity context | API key; free noncommercial use subject to terms, commercial agreement otherwise | Candidate source, no new adapter |
| Discogs | Edition, physical-media and detailed credit information | Review current API/data licensing; do not assume all content is unrestricted | Candidate source, no new adapter |
| Local DSP / optional models | Tempo, key, energy, boundaries and quality estimates | No per-call API cost; CPU, memory and model licensing still matter | Existing analyser with coverage/provenance fixes |
| User / artist / DAW import | Recording-session setup, stems, intent, rights, scores and corrections | Usually requires supplied project/session data | Manual fields supported; automatic DAW import not added |

Microphone position, ownership splits, original stems and exact performance intent cannot reliably be reconstructed from a mixed audio file. Inferred stems are distinct from original multitracks. Mixing suitability belongs to a pair of passages and proposed settings, not one universal score attached to a song.

## Identity and units

Keep work, performance/recording, release edition, audio asset, personal preferences, transition and session identities distinct. The catalogue's scope indicates where a value belongs; more complex relationships can be stored as structured JSON or custom fields. Time units must accompany machine-derived claims. Playback lyric timestamps are milliseconds; current beat-grid claims use seconds. Confidence must describe a calibrated method, never an invented percentage.

## Complete field catalogue

The following fields match the in-app catalogue. Source lists describe possible sources, not implemented integrations or guaranteed coverage. Algorithm and session entries remain unknown until measured, imported or supplied by the user.
### Identity — NEED

Scope: RECORDING. Candidate sources: Embedded tags,MusicBrainz,AcoustID,Manual.

- Work ID
- Recording ID
- Release ID
- Asset ID
- ISRC
- ISWC
- MusicBrainz recording ID
- MusicBrainz work ID
- MusicBrainz release ID
- Provider IDs
- Fingerprint
- Version match confidence

### Song — NEED

Scope: RECORDING. Candidate sources: Embedded tags,MusicBrainz,Artist submission,Manual.

- Title
- Original title
- Sort title
- Subtitle
- Version name
- Primary artists
- Featured artists
- Artist ordering
- Display credit
- Artist IDs
- Studio or live
- Original or cover
- Remix source
- Remaster
- Radio edit
- Instrumental status
- Languages
- Explicit status
- Catalogue duration

### Release — NEED

Scope: RELEASE. Candidate sources: Embedded tags,MusicBrainz,Discogs,Manual.

- Album title
- Album artist
- Release type
- Release status
- Track number
- Disc number
- Total tracks
- Total discs
- Original release date
- Edition release date
- Date precision
- Country
- Label
- Catalogue number
- Barcode
- Medium
- Edition
- Packaging
- Matrix and runout

### Audio asset — NEED

Scope: ASSET. Candidate sources: File inspection,Local decoder,Manual.

- Location
- Filename
- Availability
- Duration
- Playable start
- Playable end
- Container
- Codec
- Sample rate
- Channels
- Channel layout
- Bitrate
- Bit depth
- File size
- SHA256
- Decode status
- Corrupt file
- Codec delay
- Encoder padding
- Market
- Availability checked at
- Territory restrictions

### Provenance — NEED

Scope: ASSET. Candidate sources: Application,Manual.

- Schema version
- Created at
- Updated at
- Field sources
- Retrieval dates
- Verification status
- Conflicting values
- Manual locks
- Analysis state
- Analysis coverage
- Artwork source
- Artwork rights
- Artwork attribution

### Classification — WANT

Scope: RECORDING. Candidate sources: MusicBrainz,Last.fm,Local model,Manual.

- Genres
- Subgenres
- Styles
- Era
- Tags
- Mood labels
- Energy
- Emotional tone
- Tension
- Brightness
- Calmness
- Acousticness
- Danceability
- Instrumentalness
- Model and confidence

### Performance — WANT

Scope: RECORDING. Candidate sources: MusicBrainz,Artist submission,Local model,Manual.

- Instruments
- Dominant instruments
- Ensemble
- Lead vocals
- Backing vocals
- Spoken sections
- Vocal range
- Studio
- Venue
- Recording location
- Recording date
- Audience presence
- Tempo
- Key
- Mode
- Metre
- Tuning reference

### Credits — WANT

Scope: RECORDING. Candidate sources: MusicBrainz,Discogs,Artist submission,Manual.

- Composer
- Songwriter
- Lyricist
- Performers
- Producer
- Mixing engineer
- Mastering engineer
- Recording engineer
- Arranger
- Remixer
- Session musicians
- Publisher
- Copyright
- Licensing
- Rights holders
- Ownership splits
- Contributor identifiers

### Lyrics — WANT

Scope: ASSET. Candidate sources: Embedded lyrics,LRCLIB,User import,Validated alignment model,Manual.

- Plain text
- Line timing
- Word timing
- Syllable timing
- Phoneme timing
- Phoneme alphabet
- Language
- Translation
- Romanization
- Voice lanes
- Note events
- Asset hash
- Text hash
- Model version
- Alignment quality
- Review status
- Recording offset
- Output latency offset

### Discovery — WANT

Scope: RECORDING. Candidate sources: MusicBrainz,Wikidata,Last.fm,ListenBrainz,Manual.

- Related artists
- Related recordings
- Original recording
- Alternate versions
- Samples
- Interpolations
- Artist website
- Release link
- Provider links
- Music video
- Description
- Front artwork
- Back artwork
- Booklet
- Artist image
- Popularity source
- Popularity date
- Popularity territory
- Aliases
- Transliteration

### Personal library — WANT

Scope: USER. Candidate sources: Application,User import,Manual.

- Favourite
- Rating
- Tags
- Folders
- Playlists
- Date added
- Last played
- Play count
- Completed plays
- Skip count
- Resume position
- Notes
- Colour label
- Contextual likes
- Recommendation feedback

### Musicians — HELPFUL

Scope: WORK. Candidate sources: Artist submission,MusicXML import,MIDI import,Manual.

- Instrument tuning
- Capo
- Chord chart
- Chord voicings
- Tablature
- Fingering
- Playing techniques
- Effects notes
- Groove
- Swing
- Drum notation
- Fills
- Sticking
- Count in
- Click preferences
- Performance key
- Tessitura
- Harmonies
- Breathing cues
- Pronunciation
- Score
- MIDI
- Inversions
- Harmonic analysis
- Orchestration

### Specialist catalogue — HELPFUL

Scope: WORK. Candidate sources: MusicBrainz,Wikidata,Artist submission,Manual.

- Work hierarchy
- Movement hierarchy
- Opus
- Conductor
- Orchestra
- Soloists
- Movement order
- Jazz personnel
- Take number
- Solo order
- Standard relationship
- Raga
- Maqam
- Tala
- Rhythmic cycle
- Microtonal tuning
- Regional terminology
- Source medium
- Transfer equipment
- Restoration history
- Preservation master
- Permissions
- Territory
- Permission expiry

### Rehearsal and DJ — HELPFUL

Scope: USER. Candidate sources: DJ library import,Artist submission,Manual.

- Assigned parts
- Rehearsal arrangement
- Section notes
- Readiness
- Practice tempo
- Difficult passages
- Setlist position
- Hot cues
- Loops
- Beatgrid corrections
- Transition notes
- Compatible tracks
- Crowd tags
- Clean edit
- Pronunciation notes
- Event suitability
- Permission documents

### Algorithm · timing — ALGORITHM

Scope: ASSET. Candidate sources: Local DSP,Validated model,Manual.

- BPM
- Tempo candidates
- Tempo confidence
- Tempo curve
- Beats
- Downbeats
- Bar boundaries
- Time signature timeline
- Beat unit
- Onsets
- Swing estimate
- Timing irregularity
- Pickup
- Phrase boundaries
- Beatgrid anchors
- Unreliable regions
- Sample rate
- Timeline origin
- Coverage start
- Coverage end

### Algorithm · harmony — ALGORITHM

Scope: ASSET. Candidate sources: Local DSP,Validated model,Manual.

- Key candidates
- Key confidence
- Local key timeline
- Mode
- Scale
- Chroma
- Chord timeline
- Chord confidence
- Bass note timeline
- Tuning cents
- Pitch range
- Tonal balance
- Tonal ambiguity
- Camelot
- Pitch shift assessment

### Algorithm · structure — ALGORITHM

Scope: ASSET. Candidate sources: Local DSP,Validated model,Manual.

- Sections
- Section confidence
- Repeated sections
- Energy curve
- Vocal activity
- Vocal phrase boundaries
- Instrumental regions
- Drum activity
- Bass activity
- Drops
- Fills
- Builds
- Start silence
- End silence
- Fade regions
- Decay tails
- Candidate loops
- Loop seam quality
- Entry points
- Exit points

### Algorithm · quality — ALGORITHM

Scope: ASSET. Candidate sources: Local measurement,Validated model,Manual.

- Integrated LUFS
- Short term LUFS
- Momentary LUFS
- Loudness range
- Sample peak
- True peak
- RMS
- Crest factor
- Band energy
- Spectral centroid
- Spectral flatness
- Stereo width
- Channel balance
- Correlation
- DC offset
- Clipping indicators
- Noise
- Hum
- Dropouts
- Source limitations
- Audio hash
- Analyser
- Analyser version
- Model version
- Settings
- Measured at
- Units
- Confidence
- Verification

### Algorithm · transition — ALGORITHM

Scope: TRANSITION. Candidate sources: Mix engine,Manual preview.

- Source asset IDs
- Source regions
- Exit timestamp
- Entry timestamp
- Overlap duration
- Phrase alignment
- Local tempos
- Target tempo
- Stretch ratios
- Tempo ramps
- Harmonic fit
- Pitch shift
- Tuning correction
- Vocal overlap
- Vocal collisions
- Energy direction
- Bass conflict
- Bass handover
- Regional loudness
- Gain envelopes
- Predicted headroom
- Technique
- Fader curves
- EQ automation
- Stem levels
- Effects
- Beat drift
- Stretch artefacts
- Clipping risk
- Tail truncation
- Preview
- Score breakdown
- User decision
- Engine version
- Source hashes
- Render settings

### Recording session — SESSION

Scope: SESSION. Candidate sources: Recording device,DAW import,Artist submission,Manual.

- Session ID
- Session date
- Song version
- Sample rate
- Recording format
- Tempo map
- Metre map
- Take ID
- Performer
- Instrument
- Start time
- Duration
- Take selection
- Interface
- Input channel
- Microphone or DI
- Preamp
- Input gain
- Pad
- Phantom power
- Mic position
- Mic distance
- Mic angle
- Room notes
- Reference photo
- Clock source
- Timecode
- Start offset
- Latency compensation
- Clock drift
- Peak history
- Clipping events
- Buffer underruns
- Routing
- Buses
- Sends
- Sidechain
- Plugins
- Plugin versions
- Parameters
- Automation
- Bypass
- Plugin latency
- Edit boundaries
- Fades
- Comp selection
- Timing corrections
- Pitch corrections
- Stem kind
- Stem source
- Stem alignment
- Separation model
- Monitoring path
- Cue mix
- Monitoring latency
- Export format
- Export bit depth
- Dither
- Export loudness
- Export true peak
- Tail length
- Export checksum

