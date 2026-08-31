<div align="center">

<img src="Web/icon.png" width="110" alt="VidGrab logo" />

# VidGrab

**Download any video. Any audio. Anywhere.**

VidGrab is a free, fast, ad-free Android app that turns any link into a download —
videos or audio, at the best available quality (up to 8K) from 10+ platforms.

![Platform](https://img.shields.io/badge/Platform-Android-3ddc84?logo=android&logoColor=white)
![Version](https://img.shields.io/badge/Version-2.2.2-a855f7)
![Min SDK](https://img.shields.io/badge/Min%20SDK-29%20(Android%2010)-6d5efc)
![Build](https://img.shields.io/badge/Stack-Kotlin%20%2B%20Compose-ec4899)
![License](https://img.shields.io/badge/License-MIT-34d399)

[⬇️ Download APK](#-download) · [🌐 Visit Website](https://saurabh-gzp.github.io/VidGrab/) · [📖 View Source](#-source-code)

</div>

---

## ✨ Features

| Feature | Detail |
| --- | --- |
| 🎯 **10+ platforms** | YouTube, Instagram, Facebook, X, TikTok, Pinterest, Reddit, Dailymotion, Vimeo, Twitch |
| 📺 **Up to 8K** | Full quality ladder with honest size estimates (video + audio sum) |
| 🎵 **Video & Audio** | Sorted automatically into `VidGrab/Video` and `VidGrab/Audio` |
| ⏬ **Background downloads** | Foreground service + live progress notifications |
| 📤 **Share-to-app** | Share any link straight into VidGrab, download starts instantly |
| 🔄 **Smart self-updates** | Keeps the download engine fresh for the best quality |
| 🚫 **No ads, no signup** | 100% free, no trackers, no nonsense |

## ⬇️ Download

Everything is hosted on [GitHub Releases](https://github.com/Saurabh-gzp/VidGrab/releases).

| File | Link | Size | MD5 |
| --- | --- | --- | --- |
| **VidGrab APK** (`VidGrab-v2.2.2-universal.apk`) | [Download](https://github.com/Saurabh-gzp/VidGrab/releases/latest/download/VidGrab-v2.2.2-universal.apk) | 151 MB | `5c5e55190f33a18b56b546f149597bc4` |
| **Source ZIP** (`VidGrab-v2.2.2-source.zip`) | [Download](https://github.com/Saurabh-gzp/VidGrab/releases/latest/download/VidGrab-v2.2.2-source.zip) | 3 MB | `b93c2656b9321a3720044d4b912c7246` |

> **Note:** GitHub's per-file limit is 100 MB, so the 151 MB APK lives on **Releases**
> (up to 2 GB per file) instead of the git tree. The button above downloads it directly.

### 🆕 What's new in v2.2.2

- **Notification fixes** — Cancel button on the download notification, and it now
  disappears automatically when a download finishes (plus clean "completed / failed" alerts).
- **Audio download fix** — on sites without separate audio streams, the M4A/MP3 options
  now extract only the audio track instead of pulling the whole video file.
- **New launcher icon** — fresh logo across all launchers.

**Requirements:** Android 10+ · arm64-v8a / armeabi-v7a / x86_64 (universal APK)

### 🛠️ Install

1. Tap **Download APK** above.
2. Open the file and allow *"Install unknown apps"* for your browser / file manager.
3. Install, open, and paste any link — or share a link into VidGrab.

## 📂 Repository layout

```
VidGrab/
├── VidGrab-v2.2.2-universal.apk   ← app (direct download via Releases)
├── App_source_code/               ← full app source (ZIP)
│   └── VidGrab-v2.2.2-source.zip
├── Web/                           ← official website (GitHub Pages)
│   ├── index.html                 ← complete site — single file, no build step
│   └── icon.png
└── .github/workflows/pages.yml    ← deploys Web/ to GitHub Pages
```

## 📖 Source code

Open the ZIP in **Android Studio** (Ladybug or newer) and hit Run — no extra setup.

- **Language / UI:** Kotlin · Jetpack Compose · Material 3
- **Download engine:** bundled yt-dlp (`app/src/main/assets/ytdlp_bundle`)
- **Key modules:**
  - `util/Fetcher.kt` — extraction wrapper with client fallback chain
  - `util/UrlUtils.kt` — URL detection & platform mapping
  - `util/MediaSaver.kt` — saves into `VidGrab/{Video,Audio}`
  - `core/DownloadRepository.kt` + `service/DownloadService.kt` — background downloads
  - `core/UpdateManager.kt` + `service/UpdateService.kt` — engine self-updates
  - `ui/` — Compose screens (Home, Downloads, Settings)

## 🌐 Website

The official site lives in [`Web/`](Web/index.html) — a single, dependency-free HTML file
(no build step) deployed automatically to
**[saurabh-gzp.github.io/VidGrab](https://saurabh-gzp.github.io/VidGrab/)** via GitHub Actions.

## ⚖️ License & disclaimer

MIT © Saurabh. VidGrab is an independent project, not affiliated with YouTube, Instagram,
TikTok, or any other platform. Please respect platform terms of service and copyright —
download only content you have the right to keep.
