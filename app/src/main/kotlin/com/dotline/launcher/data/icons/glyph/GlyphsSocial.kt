package com.dotline.launcher.data.icons.glyph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Social and messaging apps: chat bubbles, paper plane, ghost, mascots, letters drawn as strokes. */
object GlyphsSocial {
    val entries: List<GlyphEntry> = listOf(
        entry(
            // round speech bubble with a pointed corner tail and a small handset inside
            glyph("whatsapp") {
                arc(12.3f, 11.7f, 8.3f, 180f, 270f)
                poly(12.3f, 20f, 3.9f, 20.1f, 4f, 11.7f)
                // handset (the system phone outline scaled down, solid)
                path(fill = true) {
                    move(10.18f, 8.54f); line(11.22f, 8.38f); line(11.86f, 10.06f); line(11.14f, 10.7f)
                    cubic(11.54f, 11.66f, 12.34f, 12.46f, 13.3f, 12.86f); line(13.94f, 12.14f)
                    line(15.62f, 12.78f); line(15.46f, 13.82f)
                    cubic(15.38f, 14.54f, 14.82f, 15.1f, 14.1f, 15.02f)
                    cubic(11.3f, 14.78f, 9.22f, 12.7f, 8.98f, 9.9f)
                    cubic(8.9f, 9.18f, 9.46f, 8.62f, 10.18f, 8.54f); close()
                }
            },
            "com.whatsapp", "com.whatsapp.w4b",
        ),
        entry(
            // rounded square, lens, flash dot
            glyph("instagram") {
                rect(3.8f, 3.8f, 16.4f, 16.4f, 5f)
                circle(12f, 12f, 3.9f)
                dot(16.9f, 7.1f, 1.1f)
            },
            "com.instagram.android", "com.instagram.lite",
        ),
        entry(
            // musical note with a curled flag
            glyph("tiktok") {
                circle(9.5f, 16.5f, 3.4f)
                path {
                    move(12.9f, 16.5f); line(12.9f, 3.6f)
                    cubic(13.2f, 6.9f, 15.8f, 8.7f, 19.2f, 8.8f)
                }
            },
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite",
        ),
        entry(
            // lower-case f
            glyph("facebook") {
                path {
                    move(12f, 20.6f); line(12f, 9.2f)
                    cubic(12f, 5.9f, 13.6f, 4f, 17f, 4f)
                }
                line(7.5f, 11.4f, 16.5f, 11.4f)
            },
            "com.facebook.katana", "com.facebook.lite",
        ),
        entry(
            // round speech bubble with a small pointed tail (7 o'clock) and a solid slanted lightning bolt
            glyph("messenger") {
                arc(12f, 11.4f, 8.3f, 150f, 310f)
                poly(10.56f, 19.57f, 6.06f, 20.9f, 4.81f, 15.55f)
                poly(7f, 13.9f, 11.4f, 8f, 13.4f, 10.4f, 17f, 7.4f, 12.6f, 14.8f, 10.6f, 12.4f, fill = true)
            },
            "com.facebook.orca", "com.facebook.mlite",
        ),
        entry(
            // two crossing strokes
            glyph("x") {
                line(4.6f, 4.4f, 19.4f, 19.6f)
                line(19.4f, 4.4f, 4.6f, 19.6f)
            },
            "com.twitter.android", "com.twitter.android.lite",
        ),
        entry(
            // ghost with a scalloped hem and two eyes
            glyph("snapchat") {
                path {
                    move(5f, 19.65f); line(5f, 10.65f)
                    cubic(5f, 6.55f, 8f, 3.95f, 12f, 3.95f)
                    cubic(16f, 3.95f, 19f, 6.55f, 19f, 10.65f)
                    line(19f, 19.65f)
                    quad(17.3f, 19.65f, 15.7f, 17.65f)
                    quad(14.4f, 19.65f, 12f, 19.65f)
                    quad(9.6f, 19.65f, 8.3f, 17.65f)
                    quad(6.7f, 19.65f, 5f, 19.65f)
                    close()
                }
                dot(9.3f, 10.65f, 1.15f); dot(14.7f, 10.65f, 1.15f)
            },
            "com.snapchat.android",
        ),
        entry(
            // little mascot: wide rounded body with two feet and a smiling hem, two tall eyes
            glyph("discord") {
                path {
                    move(6.2f, 5.1f)
                    quad(12f, 3.5f, 17.8f, 5.1f)
                    cubic(19.6f, 7.9f, 20.8f, 11.5f, 20.8f, 16.1f)
                    cubic(20.8f, 18.1f, 19.6f, 19.3f, 18f, 19.3f)
                    cubic(16.6f, 19.3f, 15.8f, 18.1f, 14.8f, 17.5f)
                    quad(12f, 18.5f, 9.2f, 17.5f)
                    cubic(8.2f, 18.1f, 7.4f, 19.3f, 6f, 19.3f)
                    cubic(4.4f, 19.3f, 3.2f, 18.1f, 3.2f, 16.1f)
                    cubic(3.2f, 11.5f, 4.4f, 7.9f, 6.2f, 5.1f)
                    close()
                }
                line(8.8f, 11.1f, 8.8f, 12.9f); line(15.2f, 11.1f, 15.2f, 12.9f)
            },
            "com.discord",
        ),
        entry(
            // paper plane
            glyph("telegram") {
                poly(19.8f, 4.3f, 14.1f, 20.5f, 10.3f, 13.4f, 3.4f, 10.0f, closed = true)
                line(19.8f, 4.3f, 10.3f, 13.4f)
            },
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta",
            "org.thunderdog.challegram", "org.telegram.plus",
        ),
        entry(
            // small speech bubble (squared tail corner at the lower left) inside a ring of dots that is open at the tail
            glyph("signal") {
                for (deg in listOf(0, 30, 60, 90, 180, 210, 240, 270, 300, 330)) {
                    val a = deg * PI / 180.0
                    dot(12f + 8f * cos(a).toFloat(), 12f + 8f * sin(a).toFloat(), 1f)
                }
                arc(12f, 12f, 4.4f, 180f, 270f)
                poly(12f, 16.4f, 7.6f, 16.4f, 7.6f, 12f)
            },
            "org.thoughtcrime.securesms", "im.molly.app",
        ),
        entry(
            // "in"
            glyph("linkedin") {
                dot(5.8f, 5.4f, 1.3f)
                line(5.8f, 9.6f, 5.8f, 18.2f)
                line(11.4f, 9.6f, 11.4f, 18.2f)
                path {
                    move(11.4f, 13.6f)
                    cubic(11.4f, 11.2f, 13f, 9.6f, 15.2f, 9.6f)
                    cubic(17.4f, 9.6f, 18.8f, 11.2f, 18.8f, 13.6f)
                    line(18.8f, 18.2f)
                }
            },
            "com.linkedin.android",
        ),
        entry(
            // a "P": round bowl with the stem running down from its left side
            glyph("pinterest") {
                circle(12.9f, 9.6f, 5.2f)
                line(7.7f, 9.6f, 6.7f, 20.6f)
            },
            "com.pinterest",
        ),
        entry(
            // round alien-like head with antenna and ears
            glyph("reddit") {
                rect(5f, 9f, 14f, 10.8f, 5.2f)
                dot(4.6f, 10.8f, 1.4f); dot(19.4f, 10.8f, 1.4f)
                dot(9.3f, 12.8f, 1.25f); dot(14.7f, 12.8f, 1.25f)
                arc(12f, 14f, 3f, 40f, 100f)
                path {
                    move(12f, 9f); cubic(12f, 6.4f, 13.4f, 5f, 16.2f, 4.8f)
                }
                dot(17.7f, 4.6f, 1.3f)
            },
            "com.reddit.frontpage", "com.andrewshu.android.reddit", "ml.docilealligator.infinityforreddit",
            "free.reddit.news", "com.rubenmayayo.reddit",
        ),
        entry(
            // hash sign
            glyph("slack") {
                line(10.4f, 4.4f, 8.8f, 19.6f)
                line(15.8f, 4.4f, 14.2f, 19.6f)
                line(4.6f, 9.4f, 19.4f, 9.4f)
                line(4.6f, 14.8f, 19.4f, 14.8f)
            },
            "com.Slack",
        ),
        entry(
            // video camera inside a landscape rounded rectangle
            glyph("zoom") {
                rect(3.2f, 5.4f, 17.6f, 13.2f, 4.2f)
                rect(6.6f, 9.2f, 6.6f, 5.6f, 1.5f)
                poly(15.0f, 11.3f, 17.6f, 9.6f, 17.6f, 14.4f, 15.0f, 12.7f, fill = true)
            },
            "us.zoom.videomeetings",
        ),
        entry(
            // @ spiral
            glyph("threads") {
                circle(11.6f, 12f, 3.1f)
                path {
                    move(14.7f, 9.4f); line(14.7f, 13.0f)
                    cubic(14.7f, 16.0f, 20.5f, 16.2f, 20.5f, 12f)
                }
                arc(12f, 12f, 8.5f, 0f, -300f)
            },
            "com.instagram.barcelona",
        ),
    )
}
