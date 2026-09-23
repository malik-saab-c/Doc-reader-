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
import java.util.zip.ZipOutputStream

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
    BIG_STAT,
    BLANK
}

enum class SlideElementType {
    TITLE,
    SUBTITLE,
    TEXT_BOX,
    BULLET_LIST,
    IMAGE,
    TABLE,
    STAT_HERO
}

data class SlideElement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: SlideElementType = SlideElementType.TEXT_BOX,
    val text: String = "",
    val bulletPoints: List<String> = emptyList(),
    // Normalized coordinates (0.0f - 1.0f relative to slide bounds)
    val normX: Float = 0.05f,
    val normY: Float = 0.05f,
    val normW: Float = 0.9f,
    val normH: Float = 0.2f,
    // Styling & Typography
    val fontSizeSp: Float = 16f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val textAlign: String = "LEFT", // "LEFT", "CENTER", "RIGHT"
    val fontColorHex: String? = null,
    val backgroundColorHex: String? = null,
    val borderColorHex: String? = null,
    // Embedded image bytes (if type == IMAGE)
    val imageBytes: ByteArray? = null,
    // Embedded table rows (if type == TABLE)
    val tableRows: List<List<String>> = emptyList()
)

data class SlideItem(
    val slideNumber: Int,
    val title: String,
    val bulletPoints: List<String> = emptyList(),
    val notes: String = "",
    val subtitle: String = "",
    val categoryTag: String = "",
    val layoutType: SlideLayout = SlideLayout.TITLE_AND_CONTENT,
    val backgroundColorHex: String? = null,
    val elements: List<SlideElement> = emptyList(),
    val aspectRatio: Float = 16f / 9f
)

data class PresentationData(
    val title: String,
    val slides: List<SlideItem>,
    val slideWidthEmu: Long = 12192000L,
    val slideHeightEmu: Long = 6858000L
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
     * Pure Kotlin OpenXML PPTX parser extracting slide dimensions, shape coordinates,
     * embedded images, tables, font sizes, colors, and structured slide hierarchy.
     */
    fun parsePptx(inputStream: InputStream, title: String): PresentationData {
        val slideXmlMap = mutableMapOf<Int, ByteArray>()
        val slideRelsMap = mutableMapOf<Int, MutableMap<String, String>>()
        val mediaMap = mutableMapOf<String, ByteArray>()
        var sldWidthEmu = 12192000L // Default 16:9 widescreen
        var sldHeightEmu = 6858000L

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            var entryCounter = 1

            while (entry != null && slideXmlMap.size < MAX_SLIDES) {
                val name = entry.name.lowercase()
                when {
                    name == "ppt/presentation.xml" -> {
                        val pBytes = readEntryBytesBounded(zis, 1024 * 1024)
                        val dims = parsePresentationDimensions(pBytes)
                        if (dims != null) {
                            sldWidthEmu = dims.first
                            sldHeightEmu = dims.second
                        }
                    }
                    name.startsWith("ppt/media/") -> {
                        val mediaKey = entry.name.substringAfter("ppt/").trimStart('/')
                        val bytes = readEntryBytesBounded(zis, 4 * 1024 * 1024)
                        if (bytes.isNotEmpty()) {
                            mediaMap[mediaKey] = bytes
                            mediaMap[entry.name] = bytes
                        }
                    }
                    name.startsWith("ppt/slides/_rels/slide") && name.endsWith(".xml.rels") -> {
                        val slidePart = name.substringAfterLast("slide").substringBefore(".xml.rels")
                        val slideNum = slidePart.toIntOrNull() ?: entryCounter
                        val relBytes = readEntryBytesBounded(zis, 512 * 1024)
                        val rels = parseRelsXml(relBytes)
                        slideRelsMap[slideNum] = rels
                    }
                    name.startsWith("ppt/slides/slide") && name.endsWith(".xml") -> {
                        val slidePart = name.substringAfterLast("slide").substringBefore(".xml")
                        val slideNum = slidePart.toIntOrNull() ?: entryCounter
                        val slideBytes = readEntryBytesBounded(zis, 3 * 1024 * 1024)
                        if (slideBytes.isNotEmpty()) {
                            slideXmlMap[slideNum] = slideBytes
                        }
                        entryCounter++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading PPTX zip: ${e.message}", e)
        }

        if (slideXmlMap.isEmpty()) return emptyPresentation(title, "No presentation slides found.")

        val slideItems = mutableListOf<SlideItem>()
        val sortedSlideNums = slideXmlMap.keys.sorted()

        val aspectRatio = if (sldHeightEmu > 0) sldWidthEmu.toFloat() / sldHeightEmu.toFloat() else 16f / 9f

        for (slideNum in sortedSlideNums) {
            val xmlBytes = slideXmlMap[slideNum] ?: continue
            val rels = slideRelsMap[slideNum] ?: emptyMap()
            val item = parseSlideXmlAccurate(
                xmlBytes = xmlBytes,
                slideNum = slideNum,
                sldWidth = sldWidthEmu,
                sldHeight = sldHeightEmu,
                rels = rels,
                mediaMap = mediaMap,
                aspectRatio = aspectRatio
            )
            slideItems.add(item)
        }

        return PresentationData(
            title = title,
            slides = slideItems,
            slideWidthEmu = sldWidthEmu,
            slideHeightEmu = sldHeightEmu
        )
    }

    private fun parsePresentationDimensions(xmlBytes: ByteArray): Pair<Long, Long>? {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val local = parser.name?.substringAfterLast(":") ?: ""
                    if (local == "sldSz") {
                        val cx = parser.getAttributeValue(null, "cx")?.toLongOrNull()
                        val cy = parser.getAttributeValue(null, "cy")?.toLongOrNull()
                        if (cx != null && cy != null && cx > 0 && cy > 0) {
                            return Pair(cx, cy)
                        }
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseRelsXml(xmlBytes: ByteArray): MutableMap<String, String> {
        val rels = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val local = parser.name?.substringAfterLast(":") ?: ""
                    if (local == "Relationship") {
                        val id = parser.getAttributeValue(null, "Id")
                        val target = parser.getAttributeValue(null, "Target")
                        if (id != null && target != null) {
                            rels[id] = target
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing rels: ${e.message}")
        }
        return rels
    }

    private fun parseSlideXmlAccurate(
        xmlBytes: ByteArray,
        slideNum: Int,
        sldWidth: Long,
        sldHeight: Long,
        rels: Map<String, String>,
        mediaMap: Map<String, ByteArray>,
        aspectRatio: Float
    ): SlideItem {
        val elements = mutableListOf<SlideElement>()
        val allParagraphs = mutableListOf<String>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var eventType = parser.eventType

            // Shape parsing context
            var inShape = false
            var inPic = false
            var inTable = false
            var currentShapeName = ""
            var currentShapePlaceholderType = ""

            // Coordinates in EMUs
            var currentOffX = 0L
            var currentOffY = 0L
            var currentExtCX = 0L
            var currentExtCY = 0L
            var currentShapeBgColor: String? = null

            // Text inside current shape
            val shapeParagraphs = mutableListOf<String>()
            val shapeBulletPoints = mutableListOf<String>()
            var currentPAlign = "LEFT"
            var currentPText = StringBuilder()
            var currentPFontSizeSp = 16f
            var currentPIsBold = false
            var currentPIsItalic = false
            var currentPFontColor: String? = null

            // Image context
            var currentBlipEmbedId: String? = null

            // Table context
            val currentTableRows = mutableListOf<MutableList<String>>()
            var currentTableRow = mutableListOf<String>()
            var currentTableCellText = StringBuilder()
            var inTableCell = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawName = parser.name ?: ""
                val local = rawName.substringAfterLast(":")

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (local) {
                            "sp" -> {
                                inShape = true
                                currentShapeName = ""
                                currentShapePlaceholderType = ""
                                currentOffX = 0L
                                currentOffY = 0L
                                currentExtCX = 0L
                                currentExtCY = 0L
                                currentShapeBgColor = null
                                shapeParagraphs.clear()
                                shapeBulletPoints.clear()
                            }
                            "pic" -> {
                                inPic = true
                                currentOffX = 0L
                                currentOffY = 0L
                                currentExtCX = 0L
                                currentExtCY = 0L
                                currentBlipEmbedId = null
                            }
                            "graphicFrame" -> {
                                inTable = true
                                currentOffX = 0L
                                currentOffY = 0L
                                currentExtCX = 0L
                                currentExtCY = 0L
                                currentTableRows.clear()
                            }
                            "cNvPr" -> {
                                currentShapeName = parser.getAttributeValue(null, "name") ?: ""
                            }
                            "ph" -> {
                                currentShapePlaceholderType = parser.getAttributeValue(null, "type") ?: "body"
                            }
                            "off" -> {
                                val x = parser.getAttributeValue(null, "x")?.toLongOrNull() ?: 0L
                                val y = parser.getAttributeValue(null, "y")?.toLongOrNull() ?: 0L
                                currentOffX = x
                                currentOffY = y
                            }
                            "ext" -> {
                                val cx = parser.getAttributeValue(null, "cx")?.toLongOrNull() ?: 0L
                                val cy = parser.getAttributeValue(null, "cy")?.toLongOrNull() ?: 0L
                                if (cx > 0 && cy > 0) {
                                    currentExtCX = cx
                                    currentExtCY = cy
                                }
                            }
                            "srgbClr" -> {
                                val colorVal = parser.getAttributeValue(null, "val")
                                if (colorVal != null && colorVal.length == 6) {
                                    if (currentPText.isEmpty() && currentPFontColor == null) {
                                        currentPFontColor = "#$colorVal"
                                    } else if (currentShapeBgColor == null) {
                                        currentShapeBgColor = "#$colorVal"
                                    }
                                }
                            }
                            "pPr" -> {
                                val algn = parser.getAttributeValue(null, "algn")
                                currentPAlign = when (algn) {
                                    "ctr" -> "CENTER"
                                    "r" -> "RIGHT"
                                    "just" -> "JUSTIFY"
                                    else -> "LEFT"
                                }
                            }
                            "rPr" -> {
                                val sz = parser.getAttributeValue(null, "sz")?.toIntOrNull()
                                if (sz != null && sz > 0) {
                                    currentPFontSizeSp = (sz / 100f).coerceIn(10f, 60f)
                                }
                                val b = parser.getAttributeValue(null, "b")
                                if (b == "1" || b == "true") currentPIsBold = true
                                val i = parser.getAttributeValue(null, "i")
                                if (i == "1" || i == "true") currentPIsItalic = true
                            }
                            "p" -> {
                                currentPText.setLength(0)
                            }
                            "blip" -> {
                                val embed = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                                    ?: parser.getAttributeValue(null, "r:embed")
                                    ?: parser.getAttributeValue(null, "embed")
                                if (embed != null) {
                                    currentBlipEmbedId = embed
                                }
                            }
                            "tr" -> {
                                currentTableRow = mutableListOf()
                            }
                            "tc" -> {
                                inTableCell = true
                                currentTableCellText.setLength(0)
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text
                        if (!text.isNullOrEmpty()) {
                            if (inTableCell) {
                                currentTableCellText.append(text)
                            } else {
                                currentPText.append(text)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (local) {
                            "p" -> {
                                val pt = currentPText.toString().trim()
                                if (pt.isNotEmpty()) {
                                    shapeParagraphs.add(pt)
                                    if (pt.startsWith("•") || pt.startsWith("-") || pt.startsWith("*") || shapeParagraphs.size > 1) {
                                        shapeBulletPoints.add(pt.trimStart('•', '-', '*', ' '))
                                    }
                                    allParagraphs.add(pt)
                                }
                            }
                            "tc" -> {
                                inTableCell = false
                                currentTableRow.add(currentTableCellText.toString().trim())
                            }
                            "tr" -> {
                                if (currentTableRow.isNotEmpty()) {
                                    currentTableRows.add(currentTableRow.toMutableList())
                                }
                            }
                            "sp" -> {
                                inShape = false
                                if (shapeParagraphs.isNotEmpty()) {
                                    val isTitle = currentShapePlaceholderType.contains("title", ignoreCase = true) ||
                                            currentShapeName.contains("title", ignoreCase = true) ||
                                            (elements.none { it.type == SlideElementType.TITLE } && currentOffY < sldHeight * 0.35f && currentPFontSizeSp >= 18f)

                                    val isSubtitle = currentShapePlaceholderType.contains("sub", ignoreCase = true) ||
                                            currentShapeName.contains("sub", ignoreCase = true)

                                    val normX = if (sldWidth > 0 && currentExtCX > 0) (currentOffX.toFloat() / sldWidth).coerceIn(0f, 0.95f) else 0.06f
                                    val normY = if (sldHeight > 0 && currentExtCY > 0) (currentOffY.toFloat() / sldHeight).coerceIn(0f, 0.95f) else 0.10f
                                    val normW = if (sldWidth > 0 && currentExtCX > 0) (currentExtCX.toFloat() / sldWidth).coerceIn(0.04f, 0.98f) else 0.88f
                                    val normH = if (sldHeight > 0 && currentExtCY > 0) (currentExtCY.toFloat() / sldHeight).coerceIn(0.02f, 0.98f) else 0.20f

                                    val elType = when {
                                        isTitle -> SlideElementType.TITLE
                                        isSubtitle -> SlideElementType.SUBTITLE
                                        shapeBulletPoints.size >= 2 -> SlideElementType.BULLET_LIST
                                        else -> SlideElementType.TEXT_BOX
                                    }

                                    elements.add(
                                        SlideElement(
                                            type = elType,
                                            text = shapeParagraphs.joinToString("\n"),
                                            bulletPoints = shapeBulletPoints.toList(),
                                            normX = normX,
                                            normY = normY,
                                            normW = normW,
                                            normH = normH,
                                            fontSizeSp = currentPFontSizeSp,
                                            isBold = currentPIsBold || isTitle,
                                            isItalic = currentPIsItalic,
                                            textAlign = currentPAlign,
                                            fontColorHex = currentPFontColor,
                                            backgroundColorHex = currentShapeBgColor
                                        )
                                    )
                                }
                            }
                            "pic" -> {
                                inPic = false
                                val embedId = currentBlipEmbedId
                                if (embedId != null) {
                                    val relTarget = rels[embedId]
                                    val mediaKey = when {
                                        relTarget == null -> null
                                        relTarget.startsWith("../media/") -> "media/" + relTarget.removePrefix("../media/")
                                        relTarget.startsWith("media/") -> relTarget
                                        else -> relTarget.substringAfterLast("/")
                                    }
                                    val imgBytes = if (mediaKey != null) mediaMap[mediaKey] ?: mediaMap["ppt/$mediaKey"] else null

                                    val normX = if (sldWidth > 0 && currentExtCX > 0) (currentOffX.toFloat() / sldWidth).coerceIn(0f, 0.95f) else 0.25f
                                    val normY = if (sldHeight > 0 && currentExtCY > 0) (currentOffY.toFloat() / sldHeight).coerceIn(0f, 0.95f) else 0.25f
                                    val normW = if (sldWidth > 0 && currentExtCX > 0) (currentExtCX.toFloat() / sldWidth).coerceIn(0.04f, 0.98f) else 0.50f
                                    val normH = if (sldHeight > 0 && currentExtCY > 0) (currentExtCY.toFloat() / sldHeight).coerceIn(0.02f, 0.98f) else 0.50f

                                    elements.add(
                                        SlideElement(
                                            type = SlideElementType.IMAGE,
                                            normX = normX,
                                            normY = normY,
                                            normW = normW,
                                            normH = normH,
                                            imageBytes = imgBytes
                                        )
                                    )
                                }
                            }
                            "graphicFrame" -> {
                                inTable = false
                                if (currentTableRows.isNotEmpty()) {
                                    val normX = if (sldWidth > 0 && currentExtCX > 0) (currentOffX.toFloat() / sldWidth).coerceIn(0f, 0.95f) else 0.06f
                                    val normY = if (sldHeight > 0 && currentExtCY > 0) (currentOffY.toFloat() / sldHeight).coerceIn(0f, 0.95f) else 0.25f
                                    val normW = if (sldWidth > 0 && currentExtCX > 0) (currentExtCX.toFloat() / sldWidth).coerceIn(0.04f, 0.98f) else 0.88f
                                    val normH = if (sldHeight > 0 && currentExtCY > 0) (currentExtCY.toFloat() / sldHeight).coerceIn(0.02f, 0.98f) else 0.50f

                                    elements.add(
                                        SlideElement(
                                            type = SlideElementType.TABLE,
                                            normX = normX,
                                            normY = normY,
                                            normW = normW,
                                            normH = normH,
                                            tableRows = currentTableRows.map { it.toList() }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseSlideXmlAccurate: ${e.message}", e)
        }

        val rawTitle = elements.firstOrNull { it.type == SlideElementType.TITLE }?.text
            ?: allParagraphs.firstOrNull()
            ?: "Slide $slideNum"

        val subtitle = elements.firstOrNull { it.type == SlideElementType.SUBTITLE }?.text
            ?: allParagraphs.getOrNull(1)?.takeIf { it.length < 120 && !it.startsWith("•") }
            ?: ""

        val bullets = elements.filter { it.type == SlideElementType.BULLET_LIST }
            .flatMap { it.bulletPoints.ifEmpty { listOf(it.text) } }
            .ifEmpty {
                val rem = if (subtitle.isNotEmpty()) allParagraphs.drop(2) else allParagraphs.drop(1)
                rem.take(MAX_BULLETS_PER_SLIDE)
            }
            .ifEmpty { listOf("Slide $slideNum Content") }

        val layout = when {
            slideNum == 1 && bullets.size <= 1 -> SlideLayout.TITLE_SLIDE
            bullets.size >= 4 -> SlideLayout.TWO_COLUMN
            elements.any { it.type == SlideElementType.TABLE } -> SlideLayout.TITLE_AND_CONTENT
            else -> SlideLayout.TITLE_AND_CONTENT
        }

        // If no explicit elements were parsed from XML, construct structured standard elements
        val finalElements = if (elements.isNotEmpty()) {
            elements
        } else {
            buildDefaultSlideElements(rawTitle, subtitle, bullets, layout)
        }

        return SlideItem(
            slideNumber = slideNum,
            title = rawTitle,
            bulletPoints = bullets,
            notes = "",
            subtitle = subtitle,
            categoryTag = "SLIDE ${"%02d".format(slideNum)}",
            layoutType = layout,
            elements = finalElements,
            aspectRatio = aspectRatio
        )
    }

    /**
     * Constructs rich, positioned elements for slides lacking XML shape trees.
     */
    fun buildDefaultSlideElements(
        title: String,
        subtitle: String,
        bulletPoints: List<String>,
        layout: SlideLayout
    ): List<SlideElement> {
        val elements = mutableListOf<SlideElement>()
        when (layout) {
            SlideLayout.TITLE_SLIDE -> {
                elements.add(
                    SlideElement(
                        type = SlideElementType.TITLE,
                        text = title,
                        normX = 0.08f,
                        normY = 0.28f,
                        normW = 0.84f,
                        normH = 0.28f,
                        fontSizeSp = 26f,
                        isBold = true,
                        textAlign = "CENTER"
                    )
                )
                if (subtitle.isNotBlank()) {
                    elements.add(
                        SlideElement(
                            type = SlideElementType.SUBTITLE,
                            text = subtitle,
                            normX = 0.10f,
                            normY = 0.58f,
                            normW = 0.80f,
                            normH = 0.16f,
                            fontSizeSp = 15f,
                            isBold = false,
                            textAlign = "CENTER"
                        )
                    )
                }
            }
            SlideLayout.TWO_COLUMN -> {
                elements.add(
                    SlideElement(
                        type = SlideElementType.TITLE,
                        text = title,
                        normX = 0.06f,
                        normY = 0.08f,
                        normW = 0.88f,
                        normH = 0.16f,
                        fontSizeSp = 21f,
                        isBold = true
                    )
                )
                val mid = (bulletPoints.size + 1) / 2
                val col1 = bulletPoints.take(mid)
                val col2 = bulletPoints.drop(mid)
                elements.add(
                    SlideElement(
                        type = SlideElementType.BULLET_LIST,
                        bulletPoints = col1,
                        normX = 0.06f,
                        normY = 0.28f,
                        normW = 0.42f,
                        normH = 0.62f,
                        fontSizeSp = 13.5f
                    )
                )
                elements.add(
                    SlideElement(
                        type = SlideElementType.BULLET_LIST,
                        bulletPoints = col2,
                        normX = 0.52f,
                        normY = 0.28f,
                        normW = 0.42f,
                        normH = 0.62f,
                        fontSizeSp = 13.5f
                    )
                )
            }
            SlideLayout.BIG_STAT -> {
                elements.add(
                    SlideElement(
                        type = SlideElementType.TITLE,
                        text = title,
                        normX = 0.06f,
                        normY = 0.08f,
                        normW = 0.88f,
                        normH = 0.16f,
                        fontSizeSp = 21f,
                        isBold = true
                    )
                )
                elements.add(
                    SlideElement(
                        type = SlideElementType.STAT_HERO,
                        text = bulletPoints.firstOrNull() ?: subtitle.ifBlank { "100%" },
                        normX = 0.08f,
                        normY = 0.28f,
                        normW = 0.84f,
                        normH = 0.38f,
                        fontSizeSp = 46f,
                        isBold = true,
                        textAlign = "CENTER"
                    )
                )
                if (bulletPoints.size > 1) {
                    elements.add(
                        SlideElement(
                            type = SlideElementType.TEXT_BOX,
                            text = bulletPoints.drop(1).joinToString(" • "),
                            normX = 0.10f,
                            normY = 0.70f,
                            normW = 0.80f,
                            normH = 0.18f,
                            fontSizeSp = 14f,
                            textAlign = "CENTER"
                        )
                    )
                }
            }
            else -> {
                elements.add(
                    SlideElement(
                        type = SlideElementType.TITLE,
                        text = title,
                        normX = 0.06f,
                        normY = 0.08f,
                        normW = 0.88f,
                        normH = 0.16f,
                        fontSizeSp = 21f,
                        isBold = true
                    )
                )
                if (subtitle.isNotBlank()) {
                    elements.add(
                        SlideElement(
                            type = SlideElementType.SUBTITLE,
                            text = subtitle,
                            normX = 0.06f,
                            normY = 0.22f,
                            normW = 0.88f,
                            normH = 0.10f,
                            fontSizeSp = 13f
                        )
                    )
                }
                elements.add(
                    SlideElement(
                        type = SlideElementType.BULLET_LIST,
                        bulletPoints = bulletPoints,
                        normX = 0.06f,
                        normY = if (subtitle.isNotBlank()) 0.34f else 0.26f,
                        normW = 0.88f,
                        normH = if (subtitle.isNotBlank()) 0.58f else 0.66f,
                        fontSizeSp = 13.5f
                    )
                )
            }
        }
        return elements
    }

    /**
     * Generates a 100% genuine Microsoft PowerPoint OpenXML (.pptx) zip file.
     */
    fun exportToPptxZip(presentation: PresentationData): ByteArray {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)

        val wEmu = presentation.slideWidthEmu
        val hEmu = presentation.slideHeightEmu

        // 1. [Content_Types].xml
        val ctXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
            presentation.slides.forEachIndexed { i, _ ->
                append("""<Override PartName="/ppt/slides/slide${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
            }
            append("""</Types>""")
        }
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write(ctXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 2. _rels/.rels
        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write(rootRels.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 3. ppt/_rels/presentation.xml.rels
        val presRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            presentation.slides.forEachIndexed { i, _ ->
                append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${i + 1}.xml"/>""")
            }
            append("""</Relationships>""")
        }
        zos.putNextEntry(ZipEntry("ppt/_rels/presentation.xml.rels"))
        zos.write(presRels.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 4. ppt/presentation.xml
        val presXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
            append("""<p:sldMasterIdLst/>""")
            append("""<p:sldIdLst>""")
            presentation.slides.forEachIndexed { i, _ ->
                append("""<p:sldId id="${256 + i}" r:id="rId${i + 1}"/>""")
            }
            append("""</p:sldIdLst>""")
            append("""<p:sldSz cx="$wEmu" cy="$hEmu" type="screen16x9"/>""")
            append("""</p:presentation>""")
        }
        zos.putNextEntry(ZipEntry("ppt/presentation.xml"))
        zos.write(presXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 5. ppt/slides/slide{N}.xml & rels
        presentation.slides.forEachIndexed { i, slide ->
            val slideNum = i + 1
            val slideXml = buildSlideXml(slide, slideNum, wEmu, hEmu)
            zos.putNextEntry(ZipEntry("ppt/slides/slide$slideNum.xml"))
            zos.write(slideXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val slideRelXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""
            zos.putNextEntry(ZipEntry("ppt/slides/_rels/slide$slideNum.xml.rels"))
            zos.write(slideRelXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        zos.finish()
        zos.close()
        return baos.toByteArray()
    }

    private fun buildSlideXml(slide: SlideItem, slideNum: Int, wEmu: Long, hEmu: Long): String {
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
            append("""<p:cSld><p:spTree>""")
            append("""<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>""")
            append("""<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>""")

            var shapeId = 2
            val elements = slide.elements.ifEmpty {
                buildDefaultSlideElements(slide.title, slide.subtitle, slide.bulletPoints, slide.layoutType)
            }

            for (el in elements) {
                val offX = (el.normX * wEmu).toLong()
                val offY = (el.normY * hEmu).toLong()
                val extCX = (el.normW * wEmu).toLong()
                val extCY = (el.normH * hEmu).toLong()
                val szVal = (el.fontSizeSp * 100).toInt()
                val algn = when (el.textAlign) {
                    "CENTER" -> "ctr"
                    "RIGHT" -> "r"
                    else -> "l"
                }

                append("""<p:sp>""")
                append("""<p:nvSpPr><p:cNvPr id="$shapeId" name="Shape $shapeId"/><p:nvPr/></p:nvSpPr>""")
                append("""<p:spPr><a:xfrm><a:off x="$offX" y="$offY"/><a:ext cx="$extCX" cy="$extCY"/></a:xfrm></p:spPr>""")
                append("""<p:txBody><a:bodyPr/><a:lstStyle/>""")

                val textLines = if (el.bulletPoints.isNotEmpty()) el.bulletPoints else el.text.lines()
                for (line in textLines) {
                    val safeLine = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    append("""<a:p><a:pPr algn="$algn"/><a:r><a:rPr sz="$szVal" b="${if (el.isBold) 1 else 0}"/><a:t>$safeLine</a:t></a:r></a:p>""")
                }
                append("""</p:txBody></p:sp>""")
                shapeId++
            }

            append("""</p:spTree></p:cSld>""")
            append("""<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>""")
            append("""</p:sld>""")
        }
    }

    /**
     * Converts a presentation into a clean, portable Markdown format.
     */
    fun presentationToMarkdown(presentation: PresentationData): String {
        return buildString {
            presentation.slides.forEachIndexed { idx, slide ->
                append("# Slide ${idx + 1}: ${slide.title}\n")
                if (slide.subtitle.isNotBlank()) {
                    append("${slide.subtitle}\n")
                }
                slide.bulletPoints.forEach { pt ->
                    append("- $pt\n")
                }
                if (slide.notes.isNotBlank()) {
                    append("\n<!-- Speaker Notes: ${slide.notes} -->\n")
                }
                if (idx < presentation.slides.size - 1) {
                    append("\n---\n\n")
                }
            }
        }
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
                val layout = if (slideIndex == 1 && currentBullets.size <= 1) SlideLayout.TITLE_SLIDE else SlideLayout.TITLE_AND_CONTENT
                val slideTitle = currentTitle.ifBlank { "Slide $slideIndex" }
                val slideBullets = currentBullets.toList()
                val elements = buildDefaultSlideElements(slideTitle, "", slideBullets, layout)
                slides.add(SlideItem(slideIndex, slideTitle, slideBullets, layoutType = layout, elements = elements))
                slideIndex++
                currentBullets.clear()
                currentTitle = line
            } else if (currentTitle.isEmpty()) {
                currentTitle = line
            } else {
                currentBullets.add(line)
                if (currentBullets.size >= 8) {
                    val layout = if (slideIndex == 1) SlideLayout.TITLE_SLIDE else SlideLayout.TITLE_AND_CONTENT
                    val slideTitle = currentTitle.ifBlank { "Slide $slideIndex" }
                    val slideBullets = currentBullets.toList()
                    val elements = buildDefaultSlideElements(slideTitle, "", slideBullets, layout)
                    slides.add(SlideItem(slideIndex, slideTitle, slideBullets, layoutType = layout, elements = elements))
                    slideIndex++
                    currentBullets.clear()
                    currentTitle = ""
                }
            }
            if (slides.size >= MAX_SLIDES) break
        }

        if (currentTitle.isNotEmpty() || currentBullets.isNotEmpty()) {
            val slideTitle = currentTitle.ifBlank { "Slide $slideIndex" }
            val slideBullets = currentBullets.ifEmpty { listOf("Slide content") }
            val layout = if (slideIndex == 1) SlideLayout.TITLE_SLIDE else SlideLayout.TITLE_AND_CONTENT
            val elements = buildDefaultSlideElements(slideTitle, "", slideBullets, layout)
            slides.add(SlideItem(slideIndex, slideTitle, slideBullets, layoutType = layout, elements = elements))
        }

        if (slides.isEmpty()) {
            val elements = buildDefaultSlideElements(title, "", listOf("PowerPoint 97-2003 Presentation Loaded"), SlideLayout.TITLE_SLIDE)
            slides.add(SlideItem(1, title, listOf("PowerPoint 97-2003 Presentation Loaded"), elements = elements))
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

            val finalBullets = bulletLines.ifEmpty { listOf("Slide content") }
            val elements = buildDefaultSlideElements(cleanTitle, subtitle, finalBullets, layout)

            slides.add(
                SlideItem(
                    slideNumber = index,
                    title = cleanTitle,
                    bulletPoints = finalBullets,
                    notes = if (subtitle.isNotEmpty()) "Speaker note: $subtitle" else "",
                    subtitle = subtitle,
                    categoryTag = categoryTag,
                    layoutType = layout,
                    elements = elements
                )
            )
            index++
            if (slides.size >= MAX_SLIDES) break
        }

        if (slides.isEmpty()) {
            val defaultBullets = listOf("Universal Slide Reader", "Swipe or tap to navigate slides")
            val elements = buildDefaultSlideElements(title, "", defaultBullets, SlideLayout.TITLE_SLIDE)
            slides.add(
                SlideItem(
                    slideNumber = 1,
                    title = title,
                    bulletPoints = defaultBullets,
                    categoryTag = "PRESENTATION",
                    layoutType = SlideLayout.TITLE_SLIDE,
                    elements = elements
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
        val bullets = listOf(message, "Universal Presentation Reader")
        val elements = buildDefaultSlideElements(title, "", bullets, SlideLayout.TITLE_SLIDE)
        return PresentationData(
            title = title,
            slides = listOf(
                SlideItem(1, title, bullets, elements = elements)
            )
        )
    }
}
