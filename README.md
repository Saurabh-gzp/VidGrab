<div align="center">
<img src="Web/icon.png" width="100" alt="VidGrab logo" />

# VidGrab 3.1.0

Android video and audio downloader · Kotlin · Jetpack Compose

[Download APK](https://github.com/Saurabh-gzp/VidGrab/releases/download/v3.1.0/VidGrab-v3.1.0-arm64.apk) · [Source ZIP](https://github.com/Saurabh-gzp/VidGrab/releases/download/v3.1.0/VidGrab-v3.1.0-source.zip) · [Website](https://saurabh-gzp.github.io/VidGrab/)
</div>

## Download

The main APK supports **ARM64 phones running Android 10 or newer**. It is about **62.1 MB**, compared with 158.8 MB for the previous APK containing three CPU runtimes. ARM64 is a CPU architecture; Android 10 remains the minimum OS version.

- [VidGrab-v3.1.0-arm64.apk](https://github.com/Saurabh-gzp/VidGrab/releases/download/v3.1.0/VidGrab-v3.1.0-arm64.apk)
- [Complete source ZIP](https://github.com/Saurabh-gzp/VidGrab/releases/download/v3.1.0/VidGrab-v3.1.0-source.zip)
- [SHA-256 checksums](https://github.com/Saurabh-gzp/VidGrab/releases/download/v3.1.0/SHA256SUMS-v3.1.0.txt)

The APK retains the existing signing identity and uses versionCode 31. Install it over the previous official app. For a different CPU, build the source with the appropriate targetAbis property.

## What's changed

- New violet UI, artwork-led results, quality tiles and custom navigation.
- Quality choices and size estimates now use the same concrete stream IDs.
- Reuse recently fetched metadata to avoid extracting the video again when Download is tapped.
- Enable the private engine cache and parse measured video/audio bytes for progress.
- Show connection progress before bytes arrive; mark estimates and unknown sizes honestly.
- Correct portrait quality labels using the shorter dimension and inspect saved media for history.
- Preserve durable local thumbnails and the queue/storage fixes from version 3.0.

## Source and build

The full Android project is directly browsable in [Android/](Android/). A ready-to-open ZIP is also in [App_source_code/](App_source_code/).

Install JDK 17, Android SDK platform 36 and Build Tools 36.0.0. Open Android/ in Android Studio or run:

```sh
cd Android
./gradlew testDebugUnitTest assembleRelease
```

The default APK includes ARM64 only. Build another CPU with `-PtargetAbis=armeabi-v7a` or `-PtargetAbis=x86_64`. Signing keys/passwords and local SDK paths are excluded. Configure your own local keystore.properties to reproduce your signing identity; see [build/signing instructions](Android/README.md).

## Verification

**73 JVM regression tests passed**, and the release APK built successfully with signature verification. Actual bundled-engine sample downloads passed for YouTube, Instagram, Facebook, X and direct MP3 audio. Some other public samples encountered login or platform restrictions.

For the reported YouTube video, the selected streams totalled 48,960,951 bytes and the real merged file was 49,003,825 bytes. First bytes arrived in 1.306 seconds on the server test; this is not a phone performance guarantee.

Android runtime tests compile, but their execution could not finish because the software emulator boot-looped. Further testing was stopped at the owner's request. Phone UI, storage and lifecycle testing remain pending. See [3.1 changes](Android/docs/CHANGES-3.1.md), [design references](Android/docs/DESIGN.md) and [validation](Android/docs/VALIDATION.md).

## Notes

Availability depends on the source platform. Download only media you have permission to save. VidGrab is independent of the supported platforms. The original repository describes the project as MIT; dependency licenses remain applicable.
