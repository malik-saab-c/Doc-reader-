package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.DocxSection
import com.example.engine.OfficeDocumentEngine
import com.example.engine.SectionType
import com.example.engine.WordDocumentData
import com.example.model.DocumentItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class WordDocTheme(
    val label: String,
    val desktopBg: Color,
    val paperBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentColor: Color,
    val borderColor: Color
) {
    WORD_CLASSIC(
        label = "Word Classic",
        desktopBg = Color(0xFFECEFF1),
        paperBg = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF1E293B),
        textSecondary = Color(0xFF64748B),
        accentColor = Color(0xFF1D4ED8),
        borderColor = Color(0xFFCBD5E1)
    ),
    SEPIA_BOOK(
        label = "Sepia Warm",
        desktopBg = Color(0xFFEFE8D3),
        paperBg = Color(0xFFFDF6E2),
        textPrimary = Color(0xFF2C2518),
        textSecondary = Color(0xFF7D725C),
        accentColor = Color(0xFFB45309),
        borderColor = Color(0xFFD6CDB5)
    ),
    DARK_SLATE(
        label = "Dark Slate",
        desktopBg = Color(0xFF0F172A),
        paperBg = Color(0xFF1E293B),
        textPrimary = Color(0xFFF8FAFC),
        textSecondary = Color(0xFF94A3B8),
        accentColor = Color(0xFF60A5FA),
        borderColor = Color(0xFF334155)
    ),
    NIGHT_BLACK(
        label = "OLED Black",
        desktopBg = Color(0xFF000000),
        paperBg = Color(0xFF121214),
        textPrimary = Color(0xFFE4E4E7),
        textSecondary = Color(0xFFA1A1AA),
        accentColor = Color(0xFF38BDF8),
        borderColor = Color(0xFF27272A)
    )
}

enum class DocumentViewMode {
    PRINT_LAYOUT_PAGES,
    CONTINUOUS_FLOW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReaderScreen(
    document: DocumentItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var docData by remember { mutableStateOf<WordDocumentData?>(null) }
    var fontSizeSp by remember { mutableFloatStateOf(16f) }
    var selectedFontFamily by remember { mutableStateOf(FontFamily.SansSerif) }
    var selectedTheme by remember { mutableStateOf(WordDocTheme.WORD_CLASSIC) }
    var viewMode by remember { mutableStateOf(DocumentViewMode.PRINT_LAYOUT_PAGES) }

    // Search
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Navigation Drawer / Table of Contents
    var showOutlineSheet by remember { mutableStateOf(false) }

    // Formatting & Statistics Dialogs
    var showFormattingMenu by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }

    // Zoom
    var zoomScale by remember { mutableFloatStateOf(1f) }

    // TTS Voice Reader
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsRate by remember { mutableFloatStateOf(1.0f) }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts?.language = Locale.getDefault()
                    } catch (e: Exception) {
                        Log.e("TTS", "Language set error: ${e.message}")
                    }
                }
            }
            ttsInstance = tts
        } catch (e: Exception) {
            Log.e("TTS", "TTS init failed: ${e.message}")
            ttsInstance = null
        }

        onDispose {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                Log.e("TTS", "Error shutting down TTS: ${e.message}")
            }
        }
    }

    LaunchedEffect(document.id, document.filePath) {
        withContext(Dispatchers.IO) {
            val data = OfficeDocumentEngine.loadWordDocument(
                context = context,
                pathOrUri = document.filePath,
                title = document.name,
                extensionHint = document.extension,
                fallbackContent = document.rawTextContent
            )
            docData = data
        }
    }

    val headingsList = remember(docData) {
        docData?.sections?.mapIndexedNotNull { index, section ->
            if (section.type == SectionType.TITLE || section.type == SectionType.HEADING1 || section.type == SectionType.HEADING2 || section.type == SectionType.HEADING3) {
                Pair(index, section)
            } else null
        } ?: emptyList()
    }

    // Split sections into virtual pages for desktop Print Layout mode (approx 6-8 sections per page)
    val pagedSections = remember(docData) {
        val sections = docData?.sections ?: emptyList()
        if (sections.isEmpty()) emptyList<List<DocxSection>>()
        else sections.chunked(7)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = TextPrimary
                        )
                        Text(
                            text = "${docData?.wordCount ?: 0} words • ${if (viewMode == DocumentViewMode.PRINT_LAYOUT_PAGES) "Print Layout" else "Continuous Flow"}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        ttsInstance?.stop()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    // Document Outline / Navigation Pane (Desktop Word Left Sidebar)
                    if (headingsList.isNotEmpty()) {
                        IconButton(onClick = { showOutlineSheet = true }) {
                            Icon(Icons.Default.MenuBook, contentDescription = "Outline", tint = WordBlue)
                        }
                    }

                    // View Mode Switcher: Desktop Print Layout vs Continuous Flow
                    IconButton(onClick = {
                        viewMode = if (viewMode == DocumentViewMode.PRINT_LAYOUT_PAGES) DocumentViewMode.CONTINUOUS_FLOW else DocumentViewMode.PRINT_LAYOUT_PAGES
                    }) {
                        Icon(
                            imageVector = if (viewMode == DocumentViewMode.PRINT_LAYOUT_PAGES) Icons.Default.VerticalSplit else Icons.Default.Description,
                            contentDescription = "Toggle View Mode",
                            tint = if (viewMode == DocumentViewMode.PRINT_LAYOUT_PAGES) WordBlue else TextPrimary
                        )
                    }

                    // Find in Document
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) WordBlue else TextPrimary
                        )
                    }

                    // TTS Voice Reader
                    IconButton(
                        onClick = {
                            val tts = ttsInstance
                            val text = docData?.fullText
                            if (tts != null && text != null) {
                                try {
                                    if (isSpeaking) {
                                        tts.stop()
                                        isSpeaking = false
                                    } else {
                                        tts.setSpeechRate(ttsRate)
                                        tts.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, null, "tts_id")
                                        isSpeaking = true
                                    }
                                } catch (e: Exception) {
                                    Log.e("TTS", "Speech synthesis failed: ${e.message}")
                                    isSpeaking = false
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Text to Speech",
                            tint = if (isSpeaking) WordBlue else TextPrimary
                        )
                    }

                    // Formatting & Theme Dialog
                    IconButton(onClick = { showFormattingMenu = true }) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Typography & Themes", tint = TextPrimary)
                    }

                    // Document Stats
                    IconButton(onClick = { showStatsDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Document Info", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        bottomBar = {
            Surface(
                color = PureWhite,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Zoom: ${(zoomScale * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                        if (zoomScale != 1f) {
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = { zoomScale = 1f },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("100%", fontSize = 11.sp, color = WordBlue)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${docData?.sections?.size ?: 0} Blocks • ${docData?.characterCount ?: 0} Chars",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        },
        containerColor = selectedTheme.desktopBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Find in Document Input Row
            AnimatedVisibility(visible = isSearchActive) {
                Surface(
                    color = PureWhite,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Find in document...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WordBlue,
                            unfocusedBorderColor = CardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            val data = docData
            if (data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WordBlue)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(0.75f, 2.5f)
                            }
                        }
                ) {
                    when (viewMode) {
                        DocumentViewMode.PRINT_LAYOUT_PAGES -> {
                            // Desktop Word "Print Layout" Mode: Realistic A4 sheets of paper on desktop canvas
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(pagedSections) { pageIndex, pageSections ->
                                    DesktopWordPageSheet(
                                        pageIndex = pageIndex + 1,
                                        totalPages = pagedSections.size,
                                        sections = pageSections,
                                        theme = selectedTheme,
                                        fontSizeSp = fontSizeSp,
                                        fontFamily = selectedFontFamily,
                                        searchHighlight = searchQuery
                                    )
                                }
                            }
                        }

                        DocumentViewMode.CONTINUOUS_FLOW -> {
                            // Continuous Reading Document Layout
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(data.sections) { section ->
                                    RenderDesktopDocxSection(
                                        section = section,
                                        fontSizeSp = fontSizeSp,
                                        fontFamily = selectedFontFamily,
                                        theme = selectedTheme,
                                        searchHighlight = searchQuery
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 3. TABLE OF CONTENTS / OUTLINE BOTTOM SHEET
    // ==========================================
    if (showOutlineSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOutlineSheet = false },
            containerColor = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Document Outline",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = { showOutlineSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(headingsList) { (sectionIndex, section) ->
                        val indentDp = when (section.type) {
                            SectionType.TITLE -> 0.dp
                            SectionType.HEADING1 -> 8.dp
                            SectionType.HEADING2 -> 18.dp
                            SectionType.HEADING3 -> 28.dp
                            else -> 0.dp
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SoftSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indentDp)
                                .clickable {
                                    showOutlineSheet = false
                                    coroutineScope.launch {
                                        if (viewMode == DocumentViewMode.PRINT_LAYOUT_PAGES) {
                                            val pageTarget = (sectionIndex / 7).coerceIn(0, (pagedSections.size - 1).coerceAtLeast(0))
                                            listState.animateScrollToItem(pageTarget)
                                        } else {
                                            listState.animateScrollToItem(sectionIndex)
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(WordBlue, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = section.text,
                                    fontSize = 13.sp,
                                    fontWeight = if (section.type == SectionType.TITLE || section.type == SectionType.HEADING1) FontWeight.Bold else FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 4. DESKTOP TYPOGRAPHY & THEME DIALOG
    // ==========================================
    if (showFormattingMenu) {
        AlertDialog(
            onDismissRequest = { showFormattingMenu = false },
            title = { Text("Desktop Reading Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Desktop Reading Themes
                    Text("Reading Theme", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WordDocTheme.values().forEach { theme ->
                            FilterChip(
                                selected = selectedTheme == theme,
                                onClick = { selectedTheme = theme },
                                label = { Text(theme.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Font Size Slider
                    Text("Font Size: ${fontSizeSp.toInt()} sp", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 12f..24f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = WordBlue, activeTrackColor = WordBlue)
                    )

                    // Font Family
                    Text("Typeface", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilterChip(
                            selected = selectedFontFamily == FontFamily.SansSerif,
                            onClick = { selectedFontFamily = FontFamily.SansSerif },
                            label = { Text("Sans") }
                        )
                        FilterChip(
                            selected = selectedFontFamily == FontFamily.Serif,
                            onClick = { selectedFontFamily = FontFamily.Serif },
                            label = { Text("Serif") }
                        )
                        FilterChip(
                            selected = selectedFontFamily == FontFamily.Monospace,
                            onClick = { selectedFontFamily = FontFamily.Monospace },
                            label = { Text("Mono") }
                        )
                    }

                    // Voice Reading Speed
                    Text("Voice Reader Speed: ${"%.2f".format(ttsRate)}x", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { rate ->
                            FilterChip(
                                selected = ttsRate == rate,
                                onClick = {
                                    ttsRate = rate
                                    ttsInstance?.setSpeechRate(rate)
                                },
                                label = { Text("${rate}x", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormattingMenu = false }) {
                    Text("Done")
                }
            }
        )
    }

    // ==========================================
    // 5. DOCUMENT STATS DIALOG
    // ==========================================
    if (showStatsDialog) {
        val data = docData
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = { Text("Document Statistics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("File: ${document.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    HorizontalDivider()
                    StatRow(label = "Word Count", value = "${data?.wordCount ?: 0}")
                    StatRow(label = "Character Count", value = "${data?.characterCount ?: 0}")
                    StatRow(label = "Total Sections", value = "${data?.sections?.size ?: 0}")
                    StatRow(label = "Headings", value = "${headingsList.size}")
                    StatRow(label = "Estimated Read Time", value = "~${data?.readTimeMinutes ?: 1} min")
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

/**
 * Desktop Word Page Sheet. Renders a distinct sheet of paper with standard margins and page numbering.
 */
@Composable
private fun DesktopWordPageSheet(
    pageIndex: Int,
    totalPages: Int,
    sections: List<DocxSection>,
    theme: WordDocTheme,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    searchHighlight: String
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = theme.paperBg,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, theme.borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(4.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            // Document Content
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sections.forEach { section ->
                    RenderDesktopDocxSection(
                        section = section,
                        fontSizeSp = fontSizeSp,
                        fontFamily = fontFamily,
                        theme = theme,
                        searchHighlight = searchHighlight
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Desktop Word Page Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(0.5.dp, theme.borderColor.copy(alpha = 0.5f)),
                        RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page $pageIndex of $totalPages",
                    fontSize = 10.sp,
                    color = theme.textSecondary
                )
                Text(
                    text = "Desktop Word Layout",
                    fontSize = 10.sp,
                    color = theme.textSecondary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Renders rich DOCX elements: Tables, Quotes, Headings, Code Blocks, Numbered Lists, Bullet Lists, and Paragraphs.
 */
@Composable
private fun RenderDesktopDocxSection(
    section: DocxSection,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    theme: WordDocTheme,
    searchHighlight: String
) {
    val isHighlighted = searchHighlight.isNotBlank() && section.text.contains(searchHighlight, ignoreCase = true)
    val clipboard = LocalClipboardManager.current

    val backgroundModifier = if (isHighlighted) {
        Modifier.background(Color(0xFFFEF08A), shape = RoundedCornerShape(4.dp))
    } else Modifier

    when (section.type) {
        SectionType.TITLE -> {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = section.text,
                    fontSize = (fontSizeSp + 8).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = fontFamily,
                    color = theme.textPrimary,
                    lineHeight = (fontSizeSp + 14).sp,
                    modifier = backgroundModifier
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(3.dp)
                        .background(theme.accentColor, RoundedCornerShape(2.dp))
                )
            }
        }

        SectionType.HEADING1 -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height((fontSizeSp + 6).dp)
                        .background(theme.accentColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = section.text,
                    fontSize = (fontSizeSp + 4).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = theme.accentColor,
                    lineHeight = (fontSizeSp + 10).sp,
                    modifier = backgroundModifier
                )
            }
        }

        SectionType.HEADING2 -> {
            Text(
                text = section.text,
                fontSize = (fontSizeSp + 2).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                color = theme.textPrimary,
                modifier = backgroundModifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        SectionType.HEADING3 -> {
            Text(
                text = section.text,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = fontFamily,
                color = theme.textSecondary,
                modifier = backgroundModifier.padding(top = 6.dp)
            )
        }

        SectionType.QUOTE -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = theme.desktopBg.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, theme.borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(theme.accentColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = section.text,
                        fontSize = fontSizeSp.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = fontFamily,
                        color = theme.textPrimary,
                        lineHeight = (fontSizeSp * 1.5).sp
                    )
                }
            }
        }

        SectionType.TABLE -> {
            // Full Desktop Rendered Table
            if (section.tableRows.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = theme.paperBg,
                    border = BorderStroke(1.dp, theme.borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        section.tableRows.forEachIndexed { rowIndex, rowCells ->
                            val isHeader = rowIndex == 0
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (isHeader) theme.desktopBg else if (rowIndex % 2 == 1) theme.paperBg else theme.desktopBg.copy(alpha = 0.35f)
                                    )
                                    .padding(vertical = 8.dp)
                            ) {
                                rowCells.forEach { cellText ->
                                    Box(
                                        modifier = Modifier
                                            .widthIn(min = 100.dp, max = 220.dp)
                                            .padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = cellText,
                                            fontSize = (fontSizeSp - 2).sp,
                                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isHeader) theme.accentColor else theme.textPrimary,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = theme.borderColor.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        SectionType.BULLET_ITEM -> {
            Row(
                modifier = backgroundModifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, end = 10.dp)
                        .size(5.dp)
                        .background(theme.accentColor, CircleShape)
                )
                Text(
                    text = section.text,
                    fontSize = fontSizeSp.sp,
                    fontFamily = fontFamily,
                    color = theme.textPrimary,
                    lineHeight = (fontSizeSp * 1.5).sp
                )
            }
        }

        SectionType.NUMBERED_ITEM -> {
            Row(
                modifier = backgroundModifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.accentColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = section.text,
                    fontSize = fontSizeSp.sp,
                    fontFamily = fontFamily,
                    color = theme.textPrimary,
                    lineHeight = (fontSizeSp * 1.5).sp
                )
            }
        }

        SectionType.CODE_BLOCK -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Code",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(section.text)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy Code",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = section.text,
                        fontSize = (fontSizeSp - 2).sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 20.sp
                    )
                }
            }
        }

        SectionType.DIVIDER -> {
            HorizontalDivider(
                color = theme.borderColor,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        SectionType.PARAGRAPH -> {
            Text(
                text = section.text,
                fontSize = fontSizeSp.sp,
                fontFamily = fontFamily,
                fontWeight = if (section.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (section.isItalic) FontStyle.Italic else FontStyle.Normal,
                color = theme.textPrimary,
                lineHeight = (fontSizeSp * 1.55).sp,
                modifier = backgroundModifier
            )
        }
    }
}
