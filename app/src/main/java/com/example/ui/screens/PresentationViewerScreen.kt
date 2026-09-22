package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.OfficeDocumentEngine
import com.example.engine.PresentationData
import com.example.engine.SlideItem
import com.example.model.DocumentItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationViewerScreen(
    document: DocumentItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var presentationData by remember { mutableStateOf<PresentationData?>(null) }
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    var isSlideshowActive by remember { mutableStateOf(false) }
    var autoPlaySlideshow by remember { mutableStateOf(false) }

    LaunchedEffect(document.filePath) {
        withContext(Dispatchers.IO) {
            val data = OfficeDocumentEngine.loadPresentation(context, document.filePath)
            presentationData = data
        }
    }

    // Auto-advance slideshow timer
    LaunchedEffect(autoPlaySlideshow, currentSlideIndex) {
        if (autoPlaySlideshow) {
            delay(4000)
            val total = presentationData?.slides?.size ?: 0
            if (total > 0) {
                currentSlideIndex = (currentSlideIndex + 1) % total
            }
        }
    }

    val slides = presentationData?.slides ?: emptyList()
    val currentSlide = slides.getOrNull(currentSlideIndex)

    if (isSlideshowActive && currentSlide != null) {
        // Fullscreen Slideshow View
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .clickable {
                    // Tap to advance
                    val total = slides.size
                    if (total > 0) currentSlideIndex = (currentSlideIndex + 1) % total
                }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = currentSlide.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                currentSlide.bulletPoints.forEach { point ->
                    Text(
                        text = "• $point",
                        fontSize = 18.sp,
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { isSlideshowActive = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Slideshow", tint = PureWhite)
                }
                Text(
                    text = "Slide ${currentSlideIndex + 1} of ${slides.size}",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
                IconButton(onClick = { autoPlaySlideshow = !autoPlaySlideshow }) {
                    Icon(
                        imageVector = if (autoPlaySlideshow) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Autoplay",
                        tint = PureWhite
                    )
                }
            }
        }
    } else {
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
                                text = "Slide ${currentSlideIndex + 1} of ${slides.size.coerceAtLeast(1)} • Pure Offline PPTX",
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
                        // Play Slideshow
                        IconButton(onClick = { isSlideshowActive = true }) {
                            Icon(Icons.Default.Slideshow, contentDescription = "Play Slideshow", tint = PptOrange)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
                )
            },
            bottomBar = {
                Surface(
                    color = PureWhite,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Slide Thumbnails Strip
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            itemsIndexed(slides) { idx, slide ->
                                val isSelected = idx == currentSlideIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PptOrangeLight else SoftSurface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) PptOrange else CardBorder
                                    ),
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 52.dp)
                                        .clickable { currentSlideIndex = idx }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Text(
                                            text = "Slide ${idx + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) PptOrange else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Navigation buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { if (currentSlideIndex > 0) currentSlideIndex-- },
                                enabled = currentSlideIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous")
                            }

                            val displayCurrent = if (slides.isEmpty()) 0 else (currentSlideIndex + 1).coerceAtMost(slides.size)
                            Text(
                                text = "Slide $displayCurrent / ${slides.size}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )

                            IconButton(
                                onClick = { if (currentSlideIndex < slides.size - 1) currentSlideIndex++ },
                                enabled = currentSlideIndex < slides.size - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next")
                            }
                        }
                    }
                }
            },
            containerColor = OffWhite
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentSlide != null) {
                    // Presentation Slide Card Canvas
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PureWhite,
                        shadowElevation = 4.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentSlide.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            currentSlide.bulletPoints.forEach { point ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("•", color = PptOrange, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                                    Text(
                                        text = point,
                                        fontSize = 14.sp,
                                        color = TextSecondary,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    CircularProgressIndicator(color = PptOrange)
                }
            }
        }
    }
}
