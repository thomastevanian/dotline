package com.dotline.launcher.data.icons.glyph

/** Core phone apps: phone, messages, camera, gallery, clock, calendar, calculator, settings, files, contacts. */
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
    )
}
