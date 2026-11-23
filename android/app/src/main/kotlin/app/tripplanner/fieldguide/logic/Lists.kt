package app.tripplanner.fieldguide.logic

import java.util.Locale

enum class ListKind { CHECKLIST, REFERENCE }

data class ListLine(val key: String, val text: String, val doneInSource: Boolean, val label: String? = null)

data class ListSection(val heading: String?, val lines: List<ListLine>)

data class TripList(
    val blockId: String,
    val emoji: String,
    val title: String,
    val kind: ListKind,
    val intro: String?,
    val sections: List<ListSection>,
) {
    val items: List<ListLine> get() = sections.flatMap { it.lines }
    val topic: ListTopic = topicOf(title)
}

enum class ListTopic { BEFORE_FLY, PACKING, MONEY, SAFETY, FOOD, OTHER }

fun topicOf(title: String): ListTopic {
    val t = title.lowercase(Locale.ROOT)
    return when {
        t.contains("before you fly") || t.contains("book & confirm") || t.contains("to do") -> ListTopic.BEFORE_FLY
        t.contains("packing") || t.contains("pack ") -> ListTopic.PACKING
        t.contains("money") || t.contains("sim") -> ListTopic.MONEY
        t.contains("emergency") || t.contains("safety") || t.contains("insurance") -> ListTopic.SAFETY
        t.contains("food") || t.contains("phrase") || t.contains("etiquette") -> ListTopic.FOOD
        else -> ListTopic.OTHER
    }
}

private val numbered = Regex("""^(\d{1,2})[.)]\s+(.+)$""")
private val labelled = Regex("""^([A-Z][A-Za-z0-9 &/()'’.-]{1,40}?):\s*(.*)$""")
private val doneRe = Regex("""^(done\b|[^:]{1,40}:\s*done\b)""", RegexOption.IGNORE_CASE)
private val bullet = Regex("""^[•·*\-–]\s*""")

/** Stable key for a line, so a tick survives a re-sync as long as the wording stays the same. */
fun lineKey(text: String): String {
    val norm = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(80)
    return norm.hashCode().toUInt().toString(16)
}

/** Splits a leading emoji from a title: "🎒 Packing list" -> ("🎒", "Packing list"). */
fun splitEmoji(title: String): Pair<String, String> {
    val idx = title.indexOfFirst { it.isLetterOrDigit() }
    if (idx <= 0) return "" to title.trim()
    return title.substring(0, idx).trim() to title.substring(idx).trim()
}

/**
 * Lists are stored on the planner as one unscheduled block whose notes are long text.
 * Before-you-fly and packing become checklists; the rest become reference sheets with headed sections.
 */
fun parseList(blockId: String, rawTitle: String, notes: String?): TripList {
    val (emoji, title) = splitEmoji(rawTitle)
    val topic = topicOf(title)
    val lines = notes.orEmpty().lines().map { calmCaps(it.trim()) }.filter { it.isNotEmpty() }
    val numberedCount = lines.count { numbered.matches(it) }
    val kind = if (topic == ListTopic.BEFORE_FLY || topic == ListTopic.PACKING || numberedCount >= 3) ListKind.CHECKLIST else ListKind.REFERENCE

    var intro: String? = null
    val sections = mutableListOf<ListSection>()

    if (kind == ListKind.CHECKLIST && numberedCount >= 3) {
        val items = mutableListOf<ListLine>()
        for (l in lines) {
            val m = numbered.find(l)
            if (m == null) {
                if (items.isEmpty()) intro = l.trimEnd(':') else items += ListLine(lineKey(l), l, doneRe.containsMatchIn(l))
                continue
            }
            val text = m.groupValues[2]
            items += ListLine(lineKey(text), text, doneRe.containsMatchIn(text))
        }
        sections += ListSection(null, items)
    } else if (kind == ListKind.CHECKLIST) {
        // "Clothes: jacket, gloves, beanie" becomes a group of items.
        for (l in lines) {
            val m = labelled.find(l)
            if (m == null) {
                sections += ListSection(null, listOf(ListLine(lineKey(l), l, false)))
                continue
            }
            val heading = m.groupValues[1].trim()
            val parts = splitList(m.groupValues[2])
            sections += ListSection(heading, parts.map { p -> ListLine(lineKey("$heading $p"), p.replaceFirstChar { it.titlecase(Locale.ROOT) }, false) })
        }
    } else {
        var heading: String? = null
        var bucket = mutableListOf<ListLine>()
        fun flush() {
            if (bucket.isNotEmpty() || heading != null) sections += ListSection(heading, bucket)
            bucket = mutableListOf()
        }
        for (l in lines) {
            val isBullet = bullet.containsMatchIn(l)
            val text = l.replace(bullet, "")
            val m = labelled.find(text)
            when {
                isBullet -> {
                    val label = m?.groupValues?.get(1)
                    val body = m?.groupValues?.get(2) ?: text
                    bucket += ListLine(lineKey(text), body, false, label)
                }
                m != null && m.groupValues[2].isEmpty() -> {
                    flush(); heading = m.groupValues[1]
                }
                m != null -> {
                    flush(); heading = m.groupValues[1]
                    bucket += ListLine(lineKey(text), m.groupValues[2], false)
                }
                else -> bucket += ListLine(lineKey(text), text, false)
            }
        }
        flush()
    }
    return TripList(blockId, emoji, title, kind, intro, sections.filter { it.lines.isNotEmpty() })
}

data class Phone(val label: String, val number: String)

private val phoneRe = Regex("""(?<![\w/])(\+?\d[\d ]{1,4}(?:[ -]?\d{2,4}){2,4})(?![\w/])""")

/** Numbers worth a call button, such as an insurer hotline or the emergency number. */
fun phonesIn(text: String): List<Phone> =
    text.lines().flatMap { line ->
        phoneRe.findAll(line).mapNotNull { m ->
            val number = m.value.trim()
            val digits = number.count { it.isDigit() }
            if (digits < 6 && number != "112") return@mapNotNull null
            val label = line.substring(0, m.range.first).replace(bullet, "").trim().trimEnd(':', ',', '(').trim()
            Phone(label.substringAfterLast(". ").ifEmpty { "Call" }, number)
        }.toList()
    }

data class Phrase(val local: String, val meaning: String)

private val phraseRe = Regex("""([\p{L}' /]+?)\s*\(([^)]+)\)""")

/** "Phrases: gamarjoba (hello), madloba (thank you)" -> phrase cards. Other lines are ignored. */
fun phrasesIn(text: String?): List<Phrase> {
    val line = text.orEmpty().lines().firstOrNull { it.trim().startsWith("Phrases", ignoreCase = true) } ?: return emptyList()
    val body = line.substringAfter(':')
    return phraseRe.findAll(body).map { Phrase(it.groupValues[1].trim().trimStart(',').trim(), it.groupValues[2].trim()) }
        .filter { it.local.isNotEmpty() }.toList()
}
