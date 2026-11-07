# VidGrab 3.1.0

A redesigned Android video/audio downloader. Kotlin · Jetpack Compose · Android 10+

## Build

Install JDK 17 and Android SDK Platform 36 / Build Tools 36.0.0. Open this folder in Android Studio, or create `local.properties` containing `sdk.dir=/your/android/sdk` and run:

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Windows: use `gradlew.bat`. Gradle downloads the declared, pinned Maven dependencies.

The project includes the verified yt-dlp 2026.10.26 zipapp and uses youtubedl-android / FFmpeg 0.18.1. First engine initialization extracts its native runtime on device. Downloads require a connection and depend on the source platform allowing access. DRM and account-only media are not supported by this workflow.

## Signing

The delivered APK uses the existing signing identity found in the original source archive, with versionCode 31 (3.1.0). An existing installation with that identity can upgrade without uninstalling. **Signing keys, keystore.properties and device-specific paths are deliberately excluded from this source ZIP.**

To reproduce an APK with your own release identity, create `keystore.properties` locally:

```properties
storeFile=/absolute/path/to/your-release.keystore
storePassword=YOUR_LOCAL_SECRET
keyAlias=YOUR_ALIAS
keyPassword=YOUR_LOCAL_SECRET
```

Without that file, release builds use the local Android debug identity for development. They cannot update an installation signed with another identity. Keep production signing credentials out of shared source archives. The old repository's ZIP contained a signing key; plan signing-key migration separately before a public production release.

## What changed

- Graphite/mint dark theme and ivory/teal light theme; new folded-V logo.
- Rebuilt Home, Library and Settings, persistent tab state, labelled navigation and nonblocking engine update feedback.
- Paste/clear/keyboard submit, cancellable metadata fetches, quality choices, library search and media filters, retry and cancellation.
- Artwork is saved in internal **files**, not the disposable image cache. Existing history is backfilled from remote art or a saved video frame where available. Missing artwork always has a fallback.
- Job-specific download directories, real cancellation, interrupted-output rejection, durable save/history handoff and safer scoped-storage open/share/delete.
- Best quality selects the best stream; quality choices keep their exact height, audio-only sources stay audio-only, DRM formats are excluded, 4320p is correctly labelled 8K.
- Official engine release downloads are bounded and verified against published SHA-256 checksums before atomic installation; metadata fetch/download can run concurrently while engine replacement waits for exclusive access.

See `docs/REVIEW.md` for findings and verification limits, `docs/VALIDATION.md` for actual build results, and `docs/brand/` for the editable logo and PNG.

## Storage

By default, video files are in `Movies/VidGrab/Video` and audio in `Music/VidGrab/Audio` via MediaStore, without broad storage permission. Existing installations that have already granted All Files Access retain their top-level `VidGrab/Video` and `VidGrab/Audio` behavior. Uninstalling clears app history and local artwork; public downloaded media remains in device storage.

## Source provenance

Based on Saurabh-gzp/VidGrab commit `e859c33`, source archive `App_source_code/VidGrab-v2.2.3-source.zip`. The supplied `VidGrabye` URL was unavailable; `VidGrab` on the same account contained the Android application source.

Download only media you have permission to save. Upstream dependency licenses remain applicable. The original repository describes the project as MIT, but did not include a standalone LICENSE file in the supplied app archive.

## 3.1 update
See docs/CHANGES-3.1.md. Default APK is ARM64 only, Android10+. Build other CPUs with -PtargetAbis=armeabi-v7a or -PtargetAbis=x86_64.
