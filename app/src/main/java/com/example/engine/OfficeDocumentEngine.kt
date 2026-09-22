package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

enum class SectionType {
    TITLE,
    HEADING1,
    HEADING2,
    PARAGRAPH,
    BULLET_ITEM,
    CODE_BLOCK
}

data class DocxSection(
    val type: SectionType,
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

data class WordDocumentData(
    val title: String,
    val sections: List<DocxSection>,
    val fullText: String,
    val wordCount: Int,
    val characterCount: Int,
    val readTimeMinutes: Int
)

data class SlideItem(
    val slideNumber: Int,
    val title: String,
    val bulletPoints: List<String>,
    val notes: String = ""
)

data class PresentationData(
    val title: String,
    val slides: List<SlideItem>
)

object OfficeDocumentEngine {
    private const val TAG = "OfficeDocumentEngine"

    fun loadWordDocument(context: Context, pathOrUri: String): WordDocumentData {
        return try {
            val inputStream: InputStream? = if (pathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))
            } else {
                val f = File(pathOrUri)
                if (f.exists()) f.inputStream() else null
            }

            if (inputStream == null) return emptyDocument("File could not be opened")

            if (pathOrUri.lowercase().endsWith(".docx")) {
                parseDocx(inputStream, File(pathOrUri).nameWithoutExtension)
            } else {
                // Plain text / RTF / Markdown fallback
                val text = inputStream.bufferedReader().use { it.readText() }
                parsePlainText(text, File(pathOrUri).nameWithoutExtension)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Word document: ${e.message}", e)
            emptyDocument("Error: ${e.message}")
        }
    }

    fun parsePlainText(rawText: String, title: String): WordDocumentData {
        val lines = rawText.lines()
        val sections = mutableListOf<DocxSection>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            when {
                trimmed.startsWith("# ") -> sections.add(DocxSection(SectionType.TITLE, trimmed.removePrefix("# ").trim(), isBold = true))
                trimmed.startsWith("## ") -> sections.add(DocxSection(SectionType.HEADING1, trimmed.removePrefix("## ").trim(), isBold = true))
                trimmed.startsWith("### ") -> sections.add(DocxSection(SectionType.HEADING2, trimmed.removePrefix("### ").trim(), isBold = true))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    sections.add(DocxSection(SectionType.BULLET_ITEM, trimmed.drop(2).trim()))
                }
                else -> sections.add(DocxSection(SectionType.PARAGRAPH, trimmed))
            }
        }

        val words = rawText.split("\\s+".toRegex()).count { it.isNotBlank() }
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

    /**
     * Pure Kotlin DOCX parser extracting paragraphs and run elements from word/document.xml.
     */
    fun parseDocx(inputStream: InputStream, title: String): WordDocumentData {
        val sections = mutableListOf<DocxSection>()
        val fullTextBuilder = StringBuilder()

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            var docXmlBytes: ByteArray? = null

            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    docXmlBytes = zis.readBytes()
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()

            if (docXmlBytes != null) {
                val parser = Xml.newPullParser()
                parser.setInput(docXmlBytes.inputStream(), "UTF-8")
                var eventType = parser.eventType

                var inParagraph = false
                var inRun = false
                var inText = false
                var isBold = false
                var isItalic = false
                var styleVal = ""
                val paraBuilder = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name ?: ""
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (tag) {
                                "w:p" -> {
                                    inParagraph = true
                                    paraBuilder.setLength(0)
                                    styleVal = ""
                                }
                                "w:pStyle" -> {
                                    styleVal = parser.getAttributeValue(null, "w:val") ?: ""
                                }
                                "w:r" -> {
                                    inRun = true
                                    isBold = false
                                    isItalic = false
                                }
                                "w:b" -> isBold = true
                                "w:i" -> isItalic = true
                                "w:t" -> inText = true
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inText) {
                                val t = parser.text
                                paraBuilder.append(t)
                                fullTextBuilder.append(t)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (tag) {
                                "w:t" -> inText = false
                                "w:r" -> inRun = false
                                "w:p" -> {
                                    inParagraph = false
                                    val text = paraBuilder.toString().trim()
                                    if (text.isNotEmpty()) {
                                        val sectionType = when {
                                            styleVal.contains("Title", ignoreCase = true) -> SectionType.TITLE
                                            styleVal.contains("Heading1", ignoreCase = true) || styleVal.contains("Heading 1", ignoreCase = true) -> SectionType.HEADING1
                                            styleVal.contains("Heading2", ignoreCase = true) || styleVal.contains("Heading 2", ignoreCase = true) -> SectionType.HEADING2
                                            styleVal.contains("List", ignoreCase = true) || text.startsWith("•") || text.startsWith("-") -> SectionType.BULLET_ITEM
                                            else -> SectionType.PARAGRAPH
                                        }
                                        sections.add(DocxSection(sectionType, text, isBold, isItalic))
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
            return emptyDocument(title)
        }

        val allText = fullTextBuilder.toString()
        val words = allText.split("\\s+".toRegex()).count { it.isNotBlank() }
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
     * Pure Kotlin PPTX parser extracting slide titles and bullet points from ppt/slides/slide*.xml.
     */
    fun loadPresentation(context: Context, pathOrUri: String): PresentationData {
        return try {
            val inputStream: InputStream? = if (pathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))
            } else {
                val f = File(pathOrUri)
                if (f.exists()) f.inputStream() else null
            }

            if (inputStream == null) return emptyPresentation("File not accessible")

            if (pathOrUri.lowercase().endsWith(".pptx")) {
                parsePptx(inputStream, File(pathOrUri).nameWithoutExtension)
            } else {
                // Fallback structured slide text
                val text = inputStream.bufferedReader().use { it.readText() }
                parseTextPresentation(text, File(pathOrUri).nameWithoutExtension)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading presentation: ${e.message}", e)
            emptyPresentation("Error: ${e.message}")
        }
    }

    private fun parseTextPresentation(text: String, title: String): PresentationData {
        val slideBlocks = text.split("---").filter { it.isNotBlank() }
        val slides = mutableListOf<SlideItem>()

        var index = 1
        for (block in slideBlocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val slideTitle = lines.firstOrNull { it.startsWith("#") }?.removePrefix("#")?.trim()
                ?: lines.firstOrNull()
                ?: "Slide $index"
            val bullets = lines.filter { it != slideTitle }
            slides.add(SlideItem(index, slideTitle, bullets))
            index++
        }

        if (slides.isEmpty()) {
            slides.add(SlideItem(1, title, listOf(text)))
        }

        return PresentationData(title = title, slides = slides)
    }

    fun parsePptx(inputStream: InputStream, title: String): PresentationData {
        val slideXmlMap = mutableMapOf<Int, ByteArray>()

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    val numStr = name.substringAfter("slide").substringBefore(".xml")
                    val num = numStr.toIntOrNull() ?: 1
                    slideXmlMap[num] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading PPTX zip: ${e.message}", e)
        }

        if (slideXmlMap.isEmpty()) return emptyPresentation(title)

        val slides = mutableListOf<SlideItem>()
        for (slideNum in slideXmlMap.keys.sorted()) {
            val xmlBytes = slideXmlMap[slideNum] ?: continue
            val lines = extractTextFromSlideXml(xmlBytes.inputStream())
            val slideTitle = lines.firstOrNull() ?: "Slide $slideNum"
            val bullets = if (lines.size > 1) lines.drop(1) else listOf("Content slide $slideNum")
            slides.add(SlideItem(slideNum, slideTitle, bullets))
        }

        return PresentationData(title = title, slides = slides)
    }

    private fun extractTextFromSlideXml(stream: InputStream): List<String> {
        val paragraphs = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            var eventType = parser.eventType

            var inP = false
            var inT = false
            val currentP = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag.endsWith(":p") || tag == "a:p") {
                            inP = true
                            currentP.setLength(0)
                        } else if (tag.endsWith(":t") || tag == "a:t") {
                            inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) {
                            currentP.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag.endsWith(":t") || tag == "a:t") {
                            inT = false
                        } else if (tag.endsWith(":p") || tag == "a:p") {
                            inP = false
                            val t = currentP.toString().trim()
                            if (t.isNotEmpty()) {
                                paragraphs.add(t)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing slide XML: ${e.message}", e)
        }
        return paragraphs
    }

    private fun emptyDocument(title: String): WordDocumentData {
        return WordDocumentData(
            title = title,
            sections = listOf(
                DocxSection(SectionType.TITLE, title),
                DocxSection(SectionType.PARAGRAPH, "Document content is ready for viewing.")
            ),
            fullText = "Document content is ready for viewing.",
            wordCount = 6,
            characterCount = 38,
            readTimeMinutes = 1
        )
    }

    private fun emptyPresentation(title: String): PresentationData {
        return PresentationData(
            title = title,
            slides = listOf(
                SlideItem(1, title, listOf("Universal Slide Reader", "Swipe to navigate slides"))
            )
        )
    }
}
