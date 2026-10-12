package com.dotline.launcher.data.icons.glyph

/** Mail, chat, cloud storage and documents from the common office suites. */
object GlyphsOffice {
    val entries: List<GlyphEntry> = listOf(
        entry(
            // An envelope with a round mark.
            glyph("outlook") {
                rect(3.5f, 5.5f, 17f, 13f, 3f)
                circle(12f, 12f, 3.2f)
            },
            "com.microsoft.office.outlook",
        ),
        entry(
            // Two people.
            glyph("teams") {
                circle(9f, 8f, 2.6f)
                path { move(4f, 19f); cubic(4f, 14.5f, 6.2f, 13f, 9f, 13f); cubic(11.8f, 13f, 14f, 14.5f, 14f, 19f) }
                circle(16.8f, 9f, 2.1f)
                path { move(15.6f, 13.4f); cubic(18.6f, 13f, 20.5f, 14.2f, 20.5f, 17.5f) }
            },
            "com.microsoft.teams",
        ),
        entry(
            // A cloud.
            glyph("onedrive") {
                path {
                    move(7.5f, 18.5f); line(17.5f, 18.5f)
                    cubic(20f, 18.5f, 21.5f, 16.8f, 21.5f, 14.8f)
                    cubic(21.5f, 12.6f, 19.8f, 11f, 17.8f, 11f)
                    cubic(17.2f, 7.8f, 14.8f, 5.5f, 12f, 5.5f)
                    cubic(9.3f, 5.5f, 7.1f, 7.5f, 6.6f, 10.2f)
                    cubic(4.4f, 10.5f, 2.5f, 12.3f, 2.5f, 14.7f)
                    cubic(2.5f, 16.9f, 4.3f, 18.5f, 6.5f, 18.5f)
                    close()
                }
            },
            "com.microsoft.skydrive",
        ),
        entry(
            // A page with a folded corner and text lines.
            glyph("word") {
                poly(6f, 3.5f, 14f, 3.5f, 19f, 8.5f, 19f, 20.5f, 6f, 20.5f, closed = true)
                poly(14f, 3.5f, 14f, 8.5f, 19f, 8.5f)
                line(9f, 13f, 16f, 13f)
                line(9f, 16.5f, 16f, 16.5f)
            },
            "com.microsoft.office.word", "com.microsoft.office.officehubrow",
        ),
    )
}
