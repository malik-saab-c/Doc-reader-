package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.model.DocumentType
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FileAppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: FileAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Mobile Ads (AdMob) SDK
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) { status ->
                android.util.Log.d("MainActivity", "Google Mobile Ads initialized: $status")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize AdMob: ${e.message}", e)
        }

        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                val showSplash by viewModel.showSplash.collectAsState()
                val activeDoc by viewModel.activeDocument.collectAsState()
                val documents by viewModel.documents.collectAsState()
                val selectedCategory by viewModel.selectedCategory.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val sortBy by viewModel.sortBy.collectAsState()
                val showFavoritesOnly by viewModel.showOnlyFavorites.collectAsState()
                val isScanning by viewModel.isScanning.collectAsState()

                Crossfade(
                    targetState = Pair(showSplash, activeDoc),
                    label = "screen_transition",
                    modifier = Modifier.fillMaxSize()
                ) { (splash, doc) ->
                    when {
                        splash -> {
                            SplashScreen(
                                onFinished = { viewModel.dismissSplash() }
                            )
                        }

                        doc != null -> {
                            BackHandler {
                                viewModel.closeActiveDocument()
                            }

                            when (doc.type) {
                                DocumentType.PDF -> {
                                    PdfViewerScreen(
                                        document = doc,
                                        onBack = { viewModel.closeActiveDocument() }
                                    )
                                }

                                DocumentType.SPREADSHEET -> {
                                    SpreadsheetViewerScreen(
                                        document = doc,
                                        onBack = { viewModel.closeActiveDocument() },
                                        onSaveContent = { newCsv ->
                                            viewModel.saveEditedContent(doc, newCsv)
                                        }
                                    )
                                }

                                DocumentType.WORD -> {
                                    DocumentReaderScreen(
                                        document = doc,
                                        onBack = { viewModel.closeActiveDocument() }
                                    )
                                }

                                DocumentType.PRESENTATION -> {
                                    PresentationViewerScreen(
                                        document = doc,
                                        onBack = { viewModel.closeActiveDocument() },
                                        onSaveContent = { newContent ->
                                            viewModel.saveEditedContent(doc, newContent)
                                        },
                                        onSaveBinaryContent = { newBytes ->
                                            viewModel.saveEditedBinaryContent(doc, newBytes)
                                        }
                                    )
                                }

                                DocumentType.CODE_TEXT -> {
                                    CodeEditorScreen(
                                        document = doc,
                                        onBack = { viewModel.closeActiveDocument() },
                                        onSaveContent = { newText ->
                                            viewModel.saveEditedContent(doc, newText)
                                        }
                                    )
                                }

                                DocumentType.IMAGE -> {
                                    ImageViewerScreen(
                                        document = doc,
                                        onBack = { viewModel.closeActiveDocument() }
                                    )
                                }

                                DocumentType.OTHER -> {
                                    CodeEditorScreen(
                                        document = doc,
                                        onBack = { viewModel.closeActiveDocument() },
                                        onSaveContent = { newText ->
                                            viewModel.saveEditedContent(doc, newText)
                                        }
                                    )
                                }
                            }
                        }

                        else -> {
                            FileExplorerScreen(
                                documents = documents,
                                selectedCategory = selectedCategory,
                                searchQuery = searchQuery,
                                sortBy = sortBy,
                                showFavoritesOnly = showFavoritesOnly,
                                isScanning = isScanning,
                                onRefreshDocuments = { viewModel.refreshDocuments() },
                                onSelectCategory = { viewModel.setCategory(it) },
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                onSortChange = { viewModel.setSortBy(it) },
                                onToggleFavoritesOnly = { viewModel.toggleFavoritesOnly() },
                                onOpenDocument = { viewModel.openDocument(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onDeleteDocument = { viewModel.deleteDocument(it) },
                                onImportUri = { viewModel.importFile(it) },
                                onCreatePdf = { title, body -> viewModel.createNewPdf(title, body) },
                                onCreateSpreadsheet = { name, headers, rows ->
                                    viewModel.createNewSpreadsheet(name, headers, rows)
                                },
                                onCreatePresentation = { name, title, subtitle, bullets ->
                                    viewModel.createNewPresentation(name, title, subtitle, bullets)
                                },
                                onCreateText = { name, content, ext ->
                                    viewModel.createNewTextFile(name, content, ext)
                                },
                                onReplaySplash = { viewModel.replaySplash() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        try {
            val uri = intent?.data
            if (uri != null) {
                viewModel.importFile(uri)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error handling external intent: ${e.message}", e)
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}

