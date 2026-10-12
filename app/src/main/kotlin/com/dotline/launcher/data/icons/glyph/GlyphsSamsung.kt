package com.dotline.launcher.data.icons.glyph

/** Samsung's own apps: internet, notes, health, smart home, store and wallet. */
object GlyphsSamsung {
    val entries: List<GlyphEntry> = listOf(
        entry(
            // A globe.
            glyph("samsung_internet") {
                circle(12f, 12f, 8.5f)
                line(3.5f, 12f, 20.5f, 12f)
                path { move(12f, 3.5f); cubic(8.8f, 6f, 8.8f, 18f, 12f, 20.5f) }
                path { move(12f, 3.5f); cubic(15.2f, 6f, 15.2f, 18f, 12f, 20.5f) }
            },
            "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        ),
        entry(
            // A notepad.
            glyph("samsung_notes") {
                rect(5f, 3.5f, 14f, 17f, 2.5f)
                line(8.5f, 9f, 15.5f, 9f)
                line(8.5f, 12.5f, 15.5f, 12.5f)
                line(8.5f, 16f, 12.5f, 16f)
            },
            "com.samsung.android.app.notes", "com.samsung.android.app.reminder",
        ),
        entry(
            // A heart.
            glyph("samsung_health") {
                path {
                    move(12f, 19.5f)
                    cubic(5f, 14.5f, 3.5f, 10.5f, 3.5f, 8.5f)
                    cubic(3.5f, 6f, 5.4f, 4.5f, 7.5f, 4.5f)
                    cubic(9.4f, 4.5f, 11f, 5.5f, 12f, 7.2f)
                    cubic(13f, 5.5f, 14.6f, 4.5f, 16.5f, 4.5f)
                    cubic(18.6f, 4.5f, 20.5f, 6f, 20.5f, 8.5f)
                    cubic(20.5f, 10.5f, 19f, 14.5f, 12f, 19.5f)
                    close()
                }
            },
            "com.sec.android.app.shealth", "com.google.android.apps.fitness",
        ),
        entry(
            // A hub with four connected nodes.
            glyph("smartthings") {
                circle(12f, 12f, 2.6f)
                dot(5.5f, 6.5f, 1.8f)
                dot(18.5f, 6.5f, 1.8f)
                dot(5.5f, 17.5f, 1.8f)
                dot(18.5f, 17.5f, 1.8f)
                line(10.2f, 10.2f, 6.8f, 7.8f)
                line(13.8f, 10.2f, 17.2f, 7.8f)
                line(10.2f, 13.8f, 6.8f, 16.2f)
                line(13.8f, 13.8f, 17.2f, 16.2f)
            },
            "com.samsung.android.oneconnect",
        ),
        entry(
            // A shopping bag.
            glyph("galaxy_store") {
                rect(4.5f, 8f, 15f, 12f, 3f)
                arc(12f, 8f, 3.5f, 180f, 180f)
            },
            "com.sec.android.app.samsungapps",
        ),
        entry(
            // A wallet with a clasp.
            glyph("samsung_wallet") {
                rect(3.5f, 6f, 17f, 13f, 3f)
                line(3.5f, 10f, 20.5f, 10f)
                dot(16.5f, 14.5f, 1.4f)
            },
            "com.samsung.android.spay", "com.samsung.android.samsungpass",
        ),
    )
}
