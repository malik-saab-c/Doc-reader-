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

    init {
        loadDocuments()
    }

    fun loadDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = DocumentStorageManager.ensureSampleDocuments(getApplication())
            _documents.value = docs
        }
    }

    fun dismissSplash() {
        _showSplash.value = false
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
            val updated = DocumentStorageManager.loadAllDocuments(getApplication())
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
                val updated = DocumentStorageManager.loadAllDocuments(getApplication())
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
                val updated = DocumentStorageManager.loadAllDocuments(getApplication())
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
                val updated = DocumentStorageManager.loadAllDocuments(getApplication())
                _documents.value = updated
                _activeDocument.value = doc
            }
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
                val updated = DocumentStorageManager.loadAllDocuments(getApplication())
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
                val updated = DocumentStorageManager.loadAllDocuments(getApplication())
                _documents.value = updated
                _activeDocument.value = doc.copy(
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
        }
    }
}
