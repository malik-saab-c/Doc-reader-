package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.OfficeDocumentEngine
import com.example.engine.PresentationData
import com.example.engine.SlideElement
import com.example.engine.SlideElementType
import com.example.engine.SlideItem
import com.example.engine.SlideLayout
import com.example.model.DocumentItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PptTheme(
    val displayName: String,
    val canvasBg: Color,
    val cardBg: Color,
    val titleColor: Color,
    val textColor: Color,
    val accentColor: Color,
    val badgeBg: Color
) {
    EXECUTIVE_NAVY(
        displayName = "Navy Executive",
        canvasBg = Color(0xFF0F172A),
        cardBg = Color(0xFF1E293B),
        titleColor = Color(0xFFF8FAFC),
        textColor = Color(0xFFCBD5E1),
        accentColor = Color(0xFF38BDF8),
        badgeBg = Color(0xFF0369A1)
    ),
    MODERN_WHITE(
        displayName = "Modern Light",
        canvasBg = Color(0xFFF1F5F9),
        cardBg = Color(0xFFFFFFFF),
        titleColor = Color(0xFF0F172A),
        textColor = Color(0xFF334155),
        accentColor = Color(0xFFEA580C),
        badgeBg = Color(0xFFFFEDD5)
    ),
    OBSIDIAN_GOLD(
        displayName = "Obsidian Gold",
        canvasBg = Color(0xFF18181B),
        cardBg = Color(0xFF27272A),
        titleColor = Color(0xFFF59E0B),
        textColor = Color(0xFFE4E4E7),
        accentColor = Color(0xFFFBBF24),
        badgeBg = Color(0xFF78350F)
    ),
    TEAL_ENTERPRISE(
        displayName = "Teal Enterprise",
        canvasBg = Color(0xFF064E3B),
        cardBg = Color(0xFF065F46),
        titleColor = Color(0xFFF0FDF4),
        textColor = Color(0xFFD1FAE5),
        accentColor = Color(0xFF34D399),
        badgeBg = Color(0xFF047857)
    )
}

enum class PresentationViewMode {
    SLIDE_VIEW,
    SLIDE_SORTER_GRID
}

enum class AspectRatioMode(val label: String, val ratio: Float) {
    WIDESCREEN_16_9("16:9 Widescreen", 16f / 9f),
    STANDARD_4_3("4:3 Standard", 4f / 3f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationViewerScreen(
    document: DocumentItem,
    onBack: () -> Unit,
    onSaveContent: (String) -> Unit = {},
    onSaveBinaryContent: (ByteArray) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var presentationData by remember { mutableStateOf<PresentationData?>(null) }
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    var viewMode by remember { mutableStateOf(PresentationViewMode.SLIDE_VIEW) }
    var isEditMode by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(PptTheme.EXECUTIVE_NAVY) }
    var selectedAspect by remember { mutableStateOf(AspectRatioMode.WIDESCREEN_16_9) }

    // Fullscreen Slide Show state
    var isSlideshowActive by remember { mutableStateOf(false) }
    var autoPlaySlideshow by remember { mutableStateOf(false) }
    var autoPlayDelayMs by remember { mutableLongStateOf(4000L) }
    var isLaserActive by remember { mutableStateOf(false) }
    var laserPosition by remember { mutableStateOf<Offset?>(null) }
    var isBlackScreenActive by remember { mutableStateOf(false) }
    var showSlideshowControls by remember { mutableStateOf(true) }

    // Slide view interactive zoom & pan
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Search and menus
    var searchQuery by remember { mutableStateOf("") }
    var showNotesDrawer by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Edit Slide Dialog
    var showEditSlideDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Load Presentation
    LaunchedEffect(document.id, document.filePath) {
        withContext(Dispatchers.IO) {
            val data = OfficeDocumentEngine.loadPresentation(
                context = context,
                pathOrUri = document.filePath,
                title = document.name,
                extensionHint = document.extension,
                fallbackContent = document.rawTextContent
            )
            presentationData = data
        }
    }

    // Auto-advance slideshow timer
    LaunchedEffect(autoPlaySlideshow, currentSlideIndex, isSlideshowActive, autoPlayDelayMs) {
        if (autoPlaySlideshow && isSlideshowActive) {
            delay(autoPlayDelayMs)
            val total = presentationData?.slides?.size ?: 0
            if (total > 0) {
                currentSlideIndex = (currentSlideIndex + 1) % total
            }
        }
    }

    val allSlides = presentationData?.slides ?: emptyList()
    val filteredSlides = if (searchQuery.isBlank()) allSlides else {
        allSlides.filter { slide ->
            slide.title.contains(searchQuery, ignoreCase = true) ||
                    slide.bulletPoints.any { it.contains(searchQuery, ignoreCase = true) } ||
                    slide.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }
    val currentSlide = allSlides.getOrNull(currentSlideIndex)

    // Helper functions for Slide Management
    fun savePresentationState(updatedSlides: List<SlideItem>, message: String = "Presentation saved") {
        val renumbered = updatedSlides.mapIndexed { idx, s ->
            s.copy(slideNumber = idx + 1, categoryTag = "SLIDE ${"%02d".format(idx + 1)}")
        }
        val newPresData = PresentationData(
            title = presentationData?.title ?: document.name,
            slides = renumbered,
            slideWidthEmu = presentationData?.slideWidthEmu ?: 12192000L,
            slideHeightEmu = presentationData?.slideHeightEmu ?: 6858000L
        )
        presentationData = newPresData

        coroutineScope.launch(Dispatchers.IO) {
            val pptxBytes = OfficeDocumentEngine.exportToPptxZip(newPresData)
            onSaveBinaryContent(pptxBytes)
            val markdown = OfficeDocumentEngine.presentationToMarkdown(newPresData)
            onSaveContent(markdown)
            snackbarHostState.showSnackbar(message)
        }
    }

    fun addNewSlide() {
        val newIndex = (currentSlideIndex + 1).coerceAtMost(allSlides.size)
        val newSlideNumber = newIndex + 1
        val newTitle = "New Slide $newSlideNumber"
        val newBullets = listOf("Point 1: Key observation", "Point 2: Supporting metric", "Point 3: Next action")
        val defaultElements = OfficeDocumentEngine.buildDefaultSlideElements(
            title = newTitle,
            subtitle = "Overview & Summary",
            bulletPoints = newBullets,
            layout = SlideLayout.TITLE_AND_CONTENT
        )
        val newSlide = SlideItem(
            slideNumber = newSlideNumber,
            title = newTitle,
            subtitle = "Overview & Summary",
            bulletPoints = newBullets,
            layoutType = SlideLayout.TITLE_AND_CONTENT,
            elements = defaultElements
        )
        val mutable = allSlides.toMutableList()
        mutable.add(newIndex, newSlide)
        currentSlideIndex = newIndex
        savePresentationState(mutable, "Added new slide $newSlideNumber")
    }

    fun duplicateCurrentSlide() {
        if (currentSlide == null) return
        val dupSlide = currentSlide.copy(
            title = "${currentSlide.title} (Copy)"
        )
        val mutable = allSlides.toMutableList()
        val insertIndex = currentSlideIndex + 1
        mutable.add(insertIndex, dupSlide)
        currentSlideIndex = insertIndex
        savePresentationState(mutable, "Duplicated slide ${currentSlide.slideNumber}")
    }

    fun deleteCurrentSlide() {
        if (allSlides.size <= 1) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Cannot delete the only slide in the deck.")
            }
            return
        }
        val mutable = allSlides.toMutableList()
        mutable.removeAt(currentSlideIndex)
        currentSlideIndex = currentSlideIndex.coerceAtMost(mutable.size - 1)
        savePresentationState(mutable, "Deleted slide")
    }

    fun moveCurrentSlide(direction: Int) { // -1 for up/earlier, +1 for down/later
        val targetIndex = currentSlideIndex + direction
        if (targetIndex in allSlides.indices) {
            val mutable = allSlides.toMutableList()
            val item = mutable.removeAt(currentSlideIndex)
            mutable.add(targetIndex, item)
            currentSlideIndex = targetIndex
            savePresentationState(mutable, "Reordered slide")
        }
    }

    fun shareDeck() {
        if (presentationData == null) return
        val text = OfficeDocumentEngine.presentationToMarkdown(presentationData!!)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, document.name)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Presentation")
        context.startActivity(shareIntent)
    }

    // ==========================================
    // 1. FULLSCREEN SLIDESHOW PRESENTATION MODE
    // ==========================================
    if (isSlideshowActive && currentSlide != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isBlackScreenActive) Color.Black else selectedTheme.canvasBg)
                .pointerInput(isLaserActive) {
                    if (isLaserActive) {
                        detectTransformGestures { _, pan, _, _ ->
                            laserPosition = (laserPosition ?: Offset(size.width / 2f, size.height / 2f)) + pan
                        }
                    }
                }
        ) {
            if (!isBlackScreenActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SlideCanvasCard(
                        slide = currentSlide,
                        totalSlides = allSlides.size,
                        theme = selectedTheme,
                        aspectRatio = selectedAspect.ratio,
                        isEditMode = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                    )
                }

                // Virtual Laser Pointer simulation
                if (isLaserActive && laserPosition != null) {
                    val pos = laserPosition!!
                    Box(
                        modifier = Modifier
                            .offset(x = (pos.x - 14).dp, y = (pos.y - 14).dp)
                            .size(28.dp)
                            .background(Color(0xFFFF2222).copy(alpha = 0.35f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = (pos.x - 6).dp, y = (pos.y - 6).dp)
                            .size(12.dp)
                            .background(Color(0xFFFF0000), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Screen Blanked (Tap screen to restore)",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
            }

            // Interactive tap zones: Left = Prev, Right = Next, Center = Toggle controls
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            if (isBlackScreenActive) {
                                isBlackScreenActive = false
                            } else if (currentSlideIndex > 0) {
                                currentSlideIndex--
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .clickable {
                            if (isBlackScreenActive) isBlackScreenActive = false
                            else showSlideshowControls = !showSlideshowControls
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            if (isBlackScreenActive) {
                                isBlackScreenActive = false
                            } else if (currentSlideIndex < allSlides.size - 1) {
                                currentSlideIndex++
                            } else {
                                currentSlideIndex = 0
                            }
                        }
                )
            }

            // Top Control Bar Overlay
            AnimatedVisibility(
                visible = showSlideshowControls,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSlideshowActive = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Slideshow", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Slide ${currentSlideIndex + 1} / ${allSlides.size}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isLaserActive = !isLaserActive }) {
                                Icon(
                                    imageVector = Icons.Default.Highlight,
                                    contentDescription = "Laser Pointer",
                                    tint = if (isLaserActive) Color.Red else Color.White
                                )
                            }
                            IconButton(onClick = { isBlackScreenActive = !isBlackScreenActive }) {
                                Icon(
                                    imageVector = Icons.Default.Brightness2,
                                    contentDescription = "Blank Screen",
                                    tint = if (isBlackScreenActive) selectedTheme.accentColor else Color.White
                                )
                            }
                            IconButton(onClick = { autoPlaySlideshow = !autoPlaySlideshow }) {
                                Icon(
                                    imageVector = if (autoPlaySlideshow) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Autoplay",
                                    tint = if (autoPlaySlideshow) selectedTheme.accentColor else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Floating Next/Prev Pill
            AnimatedVisibility(
                visible = showSlideshowControls,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentSlideIndex > 0) currentSlideIndex-- },
                            enabled = currentSlideIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Slide", tint = Color.White)
                        }
                        Text(
                            text = "${currentSlideIndex + 1} / ${allSlides.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { if (currentSlideIndex < allSlides.size - 1) currentSlideIndex++ },
                            enabled = currentSlideIndex < allSlides.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Slide", tint = Color.White)
                        }
                    }
                }
            }
        }
        return
    }

    // ==========================================
    // 2. DESKTOP WORKSPACE PRESENTATION VIEWER & EDITOR
    // ==========================================
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = document.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = TextPrimary
                            )
                            if (isEditMode) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PptOrange
                                ) {
                                    Text(
                                        text = "EDIT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Slide ${currentSlideIndex + 1} of ${allSlides.size.coerceAtLeast(1)} • ${selectedAspect.label}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    // Edit Mode Toggle Button
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.EditOff else Icons.Default.Edit,
                            contentDescription = if (isEditMode) "Exit Edit Mode" else "Enter Edit Mode",
                            tint = if (isEditMode) PptOrange else TextPrimary
                        )
                    }

                    // View Mode Switcher: Single Slide vs Slide Sorter Grid
                    IconButton(onClick = {
                        viewMode = if (viewMode == PresentationViewMode.SLIDE_VIEW) PresentationViewMode.SLIDE_SORTER_GRID else PresentationViewMode.SLIDE_VIEW
                    }) {
                        Icon(
                            imageVector = if (viewMode == PresentationViewMode.SLIDE_VIEW) Icons.Default.GridView else Icons.Default.ViewCarousel,
                            contentDescription = "Toggle Slide Sorter",
                            tint = if (viewMode == PresentationViewMode.SLIDE_SORTER_GRID) PptOrange else TextPrimary
                        )
                    }

                    // Aspect Ratio Toggle: 16:9 vs 4:3
                    IconButton(onClick = {
                        selectedAspect = if (selectedAspect == AspectRatioMode.WIDESCREEN_16_9) AspectRatioMode.STANDARD_4_3 else AspectRatioMode.WIDESCREEN_16_9
                    }) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Toggle Aspect Ratio",
                            tint = TextPrimary
                        )
                    }

                    // Theme selector button
                    IconButton(onClick = { showThemeMenu = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Slide Themes", tint = PptOrange)
                    }

                    DropdownMenu(
                        expanded = showThemeMenu,
                        onDismissRequest = { showThemeMenu = false }
                    ) {
                        PptTheme.values().forEach { theme ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(theme.canvasBg, CircleShape)
                                                .border(1.dp, Color.Gray, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(theme.displayName, fontWeight = if (selectedTheme == theme) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                onClick = {
                                    selectedTheme = theme
                                    showThemeMenu = false
                                }
                            )
                        }
                    }

                    // Options Menu (Save, Export, Share, Slideshow)
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = TextPrimary)
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Start Slide Show (F5)") },
                            leadingIcon = { Icon(Icons.Default.Slideshow, contentDescription = null, tint = PptOrange) },
                            onClick = {
                                showOptionsMenu = false
                                isSlideshowActive = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save Presentation") },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = TextPrimary) },
                            onClick = {
                                showOptionsMenu = false
                                savePresentationState(allSlides, "Presentation saved successfully!")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Slide Deck") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary) },
                            onClick = {
                                showOptionsMenu = false
                                shareDeck()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        bottomBar = {
            if (viewMode == PresentationViewMode.SLIDE_VIEW && allSlides.isNotEmpty()) {
                Surface(
                    color = PureWhite,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        // Speaker Notes Collapsible Drawer Toggle
                        if (currentSlide?.notes?.isNotBlank() == true || currentSlide?.subtitle?.isNotBlank() == true) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showNotesDrawer = !showNotesDrawer }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Note,
                                        contentDescription = null,
                                        tint = PptOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Presenter Notes",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Icon(
                                    imageVector = if (showNotesDrawer) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = showNotesDrawer) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SoftSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = currentSlide.notes.ifBlank { currentSlide.subtitle },
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        // Slide Thumbnails Strip (Desktop PowerPoint Slide Rail)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            itemsIndexed(allSlides) { idx, slide ->
                                val isSelected = idx == currentSlideIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PptOrangeLight else SoftSurface,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) PptOrange else CardBorder
                                    ),
                                    modifier = Modifier
                                        .size(width = 84.dp, height = 54.dp)
                                        .clickable {
                                            currentSlideIndex = idx
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) PptOrange else TextSecondary
                                        )
                                        Text(
                                            text = slide.title,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Navigation buttons & Zoom Reset
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentSlideIndex > 0) {
                                        currentSlideIndex--
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                },
                                enabled = currentSlideIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous")
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Slide ${currentSlideIndex + 1} / ${allSlides.size}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                if (zoomScale > 1.05f) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Reset Zoom", fontSize = 11.sp, color = PptOrange)
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (currentSlideIndex < allSlides.size - 1) {
                                        currentSlideIndex++
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                },
                                enabled = currentSlideIndex < allSlides.size - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next")
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (isEditMode && currentSlide != null) {
                FloatingActionButton(
                    onClick = { showEditSlideDialog = true },
                    containerColor = PptOrange,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Current Slide")
                }
            }
        },
        containerColor = selectedTheme.canvasBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (viewMode) {
                PresentationViewMode.SLIDE_VIEW -> {
                    // Single Slide Desktop Viewport with interactive pinch zoom
                    if (currentSlide != null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Edit Mode Slide Action Strip
                            if (isEditMode) {
                                Surface(
                                    color = PureWhite,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { addNewSlide() }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Slide", tint = PptOrange)
                                        }
                                        IconButton(onClick = { duplicateCurrentSlide() }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Slide", tint = TextPrimary)
                                        }
                                        IconButton(
                                            onClick = { moveCurrentSlide(-1) },
                                            enabled = currentSlideIndex > 0
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Earlier", tint = TextPrimary)
                                        }
                                        IconButton(
                                            onClick = { moveCurrentSlide(1) },
                                            enabled = currentSlideIndex < allSlides.size - 1
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Later", tint = TextPrimary)
                                        }
                                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Slide", tint = ErrorRed)
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            zoomScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                            if (zoomScale > 1f) {
                                                panOffset += pan
                                            } else {
                                                panOffset = Offset.Zero
                                            }
                                        }
                                    }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                SlideCanvasCard(
                                    slide = currentSlide,
                                    totalSlides = allSlides.size,
                                    theme = selectedTheme,
                                    aspectRatio = selectedAspect.ratio,
                                    isEditMode = isEditMode,
                                    onEditRequest = { showEditSlideDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = zoomScale
                                            scaleY = zoomScale
                                            translationX = panOffset.x
                                            translationY = panOffset.y
                                        }
                                )
                            }
                        }
                    } else if (presentationData != null) {
                        EmptyPresentationPlaceholder()
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PptOrange)
                        }
                    }
                }

                PresentationViewMode.SLIDE_SORTER_GRID -> {
                    // Desktop PowerPoint "Slide Sorter" Grid View
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Search bar in Sorter Mode
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search slides...", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PureWhite,
                                unfocusedContainerColor = PureWhite,
                                focusedBorderColor = PptOrange,
                                unfocusedBorderColor = CardBorder
                            ),
                            singleLine = true
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(filteredSlides) { _, slide ->
                                val realIndex = allSlides.indexOf(slide)
                                val isSelected = realIndex == currentSlideIndex
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = selectedTheme.cardBg,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) PptOrange else CardBorder
                                    ),
                                    shadowElevation = if (isSelected) 6.dp else 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 10f)
                                        .clickable {
                                            currentSlideIndex = realIndex
                                            viewMode = PresentationViewMode.SLIDE_VIEW
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = selectedTheme.badgeBg
                                            ) {
                                                Text(
                                                    text = "#${slide.slideNumber}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = slide.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = selectedTheme.titleColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            text = "${slide.bulletPoints.size} points",
                                            fontSize = 10.sp,
                                            color = selectedTheme.textColor.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 3. EDIT SLIDE DIALOG
    // ==========================================
    if (showEditSlideDialog && currentSlide != null) {
        SlideEditDialog(
            slide = currentSlide,
            onDismiss = { showEditSlideDialog = false },
            onSaveSlide = { updatedSlide ->
                val mutable = allSlides.toMutableList()
                mutable[currentSlideIndex] = updatedSlide
                savePresentationState(mutable, "Slide ${currentSlide.slideNumber} updated")
                showEditSlideDialog = false
            }
        )
    }

    // ==========================================
    // 4. DELETE CONFIRMATION DIALOG
    // ==========================================
    if (showDeleteConfirmDialog && currentSlide != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Slide ${currentSlide.slideNumber}?") },
            text = { Text("Are you sure you want to remove '${currentSlide.title}' from the presentation deck?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        deleteCurrentSlide()
                    }
                ) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Desktop-Grade High Fidelity Presentation Slide Canvas.
 * Renders actual OpenXML shape geometry (normX, normY, normW, normH), embedded images,
 * tables, font styling, alignments, and structured layouts.
 */
@Composable
fun SlideCanvasCard(
    slide: SlideItem,
    totalSlides: Int,
    theme: PptTheme,
    aspectRatio: Float,
    isEditMode: Boolean = false,
    onEditRequest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bgColor = slide.backgroundColorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: theme.cardBg

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        shadowElevation = 8.dp,
        border = BorderStroke(if (isEditMode) 2.dp else 1.dp, if (isEditMode) PptOrange else Color.White.copy(alpha = 0.15f)),
        modifier = modifier
            .aspectRatio(aspectRatio)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clickable(enabled = isEditMode) { onEditRequest() }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val canvasW = maxWidth
            val canvasH = maxHeight

            // If slide has explicit positioned elements, render true geometric shapes!
            if (slide.elements.isNotEmpty()) {
                slide.elements.forEach { el ->
                    val elLeft = canvasW * el.normX
                    val elTop = canvasH * el.normY
                    val elWidth = canvasW * el.normW
                    val elHeight = canvasH * el.normH

                    val fontColor = el.fontColorHex?.let {
                        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                    } ?: if (el.type == SlideElementType.TITLE) theme.titleColor else theme.textColor

                    val elBgColor = el.backgroundColorHex?.let {
                        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                    } ?: Color.Transparent

                    val alignment = when (el.textAlign) {
                        "CENTER" -> TextAlign.Center
                        "RIGHT" -> TextAlign.End
                        else -> TextAlign.Start
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = elLeft, y = elTop)
                            .size(width = elWidth, height = elHeight)
                            .background(elBgColor, RoundedCornerShape(4.dp))
                            .padding(2.dp)
                    ) {
                        when (el.type) {
                            SlideElementType.IMAGE -> {
                                if (el.imageBytes != null) {
                                    val bitmap = remember(el.imageBytes) {
                                        runCatching {
                                            BitmapFactory.decodeByteArray(el.imageBytes, 0, el.imageBytes.size)?.asImageBitmap()
                                        }.getOrNull()
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Slide Image",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        ImagePlaceholder(theme)
                                    }
                                } else {
                                    ImagePlaceholder(theme)
                                }
                            }

                            SlideElementType.TABLE -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(1.dp, theme.textColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                ) {
                                    el.tableRows.forEachIndexed { rowIdx, row ->
                                        val isHeader = rowIdx == 0
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isHeader) theme.badgeBg.copy(alpha = 0.45f) else Color.Transparent)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            row.forEach { cell ->
                                                Text(
                                                    text = cell,
                                                    fontSize = (el.fontSizeSp * 0.75f).coerceIn(9f, 16f).sp,
                                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isHeader) theme.titleColor else theme.textColor,
                                                    modifier = Modifier.weight(1f).padding(2.dp),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SlideElementType.STAT_HERO -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = el.text,
                                        fontSize = el.fontSizeSp.coerceIn(24f, 48f).sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = theme.accentColor,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            SlideElementType.BULLET_LIST -> {
                                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    val pts = if (el.bulletPoints.isNotEmpty()) el.bulletPoints else el.text.lines()
                                    pts.forEach { pt ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 4.dp, end = 6.dp)
                                                    .size(5.dp)
                                                    .background(theme.accentColor, CircleShape)
                                            )
                                            Text(
                                                text = pt,
                                                fontSize = el.fontSizeSp.coerceIn(11f, 20f).sp,
                                                color = fontColor,
                                                lineHeight = (el.fontSizeSp * 1.35f).sp
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                // TITLE, SUBTITLE, TEXT_BOX
                                Text(
                                    text = el.text,
                                    fontSize = el.fontSizeSp.coerceIn(11f, 32f).sp,
                                    fontWeight = if (el.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (el.isItalic) FontStyle.Italic else FontStyle.Normal,
                                    color = fontColor,
                                    textAlign = alignment,
                                    lineHeight = (el.fontSizeSp * 1.3f).sp,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                // Fallback structured layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = slide.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.titleColor
                        )
                        if (slide.subtitle.isNotBlank()) {
                            Text(
                                text = slide.subtitle,
                                fontSize = 13.sp,
                                color = theme.accentColor,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )
                        }
                        slide.bulletPoints.forEach { pt ->
                            BulletPointCard(point = pt, theme = theme)
                        }
                    }
                }
            }

            // Top Header: Slide Category Tag
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = theme.badgeBg,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = "${slide.slideNumber} / $totalSlides",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ImagePlaceholder(theme: PptTheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, theme.textColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .background(theme.canvasBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Image, contentDescription = null, tint = theme.accentColor, modifier = Modifier.size(28.dp))
            Text("Image Asset", fontSize = 10.sp, color = theme.textColor)
        }
    }
}

@Composable
private fun BulletPointCard(point: String, theme: PptTheme) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = theme.canvasBg.copy(alpha = 0.45f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .size(6.dp)
                    .background(theme.accentColor, CircleShape)
            )
            Text(
                text = point,
                fontSize = 13.sp,
                color = theme.textColor,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Slide Editor Dialog: Allows real-time modification of slide title, subtitle,
 * bullet points, speaker notes, and layout.
 */
@Composable
fun SlideEditDialog(
    slide: SlideItem,
    onDismiss: () -> Unit,
    onSaveSlide: (SlideItem) -> Unit
) {
    var editTitle by remember { mutableStateOf(slide.title) }
    var editSubtitle by remember { mutableStateOf(slide.subtitle) }
    var editNotes by remember { mutableStateOf(slide.notes) }
    var editLayout by remember { mutableStateOf(slide.layoutType) }
    var bulletsList by remember { mutableStateOf(slide.bulletPoints.toMutableList()) }
    var newBulletText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = PptOrange)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Slide ${slide.slideNumber}")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("Slide Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editSubtitle,
                    onValueChange = { editSubtitle = it },
                    label = { Text("Subtitle / Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Layout picker chips
                Text("Slide Layout", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = editLayout == SlideLayout.TITLE_SLIDE,
                        onClick = { editLayout = SlideLayout.TITLE_SLIDE },
                        label = { Text("Title", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = editLayout == SlideLayout.TITLE_AND_CONTENT,
                        onClick = { editLayout = SlideLayout.TITLE_AND_CONTENT },
                        label = { Text("Content", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = editLayout == SlideLayout.TWO_COLUMN,
                        onClick = { editLayout = SlideLayout.TWO_COLUMN },
                        label = { Text("2-Col", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = editLayout == SlideLayout.BIG_STAT,
                        onClick = { editLayout = SlideLayout.BIG_STAT },
                        label = { Text("Hero", fontSize = 11.sp) }
                    )
                }

                // Bullet Points List
                Text("Bullet Points (${bulletsList.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                bulletsList.forEachIndexed { idx, bullet ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${idx + 1}.", fontSize = 12.sp, modifier = Modifier.width(20.dp), color = TextSecondary)
                        OutlinedTextField(
                            value = bullet,
                            onValueChange = { updated ->
                                val copy = bulletsList.toMutableList()
                                copy[idx] = updated
                                bulletsList = copy
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(onClick = {
                            val copy = bulletsList.toMutableList()
                            copy.removeAt(idx)
                            bulletsList = copy
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Add bullet point
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newBulletText,
                        onValueChange = { newBulletText = it },
                        placeholder = { Text("Add new point...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (newBulletText.isNotBlank()) {
                                bulletsList = (bulletsList + newBulletText.trim()).toMutableList()
                                newBulletText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = PptOrange)
                    }
                }

                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text("Presenter Speaker Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedElements = OfficeDocumentEngine.buildDefaultSlideElements(
                        title = editTitle.ifBlank { "Slide" },
                        subtitle = editSubtitle,
                        bulletPoints = bulletsList.filter { it.isNotBlank() },
                        layout = editLayout
                    )
                    val updated = slide.copy(
                        title = editTitle.ifBlank { "Slide" },
                        subtitle = editSubtitle,
                        bulletPoints = bulletsList.filter { it.isNotBlank() },
                        notes = editNotes,
                        layoutType = editLayout,
                        elements = updatedElements
                    )
                    onSaveSlide(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PptOrange)
            ) {
                Text("Save Changes", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyPresentationPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Icon(
            Icons.Default.Slideshow,
            contentDescription = null,
            tint = PptOrange,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No presentation slides found",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
