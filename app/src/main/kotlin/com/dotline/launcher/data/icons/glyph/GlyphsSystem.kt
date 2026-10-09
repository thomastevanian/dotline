package com.dotline.launcher.data.icons.glyph

/** Core phone apps: phone, messages, camera, gallery, clock, calendar, calculator, settings, files, contacts, recorder, weather. */
object GlyphsSystem {
    val entries: List<GlyphEntry> = listOf(
        entry(
            glyph("phone") {
                path {
                    move(7.2f, 3.6f); line(9.8f, 3.2f); line(11.4f, 7.4f); line(9.6f, 9.0f)
                    cubic(10.6f, 11.4f, 12.6f, 13.4f, 15.0f, 14.4f); line(16.6f, 12.6f)
                    line(20.8f, 14.2f); line(20.4f, 16.8f)
                    cubic(20.2f, 18.6f, 18.8f, 20.0f, 17.0f, 19.8f)
                    cubic(10.0f, 19.2f, 4.8f, 14.0f, 4.2f, 7.0f)
                    cubic(4.0f, 5.2f, 5.4f, 3.8f, 7.2f, 3.6f); close()
                }
            },
            "com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer", "com.android.contacts.dialer",
        ),
        entry(
            glyph("messages") {
                path {
                    move(6.0f, 4.5f); line(18.0f, 4.5f)
                    cubic(19.4f, 4.5f, 20.5f, 5.6f, 20.5f, 7.0f); line(20.5f, 14.0f)
                    cubic(20.5f, 15.4f, 19.4f, 16.5f, 18.0f, 16.5f); line(11.0f, 16.5f)
                    line(7.0f, 20.0f); line(7.0f, 16.5f); line(6.0f, 16.5f)
                    cubic(4.6f, 16.5f, 3.5f, 15.4f, 3.5f, 14.0f); line(3.5f, 7.0f)
                    cubic(3.5f, 5.6f, 4.6f, 4.5f, 6.0f, 4.5f); close()
                }
                dot(8.5f, 10.5f); dot(12f, 10.5f); dot(15.5f, 10.5f)
            },
            "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms", "com.android.messaging",
        ),
        entry(
            glyph("camera") {
                rect(3.5f, 7f, 17f, 12f, 3f)
                poly(8.3f, 7f, 9.6f, 4.7f, 14.4f, 4.7f, 15.7f, 7f)
                circle(12f, 13f, 3.4f)
            },
            "com.sec.android.app.camera", "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera",
        ),
        entry(
            glyph("gallery") {
                rect(3.5f, 4.5f, 17f, 15f, 3f)
                circle(8.6f, 9.6f, 1.6f)
                poly(3.5f, 16.5f, 9f, 12.5f, 13.5f, 16.5f, 15.5f, 14.8f, 20.2f, 17.8f)
            },
            "com.sec.android.gallery3d", "com.android.gallery3d",
        ),
        entry(
            glyph("clock") {
                circle(12f, 12f, 8.5f)
                poly(12f, 7.2f, 12f, 12f, 15.4f, 14f)
            },
            "com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock",
        ),
        entry(
            glyph("calendar") {
                rect(3.5f, 5f, 17f, 15.5f, 3f)
                line(3.5f, 10f, 20.5f, 10f)
                line(8f, 3.2f, 8f, 6.8f)
                line(16f, 3.2f, 16f, 6.8f)
                dot(8f, 14f); dot(12f, 14f); dot(16f, 14f)
                dot(8f, 17.2f); dot(12f, 17.2f)
            },
            "com.samsung.android.calendar", "com.google.android.calendar", "com.android.calendar",
        ),
        entry(
            glyph("calculator") {
                rect(5f, 3.5f, 14f, 17f, 3f)
                line(8.5f, 8f, 15.5f, 8f)
                dot(9f, 12.5f); dot(12f, 12.5f); dot(15f, 12.5f)
                dot(9f, 16.5f); dot(12f, 16.5f); dot(15f, 16.5f)
            },
            "com.sec.android.app.popupcalculator", "com.google.android.calculator", "com.android.calculator2",
        ),
        entry(
            glyph("settings") {
                poly(
                    10.4f, 5.7f, 10.8f, 3.5f, 13.2f, 3.5f, 13.6f, 5.7f, 15.3f, 6.4f, 17.2f, 5.1f, 18.9f, 6.8f, 17.6f, 8.7f,
                    18.3f, 10.4f, 20.5f, 10.8f, 20.5f, 13.2f, 18.3f, 13.6f, 17.6f, 15.3f, 18.9f, 17.2f, 17.2f, 18.9f, 15.3f, 17.6f,
                    13.6f, 18.3f, 13.2f, 20.5f, 10.8f, 20.5f, 10.4f, 18.3f, 8.7f, 17.6f, 6.8f, 18.9f, 5.1f, 17.2f, 6.4f, 15.3f,
                    5.7f, 13.6f, 3.5f, 13.2f, 3.5f, 10.8f, 5.7f, 10.4f, 6.4f, 8.7f, 5.1f, 6.8f, 6.8f, 5.1f, 8.7f, 6.4f,
                    closed = true,
                )
                circle(12f, 12f, 2.8f)
            },
            "com.android.settings",
        ),
        entry(
            glyph("files") {
                path {
                    move(5.5f, 5f); line(9.2f, 5f); line(11.6f, 7.6f); line(18.5f, 7.6f)
                    quad(20.5f, 7.6f, 20.5f, 9.6f); line(20.5f, 17.5f)
                    quad(20.5f, 19.5f, 18.5f, 19.5f); line(5.5f, 19.5f)
                    quad(3.5f, 19.5f, 3.5f, 17.5f); line(3.5f, 7f)
                    quad(3.5f, 5f, 5.5f, 5f); close()
                }
            },
            "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files", "com.android.documentsui", "com.google.android.documentsui",
        ),
        entry(
            glyph("contacts") {
                circle(12f, 7.2f, 3.4f)
                arc(12f, 20.5f, 7.5f, 180f, 180f)
            },
            "com.samsung.android.app.contacts", "com.google.android.contacts", "com.android.contacts",
        ),
        entry(
            glyph("recorder") {
                rect(9f, 3.5f, 6f, 11f, 3f)
                arc(12f, 11f, 7f, 0f, 180f)
                line(12f, 18f, 12f, 20.5f)
                line(8.5f, 20.5f, 15.5f, 20.5f)
            },
            "com.sec.android.app.voicenote", "com.google.android.apps.recorder",
        ),
        entry(
            glyph("weather") {
                // cloud: flat base + three arcs
                line(7.6f, 19.5f, 17.8f, 19.5f)
                arc(7.6f, 16.6f, 2.9f, 90f, 194f)
                arc(13f, 13.6f, 4.7f, 177.7f, 173.8f)
                arc(17.8f, 16.2f, 3.3f, 267.4f, 182.6f)
                // sun peeking out behind it
                arc(8.6f, 8.6f, 2.6f, 135f, 185f)
                line(4.2f, 8.6f, 3.2f, 8.6f)
                line(5.5f, 5.5f, 4.8f, 4.8f)
                line(8.6f, 4.2f, 8.6f, 3.2f)
                line(11.7f, 5.5f, 12.4f, 4.8f)
            },
            "com.sec.android.daemonapp", "com.google.android.apps.weather",
        ),
    )
}
