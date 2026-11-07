# VidGrab 3.1

- Fix the reported 720p size mismatch: the original size came from H.264 format136 while the downloader picked AV1 format398. Both selected format IDs and sizes now come from the same concrete video/audio pair. The user's actual video selects398+251:48,960,951 input bytes; real merged output49,003,825 bytes.
- Preserve the extraction snapshot for15minutes and load it with --load-info-json when Download is tapped. The normal path performs no second extraction. Explicit retries refresh expired metadata and re-resolve the same quality choice.
- Enable the extraction engine's private cache; the Android wrapper otherwise inserts --no-cache-dir. Repeated Home fetches also reuse recent metadata for5minutes.
- Parse structured progress measured in bytes, aggregate video/audio streams, preserve exact/estimated/unknown totals and show Connecting while no bytes have arrived.
- Label portrait resolution by its shorter dimension and reconcile older readable history using actual local media metadata and byte counts.
- Distinguish network transfer size from transcoded output size. MP3 conversion uses192kbps and an explicit output estimate; unknown converted sizes are not invented.
- Replace the previous UI with the violet media-studio design. See DESIGN.md.
- Keep minSdk29(Android10). The default APK includes ARM64 only, instead of bundling three separate engine runtimes. CPU architecture and Android version are separate constraints. To build a different architecture use -PtargetAbis=armeabi-v7a or -PtargetAbis=x86_64.

See validation-v3.1 for actual test outcomes and environment limitations. Public-platform sample checks do not guarantee every URL or private/account-only content will work.
