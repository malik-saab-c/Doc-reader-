package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.DocxSection
import com.example.engine.OfficeDocumentEngine
import com.example.engine.SectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfficeDocumentEngineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testWordDocumentFromPlainTextFallback() {
        val raw = """
            # Quarterly Review
            ## Section 1: Growth
            Our performance this quarter exceeded targets.
            - 25% increase in user retention
            - 40% reduction in app startup time
        """.trimIndent()

        val file = File(context.cacheDir, "sample_review.docx")
        file.writeText(raw)

        val doc = OfficeDocumentEngine.loadWordDocument(
            context = context,
            pathOrUri = file.absolutePath,
            title = "Quarterly Review"
        )

        assertNotNull(doc)
        assertEquals("Quarterly Review", doc.title)
        assertTrue("Sections should not be empty", doc.sections.isNotEmpty())
        assertEquals(SectionType.TITLE, doc.sections[0].type)
        assertEquals("Quarterly Review", doc.sections[0].text)
        assertTrue(doc.wordCount > 10)
    }

    @Test
    fun testRealDocxZipParsing() {
        // Construct a real DOCX ZIP structure in memory
        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr><w:pStyle w:val="Title"/></w:pPr>
                        <w:r><w:t>Project Alpha Specification</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                        <w:r><w:t>1. Architecture Overview</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:r><w:t>This is a paragraph with high performance streaming.</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:pPr><w:pStyle w:val="ListParagraph"/></w:pPr>
                        <w:r><w:t>First bullet item</w:t></w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val docxFile = File(context.cacheDir, "test_real.docx")
        docxFile.writeBytes(baos.toByteArray())

        val result = OfficeDocumentEngine.loadWordDocument(
            context = context,
            pathOrUri = docxFile.absolutePath,
            title = "Project Alpha"
        )

        assertNotNull(result)
        assertEquals("Project Alpha", result.title)
        assertTrue(result.sections.size >= 4)
        assertEquals(SectionType.TITLE, result.sections[0].type)
        assertEquals("Project Alpha Specification", result.sections[0].text)
        assertEquals(SectionType.HEADING1, result.sections[1].type)
        assertEquals("1. Architecture Overview", result.sections[1].text)
        assertEquals(SectionType.PARAGRAPH, result.sections[2].type)
        assertEquals(SectionType.BULLET_ITEM, result.sections[3].type)
        assertEquals("First bullet item", result.sections[3].text)
    }

    @Test
    fun testRealPptxZipParsingMultipleSlides() {
        // Construct a real PPTX ZIP structure with 2 slides in memory
        val slide1Xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                   xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:txBody>
                                <a:p><a:r><a:t>Slide 1: Executive Deck</a:t></a:r></a:p>
                                <a:p><a:r><a:t>Key capability item 1</a:t></a:r></a:p>
                                <a:p><a:r><a:t>Key capability item 2</a:t></a:r></a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val slide2Xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                   xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
                <p:cSld>
                    <p:spTree>
                        <p:sp>
                            <p:txBody>
                                <a:p><a:r><a:t>Slide 2: Roadmap and Strategy</a:t></a:r></a:p>
                                <a:p><a:r><a:t>Milestone Q1: Mobile release</a:t></a:r></a:p>
                                <a:p><a:r><a:t>Milestone Q2: Enterprise scale</a:t></a:r></a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:cSld>
            </p:sld>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
            zos.write(slide1Xml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ppt/slides/slide2.xml"))
            zos.write(slide2Xml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val pptxFile = File(context.cacheDir, "test_multi_slide.pptx")
        pptxFile.writeBytes(baos.toByteArray())

        val presentation = OfficeDocumentEngine.loadPresentation(
            context = context,
            pathOrUri = pptxFile.absolutePath,
            title = "Executive Deck"
        )

        assertNotNull(presentation)
        assertEquals(2, presentation.slides.size)

        // Slide 1
        val s1 = presentation.slides[0]
        assertEquals(1, s1.slideNumber)
        assertEquals("Slide 1: Executive Deck", s1.title)
        assertEquals(2, s1.bulletPoints.size)
        assertEquals("Key capability item 1", s1.bulletPoints[0])
        assertEquals("Key capability item 2", s1.bulletPoints[1])

        // Slide 2
        val s2 = presentation.slides[1]
        assertEquals(2, s2.slideNumber)
        assertEquals("Slide 2: Roadmap and Strategy", s2.title)
        assertEquals(2, s2.bulletPoints.size)
        assertEquals("Milestone Q1: Mobile release", s2.bulletPoints[0])
        assertEquals("Milestone Q2: Enterprise scale", s2.bulletPoints[1])
    }

    @Test
    fun testLegacyBinaryDocNeverCrashes() {
        // Create an OLE2 binary header followed by binary and UTF-16LE stream data
        val ole2Header = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
        )

        val text1 = "Annual Operating Plan 2026"
        val text2 = "Revenue targets achieved across all business units."
        val text3 = "Capital expenditure remains on forecast."

        val stream = ByteArrayOutputStream()
        stream.write(ole2Header)
        // Write binary padding
        stream.write(ByteArray(512) { 0x00 })

        // Write UTF-16LE characters
        for (ch in text1) {
            stream.write(ch.code and 0xFF)
            stream.write((ch.code shr 8) and 0xFF)
        }
        stream.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))

        for (ch in text2) {
            stream.write(ch.code and 0xFF)
            stream.write((ch.code shr 8) and 0xFF)
        }
        stream.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))

        for (ch in text3) {
            stream.write(ch.code and 0xFF)
            stream.write((ch.code shr 8) and 0xFF)
        }

        val binaryDocFile = File(context.cacheDir, "legacy_plan.doc")
        binaryDocFile.writeBytes(stream.toByteArray())

        val result = OfficeDocumentEngine.loadWordDocument(
            context = context,
            pathOrUri = binaryDocFile.absolutePath,
            title = "Legacy Plan"
        )

        assertNotNull(result)
        assertTrue(result.sections.isNotEmpty())
        assertTrue("Extracted text should contain target words", result.fullText.contains("Annual Operating Plan"))
    }

    @Test
    fun testLegacyBinaryPptNeverCrashes() {
        val ole2Header = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
        )

        val stream = ByteArrayOutputStream()
        stream.write(ole2Header)
        stream.write(ByteArray(256) { 0x05 })

        // Write ASCII chunks
        stream.write("Welcome to Financial Overview\r\n".toByteArray(Charsets.US_ASCII))
        stream.write(ByteArray(32) { 0x00 })
        stream.write("Q1 operating income grew by 18 percent\r\n".toByteArray(Charsets.US_ASCII))
        stream.write(ByteArray(32) { 0x00 })
        stream.write("International expansion scheduled for Q3\r\n".toByteArray(Charsets.US_ASCII))

        val binaryPptFile = File(context.cacheDir, "legacy_presentation.ppt")
        binaryPptFile.writeBytes(stream.toByteArray())

        val presentation = OfficeDocumentEngine.loadPresentation(
            context = context,
            pathOrUri = binaryPptFile.absolutePath,
            title = "Financial Overview"
        )

        assertNotNull(presentation)
        assertTrue(presentation.slides.isNotEmpty())
        val firstSlide = presentation.slides[0]
        assertNotNull(firstSlide.title)
    }

    @Test
    fun testFastWordCounter() {
        val text = "  The quick brown   fox jumps\nover the lazy dog.  "
        val count = OfficeDocumentEngine.countWordsFast(text)
        assertEquals(9, count)

        assertEquals(0, OfficeDocumentEngine.countWordsFast(""))
        assertEquals(0, OfficeDocumentEngine.countWordsFast("   \n\t  "))
        assertEquals(1, OfficeDocumentEngine.countWordsFast("Word"))
    }

    @Test
    fun testAutomaticDocumentScanning() {
        // Create simulated documents of different formats: PDF, PPTX, DOCX, CSV
        val docsDir = File(context.filesDir, "documents")
        if (!docsDir.exists()) docsDir.mkdirs()

        val sampleDocx = File(docsDir, "test_scan_word.docx")
        sampleDocx.writeText("# Test Word Doc")

        val samplePptx = File(docsDir, "test_scan_presentation.pptx")
        samplePptx.writeText("# Slide 1: Presentation Deck")

        val samplePdf = File(docsDir, "test_scan_report.pdf")
        samplePdf.writeText("%PDF-1.4 simulated pdf data")

        val scannedDocs = com.example.engine.DocumentStorageManager.scanAllReadableDocuments(context)
        assertTrue("Scanned documents should not be empty", scannedDocs.isNotEmpty())

        val foundWord = scannedDocs.any { it.name == "test_scan_word.docx" && it.type == com.example.model.DocumentType.WORD }
        val foundPpt = scannedDocs.any { it.name == "test_scan_presentation.pptx" && it.type == com.example.model.DocumentType.PRESENTATION }
        val foundPdf = scannedDocs.any { it.name == "test_scan_report.pdf" && it.type == com.example.model.DocumentType.PDF }

        assertTrue("Should automatically find DOCX file", foundWord)
        assertTrue("Should automatically find PPTX file", foundPpt)
        assertTrue("Should automatically find PDF file", foundPdf)
    }

    @Test
    fun testStoragePermissionHelperIntent() {
        val intent = com.example.util.StoragePermissionHelper.createManageStorageIntent(context)
        assertNotNull("Storage intent must not be null", intent)
        assertNotNull("Storage intent action must not be null", intent.action)
    }
}
