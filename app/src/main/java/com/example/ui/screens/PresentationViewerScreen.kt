package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.OfficeDocumentEngine
import com.example.engine.PresentationData
import com.example.engine.SlideItem
import com.example.engine.SlideLayout
import com.example.model.DocumentItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var presentationData by remember { mutableStateOf<PresentationData?>(null) }
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    var viewMode by remember { mutableStateOf(PresentationViewMode.SLIDE_VIEW) }
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

    // Search and notes
    var searchQuery by remember { mutableStateOf("") }
    var showNotesDrawer by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

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
                // Slide Canvas in Presentation Mode
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
                // Black screen message
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

            // Interactive tap overlay: Left side = Prev, Right side = Next, Center = Toggle HUD
            Row(
                modifier = Modifier
                    .fillMaxSize()
            ) {
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
                    color = Color.Black.copy(alpha = 0.75f),
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
                            // Laser Pointer Toggle
                            IconButton(onClick = { isLaserActive = !isLaserActive }) {
                                Icon(
                                    imageVector = Icons.Default.Highlight,
                                    contentDescription = "Laser Pointer",
                                    tint = if (isLaserActive) Color.Red else Color.White
                                )
                            }

                            // Blank Screen Toggle (PowerPoint 'B' key equivalent)
                            IconButton(onClick = { isBlackScreenActive = !isBlackScreenActive }) {
                                Icon(
                                    imageVector = Icons.Default.Brightness2,
                                    contentDescription = "Blank Screen",
                                    tint = if (isBlackScreenActive) selectedTheme.accentColor else Color.White
                                )
                            }

                            // Autoplay Toggle
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
    // 2. DESKTOP WORKSPACE PRESENTATION VIEWER
    // ==========================================
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
                            text = "Slide ${currentSlideIndex + 1} of ${allSlides.size.coerceAtLeast(1)} • ${selectedAspect.label}",
                            fontSize = 12.sp,
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

                    // Fullscreen Presentation Mode (Desktop F5 Slide Show)
                    IconButton(onClick = { isSlideshowActive = true }) {
                        Icon(
                            imageVector = Icons.Default.Slideshow,
                            contentDescription = "Start Slide Show",
                            tint = PptOrange
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
}

/**
 * Desktop-Grade High Fidelity Presentation Slide Canvas.
 * Supports Title Slide layout, Two-Column comparison layout, and standard card layout.
 */
@Composable
fun SlideCanvasCard(
    slide: SlideItem,
    totalSlides: Int,
    theme: PptTheme,
    aspectRatio: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = theme.cardBg,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier
            .aspectRatio(aspectRatio)
            .shadow(12.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Slide Header: Category Pill & Slide Index
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = theme.badgeBg
                ) {
                    Text(
                        text = slide.categoryTag.ifBlank { "SLIDE ${"%02d".format(slide.slideNumber)}" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${slide.slideNumber} / $totalSlides",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textColor.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Slide Content Based on Layout
            when (slide.layoutType) {
                SlideLayout.TITLE_SLIDE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = slide.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = theme.titleColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        )

                        if (slide.subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = slide.subtitle,
                                fontSize = 14.sp,
                                color = theme.accentColor,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(3.dp)
                                .background(theme.accentColor, RoundedCornerShape(2.dp))
                        )
                    }
                }

                SlideLayout.TWO_COLUMN -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = slide.title,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.titleColor
                        )

                        if (slide.subtitle.isNotBlank()) {
                            Text(
                                text = slide.subtitle,
                                fontSize = 12.sp,
                                color = theme.accentColor,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val mid = (slide.bulletPoints.size + 1) / 2
                        val col1 = slide.bulletPoints.take(mid)
                        val col2 = slide.bulletPoints.drop(mid)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                col1.forEach { point ->
                                    BulletPointCard(point = point, theme = theme)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                col2.forEach { point ->
                                    BulletPointCard(point = point, theme = theme)
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Standard Title and Content Slide
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = slide.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.titleColor,
                            lineHeight = 26.sp
                        )

                        if (slide.subtitle.isNotBlank()) {
                            Text(
                                text = slide.subtitle,
                                fontSize = 13.sp,
                                color = theme.accentColor,
                                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        slide.bulletPoints.forEach { point ->
                            BulletPointCard(point = point, theme = theme)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slide Footer: Corporate Branding & Confidentiality Tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(0.5.dp, theme.textColor.copy(alpha = 0.2f)),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Universal Presentation Engine • Sir Ghulam Mustafa",
                    fontSize = 9.sp,
                    color = theme.textColor.copy(alpha = 0.6f)
                )
                Text(
                    text = "Page ${slide.slideNumber} of $totalSlides",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.accentColor
                )
            }
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
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
