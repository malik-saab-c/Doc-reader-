package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class DocumentType(
    val label: String,
    val primaryColor: Color,
    val containerColor: Color,
    val extensions: List<String>
) {
    PDF("PDF", PdfRed, PdfRedLight, listOf("pdf")),
    SPREADSHEET("Excel / CSV", ExcelGreen, ExcelGreenLight, listOf("csv", "xlsx", "xls", "tsv")),
    WORD("Word / Docs", WordBlue, WordBlueLight, listOf("docx", "doc", "rtf", "odt")),
    PRESENTATION("PowerPoint", PptOrange, PptOrangeLight, listOf("pptx", "ppt", "odp")),
    CODE_TEXT("Text & Code", CodePurple, CodePurpleLight, listOf("txt", "md", "json", "xml", "html", "kt", "java", "py", "js", "ts", "css", "sql", "log", "yaml", "yml", "ini", "properties")),
    IMAGE("Images", ImageCyan, ImageCyanLight, listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg")),
    OTHER("All Files", BrandPrimary, SoftSurface, emptyList());

    companion object {
        fun fromExtension(ext: String): DocumentType {
            val cleanExt = ext.lowercase().trimStart('.')
            return entries.firstOrNull { it.extensions.contains(cleanExt) } ?: OTHER
        }
    }
}

data class DocumentItem(
    val id: String,
    val name: String,
    val extension: String,
    val type: DocumentType,
    val sizeBytes: Long,
    val lastModified: Long,
    val isFavorite: Boolean = false,
    val filePath: String,
    val previewSnippet: String = "",
    val isSample: Boolean = false,
    val rawTextContent: String? = null
) {
    val formattedSize: String
        get() {
            return when {
                sizeBytes < 1024 -> "$sizeBytes B"
                sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
                else -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
            }
        }

    val formattedDate: String
        get() {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(lastModified))
        }
}
