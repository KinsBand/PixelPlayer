package com.theveloper.pixelplay.utils

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

internal object TtmlLyricsParser {
    private const val MAX_TTML_PARAGRAPHS = 5_000

    fun parseToEnhancedLrc(ttmlText: String): String? {
        return runCatching {
            val normalizedTtml = normalizeTtmlDocument(ttmlText)
            if (normalizedTtml.isBlank()) {
                return null
            }

            val document = parseDocument(normalizedTtml)
            val paragraphNodes = document.getElementsByTagNameNS("*", "p")
            if (paragraphNodes.length == 0 || paragraphNodes.length > MAX_TTML_PARAGRAPHS) {
                return null
            }

            buildList<Pair<Int, String>> {
                for (index in 0 until paragraphNodes.length) {
                    val paragraph = paragraphNodes.item(index) as? Element ?: continue
                    val beginMs = resolveParagraphStartMs(paragraph) ?: continue
                    val serializedBody = normalizeParagraphBody(serializeChildren(paragraph))
                    if (serializedBody.isBlank()) continue
                    add(beginMs to "[${formatLrcTimestamp(beginMs)}]$serializedBody")
                }
            }.sortedBy { it.first }
                .joinToString("\n") { it.second }
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun parseDocument(normalizedTtml: String): org.w3c.dom.Document {
            val builder = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
            }.newDocumentBuilder().apply {
                setEntityResolver(EntityResolver { _, _ -> InputSource(StringReader("")) })
            }

            return builder.parse(InputSource(StringReader(normalizedTtml)))

    }

    fun parseDetailed(ttmlText: String): com.theveloper.pixelplay.data.model.Lyrics? = runCatching {
        val lrc = parseToEnhancedLrc(ttmlText) ?: return null
        val basic = LyricsUtils.parseLyrics(lrc)
        val document = parseDocument(normalizeTtmlDocument(ttmlText))
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        val lines = basic.synced.orEmpty().toMutableList()
        for (index in 0 until paragraphs.length) {
            val p = paragraphs.item(index) as? Element ?: continue
            val start = resolveParagraphStartMs(p) ?: continue
            val lineIndex = lines.indexOfFirst { it.time == start }
            if (lineIndex < 0) continue
            val line = lines[lineIndex]
            val spans = p.getElementsByTagNameNS("*", "span")
            val ends = mutableMapOf<Pair<Int, String>, Int>()
            for (i in 0 until spans.length) {
                val span = spans.item(i) as? Element ?: continue
                val begin = parseTimeExpression(span.getAttribute("begin")) ?: continue
                val end = parseTimeExpression(span.getAttribute("end"))
                    ?: parseTimeExpression(span.getAttribute("dur"))?.let { begin + it } ?: continue
                if (end > begin) ends[begin to span.textContent.trim()] = end
            }
            val end = parseTimeExpression(p.getAttribute("end"))
                ?: parseTimeExpression(p.getAttribute("dur"))?.let { start + it }
            lines[lineIndex] = line.copy(endTime = end?.takeIf { it > start },
                voiceId = p.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "agent").takeIf { it.isNotBlank() },
                songPart = resolveSongPart(p),
                words = line.words?.map { word -> word.copy(endTime = ends[word.time to word.word]) })
        }
        basic.copy(synced = lines, timing = com.theveloper.pixelplay.data.model.LyricsTimingEvidence(source = "ttml"))
    }.getOrNull()
    /**
     * Apple-style TTML marks each block with its part of the song:
     * `<div itunes:song-part="Verse">`. Read it from the nearest enclosing div (any namespace).
     */
    private fun resolveSongPart(paragraph: Element): String? {
        var node: Node? = paragraph
        while (node != null) {
            if (node is Element) {
                val attrs = node.attributes
                for (i in 0 until attrs.length) {
                    val attr = attrs.item(i)
                    val name = (attr.localName ?: attr.nodeName).substringAfter(':').lowercase()
                    if (name == "song-part" || name == "songpart") {
                        return attr.nodeValue?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }
            }
            node = node.parentNode
        }
        return null
    }

    private fun normalizeTtmlDocument(raw: String): String {
        val withoutLeadingNoise = raw.trimStart { char ->
            char.isWhitespace() ||
                char == '\uFEFF' ||
                Character.getType(char).toByte() == Character.FORMAT
        }

        return if (withoutLeadingNoise.startsWith("<?xml", ignoreCase = true)) {
            withoutLeadingNoise.substringAfter("?>", missingDelimiterValue = withoutLeadingNoise).trimStart()
        } else {
            withoutLeadingNoise
        }
    }

    private fun resolveParagraphStartMs(paragraph: Element): Int? {
        parseTimeExpression(paragraph.getAttribute("begin"))?.let { return it }

        val spans = paragraph.getElementsByTagNameNS("*", "span")
        for (index in 0 until spans.length) {
            val span = spans.item(index) as? Element ?: continue
            parseTimeExpression(span.getAttribute("begin"))?.let { return it }
        }
        return null
    }

    private fun serializeChildren(node: Node): String {
        val builder = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            builder.append(serializeNode(child))
            child = child.nextSibling
        }
        return builder.toString()
    }

    private fun serializeNode(node: Node): String {
        return when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> sanitizeTextFragment(node.nodeValue.orEmpty())
            Node.ELEMENT_NODE -> serializeElement(node as Element)
            else -> ""
        }
    }

    private fun serializeElement(element: Element): String {
        return when (localNameOf(element)) {
            "br" -> "\n"
            "span" -> {
                val content = normalizeInlineText(serializeChildren(element))
                if (content.isBlank()) {
                    ""
                } else {
                    val beginMs = parseTimeExpression(element.getAttribute("begin"))
                    if (beginMs != null) "<${formatLrcTimestamp(beginMs)}>$content" else content
                }
            }
            else -> serializeChildren(element)
        }
    }

    private fun localNameOf(element: Element): String {
        return element.localName?.lowercase()
            ?: element.tagName.substringAfterLast(':').lowercase()
    }

    private fun sanitizeTextFragment(raw: String): String {
        if (raw.isEmpty()) return raw

        val cleaned = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .filterNot { char ->
                Character.getType(char).toByte() == Character.FORMAT ||
                    (Character.isISOControl(char) && char != '\n' && char != '\t')
            }

        if (cleaned.isEmpty()) return ""
        if (cleaned.all { it.isWhitespace() }) {
            return if (cleaned.any { it == '\n' || it == '\t' }) "" else " "
        }

        return cleaned.replace(Regex("\\s+"), " ")
    }

    private fun normalizeInlineText(value: String): String {
        return value
            .replace(Regex("[ ]{2,}"), " ")
            .replace(Regex(" *\n *"), "\n")
            .trim('\n')
    }

    private fun normalizeParagraphBody(value: String): String {
        return value
            .lineSequence()
            .map { line -> line.replace(Regex("[ ]{2,}"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun parseTimeExpression(value: String?): Int? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null

        if (normalized.endsWith("ms", ignoreCase = true)) return normalized.dropLast(2).toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE }?.roundToInt()

        if (normalized.endsWith("s", ignoreCase = true)) {
            val seconds = normalized.removeSuffix("s").toDoubleOrNull() ?: return null
            return (seconds * 1000).roundToInt()
        }

        normalized.toDoubleOrNull()?.let { seconds ->
            return (seconds * 1000).roundToInt()
        }

        val parts = normalized.split(':')
        return when (parts.size) {
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                val (seconds, millis) = parseSecondsWithFraction(parts[1]) ?: return null
                (minutes * 60_000L + seconds * 1_000L + millis).toInt()
            }
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val (seconds, millis) = parseSecondsWithFraction(parts[2]) ?: return null
                (hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis).toInt()
            }
            else -> null
        }
    }

    private fun parseSecondsWithFraction(value: String): Pair<Long, Long>? {
        val parts = value.split('.', limit = 2)
        val seconds = parts[0].toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty()
        if (fraction.isEmpty()) return seconds to 0L

        val digits = fraction.take(3).padEnd(3, '0')
        val millis = digits.toLongOrNull() ?: return null
        return seconds to millis
    }

    private fun formatLrcTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1_000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = timeMs % 1_000
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis)
    }
}


