package com.dotline.launcher.data.icons.glyph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Social and messaging apps: chat bubbles, paper plane, ghost, controller, letters drawn as strokes. */
object GlyphsSocial {
    val entries: List<GlyphEntry> = listOf(
        entry(
            // round speech bubble with a pointed corner tail and a small handset inside
            glyph("whatsapp") {
                arc(12f, 12f, 8.3f, 180f, 270f)
                poly(12f, 20.3f, 3.6f, 20.4f, 3.7f, 12f)
                // handset (the system phone outline scaled down, solid)
                path(fill = true) {
                    move(9.88f, 8.84f); line(10.92f, 8.68f); line(11.56f, 10.36f); line(10.84f, 11.0f)
                    cubic(11.24f, 11.96f, 12.04f, 12.76f, 13.0f, 13.16f); line(13.64f, 12.44f)
                    line(15.32f, 13.08f); line(15.16f, 14.12f)
                    cubic(15.08f, 14.84f, 14.52f, 15.4f, 13.8f, 15.32f)
                    cubic(11.0f, 15.08f, 8.92f, 13.0f, 8.68f, 10.2f)
                    cubic(8.6f, 9.48f, 9.16f, 8.92f, 9.88f, 8.84f); close()
                }
            },
            "com.whatsapp", "com.whatsapp.w4b",
        ),
        entry(
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
                circle(9.1f, 17.2f, 3.3f)
                path {
                    move(12.4f, 17.2f); line(12.4f, 4.3f)
                    cubic(12.7f, 7.6f, 15.3f, 9.4f, 18.7f, 9.5f)
                }
            },
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
        ),
        entry(
            // lower-case f
            glyph("facebook") {
                path {
                    move(12.4f, 20.5f); line(12.4f, 9.4f)
                    cubic(12.4f, 6.4f, 13.8f, 4.6f, 17.0f, 4.6f)
                }
                line(8.2f, 11.6f, 16.8f, 11.6f)
            },
            "com.facebook.katana", "com.facebook.lite",
        ),
        entry(
            // round speech bubble with a lightning zigzag
            glyph("messenger") {
                arc(12f, 12f, 8.3f, 150f, 300f)
                poly(12f, 20.3f, 5.6f, 20.9f, 4.8f, 16.1f)
                poly(6.8f, 14.4f, 10.6f, 9.9f, 13.2f, 12.5f, 17.3f, 8.4f)
            },
            "com.facebook.orca", "com.facebook.mlite",
        ),
        entry(
            glyph("x") {
                line(5.2f, 4.8f, 18.8f, 19.2f)
                line(18.8f, 4.8f, 5.2f, 19.2f)
            },
            "com.twitter.android", "com.twitter.android.lite",
        ),
        entry(
            // ghost with scalloped hem and two eyes
            glyph("snapchat") {
                path {
                    move(5.6f, 19.6f); line(5.6f, 10.6f)
                    cubic(5.6f, 6.6f, 8.3f, 4.0f, 12f, 4.0f)
                    cubic(15.7f, 4.0f, 18.4f, 6.6f, 18.4f, 10.6f)
                    line(18.4f, 19.6f)
                    quad(16.6f, 19.6f, 15.4f, 17.6f)
                    quad(14.2f, 19.6f, 12f, 19.6f)
                    quad(9.8f, 19.6f, 8.6f, 17.6f)
                    quad(7.4f, 19.6f, 5.6f, 19.6f)
                    close()
                }
                dot(9.5f, 10.6f, 1.1f); dot(14.5f, 10.6f, 1.1f)
            },
            "com.snapchat.android",
        ),
        entry(
            // game controller
            glyph("discord") {
                path {
                    move(8.2f, 7.4f); line(15.8f, 7.4f)
                    cubic(18.8f, 7.4f, 20.6f, 9.8f, 20.9f, 13.0f)
                    cubic(21.2f, 16.2f, 20.2f, 18.2f, 18.4f, 18.2f)
                    cubic(16.4f, 18.2f, 15.6f, 16.2f, 14.2f, 15.2f)
                    line(9.8f, 15.2f)
                    cubic(8.4f, 16.2f, 7.6f, 18.2f, 5.6f, 18.2f)
                    cubic(3.8f, 18.2f, 2.8f, 16.2f, 3.1f, 13.0f)
                    cubic(3.4f, 9.8f, 5.2f, 7.4f, 8.2f, 7.4f)
                    close()
                }
                line(6.5f, 11.4f, 10.1f, 11.4f); line(8.3f, 9.6f, 8.3f, 13.2f)
                dot(15.4f, 10.4f, 1.0f); dot(17.6f, 12.5f, 1.0f)
            },
            "com.discord",
        ),
        entry(
            // paper plane
            glyph("telegram") {
                poly(20.6f, 3.6f, 14.6f, 20.6f, 10.6f, 13.2f, 3.4f, 9.6f, closed = true)
                line(20.6f, 3.6f, 10.6f, 13.2f)
            },
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta",
            "org.thunderdog.challegram", "org.telegram.plus",
        ),
        entry(
            // ring of dots with a small solid tail
            glyph("signal") {
                for (deg in listOf(0, 30, 60, 90, 180, 210, 240, 270, 300, 330)) {
                    val a = deg * PI / 180.0
                    dot(12f + 8.2f * cos(a).toFloat(), 12f + 8.2f * sin(a).toFloat(), 1.05f)
                }
                poly(3.6f, 20.4f, 4.9f, 16.1f, 8.0f, 19.1f, fill = true)
            },
            "org.thoughtcrime.securesms",
        ),
        entry(
            // "in"
            glyph("linkedin") {
                dot(5.8f, 6.2f, 1.3f)
                line(5.8f, 10.4f, 5.8f, 19f)
                line(11.4f, 10.4f, 11.4f, 19f)
                path {
                    move(11.4f, 14.4f)
                    cubic(11.4f, 12.0f, 13.0f, 10.4f, 15.2f, 10.4f)
                    cubic(17.4f, 10.4f, 18.8f, 12.0f, 18.8f, 14.4f)
                    line(18.8f, 19f)
                }
            },
            "com.linkedin.android",
        ),
        entry(
            // a "p" loop
            glyph("pinterest") {
                circle(13f, 9.6f, 5f)
                line(8.7f, 12.2f, 7.4f, 20.4f)
            },
            "com.pinterest",
        ),
        entry(
            // round alien-like head with antenna and ears
            glyph("reddit") {
                rect(5f, 9.8f, 14f, 10.2f, 5f)
                dot(4.6f, 11.4f, 1.4f); dot(19.4f, 11.4f, 1.4f)
                dot(9.4f, 14.2f, 1.1f); dot(14.6f, 14.2f, 1.1f)
                arc(12f, 14.2f, 3.2f, 35f, 110f)
                path {
                    move(12f, 9.8f); cubic(12f, 7.0f, 13.4f, 5.6f, 16.2f, 5.4f)
                }
                dot(17.6f, 5.2f, 1.3f)
            },
            "com.reddit.frontpage",
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
            // video camera inside a rounded square
            glyph("zoom") {
                rect(3.5f, 3.5f, 17f, 17f, 5f)
                rect(6.6f, 9.3f, 6.8f, 5.6f, 1.6f)
                poly(15.2f, 11.2f, 17.6f, 9.6f, 17.6f, 14.6f, 15.2f, 13.0f, fill = true)
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
