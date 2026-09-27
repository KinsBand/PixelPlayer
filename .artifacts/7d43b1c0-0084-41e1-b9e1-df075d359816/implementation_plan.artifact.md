# Fix Build Error: Could Not Resolve NewPipeExtractor Dependencies

The project is failing to build because the `NewPipeExtractor` library and its transitive dependencies (like `extractor` and `timeago-parser`) are hosted on JitPack, but the repository filtering in `settings.gradle.kts` is too restrictive. It only allows the exact group `com.github.TeamNewPipe`, while the submodules use `com.github.TeamNewPipe.NewPipeExtractor`.

## Proposed Changes

### Build Configuration

#### [MODIFY] [settings.gradle.kts](file:///C:/Users/trai/Documents/GitHub/PixelPlayer-Clean-/settings.gradle.kts)

Update the JitPack repository configuration to allow all groups starting with `com.github.TeamNewPipe` using a regular expression. This will ensure that transitive dependencies of `NewPipeExtractor` can be resolved from JitPack.

```kotlin
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.FaceOnLive")
                includeGroup("com.github.philburk")
                includeGroup("com.github.racra")
                includeGroup("com.github.tdlibx")
                includeGroupByRegex("com\\.github\\.TeamNewPipe.*") // Changed from includeGroup
            }
        }
```

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` (or equivalent Gradle sync/build) to verify that dependencies are now resolved correctly.

### Manual Verification
- Verify in Android Studio that the Gradle sync completes without errors.
