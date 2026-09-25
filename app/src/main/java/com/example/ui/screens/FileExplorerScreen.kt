package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.DocumentItem
import com.example.model.DocumentType
import com.example.ui.components.AdMobBanner
import com.example.ui.theme.*
import com.example.util.StoragePermissionHelper
import com.example.viewmodel.SortOption
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    documents: List<DocumentItem>,
    selectedCategory: DocumentType?,
    searchQuery: String,
    sortBy: SortOption,
    showFavoritesOnly: Boolean,
    isScanning: Boolean = false,
    onRefreshDocuments: () -> Unit = {},
    onSelectCategory: (DocumentType?) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onOpenDocument: (DocumentItem) -> Unit,
    onToggleFavorite: (DocumentItem) -> Unit,
    onDeleteDocument: (DocumentItem) -> Unit,
    onImportUri: (Uri) -> Unit,
    onCreatePdf: (title: String, body: String) -> Unit,
    onCreateSpreadsheet: (name: String, headers: List<String>, rows: List<List<String>>) -> Unit,
    onCreatePresentation: (name: String, title: String, subtitle: String, bullets: List<String>) -> Unit = { _, _, _, _ -> },
    onCreateText: (name: String, content: String, ext: String) -> Unit,
    onReplaySplash: () -> Unit
) {
    val context = LocalContext.current
    var showCreateMenu by remember { mutableStateOf(false) }
    var activeCreateDialog by remember { mutableStateOf<CreateDocType?>(null) }
    var inspectItem by remember { mutableStateOf<DocumentItem?>(null) }
    var showAppInfoDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Storage permission tracking
    var hasStorageAccess by remember {
        mutableStateOf(StoragePermissionHelper.hasFullStorageAccess(context))
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted || StoragePermissionHelper.hasFullStorageAccess(context)) {
            hasStorageAccess = true
            onRefreshDocuments()
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = StoragePermissionHelper.hasFullStorageAccess(context)
        hasStorageAccess = granted
        if (granted) {
            onRefreshDocuments()
        }
    }

    fun requestCompleteStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                manageStorageLauncher.launch(StoragePermissionHelper.createManageStorageIntent(context))
            } catch (e: Exception) {
                legacyStoragePermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    // System File Picker for opening external documents offline
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportUri(uri)
        }
    }

    // Filter documents
    val filteredDocs = remember(documents, selectedCategory, searchQuery, showFavoritesOnly, sortBy) {
        var list = documents.filter { doc ->
            val matchesCategory = selectedCategory == null || doc.type == selectedCategory
            val matchesSearch = searchQuery.isBlank() || doc.name.contains(searchQuery, ignoreCase = true)
            val matchesFavorites = !showFavoritesOnly || doc.isFavorite
            matchesCategory && matchesSearch && matchesFavorites
        }

        list = when (sortBy) {
            SortOption.DATE_NEWEST -> list.sortedByDescending { it.lastModified }
            SortOption.DATE_OLDEST -> list.sortedBy { it.lastModified }
            SortOption.NAME_AZ -> list.sortedBy { it.name.lowercase() }
            SortOption.NAME_ZA -> list.sortedByDescending { it.name.lowercase() }
            SortOption.SIZE_LARGEST -> list.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_SMALLEST -> list.sortedBy { it.sizeBytes }
        }
        list
    }

    val totalStorageBytes = remember(documents) { documents.sumOf { it.sizeBytes } }
    val formattedTotalStorage = remember(totalStorageBytes) {
        when {
            totalStorageBytes < 1024 -> "$totalStorageBytes B"
            totalStorageBytes < 1024 * 1024 -> String.format("%.1f KB", totalStorageBytes / 1024.0)
            else -> String.format("%.2f MB", totalStorageBytes / (1024.0 * 1024.0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BrandAccentLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Logo",
                                    tint = BrandAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "All File Reader",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "By Sir Ghulam Mustafa • 100% Offline",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // Refresh / Scan documents button
                    IconButton(
                        onClick = {
                            if (!hasStorageAccess) {
                                requestCompleteStorageAccess()
                            }
                            onRefreshDocuments()
                        },
                        modifier = Modifier.testTag("scan_storage_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = BrandAccent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Storage",
                                tint = BrandAccent
                            )
                        }
                    }

                    // Replay splash screen animation
                    IconButton(onClick = onReplaySplash, modifier = Modifier.testTag("replay_splash_button")) {
                        Icon(
                            imageVector = Icons.Default.Animation,
                            contentDescription = "Replay Intro Animation",
                            tint = BrandAccent
                        )
                    }

                    // App developer & info
                    IconButton(onClick = { showAppInfoDialog = true }, modifier = Modifier.testTag("app_info_button")) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Developer & App Info",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureWhite,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Secondary actions when FAB menu expanded
                AnimatedVisibility(visible = showCreateMenu) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        // Open Device File
                        ExtendedFloatingActionButton(
                            onClick = {
                                showCreateMenu = false
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            text = { Text("Open From Device Storage") },
                            containerColor = PureWhite,
                            contentColor = TextPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        )

                        // New PDF
                        ExtendedFloatingActionButton(
                            onClick = {
                                showCreateMenu = false
                                activeCreateDialog = CreateDocType.PDF
                            },
                            icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PdfRed) },
                            text = { Text("Create New PDF") },
                            containerColor = PureWhite,
                            contentColor = TextPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        )

                        // New Spreadsheet
                        ExtendedFloatingActionButton(
                            onClick = {
                                showCreateMenu = false
                                activeCreateDialog = CreateDocType.SPREADSHEET
                            },
                            icon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = ExcelGreen) },
                            text = { Text("Create New Spreadsheet") },
                            containerColor = PureWhite,
                            contentColor = TextPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        )

                        // New Presentation (.pptx)
                        ExtendedFloatingActionButton(
                            onClick = {
                                showCreateMenu = false
                                activeCreateDialog = CreateDocType.PRESENTATION
                            },
                            icon = { Icon(Icons.Default.Slideshow, contentDescription = null, tint = PptOrange) },
                            text = { Text("Create Presentation (.pptx)") },
                            containerColor = PureWhite,
                            contentColor = TextPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        )

                        // New Text File
                        ExtendedFloatingActionButton(
                            onClick = {
                                showCreateMenu = false
                                activeCreateDialog = CreateDocType.TEXT_NOTE
                            },
                            icon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = CodePurple) },
                            text = { Text("Create New Text / Code") },
                            containerColor = PureWhite,
                            contentColor = TextPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        )
                    }
                }

                // Main FAB
                FloatingActionButton(
                    onClick = { showCreateMenu = !showCreateMenu },
                    containerColor = BrandPrimary,
                    contentColor = PureWhite,
                    shape = CircleShape,
                    modifier = Modifier.testTag("main_fab_button")
                ) {
                    Icon(
                        imageVector = if (showCreateMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Create or Open Document"
                    )
                }
            }
        },
        bottomBar = {
            AdMobBanner()
        },
        containerColor = PureWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scanning progress indicator
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = BrandAccent,
                    trackColor = CardBorderSubtle
                )
            }

            // Storage permission prompt card if full access not granted
            if (!hasStorageAccess) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("storage_permission_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftSurface),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PureWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = BrandAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Full Storage Access",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Find & load all PDF, Word, PowerPoint, and Excel files on this phone automatically.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { requestCompleteStorageAccess() },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("grant_storage_button")
                        ) {
                            Text("Grant", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search files by name or type...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandAccent,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = OffWhite,
                    unfocusedContainerColor = OffWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("file_search_input")
            )

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // All Chip
                item {
                    FilterChip(
                        selected = selectedCategory == null && !showFavoritesOnly,
                        onClick = {
                            if (showFavoritesOnly) onToggleFavoritesOnly()
                            onSelectCategory(null)
                        },
                        label = { Text("All Files (${documents.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPrimary,
                            selectedLabelColor = PureWhite
                        )
                    )
                }

                // Starred / Favorites Chip
                item {
                    FilterChip(
                        selected = showFavoritesOnly,
                        onClick = onToggleFavoritesOnly,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = if (showFavoritesOnly) GoldAccent else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Favorites") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldAccentLight,
                            selectedLabelColor = GoldAccent
                        )
                    )
                }

                // Specific Category Chips
                items(DocumentType.entries.filter { it != DocumentType.OTHER }) { cat ->
                    val count = documents.count { it.type == cat }
                    FilterChip(
                        selected = selectedCategory == cat && !showFavoritesOnly,
                        onClick = {
                            if (showFavoritesOnly) onToggleFavoritesOnly()
                            onSelectCategory(if (selectedCategory == cat) null else cat)
                        },
                        label = { Text("${cat.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cat.containerColor,
                            selectedLabelColor = cat.primaryColor
                        )
                    )
                }
            }

            // Stats & Sorting Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredDocs.size} documents • $formattedTotalStorage",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Box {
                    TextButton(
                        onClick = { showSortMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = sortBy.label, fontSize = 12.sp, color = TextSecondary)
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortChange(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Divider(color = CardBorderSubtle, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Document List
            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "No documents found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap + to create a new PDF, Spreadsheet, or open files from storage",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentItemCard(
                            document = doc,
                            onClick = { onOpenDocument(doc) },
                            onToggleFavorite = { onToggleFavorite(doc) },
                            onInspect = { inspectItem = doc },
                            onDelete = { onDeleteDocument(doc) }
                        )
                    }
                }
            }
        }
    }

    // Document Inspector Sheet
    inspectItem?.let { doc ->
        DocumentInspectorDialog(
            document = doc,
            onDismiss = { inspectItem = null },
            onOpen = {
                inspectItem = null
                onOpenDocument(doc)
            },
            onDelete = {
                onDeleteDocument(doc)
                inspectItem = null
            }
        )
    }

    // Create Document Dialog
    activeCreateDialog?.let { type ->
        DocumentCreatorDialog(
            type = type,
            onDismiss = { activeCreateDialog = null },
            onCreatePdf = onCreatePdf,
            onCreateSpreadsheet = onCreateSpreadsheet,
            onCreatePresentation = onCreatePresentation,
            onCreateText = onCreateText
        )
    }

    // App Developer Info Dialog (Developer Sir Ghulam Mustafa honors & specs)
    if (showAppInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAppInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "All File Reader Studio",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OffWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "LEAD ARCHITECT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandAccent
                            )
                            Text(
                                text = "developer Sir Ghulam Mustafa",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    Text("Features & Capabilities:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("• 100% Offline: Zero internet required, zero tracking", fontSize = 12.sp)
                    Text("• PDF Reader: Native hardware-accelerated rendering & night mode", fontSize = 12.sp)
                    Text("• Excel/CSV Grid: Interactive cell editing + live SUM, AVG formulas", fontSize = 12.sp)
                    Text("• Word/Docs Reader: DOCX parser + built-in offline Text-to-Speech", fontSize = 12.sp)
                    Text("• PowerPoint: Fullscreen presentation slideshow mode", fontSize = 12.sp)
                    Text("• Code & Text Editor: Line numbers, find/replace, JSON format", fontSize = 12.sp)
                    Text("• Ultra Lean: Total app size strictly kept < 25 MB", fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAppInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        com.example.util.PrivacyConsentManager.openPrivacyPolicyWeb(context)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Privacy Policy")
                }
            }
        )
    }
}

@Composable
private fun DocumentItemCard(
    document: DocumentItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("doc_card_${document.extension}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Type Icon Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = document.type.containerColor,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = document.extension.uppercase().take(4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = document.type.primaryColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = document.formattedSize,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(text = " • ", fontSize = 12.sp, color = TextMuted)
                    Text(
                        text = document.formattedDate,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Star / Favorite
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (document.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (document.isFavorite) GoldAccent else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            // More Options
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Document") },
                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Document Details") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onInspect()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFDC2626)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentInspectorDialog(
    document: DocumentItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Document Properties", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyRow(label = "File Name", value = document.name)
                PropertyRow(label = "Type", value = "${document.type.label} (.${document.extension})")
                PropertyRow(label = "File Size", value = document.formattedSize)
                PropertyRow(label = "Modified", value = document.formattedDate)
                PropertyRow(label = "Storage", value = "Internal Offline Sandbox")
                PropertyRow(label = "Path", value = document.filePath)
            }
        },
        confirmButton = {
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 13.sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
