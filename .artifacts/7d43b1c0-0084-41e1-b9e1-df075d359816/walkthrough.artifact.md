# Walkthrough: Resolved NewPipeExtractor Dependency Issue

I have fixed the issue where `NewPipeExtractor` and its submodules could not be resolved during the build.

## Changes Made

### Build Configuration

#### [settings.gradle.kts](file:///C:/Users/trai/Documents/GitHub/PixelPlayer-Clean-/settings.gradle.kts)

Updated the `dependencyResolutionManagement` block to allow all groups starting with `com.github.TeamNewPipe` from JitPack. Previously, it was restricted to only the exact group `com.github.TeamNewPipe`, which prevented submodules like `com.github.TeamNewPipe.NewPipeExtractor` from being fetched.

```diff
-                includeGroup("com.github.TeamNewPipe")
+                includeGroupByRegex("com\\.github\\.TeamNewPipe.*")
```

## Verification Results

### Gradle Sync
- Performed a Gradle sync, which completed successfully. This confirms that all dependencies, including `com.github.TeamNewPipe.NewPipeExtractor:extractor` and `timeago-parser`, are now correctly resolved.
