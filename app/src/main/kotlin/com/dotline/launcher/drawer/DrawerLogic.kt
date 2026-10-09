package com.dotline.launcher.drawer

import androidx.compose.runtime.Immutable
import com.dotline.launcher.data.model.AppInfo
import java.text.Normalizer
import java.util.Locale

/** A-Z section of the drawer list: the letter and where it starts in the (filtered) app list. */
@Immutable
data class DrawerSection(val letter: Char, val startIndex: Int)

/** Pure search / grouping logic for the app drawer (no Android types, unit-tested). */
object DrawerLogic {
    const val OTHER = '#'

    /** Lower-case, accent-free form used for matching ("Café" -> "cafe"). */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

    /** Section letter of a label: A-Z, or '#' for digits, symbols and non-Latin scripts. */
    fun sectionLetter(label: String): Char {
        val first = normalize(label).firstOrNull { it.isLetterOrDigit() } ?: return OTHER
        return if (first in 'a'..'z') first.uppercaseChar() else OTHER
    }

    /** Apps the user did not hide. A hidden entry matches the app's flat key or its package name. */
    fun visible(apps: List<AppInfo>, hidden: Set<String>): List<AppInfo> =
        if (hidden.isEmpty()) apps else apps.filter { it.key.flat !in hidden && it.packageName !in hidden }

    /**
     * Search as you type. Ranks: label starts with the query, a word of the label starts with it,
     * the initials of the words match ("ym" finds YouTube Music), the label contains it, the package
     * name contains it. Within a rank the incoming (alphabetical) order is kept. Blank query = all.
     */
    fun search(apps: List<AppInfo>, query: String): List<AppInfo> {
        val q = normalize(query)
        if (q.isEmpty()) return apps
        val ranked = ArrayList<Pair<Int, AppInfo>>()
        for (app in apps) {
            val rank = rank(normalize(app.label), app.packageName.lowercase(Locale.ROOT), q)
            if (rank >= 0) ranked += rank to app
        }
        // sortedBy is stable, so equal ranks keep alphabetical order.
        return ranked.sortedBy { it.first }.map { it.second }
    }

    private fun rank(label: String, pkg: String, q: String): Int {
        if (label.startsWith(q)) return 0
        val words = label.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 1
        if (q.length >= 2 && words.size >= 2 && words.joinToString("") { it.take(1) }.startsWith(q)) return 2
        if (label.contains(q)) return 3
        if (q.length >= 3 && pkg.contains(q)) return 4
        return -1
    }

    /** Sections for an alphabetically sorted list (the scroller jumps to [DrawerSection.startIndex]). */
    fun sections(apps: List<AppInfo>): List<DrawerSection> {
        val out = ArrayList<DrawerSection>()
        var last: Char? = null
        apps.forEachIndexed { index, app ->
            val letter = sectionLetter(app.label)
            if (letter != last) {
                out += DrawerSection(letter, index)
                last = letter
            }
        }
        return out
    }

    /**
     * Index to scroll to for a touched [letter]: that section if it exists, else the next section
     * that does, else the last one (so dragging along the scroller never dead-ends).
     */
    fun indexForLetter(sections: List<DrawerSection>, letter: Char): Int {
        if (sections.isEmpty()) return 0
        sections.firstOrNull { it.letter == letter }?.let { return it.startIndex }
        val next = sections.firstOrNull { it.letter > letter && it.letter != OTHER }
        return (next ?: sections.last()).startIndex
    }

    /** The scroller's letters: A-Z, plus '#' when some app starts with a non-letter. */
    fun scrollerLetters(sections: List<DrawerSection>): List<Char> {
        val letters = ('A'..'Z').toMutableList()
        if (sections.any { it.letter == OTHER }) letters += OTHER
        return letters
    }

    /** Maps a vertical touch position (0 until height) on the scroller to a letter. */
    fun letterAt(y: Float, height: Float, letters: List<Char>): Char {
        if (letters.isEmpty() || height <= 0f) return 'A'
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
        return letters[i]
    }
}
