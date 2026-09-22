package com.example

import com.example.engine.OfficeDocumentEngine
import com.example.engine.SpreadsheetEngine
import com.example.model.DocumentType
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testDocumentTypeMapping() {
        assertEquals(DocumentType.PDF, DocumentType.fromExtension("pdf"))
        assertEquals(DocumentType.WORD, DocumentType.fromExtension("docx"))
        assertEquals(DocumentType.WORD, DocumentType.fromExtension("doc"))
        assertEquals(DocumentType.SPREADSHEET, DocumentType.fromExtension("xlsx"))
        assertEquals(DocumentType.SPREADSHEET, DocumentType.fromExtension("csv"))
        assertEquals(DocumentType.PRESENTATION, DocumentType.fromExtension("pptx"))
        assertEquals(DocumentType.CODE_TEXT, DocumentType.fromExtension("kt"))
        assertEquals(DocumentType.CODE_TEXT, DocumentType.fromExtension("json"))
        assertEquals(DocumentType.IMAGE, DocumentType.fromExtension("png"))
        assertEquals(DocumentType.IMAGE, DocumentType.fromExtension("jpg"))
    }

    @Test
    fun testCsvParserAndStats() {
        val sampleCsv = """Product,Price,Quantity
Laptop,999.0,5
Mouse,25.0,20
Keyboard,75.0,10"""

        val data = SpreadsheetEngine.parseCsvString(sampleCsv)
        assertEquals(3, data.headers.size)
        assertEquals("Product", data.headers[0])
        assertEquals("Price", data.headers[1])
        assertEquals("Quantity", data.headers[2])
        assertEquals(3, data.rows.size)

        // Test statistics on Price column (index 1)
        val stats = SpreadsheetEngine.computeColumnStats(data, 1)
        assertNotNull(stats.sum)
        assertEquals(1099.0, stats.sum!!, 0.01)
        assertEquals(25.0, stats.min!!, 0.01)
        assertEquals(999.0, stats.max!!, 0.01)
    }

    @Test
    fun testOfficeTextParser() {
        val rawText = """# Title of Document
## Section One
- First bullet item
- Second bullet item
Regular paragraph content."""

        val doc = OfficeDocumentEngine.parsePlainText(rawText, "TestDoc")
        assertEquals("TestDoc", doc.title)
        assertTrue(doc.sections.isNotEmpty())
        assertEquals(doc.sections[0].text, "Title of Document")
        assertTrue(doc.wordCount > 0)
    }
}
