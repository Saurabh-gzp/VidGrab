package com.vidgrab.app.util

import java.util.Locale

object UrlUtils {

    val PLATFORMS = listOf(
        "YouTube", "Instagram", "Facebook", "X", "TikTok",
        "Pinterest", "Reddit", "Dailymotion", "Vimeo", "Twitch"
    )

    /** Pehli si URL nikalo (text share se aayi ho to bhi) */
    fun extractUrl(text: String): String? {
        val rx = Regex("""https?://\S+""")
        return rx.find(text)?.value?.trim('\"', '\'', ')', ']', '>', ',')
    }

    fun normalize(raw: String): String {
        val text = extractUrl(raw.trim()) ?: raw.trim()
        if (text.isEmpty()) return text
        val url = (if (text.contains("://")) text else "https://$text").substringBefore('#')
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        require(uri != null && uri.scheme.lowercase(Locale.ROOT) in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null) { "Enter a valid http or https video link" }
        return url.substringBefore('#')
    }

    fun platformOf(url: String): String {
        val host = runCatching { java.net.URI(if (url.contains("://")) url else "https://$url").host }
            .getOrNull()?.lowercase(Locale.ROOT) ?: return "Web"
        fun isHost(domain: String) = host == domain || host.endsWith(".$domain")
        return when {
            isHost("youtu.be") || isHost("youtube.com") -> "YouTube"
            isHost("instagram.com") -> "Instagram"
            isHost("facebook.com") || isHost("fb.watch") -> "Facebook"
            isHost("twitter.com") || isHost("x.com") -> "X"
            isHost("tiktok.com") -> "TikTok"
            isHost("pinterest.com") || isHost("pin.it") -> "Pinterest"
            isHost("reddit.com") || isHost("redd.it") -> "Reddit"
            isHost("dailymotion.com") || isHost("dai.ly") -> "Dailymotion"
            isHost("vimeo.com") -> "Vimeo"
            isHost("twitch.tv") -> "Twitch"
            else -> "Web"
        }
    }

    fun isSupported(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.host != "vidgrab.local" && uri.userInfo == null
    }.getOrDefault(false)

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "—"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bps: Long): String {
        if (bps <= 0) return ""
        return formatBytes(bps) + "/s"
    }

    fun formatDuration(sec: Long?): String {
        if (sec == null || sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatEta(sec: Long): String {
        if (sec < 0) return ""
        return if (sec < 60) "~${sec}s left"
        else "~${sec / 60}m ${sec % 60}s left"
    }

    fun qualityLabel(height: Int): String = when {
        height >= 4320 -> "8K"
        height >= 2880 -> "5K"
        height >= 2160 -> "4K"
        height >= 1440 -> "2K"
        height >= 1080 -> "Full HD"
        height >= 720 -> "HD"
        else -> "${height}p"
    }

    /**
     * FILENAME ke liye compact quality tag — user ki request:
     * "VideoName (1080p).mp4" style. Audio: M4A / MP3.
     */
    fun qualityTag(height: Int?, isAudioOnly: Boolean, formatLabel: String): String {
        if (isAudioOnly) {
            val l = formatLabel.uppercase(Locale.US)
            return when {
                "MP3" in l -> "MP3"
                "M4A" in l -> "M4A"
                "OPUS" in l -> "OPUS"
                else -> "Audio"
            }
        }
        if (height == null) return "Best"
        return when {
            height >= 4320 -> "8K"
            height >= 2880 -> "5K"
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            else -> "${height}p"
        }
    }

    /** Filename-safe sanitization (MediaStore DISPLAY_NAME ke liye) */
    fun sanitizeFileName(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""_{2,}"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(80)
            .ifBlank { "Video" }

    /**
     * Cryptic yt-dlp errors ko insaani bhasha mein — download side bhi
     * wahi friendly message dikhaye jo fetch side dikhata hai.
     */
    fun friendlyEngineError(raw: String): String {
        val r = raw.lowercase(Locale.US)
        return when {
            "sign in to confirm" in r || "not a bot" in r ->
                "YouTube ne abhi request block ki hai — thodi der baad try karo, ya video ko Share > VidGrab se bhejo."
            "nsig extraction failed" in r || "nsig extraction" in r ->
                "Engine update chahiye — Settings > Check for update chalao, phir retry karo."
            "http error 403" in r || "precondition check failed" in r ->
                "Download link expire ho gaya — dobara Fetch karke try karo."
            "requested format is not available" in r ->
                "Ye quality is video mein available nahi — doosri quality chuno."
            "does not exist" in r || "video unavailable" in r || "has been removed" in r ->
                "Ye video exist nahi karta ya hata diya gaya hai."
            "private video" in r ->
                "Ye video private hai."
            "unsupported url" in r ->
                "Ye link abhi supported nahi hai."
            "failed to resolve" in r || "unable to resolve host" in r || "no address associated" in r ->
                "Internet connection check karo."
            "no such file or directory" in r && "python" in r ->
                "Engine setup adhoora hai — app ko dobara kholo."
            else -> raw.trim().takeLast(160).ifBlank { "Download fail ho gaya — retry karo" }
        }
    }
}
