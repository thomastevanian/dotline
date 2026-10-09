package com.dotline.launcher.data.icons.glyph

/**
 * Music, video and streaming apps. Every pictogram is a generic idea (waves, a clapperboard, a
 * broadcast, headphones, a cone), never a copy of a brand mark.
 */
object GlyphsMedia {
    val entries: List<GlyphEntry> = listOf(
        entry(
            // Three sound waves in a ring.
            glyph("spotify") {
                circle(12f, 12f, 8.8f)
                path { move(7.2f, 9.6f); quad(12f, 7.6f, 16.8f, 9.6f) }
                path { move(8f, 12.8f); quad(12f, 11.2f, 16f, 12.8f) }
                path { move(9f, 15.8f); quad(12f, 14.6f, 15f, 15.8f) }
            },
            "com.spotify.music", "com.spotify.lite",
        ),
        entry(
            // A clapperboard.
            glyph("netflix") {
                rect(3.5f, 10f, 17f, 9.5f, 2.5f)
                poly(3.6f, 9.6f, 4.5f, 5.2f, 20.4f, 7.4f, 19.8f, 9.6f, closed = true)
                line(8.4f, 5.8f, 7.4f, 9.6f)
                line(12.8f, 6.4f, 11.8f, 9.6f)
                line(17.2f, 7f, 16.2f, 9.6f)
            },
            "com.netflix.mediaclient",
        ),
        entry(
            // Play button in a ring.
            glyph("prime_video") {
                circle(12f, 12f, 8.5f)
                poly(10f, 8.6f, 15.4f, 12f, 10f, 15.4f, closed = true)
            },
            "com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom",
        ),
        entry(
            // A four-point sparkle.
            glyph("disney_plus") {
                poly(12f, 3.8f, 14.2f, 9.8f, 20.2f, 12f, 14.2f, 14.2f, 12f, 20.2f, 9.8f, 14.2f, 3.8f, 12f, 9.8f, 9.8f, closed = true)
            },
            "com.disney.disneyplus", "com.disney.disneyplus.sg",
        ),
        entry(
            // A live broadcast: a dot with waves either side.
            glyph("twitch") {
                dot(12f, 12f, 2f)
                arc(12f, 12f, 5.5f, -40f, 80f)
                arc(12f, 12f, 5.5f, 140f, 80f)
                arc(12f, 12f, 9f, -40f, 80f)
                arc(12f, 12f, 9f, 140f, 80f)
            },
            "tv.twitch.android.app", "tv.twitch.android.viewer",
        ),
        entry(
            // A sound waveform.
            glyph("soundcloud") {
                line(5f, 10f, 5f, 14f)
                line(8f, 7f, 8f, 17f)
                line(11f, 4.5f, 11f, 19.5f)
                line(14f, 8f, 14f, 16f)
                line(17f, 6f, 17f, 18f)
                line(20f, 10f, 20f, 14f)
            },
            "com.soundcloud.android",
        ),
        entry(
            // Headphones.
            glyph("podcasts") {
                path { move(4.5f, 14f); cubic(4.5f, 8f, 8f, 4.5f, 12f, 4.5f); cubic(16f, 4.5f, 19.5f, 8f, 19.5f, 14f) }
                rect(3.5f, 13.5f, 4.2f, 6f, 1.8f)
                rect(16.3f, 13.5f, 4.2f, 6f, 1.8f)
            },
            "com.google.android.apps.podcasts", "au.com.shiftyjelly.pocketcasts", "fm.player", "com.apple.android.music",
        ),
        entry(
            // A cone with a stripe.
            glyph("vlc") {
                poly(12f, 4.5f, 19f, 19.5f, 5f, 19.5f, closed = true)
                line(8.7f, 14f, 15.3f, 14f)
            },
            "org.videolan.vlc", "com.mxtech.videoplayer.ad",
        ),
    )
}
