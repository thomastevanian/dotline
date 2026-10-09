package com.dotline.launcher.data.icons.glyph

/** Google apps: Chrome, Gmail, Search, Maps, Play Store, YouTube, Photos, Drive, Docs, Meet, Keep, Translate, Wallet, Home. */
object GlyphsGoogle {
    val entries: List<GlyphEntry> = listOf(
        // Browser: ring, centre lens and three spokes.
        entry(
            glyph("chrome") {
                circle(12f, 12f, 8.5f)
                circle(12f, 12f, 3.4f)
                line(12f, 8.6f, 12f, 3.5f)
                line(14.94f, 13.7f, 19.36f, 16.25f)
                line(9.06f, 13.7f, 4.64f, 16.25f)
            },
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
        ),
        // Envelope with a V fold.
        entry(
            glyph("gmail") {
                rect(3.5f, 5.5f, 17f, 13f, 2.5f)
                poly(4.5f, 7.5f, 12f, 13.5f, 19.5f, 7.5f)
            },
            "com.google.android.gm", "com.google.android.gm.lite",
        ),
        // A plain G: open ring with a bar.
        entry(
            glyph("google_search") {
                arc(12f, 12f, 8.2f, -45f, -315f)
                line(20.2f, 12f, 12.5f, 12f)
            },
            "com.google.android.googlequicksearchbox", "com.google.android.apps.searchlite",
        ),
        // Map pin.
        entry(
            glyph("google_maps") {
                path {
                    move(12f, 20.5f)
                    cubic(8.2f, 17.2f, 5f, 13.8f, 5f, 9.6f)
                    cubic(5f, 6f, 8f, 3.5f, 12f, 3.5f)
                    cubic(16f, 3.5f, 19f, 6f, 19f, 9.6f)
                    cubic(19f, 13.8f, 15.8f, 17.2f, 12f, 20.5f)
                    close()
                }
                circle(12f, 9.6f, 2.4f)
            },
            "com.google.android.apps.maps", "com.google.android.apps.mapslite",
        ),
        // Shopping bag with a play triangle.
        entry(
            glyph("play_store") {
                rect(4.5f, 7.5f, 15f, 13f, 2.5f)
                arc(12f, 8f, 3.4f, 180f, 180f)
                poly(10.4f, 11.8f, 15f, 14.5f, 10.4f, 17.2f, closed = true, fill = true)
                poly(10.4f, 11.8f, 15f, 14.5f, 10.4f, 17.2f, closed = true)
            },
            "com.android.vending",
        ),
        // Video pill with a play triangle.
        entry(
            glyph("youtube") {
                rect(3f, 5.5f, 18f, 13f, 4.5f)
                poly(10.2f, 9.6f, 15f, 12f, 10.2f, 14.4f, closed = true, fill = true)
                poly(10.2f, 9.6f, 15f, 12f, 10.2f, 14.4f, closed = true)
            },
            "com.google.android.youtube", "com.google.android.apps.youtube.mango",
        ),
        // Music: circle with a play triangle.
        entry(
            glyph("youtube_music") {
                circle(12f, 12f, 8.5f)
                poly(10.3f, 8.9f, 15.2f, 12f, 10.3f, 15.1f, closed = true, fill = true)
                poly(10.3f, 8.9f, 15.2f, 12f, 10.3f, 15.1f, closed = true)
            },
            "com.google.android.apps.youtube.music",
        ),
        // Pinwheel of four petals.
        entry(
            glyph("google_photos") {
                path { move(12f, 11f); line(12f, 3.7f); cubic(17.94f, 3.7f, 18.6f, 10.5f, 13.65f, 11f); close() }
                path { move(13f, 12f); line(20.3f, 12f); cubic(20.3f, 17.94f, 13.5f, 18.6f, 13f, 13.65f); close() }
                path { move(12f, 13f); line(12f, 20.3f); cubic(6.06f, 20.3f, 5.4f, 13.5f, 10.35f, 13f); close() }
                path { move(11f, 12f); line(3.7f, 12f); cubic(3.7f, 6.06f, 10.5f, 5.4f, 11f, 10.35f); close() }
            },
            "com.google.android.apps.photos", "com.google.android.apps.photosgo",
        ),
        // Drive: truncated triangle with a base bar.
        entry(
            glyph("google_drive") {
                poly(8.8f, 4.5f, 15.2f, 4.5f, 20.8f, 14.5f, 17.5f, 19.8f, 6.5f, 19.8f, 3.2f, 14.5f, closed = true)
                line(3.2f, 14.5f, 20.8f, 14.5f)
            },
            "com.google.android.apps.docs",
        ),
        // Page with a folded corner and text lines.
        entry(
            glyph("google_docs") {
                path {
                    move(7.5f, 3.5f); line(13.5f, 3.5f); line(18.5f, 8.5f); line(18.5f, 18.5f)
                    quad(18.5f, 20.5f, 16.5f, 20.5f); line(7.5f, 20.5f)
                    quad(5.5f, 20.5f, 5.5f, 18.5f); line(5.5f, 5.5f)
                    quad(5.5f, 3.5f, 7.5f, 3.5f); close()
                }
                poly(13.5f, 3.5f, 13.5f, 8.5f, 18.5f, 8.5f)
                line(8.8f, 13f, 15.2f, 13f)
                line(8.8f, 16.5f, 12.6f, 16.5f)
            },
            "com.google.android.apps.docs.editors.docs",
        ),
        // Page with a table grid.
        entry(
            glyph("google_sheets") {
                path {
                    move(7.5f, 3.5f); line(13.5f, 3.5f); line(18.5f, 8.5f); line(18.5f, 18.5f)
                    quad(18.5f, 20.5f, 16.5f, 20.5f); line(7.5f, 20.5f)
                    quad(5.5f, 20.5f, 5.5f, 18.5f); line(5.5f, 5.5f)
                    quad(5.5f, 3.5f, 7.5f, 3.5f); close()
                }
                poly(13.5f, 3.5f, 13.5f, 8.5f, 18.5f, 8.5f)
                rect(8.8f, 11.6f, 6.4f, 5.8f, 0.5f)
                line(12f, 11.6f, 12f, 17.4f)
                line(8.8f, 14.5f, 15.2f, 14.5f)
            },
            "com.google.android.apps.docs.editors.sheets",
        ),
        // Page with a presentation frame.
        entry(
            glyph("google_slides") {
                path {
                    move(7.5f, 3.5f); line(13.5f, 3.5f); line(18.5f, 8.5f); line(18.5f, 18.5f)
                    quad(18.5f, 20.5f, 16.5f, 20.5f); line(7.5f, 20.5f)
                    quad(5.5f, 20.5f, 5.5f, 18.5f); line(5.5f, 5.5f)
                    quad(5.5f, 3.5f, 7.5f, 3.5f); close()
                }
                poly(13.5f, 3.5f, 13.5f, 8.5f, 18.5f, 8.5f)
                rect(8.6f, 12f, 6.8f, 4.8f, 0.8f)
            },
            "com.google.android.apps.docs.editors.slides",
        ),
        // Video camera.
        entry(
            glyph("google_meet") {
                rect(3.5f, 6.5f, 12.5f, 11f, 2.8f)
                poly(16f, 10.6f, 20.5f, 7.8f, 20.5f, 16.2f, 16f, 13.4f)
            },
            "com.google.android.apps.tachyon", "com.google.android.apps.meetings",
        ),
        // Light bulb.
        entry(
            glyph("google_keep") {
                arc(12f, 9.5f, 6f, 125f, 290f)
                poly(8.56f, 14.41f, 9.8f, 17.4f, 14.2f, 17.4f, 15.44f, 14.41f)
                line(10.4f, 20.5f, 13.6f, 20.5f)
            },
            "com.google.android.keep",
        ),
        // An A and a simple stroke character.
        entry(
            glyph("google_translate") {
                poly(3.5f, 11.5f, 7.5f, 3.5f, 11.5f, 11.5f)
                line(5.2f, 8.6f, 9.8f, 8.6f)
                line(16.25f, 11.5f, 16.25f, 13.8f)
                line(12.5f, 13.8f, 20f, 13.8f)
                line(14.2f, 13.8f, 18.6f, 20.5f)
                line(18.6f, 13.8f, 13.8f, 20.5f)
            },
            "com.google.android.apps.translate",
        ),
        // Wallet with a card peeking out.
        entry(
            glyph("google_wallet") {
                poly(7.5f, 7.5f, 7.5f, 4f, 16.5f, 4f, 16.5f, 7.5f)
                rect(3.5f, 7.5f, 17f, 13f, 3f)
                poly(20.5f, 11.4f, 16.2f, 11.4f, 16.2f, 16.6f, 20.5f, 16.6f)
            },
            "com.google.android.apps.walletnfcrel", "com.google.android.apps.nbu.paisa.user",
        ),
        // House with a dot.
        entry(
            glyph("google_home") {
                poly(12f, 3.8f, 20.2f, 11f, 20.2f, 20f, 3.8f, 20f, 3.8f, 11f, closed = true)
                dot(12f, 14.8f, 1.4f)
            },
            "com.google.android.apps.chromecast.app",
        ),
    )
}
