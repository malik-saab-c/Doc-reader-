package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.DocumentItem
import com.example.model.DocumentType
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object DocumentStorageManager {
    private const val TAG = "DocumentStorageManager"

    fun ensureSampleDocuments(context: Context): List<DocumentItem> {
        val docsDir = File(context.filesDir, "documents")
        if (!docsDir.exists()) docsDir.mkdirs()

        // 1. Generate real offline multi-page PDF
        val pdfFile = File(docsDir, "Executive_Financial_Report_2026.pdf")
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            PdfEngine.createPdfDocument(
                outputFile = pdfFile,
                title = "All File Reader Executive Report",
                author = "Sir Ghulam Mustafa",
                contentSections = listOf(
                    "1. Executive Summary" to "This document demonstrates the ultra-fast native offline document engine created by Sir Ghulam Mustafa. The application operates with zero internet requirement and delivers instantaneous page rendering with hardware acceleration.",
                    "2. Performance & Footprint" to "Engine size is maintained strictly under 25 MB while providing support for PDF, DOCX, XLSX, PPTX, CSV, and code formats. All rendering operates locally on device without external cloud dependencies.",
                    "3. Architectural Superiority" to "Using native Android hardware rendering and streaming zip decoders, document loading achieves microsecond latency with complete memory safety.",
                    "4. Security & Privacy" to "Zero data leaves the device. Everything stays 100% encrypted and local on internal storage with complete compliance with privacy best practices."
                )
            )
        }

        // 2. Sample Financial CSV spreadsheet
        val csvFile = File(docsDir, "Global_Sales_Analytics_Q3.csv")
        if (!csvFile.exists()) {
            csvFile.writeText(
                """Region,Product,Units_Sold,Unit_Price,Total_Revenue,Profit_Margin,Status
North America,Enterprise Suite,1450,299.00,433550.00,38.5%,Approved
Europe,Cloud Storage Pro,2100,129.50,271950.00,42.0%,Approved
Asia Pacific,Security Gateway,3200,89.00,284800.00,45.2%,Approved
Latin America,Developer Tools,850,199.00,169150.00,31.4%,Pending
Middle East,Database Engine,620,450.00,279000.00,49.0%,Approved
Global Online,Mobile Subscriptions,8900,19.99,177911.00,64.0%,Active
"""
            )
        }

        // 3. Sample Word DOCX document (saved with .docx extension, parsed by DOCX engine)
        val docxFile = File(docsDir, "Project_Charter_and_Scope.docx")
        if (!docxFile.exists()) {
            docxFile.writeText(
                """# Project Charter & Systems Specification
Architect: Sir Ghulam Mustafa
Version: 3.2.0 • Status: Production Ready

## 1. Project Overview
The Universal File Studio provides a unified offline platform for reading, inspecting, and editing any file format. With pristine White Beautiful styling and micro-interactions, users experience zero-latency document access.

## 2. Key Capabilities
- Native vector PDF rendering with color filters and night mode
- Interactive spreadsheet grid with live SUM, AVERAGE, and MIN/MAX formulas
- DOCX and text reader with built-in offline Text-to-Speech (TTS)
- Fullscreen slide deck presentation mode
- Full code editor with line numbering and JSON formatter

## 3. Deployment Constraints
Strict adherence to APK size budget (< 25 MB) and 100% offline isolation without telemetry or cloud tracking.
"""
            )
        }

        // 4. Sample Presentation PPTX
        val pptxFile = File(docsDir, "Executive_Strategy_Deck.pptx")
        if (!pptxFile.exists()) {
            pptxFile.writeText(
                """# Slide 1: All File Reader Architecture
Executive presentation designed by developer Sir Ghulam Mustafa
Zero-bloat native processing engine
---
# Slide 2: Market Opportunities & Growth
- 98% faster startup compared to cloud-dependent readers
- Complete privacy protection with offline-first sandboxing
- Native hardware acceleration for PDF and spreadsheet grids
---
# Slide 3: Roadmap & Next Milestones
- Advanced formula builder for complex financial models
- Multi-column document layout customization
- Direct document signing and export tools
"""
            )
        }

        // 5. Sample Code JSON configuration
        val jsonFile = File(docsDir, "Engine_Config.json")
        if (!jsonFile.exists()) {
            jsonFile.writeText(
                """{
  "appName": "All File Reader",
  "developer": "Sir Ghulam Mustafa",
  "theme": "White Beautiful",
  "offlineMode": true,
  "hardwareAcceleration": true,
  "supportedFormats": [
    "pdf", "docx", "xlsx", "pptx", "csv", "txt", "json", "xml", "kt", "py"
  ],
  "engineSizeLimitMB": 25,
  "cachePolicy": "InMemoryLRU",
  "version": "1.0.0"
}"""
            )
        }

        // 6. Sample Markdown documentation
        val mdFile = File(docsDir, "User_Manual_and_Shortcuts.md")
        if (!mdFile.exists()) {
            mdFile.writeText(
                """# All File Reader User Guide
Welcome to the Universal File Reader & Editor engineered by developer **Sir Ghulam Mustafa**.

## Navigation Shortcuts
- **Pinch to Zoom**: Zoom in/out on PDF pages and high-resolution images
- **Formula Bar**: Tap any column header in CSV spreadsheets to inspect dynamic calculations (Sum, Average, Min, Max)
- **Listen Out Loud**: Tap the Speaker icon in Word/Docs to hear natural voice reading offline
- **Slideshow Mode**: Tap Present to auto-advance presentation slides
- **Create New**: Tap the floating '+' button to create brand new PDFs, spreadsheets, or notes.
"""
            )
        }

        return loadAllDocuments(context)
    }

    fun loadAllDocuments(context: Context): List<DocumentItem> {
        val docsDir = File(context.filesDir, "documents")
        if (!docsDir.exists()) docsDir.mkdirs()

        val files = docsDir.listFiles()?.toList() ?: emptyList()
        return files.map { file ->
            val ext = file.extension.lowercase()
            val type = DocumentType.fromExtension(ext)
            val snippet = try {
                if (ext in listOf("txt", "md", "json", "csv", "kt", "xml", "log", "py")) {
                    file.readLines().take(2).joinToString(" ")
                } else {
                    "${type.label} Document"
                }
            } catch (e: Exception) {
                "${type.label} Document"
            }

            DocumentItem(
                id = file.absolutePath,
                name = file.name,
                extension = ext,
                type = type,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                isFavorite = false,
                filePath = file.absolutePath,
                previewSnippet = snippet,
                isSample = true
            )
        }.sortedByDescending { it.lastModified }
    }

    fun importFileFromUri(context: Context, uri: Uri): DocumentItem? {
        return try {
            var fileName = "Imported_Document"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val docsDir = File(context.filesDir, "documents")
            if (!docsDir.exists()) docsDir.mkdirs()

            var destFile = File(docsDir, fileName)
            if (destFile.exists()) {
                val nameWithoutExt = destFile.nameWithoutExtension
                val ext = destFile.extension
                destFile = File(docsDir, "${nameWithoutExt}_${System.currentTimeMillis()}${if (ext.isNotEmpty()) ".$ext" else ""}")
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val ext = destFile.extension.lowercase()
            val type = DocumentType.fromExtension(ext)

            DocumentItem(
                id = destFile.absolutePath,
                name = destFile.name,
                extension = ext,
                type = type,
                sizeBytes = destFile.length(),
                lastModified = destFile.lastModified(),
                isFavorite = false,
                filePath = destFile.absolutePath,
                previewSnippet = "Imported File",
                isSample = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error importing file: ${e.message}", e)
            null
        }
    }

    fun saveNewTextFile(context: Context, name: String, content: String, extension: String = "txt"): DocumentItem? {
        return try {
            val docsDir = File(context.filesDir, "documents")
            if (!docsDir.exists()) docsDir.mkdirs()

            val fullName = if (name.endsWith(".$extension")) name else "$name.$extension"
            val file = File(docsDir, fullName)
            file.writeText(content)

            DocumentItem(
                id = file.absolutePath,
                name = file.name,
                extension = extension,
                type = DocumentType.fromExtension(extension),
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                isFavorite = false,
                filePath = file.absolutePath,
                previewSnippet = content.take(60),
                isSample = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving text file: ${e.message}", e)
            null
        }
    }

    fun deleteDocument(item: DocumentItem): Boolean {
        return try {
            val file = File(item.filePath)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}", e)
            false
        }
    }
}
