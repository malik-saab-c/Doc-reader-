package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.DocumentStorageManager
import com.example.engine.PdfEngine
import com.example.engine.SpreadsheetData
import com.example.engine.SpreadsheetEngine
import com.example.model.DocumentItem
import com.example.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption(val label: String) {
    DATE_NEWEST("Newest"),
    DATE_OLDEST("Oldest"),
    NAME_AZ("Name (A-Z)"),
    NAME_ZA("Name (Z-A)"),
    SIZE_LARGEST("Largest Size"),
    SIZE_SMALLEST("Smallest Size")
}

class FileAppViewModel(application: Application) : AndroidViewModel(application) {

    private val _documents = MutableStateFlow<List<DocumentItem>>(emptyList())
    val documents: StateFlow<List<DocumentItem>> = _documents.asStateFlow()

    private val _activeDocument = MutableStateFlow<DocumentItem?>(null)
    val activeDocument: StateFlow<DocumentItem?> = _activeDocument.asStateFlow()

    private val _selectedCategory = MutableStateFlow<DocumentType?>(null)
    val selectedCategory: StateFlow<DocumentType?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortBy = MutableStateFlow(SortOption.DATE_NEWEST)
    val sortBy: StateFlow<SortOption> = _sortBy.asStateFlow()

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites.asStateFlow()

    private val _showSplash = MutableStateFlow(true)
    val showSplash: StateFlow<Boolean> = _showSplash.asStateFlow()

    private val _isPrivacyAgreed = MutableStateFlow(
        com.example.util.PrivacyConsentManager.isPrivacyPolicyAgreed(application)
    )
    val isPrivacyAgreed: StateFlow<Boolean> = _isPrivacyAgreed.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        loadDocuments()
    }

    fun loadDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val docs = DocumentStorageManager.scanAllReadableDocuments(getApplication())
                _documents.value = docs
            } catch (e: Exception) {
                android.util.Log.e("FileAppViewModel", "Error scanning documents: ${e.message}", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun refreshDocuments() {
        loadDocuments()
    }

    fun dismissSplash() {
        _showSplash.value = false
    }

    fun agreeToPrivacyPolicy() {
        com.example.util.PrivacyConsentManager.setPrivacyPolicyAgreed(getApplication(), true)
        _isPrivacyAgreed.value = true
    }

    fun replaySplash() {
        _showSplash.value = true
    }

    fun setCategory(category: DocumentType?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortBy(sort: SortOption) {
        _sortBy.value = sort
    }

    fun toggleFavoritesOnly() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
    }

    fun openDocument(item: DocumentItem) {
        _activeDocument.value = item
    }

    fun closeActiveDocument() {
        _activeDocument.value = null
    }

    fun toggleFavorite(item: DocumentItem) {
        _documents.value = _documents.value.map {
            if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
        }
    }

    fun deleteDocument(item: DocumentItem) {
        viewModelScope.launch(Dispatchers.IO) {
            DocumentStorageManager.deleteDocument(item)
            val updated = DocumentStorageManager.scanAllReadableDocuments(getApplication())
            _documents.value = updated
            if (_activeDocument.value?.id == item.id) {
                _activeDocument.value = null
            }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = DocumentStorageManager.importFileFromUri(getApplication(), uri)
            if (doc != null) {
                val updated = DocumentStorageManager.scanAllReadableDocuments(getApplication())
                _documents.value = updated
                _activeDocument.value = doc
            }
        }
    }

    fun createNewPdf(title: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanTitle = title.ifBlank { "New_Document" }.replace(" ", "_")
            val docsDir = File(getApplication<Application>().filesDir, "documents")
            val pdfFile = File(docsDir, "$cleanTitle.pdf")

            val success = PdfEngine.createPdfDocument(
                outputFile = pdfFile,
                title = title.ifBlank { "New Document" },
                author = "Sir Ghulam Mustafa",
                contentSections = listOf(
                    "Overview" to body.ifBlank { "Generated using All File Reader Universal Engine." }
                )
            )

            if (success) {
                val updated = DocumentStorageManager.scanAllReadableDocuments(getApplication())
                _documents.value = updated
                val created = updated.firstOrNull { it.filePath == pdfFile.absolutePath }
                if (created != null) _activeDocument.value = created
            }
        }
    }

    fun createNewSpreadsheet(name: String, headers: List<String>, rows: List<List<String>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = name.ifBlank { "New_Spreadsheet" }.replace(" ", "_")
            val csvContent = SpreadsheetEngine.exportToCsv(SpreadsheetData(headers, rows))
            val doc = DocumentStorageManager.saveNewTextFile(
                context = getApplication(),
                name = cleanName,
                content = csvContent,
                extension = "csv"
            )
            if (doc != null) {
                val updated = DocumentStorageManager.scanAllReadableDocuments(getApplication())
                _documents.value = updated
                _activeDocument.value = doc
            }
        }
    }

    fun createNewPresentation(name: String, title: String, subtitle: String, bullets: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = name.ifBlank { "New_Presentation" }.replace(" ", "_")
            val docsDir = File(getApplication<Application>().filesDir, "documents")
            if (!docsDir.exists()) docsDir.mkdirs()
            val pptxFile = File(docsDir, "$cleanName.pptx")
            val initialBullets = bullets.ifEmpty {
                listOf(
                    "Professional Slide Layout Architecture",
                    "Add text, images, tables, and notes seamlessly",
                    "Fully compatible with PowerPoint & Google Slides"
                )
            }
            val initialSlide = com.example.engine.SlideItem(
                slideNumber = 1,
                title = title.ifBlank { "Presentation Overview" },
                subtitle = subtitle.ifBlank { "Created with Universal Slide Studio" },
                bulletPoints = initialBullets,
                categoryTag = "SLIDE 01",
                layoutType = com.example.engine.SlideLayout.TITLE_SLIDE,
                elements = com.example.engine.OfficeDocumentEngine.buildDefaultSlideElements(
                    title = title.ifBlank { "Presentation Overview" },
                    subtitle = subtitle.ifBlank { "Created with Universal Slide Studio" },
                    bulletPoints = initialBullets,
                    layout = com.example.engine.SlideLayout.TITLE_SLIDE
                )
            )
            val presData = com.example.engine.PresentationData(
                title = title.ifBlank { "New Presentation" },
                slides = listOf(initialSlide)
            )
            val pptxBytes = com.example.engine.OfficeDocumentEngine.exportToPptxZip(presData)
            pptxFile.writeBytes(pptxBytes)
            val updated = DocumentStorageManager.scanAllReadableDocuments(getApplication())
            _documents.value = updated
            val created = updated.firstOrNull { it.filePath == pptxFile.absolutePath }
            if (created != null) _activeDocument.value = created
        }
    }

    fun createNewTextFile(name: String, content: String, ext: String = "txt") {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = name.ifBlank { "New_Note" }.replace(" ", "_")
            val doc = DocumentStorageManager.saveNewTextFile(
                context = getApplication(),
                name = cleanName,
                content = content,
                extension = ext
            )
            if (doc != null) {
                val updated = DocumentStorageManager.scanAllReadableDocuments(getApplication())
                _documents.value = updated
                _activeDocument.value = doc
            }
        }
    }

    fun saveEditedContent(doc: DocumentItem, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(doc.filePath)
            if (file.exists()) {
                file.writeText(newContent)
                val updated = DocumentStorageManager.scanAllReadableDocuments(getApplication())
                _documents.value = updated
                _activeDocument.value = doc.copy(
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
        }
    }

    fun saveEditedBinaryContent(doc: DocumentItem, newBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(doc.filePath)
            if (file.exists()) {
                file.writeBytes(newBytes)
                val updated = DocumentStorageManager.scanAllReadableDocuments(getApplication())
                _documents.value = updated
                _activeDocument.value = doc.copy(
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
        }
    }
}
