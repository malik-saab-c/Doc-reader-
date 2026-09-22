package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

enum class SectionType {
    TITLE,
    HEADING1,
    HEADING2,
    HEADING3,
    PARAGRAPH,
    BULLET_ITEM,
    NUMBERED_ITEM,
    QUOTE,
    TABLE,
    CODE_BLOCK,
    DIVIDER
}

data class DocxSection(
    val type: SectionType,
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val tableRows: List<List<String>> = emptyList()
)

data class WordDocumentData(
    val title: String,
    val sections: List<DocxSection>,
    val fullText: String,
    val wordCount: Int,
    val characterCount: Int,
    val readTimeMinutes: Int
)

enum class SlideLayout {
    TITLE_SLIDE,
    TITLE_AND_CONTENT,
    SECTION_HEADER,
    TWO_COLUMN,
    COMPARISON,
    BLANK
}

data class SlideItem(
    val slideNumber: Int,
    val title: String,
    val bulletPoints: List<String>,
    val notes: String = "",
    val subtitle: String = "",
    val categoryTag: String = "",
    val layoutType: SlideLayout = SlideLayout.TITLE_AND_CONTENT
)

data class PresentationData(
    val title: String,
    val slides: List<SlideItem>
)

object OfficeDocumentEngine {
    private const val TAG = "OfficeDocumentEngine"
    private const val MAX_SECTIONS = 3000
    private const val MAX_BULLETS_PER_SLIDE = 40
    private const val MAX_SLIDES = 150
    private const val MAX_TEXT_FILE_READ_BYTES = 2 * 1024 * 1024 // 2MB safety limit
    private const val MAX_BINARY_SCAN_BYTES = 4 * 1024 * 1024   // 4MB safety limit

    private enum class FormatCategory {
        ZIP_OPENXML,    // Starts with PK\x03\x04 (DOCX, PPTX, XLSX)
        OLE2_BINARY,    // Starts with \xD0\xCF\x11\xE0 (Legacy Office 97-2003 DOC, PPT, XLS)
        TEXT_PLAIN      // Plain text, Markdown, RTF, JSON, etc.
    }

    /**
     * Loads and parses Word documents (.docx, .doc, and text-based formats) safely.
     */
    fun loadWordDocument(
        context: Context,
        pathOrUri: String,
        title: String = "",
        extensionHint: String = "",
        fallbackContent: String? = null
    ): WordDocumentData {
        val resolvedTitle = when {
            title.isNotBlank() -> title
            pathOrUri.startsWith("content://") -> "Document"
            else -> File(pathOrUri).nameWithoutExtension.ifBlank { "Document" }
        }

        return try {
            val rawStream = openInputStream(context, pathOrUri)
            if (rawStream == null) {
                if (!fallbackContent.isNullOrBlank()) {
                    return parsePlainText(fallbackContent, resolvedTitle)
                }
                return emptyDocument(resolvedTitle, "Document file could not be opened.")
            }

            val buffered = BufferedInputStream(rawStream, 65536)
            val pushback = PushbackInputStream(buffered, 16)
            val format = detectFormat(pushback)

            when (format) {
                FormatCategory.ZIP_OPENXML -> {
                    val result = parseDocx(pushback, resolvedTitle)
                    if (result.sections.isNotEmpty()) result else {
                        if (!fallbackContent.isNullOrBlank()) parsePlainText(fallbackContent, resolvedTitle)
                        else emptyDocument(resolvedTitle, "Empty or unsupported DOCX structure.")
                    }
                }
                FormatCategory.OLE2_BINARY -> {
                    // Legacy binary .doc (Microsoft Word 97-2003)
                    parseBinaryDoc(pushback, resolvedTitle)
                }
                FormatCategory.TEXT_PLAIN -> {
                    val text = readStreamTextBounded(pushback, MAX_TEXT_FILE_READ_BYTES)
                    if (text.isNotBlank()) {
                        parsePlainText(text, resolvedTitle)
                    } else if (!fallbackContent.isNullOrBlank()) {
                        parsePlainText(fallbackContent, resolvedTitle)
                    } else {
                        emptyDocument(resolvedTitle, "Document is empty.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Word document: ${e.message}", e)
            if (!fallbackContent.isNullOrBlank()) {
                try {
                    parsePlainText(fallbackContent, resolvedTitle)
                } catch (e2: Exception) {
                    emptyDocument(resolvedTitle, "Error reading document: ${e.localizedMessage}")
                }
            } else {
                emptyDocument(resolvedTitle, "Error reading document: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Loads and parses PowerPoint presentations (.pptx, .ppt, and structured slide text) safely.
     */
    fun loadPresentation(
        context: Context,
        pathOrUri: String,
        title: String = "",
        extensionHint: String = "",
        fallbackContent: String? = null
    ): PresentationData {
        val resolvedTitle = when {
            title.isNotBlank() -> title
            pathOrUri.startsWith("content://") -> "Presentation"
            else -> File(pathOrUri).nameWithoutExtension.ifBlank { "Presentation" }
        }

        return try {
            val rawStream = openInputStream(context, pathOrUri)
            if (rawStream == null) {
                if (!fallbackContent.isNullOrBlank()) {
                    return parseTextPresentation(fallbackContent, resolvedTitle)
                }
                return emptyPresentation(resolvedTitle, "Presentation file could not be opened.")
            }

            val buffered = BufferedInputStream(rawStream, 65536)
            val pushback = PushbackInputStream(buffered, 16)
            val format = detectFormat(pushback)

            when (format) {
                FormatCategory.ZIP_OPENXML -> {
                    val result = parsePptx(pushback, resolvedTitle)
                    if (result.slides.isNotEmpty()) result else {
                        if (!fallbackContent.isNullOrBlank()) parseTextPresentation(fallbackContent, resolvedTitle)
                        else emptyPresentation(resolvedTitle, "Presentation does not contain readable slides.")
                    }
                }
                FormatCategory.OLE2_BINARY -> {
                    // Legacy binary .ppt (Microsoft PowerPoint 97-2003)
                    parseBinaryPpt(pushback, resolvedTitle)
                }
                FormatCategory.TEXT_PLAIN -> {
                    val text = readStreamTextBounded(pushback, MAX_TEXT_FILE_READ_BYTES)
                    if (text.isNotBlank()) {
                        parseTextPresentation(text, resolvedTitle)
                    } else if (!fallbackContent.isNullOrBlank()) {
                        parseTextPresentation(fallbackContent, resolvedTitle)
                    } else {
                        emptyPresentation(resolvedTitle, "Presentation is empty.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading presentation: ${e.message}", e)
            if (!fallbackContent.isNullOrBlank()) {
                try {
                    parseTextPresentation(fallbackContent, resolvedTitle)
                } catch (e2: Exception) {
                    emptyPresentation(resolvedTitle, "Error reading presentation: ${e.localizedMessage}")
                }
            } else {
                emptyPresentation(resolvedTitle, "Error reading presentation: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Pure Kotlin OpenXML DOCX parser extracting paragraphs, headings, and runs.
     */
    fun parseDocx(inputStream: InputStream, title: String): WordDocumentData {
        val sections = mutableListOf<DocxSection>()
        val fullTextBuilder = StringBuilder()

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            var docXmlBytes: ByteArray? = null

            while (entry != null) {
                val entryName = entry.name.lowercase()
                if (entryName == "word/document.xml" || entryName.endsWith("/word/document.xml")) {
                    docXmlBytes = readEntryBytesBounded(zis, 8 * 1024 * 1024)
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()

            if (docXmlBytes != null && docXmlBytes.isNotEmpty()) {
                val parser = Xml.newPullParser()
                parser.setInput(ByteArrayInputStream(docXmlBytes), "UTF-8")
                var eventType = parser.eventType

                var inParagraph = false
                var inText = false
                var isBold = false
                var isItalic = false
                var styleVal = ""
                val paraBuilder = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT && sections.size < MAX_SECTIONS) {
                    val rawName = parser.name ?: ""
                    val localName = rawName.substringAfterLast(":")
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (localName) {
                                "p" -> {
                                    inParagraph = true
                                    paraBuilder.setLength(0)
                                    styleVal = ""
                                    isBold = false
                                    isItalic = false
                                }
                                "pStyle" -> {
                                    val v = parser.getAttributeValue(null, "val")
                                        ?: parser.getAttributeValue(null, "w:val") ?: ""
                                    styleVal = v
                                }
                                "b" -> {
                                    val v = parser.getAttributeValue(null, "val")
                                        ?: parser.getAttributeValue(null, "w:val")
                                    isBold = v == null || (v != "0" && v != "false")
                                }
                                "i" -> {
                                    val v = parser.getAttributeValue(null, "val")
                                        ?: parser.getAttributeValue(null, "w:val")
                                    isItalic = v == null || (v != "0" && v != "false")
                                }
                                "t" -> {
                                    inText = true
                                }
                                "tab" -> {
                                    if (inParagraph) paraBuilder.append("    ")
                                }
                                "br" -> {
                                    if (inParagraph) paraBuilder.append(" ")
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inText) {
                                val text = parser.text
                                if (!text.isNullOrEmpty()) {
                                    paraBuilder.append(text)
                                    fullTextBuilder.append(text)
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (localName) {
                                "t" -> inText = false
                                "p" -> {
                                    inParagraph = false
                                    val text = paraBuilder.toString().trim()
                                    if (text.isNotEmpty()) {
                                        val sectionType = when {
                                            styleVal.contains("Title", ignoreCase = true) -> SectionType.TITLE
                                            styleVal.contains("Heading1", ignoreCase = true) ||
                                                    styleVal.contains("Heading 1", ignoreCase = true) ||
                                                    styleVal.equals("H1", ignoreCase = true) -> SectionType.HEADING1
                                            styleVal.contains("Heading2", ignoreCase = true) ||
                                                    styleVal.contains("Heading 2", ignoreCase = true) ||
                                                    styleVal.equals("H2", ignoreCase = true) -> SectionType.HEADING2
                                            styleVal.contains("List", ignoreCase = true) ||
                                                    text.startsWith("•") ||
                                                    text.startsWith("- ") ||
                                                    text.startsWith("* ") -> SectionType.BULLET_ITEM
                                            else -> SectionType.PARAGRAPH
                                        }

                                        val cleanText = if (sectionType == SectionType.BULLET_ITEM) {
                                            text.trimStart('•', '-', '*', ' ')
                                        } else text

                                        sections.add(DocxSection(sectionType, cleanText, isBold, isItalic))
                                        fullTextBuilder.append("\n\n")
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in pure DOCX parser: ${e.message}", e)
        }

        if (sections.isEmpty()) {
            return emptyDocument(title, "No text elements found in DOCX file.")
        }

        val allText = fullTextBuilder.toString()
        val words = countWordsFast(allText)
        val readTime = (words / 200).coerceAtLeast(1)

        return WordDocumentData(
            title = title,
            sections = sections,
            fullText = allText,
            wordCount = words,
            characterCount = allText.length,
            readTimeMinutes = readTime
        )
    }

    /**
     * Pure Kotlin OpenXML PPTX parser extracting slide titles and bullet points.
     * Fixes slide index parsing bug where slides collapsed into a single slide.
     */
    fun parsePptx(inputStream: InputStream, title: String): PresentationData {
        val slideMap = mutableMapOf<Int, SlideItem>()

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            var entryCounter = 1

            while (entry != null && slideMap.size < MAX_SLIDES) {
                val name = entry.name.lowercase()
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    // Safely extract slide number from filename: ppt/slides/slide12.xml -> 12
                    val afterSlide = name.substringAfterLast("slide")
                    val numStr = afterSlide.substringBefore(".xml")
                    val slideNum = numStr.toIntOrNull() ?: entryCounter

                    val slideBytes = readEntryBytesBounded(zis, 2 * 1024 * 1024)
                    if (slideBytes.isNotEmpty()) {
                        val slideItem = parseSlideXml(slideBytes, slideNum)
                        slideMap[slideNum] = slideItem
                    }
                    entryCounter++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading PPTX zip: ${e.message}", e)
        }

        if (slideMap.isEmpty()) return emptyPresentation(title, "No presentation slides found.")

        val sortedSlides = slideMap.keys.sorted().map { slideMap[it]!! }
        return PresentationData(title = title, slides = sortedSlides)
    }

    private fun parseSlideXml(xmlBytes: ByteArray, slideNum: Int): SlideItem {
        val paragraphs = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var eventType = parser.eventType

            var inP = false
            var inT = false
            val currentP = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT && paragraphs.size < MAX_BULLETS_PER_SLIDE + 5) {
                val rawName = parser.name ?: ""
                val localName = rawName.substringAfterLast(":")
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (localName == "p") {
                            inP = true
                            currentP.setLength(0)
                        } else if (localName == "t") {
                            inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) {
                            val text = parser.text
                            if (!text.isNullOrEmpty()) {
                                currentP.append(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (localName == "t") {
                            inT = false
                        } else if (localName == "p") {
                            inP = false
                            val t = currentP.toString().trim()
                            if (t.isNotEmpty() && !paragraphs.contains(t)) {
                                // Cap individual point length to 1000 chars for layout safety
                                val safeText = if (t.length > 1000) t.take(1000) + "..." else t
                                paragraphs.add(safeText)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing slide XML: ${e.message}", e)
        }

        val rawTitle = paragraphs.firstOrNull() ?: "Slide $slideNum"
        val remaining = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()
        val subtitle = if (remaining.isNotEmpty() && remaining.first().length < 120 && !remaining.first().startsWith("•")) {
            remaining.first()
        } else ""

        val bullets = (if (subtitle.isNotEmpty()) remaining.drop(1) else remaining)
            .take(MAX_BULLETS_PER_SLIDE)
            .ifEmpty { listOf("Slide $slideNum content") }

        val layout = when {
            slideNum == 1 && bullets.size <= 1 -> SlideLayout.TITLE_SLIDE
            bullets.size >= 4 -> SlideLayout.TWO_COLUMN
            else -> SlideLayout.TITLE_AND_CONTENT
        }

        return SlideItem(
            slideNumber = slideNum,
            title = rawTitle,
            bulletPoints = bullets,
            notes = "",
            subtitle = subtitle,
            categoryTag = "SLIDE ${"%02d".format(slideNum)}",
            layoutType = layout
        )
    }

    /**
     * Memory-safe binary parser for legacy Word 97-2003 (.doc) files.
     * Extracts readable UTF-16LE and ASCII text blocks without regex hangs or OOM.
     */
    private fun parseBinaryDoc(inputStream: InputStream, title: String): WordDocumentData {
        val bytes = readStreamBytesBounded(inputStream, MAX_BINARY_SCAN_BYTES)
        val extractedLines = extractSensibleStrings(bytes)

        if (extractedLines.isEmpty()) {
            return emptyDocument(title, "Legacy Word (.doc) contains no readable text streams.")
        }

        val sections = mutableListOf<DocxSection>()
        val fullTextBuilder = StringBuilder()

        for (i in extractedLines.indices) {
            val line = extractedLines[i]
            val type = when {
                i == 0 && line.length < 100 -> SectionType.TITLE
                i == 1 && line.length < 80 -> SectionType.HEADING1
                line.startsWith("•") || line.startsWith("-") -> SectionType.BULLET_ITEM
                line.length < 50 && !line.endsWith(".") -> SectionType.HEADING2
                else -> SectionType.PARAGRAPH
            }
            sections.add(DocxSection(type, line))
            fullTextBuilder.append(line).append("\n\n")
            if (sections.size >= MAX_SECTIONS) break
        }

        val allText = fullTextBuilder.toString()
        val words = countWordsFast(allText)
        val readTime = (words / 200).coerceAtLeast(1)

        return WordDocumentData(
            title = title,
            sections = sections,
            fullText = allText,
            wordCount = words,
            characterCount = allText.length,
            readTimeMinutes = readTime
        )
    }

    /**
     * Memory-safe binary parser for legacy PowerPoint 97-2003 (.ppt) files.
     * Extracts text streams into structured presentation slides without crashing.
     */
    private fun parseBinaryPpt(inputStream: InputStream, title: String): PresentationData {
        val bytes = readStreamBytesBounded(inputStream, MAX_BINARY_SCAN_BYTES)
        val extractedLines = extractSensibleStrings(bytes)

        if (extractedLines.isEmpty()) {
            return emptyPresentation(title, "Legacy PowerPoint (.ppt) contains no readable slide streams.")
        }

        val slides = mutableListOf<SlideItem>()
        var slideIndex = 1
        var currentTitle = ""
        val currentBullets = mutableListOf<String>()

        for (line in extractedLines) {
            val isHeading = line.length < 60 && !line.endsWith(".") && !line.startsWith("•")
            if (isHeading && currentBullets.isNotEmpty()) {
                slides.add(SlideItem(slideIndex, currentTitle.ifBlank { "Slide $slideIndex" }, currentBullets.toList()))
                slideIndex++
                currentBullets.clear()
                currentTitle = line
            } else if (currentTitle.isEmpty()) {
                currentTitle = line
            } else {
                currentBullets.add(line)
                if (currentBullets.size >= 8) {
                    slides.add(SlideItem(slideIndex, currentTitle.ifBlank { "Slide $slideIndex" }, currentBullets.toList()))
                    slideIndex++
                    currentBullets.clear()
                    currentTitle = ""
                }
            }
            if (slides.size >= MAX_SLIDES) break
        }

        if (currentTitle.isNotEmpty() || currentBullets.isNotEmpty()) {
            slides.add(SlideItem(slideIndex, currentTitle.ifBlank { "Slide $slideIndex" }, currentBullets.ifEmpty { listOf("Slide content") }))
        }

        if (slides.isEmpty()) {
            slides.add(SlideItem(1, title, listOf("PowerPoint 97-2003 Presentation Loaded")))
        }

        return PresentationData(title = title, slides = slides)
    }

    /**
     * Safe fallback plain text / Markdown reader with table, quote, divider, and list support.
     */
    fun parsePlainText(rawText: String, title: String): WordDocumentData {
        val sections = mutableListOf<DocxSection>()
        val lines = rawText.lines()

        var currentTableRows = mutableListOf<List<String>>()
        fun flushTable() {
            if (currentTableRows.isNotEmpty()) {
                sections.add(DocxSection(type = SectionType.TABLE, text = "", tableRows = currentTableRows.toList()))
                currentTableRows = mutableListOf()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushTable()
                continue
            }

            // Detect Markdown Table row
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 2) {
                val isDivider = trimmed.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isEmpty()
                if (!isDivider) {
                    val cells = trimmed.split("|")
                        .drop(1)
                        .dropLast(1)
                        .map { it.trim() }
                    if (cells.isNotEmpty()) {
                        currentTableRows.add(cells)
                    }
                }
                continue
            } else {
                flushTable()
            }

            when {
                trimmed.startsWith("# ") -> sections.add(DocxSection(SectionType.TITLE, trimmed.removePrefix("# ").trim(), isBold = true))
                trimmed.startsWith("## ") -> sections.add(DocxSection(SectionType.HEADING1, trimmed.removePrefix("## ").trim(), isBold = true))
                trimmed.startsWith("### ") -> sections.add(DocxSection(SectionType.HEADING2, trimmed.removePrefix("### ").trim(), isBold = true))
                trimmed.startsWith("#### ") -> sections.add(DocxSection(SectionType.HEADING3, trimmed.removePrefix("#### ").trim(), isBold = true))
                trimmed.startsWith("> ") -> sections.add(DocxSection(SectionType.QUOTE, trimmed.removePrefix("> ").trim(), isItalic = true))
                trimmed == "---" || trimmed == "***" -> sections.add(DocxSection(SectionType.DIVIDER, ""))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    sections.add(DocxSection(SectionType.BULLET_ITEM, trimmed.drop(2).trim()))
                }
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val numText = trimmed.substringAfter(".").trim()
                    sections.add(DocxSection(SectionType.NUMBERED_ITEM, numText))
                }
                trimmed.startsWith("```") -> {
                    val codeContent = trimmed.removePrefix("```").removeSuffix("```").trim()
                    if (codeContent.isNotEmpty()) {
                        sections.add(DocxSection(SectionType.CODE_BLOCK, codeContent))
                    }
                }
                else -> sections.add(DocxSection(SectionType.PARAGRAPH, trimmed))
            }
            if (sections.size >= MAX_SECTIONS) break
        }
        flushTable()

        if (sections.isEmpty()) {
            sections.add(DocxSection(SectionType.TITLE, title))
            sections.add(DocxSection(SectionType.PARAGRAPH, "Document content is empty."))
        }

        val words = countWordsFast(rawText)
        val readTime = (words / 200).coerceAtLeast(1)

        return WordDocumentData(
            title = title,
            sections = sections,
            fullText = rawText,
            wordCount = words,
            characterCount = rawText.length,
            readTimeMinutes = readTime
        )
    }

    private fun parseTextPresentation(text: String, title: String): PresentationData {
        val slideBlocks = text.split("---").filter { it.isNotBlank() }
        val slides = mutableListOf<SlideItem>()

        var index = 1
        for (block in slideBlocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val rawTitle = lines.firstOrNull { it.startsWith("#") }?.removePrefix("#")?.trim()
                ?: lines.firstOrNull()
                ?: "Slide $index"

            val cleanTitle = if (rawTitle.contains(":")) rawTitle.substringAfter(":").trim() else rawTitle
            val categoryTag = if (rawTitle.contains(":")) rawTitle.substringBefore(":").trim() else "SLIDE ${"%02d".format(index)}"

            val remainingLines = lines.filter { it != rawTitle }
            val subtitle = if (remainingLines.isNotEmpty() && !remainingLines.first().startsWith("-") && !remainingLines.first().startsWith("•") && !remainingLines.first().startsWith("*")) {
                remainingLines.first()
            } else ""

            val bulletLines = (if (subtitle.isNotEmpty()) remainingLines.drop(1) else remainingLines)
                .filter { it.isNotEmpty() }
                .take(MAX_BULLETS_PER_SLIDE)
                .map { line ->
                    line.trimStart('-', '•', '*', ' ')
                }

            val layout = when {
                index == 1 && bulletLines.isEmpty() -> SlideLayout.TITLE_SLIDE
                bulletLines.size >= 4 -> SlideLayout.TWO_COLUMN
                else -> SlideLayout.TITLE_AND_CONTENT
            }

            slides.add(
                SlideItem(
                    slideNumber = index,
                    title = cleanTitle,
                    bulletPoints = bulletLines.ifEmpty { listOf("Slide content") },
                    notes = if (subtitle.isNotEmpty()) "Speaker note: $subtitle" else "",
                    subtitle = subtitle,
                    categoryTag = categoryTag,
                    layoutType = layout
                )
            )
            index++
            if (slides.size >= MAX_SLIDES) break
        }

        if (slides.isEmpty()) {
            slides.add(
                SlideItem(
                    slideNumber = 1,
                    title = title,
                    bulletPoints = listOf("Universal Slide Reader", "Swipe or tap to navigate slides"),
                    categoryTag = "PRESENTATION",
                    layoutType = SlideLayout.TITLE_SLIDE
                )
            )
        }

        return PresentationData(title = title, slides = slides)
    }

    /**
     * Inspects magic header bytes to determine file type reliably.
     */
    private fun detectFormat(pushback: PushbackInputStream): FormatCategory {
        val header = ByteArray(8)
        val read = pushback.read(header)
        if (read > 0) {
            pushback.unread(header, 0, read)
        }

        if (read >= 4) {
            // ZIP magic bytes: PK\x03\x04
            if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) {
                return FormatCategory.ZIP_OPENXML
            }
        }

        if (read >= 8) {
            // OLE2 Compound Document magic bytes: \xD0\xCF\x11\xE0\xA1\xB1\x1A\xE1
            if (header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
                header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()) {
                return FormatCategory.OLE2_BINARY
            }
        }

        return FormatCategory.TEXT_PLAIN
    }

    /**
     * Scans raw bytes for printable UTF-16LE and ASCII text chunks.
     * Prevents ANR/OOM on binary files while extracting all human-readable content.
     */
    private fun extractSensibleStrings(bytes: ByteArray): List<String> {
        val results = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        // 1. Scan UTF-16LE (common in Microsoft Office 97-2003 text streams)
        var i = 0
        val sbUtf16 = StringBuilder()
        while (i + 1 < bytes.size && results.size < 500) {
            val b1 = bytes[i].toInt() and 0xFF
            val b2 = bytes[i + 1].toInt() and 0xFF
            val codePoint = (b2 shl 8) or b1

            val isPrintable = (codePoint in 32..126) || codePoint == 10 || codePoint == 13 ||
                    codePoint == 9 || (codePoint in 160..0x04FF)

            if (isPrintable) {
                sbUtf16.append(codePoint.toChar())
                i += 2
            } else {
                if (sbUtf16.length >= 4) {
                    val candidate = sbUtf16.toString().trim()
                    if (isGoodSentence(candidate) && seen.add(candidate)) {
                        results.add(candidate)
                    }
                }
                sbUtf16.setLength(0)
                i += 2
            }
        }

        // 2. Scan 8-bit ASCII / ANSI strings
        var j = 0
        val sbAscii = StringBuilder()
        while (j < bytes.size && results.size < 1000) {
            val b = bytes[j].toInt() and 0xFF
            val isPrintable = (b in 32..126) || b == 10 || b == 13 || b == 9

            if (isPrintable) {
                sbAscii.append(b.toChar())
                j++
            } else {
                if (sbAscii.length >= 5) {
                    val candidate = sbAscii.toString().trim()
                    if (isGoodSentence(candidate) && seen.add(candidate)) {
                        results.add(candidate)
                    }
                }
                sbAscii.setLength(0)
                j++
            }
        }

        return results
    }

    private fun isGoodSentence(str: String): Boolean {
        if (str.length < 3 || str.length > 2000) return false
        var letters = 0
        var digits = 0
        for (ch in str) {
            if (ch.isLetter()) letters++
            else if (ch.isDigit()) digits++
        }
        val alphaRatio = (letters + digits).toFloat() / str.length
        return alphaRatio > 0.55f && !str.startsWith("CompObj") && !str.startsWith("Root Entry")
    }

    /**
     * Fast, zero-allocation word counter.
     */
    fun countWordsFast(text: String): Int {
        var count = 0
        var inWord = false
        for (i in 0 until text.length) {
            val c = text[i]
            if (c.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                count++
            }
        }
        return count
    }

    private fun openInputStream(context: Context, pathOrUri: String): InputStream? {
        return try {
            if (pathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))
            } else {
                val f = File(pathOrUri)
                if (f.exists() && f.canRead()) f.inputStream() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for $pathOrUri: ${e.message}")
            null
        }
    }

    private fun readStreamBytesBounded(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        val baos = ByteArrayOutputStream()
        var total = 0
        stream.use { input ->
            var read = input.read(buffer)
            while (read != -1 && total < maxBytes) {
                val toWrite = minOf(read, maxBytes - total)
                baos.write(buffer, 0, toWrite)
                total += toWrite
                if (total >= maxBytes) break
                read = input.read(buffer)
            }
        }
        return baos.toByteArray()
    }

    private fun readEntryBytesBounded(zis: ZipInputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        val baos = ByteArrayOutputStream()
        var total = 0
        var read = zis.read(buffer)
        while (read != -1 && total < maxBytes) {
            val toWrite = minOf(read, maxBytes - total)
            baos.write(buffer, 0, toWrite)
            total += toWrite
            if (total >= maxBytes) break
            read = zis.read(buffer)
        }
        return baos.toByteArray()
    }

    private fun readStreamTextBounded(stream: InputStream, maxBytes: Int): String {
        val bytes = readStreamBytesBounded(stream, maxBytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun emptyDocument(title: String, message: String): WordDocumentData {
        return WordDocumentData(
            title = title,
            sections = listOf(
                DocxSection(SectionType.TITLE, title),
                DocxSection(SectionType.PARAGRAPH, message)
            ),
            fullText = "$title\n\n$message",
            wordCount = countWordsFast(message),
            characterCount = message.length,
            readTimeMinutes = 1
        )
    }

    private fun emptyPresentation(title: String, message: String): PresentationData {
        return PresentationData(
            title = title,
            slides = listOf(
                SlideItem(1, title, listOf(message, "Universal Presentation Reader"))
            )
        )
    }
}
