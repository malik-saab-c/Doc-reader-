package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.engine.PdfEngine
import com.example.engine.PdfReadingMode
import com.example.model.DocumentItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    document: DocumentItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var totalPages by remember { mutableIntStateOf(1) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var readingMode by remember { mutableStateOf(PdfReadingMode.NORMAL) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Load PDF renderer safely
    LaunchedEffect(document.filePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val (r, p) = PdfEngine.openRenderer(context, document.filePath)
            renderer = r
            pfd = p
            totalPages = r?.pageCount ?: 1
        }
        isLoading = false
    }

    // Render page when index or reading mode changes
    LaunchedEffect(renderer, currentPageIndex, readingMode) {
        val r = renderer ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val bmp = PdfEngine.renderPageToBitmap(
                renderer = r,
                pageIndex = currentPageIndex,
                destWidth = 1080,
                destHeight = 1500,
                mode = readingMode
            )
            withContext(Dispatchers.Main) {
                currentBitmap = bmp
                // Reset zoom on page flip
                scale = 1f
                offsetX = 0f
                offsetY = 0f
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.close()
            pfd?.close()
        }
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
                            text = "Page ${currentPageIndex + 1} of $totalPages • 100% Offline",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pdf_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // Reading Mode Toggle (Day / Sepia / Night)
                    IconButton(
                        onClick = {
                            readingMode = when (readingMode) {
                                PdfReadingMode.NORMAL -> PdfReadingMode.SEPIA
                                PdfReadingMode.SEPIA -> PdfReadingMode.NIGHT_MODE
                                PdfReadingMode.NIGHT_MODE -> PdfReadingMode.NORMAL
                            }
                        }
                    ) {
                        Icon(
                            imageVector = when (readingMode) {
                                PdfReadingMode.NORMAL -> Icons.Default.WbSunny
                                PdfReadingMode.SEPIA -> Icons.Default.AutoStories
                                PdfReadingMode.NIGHT_MODE -> Icons.Default.DarkMode
                            },
                            contentDescription = "Reading Mode",
                            tint = BrandAccent
                        )
                    }

                    // Rotate
                    IconButton(onClick = { rotationAngle = (rotationAngle + 90f) % 360f }) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Rotate",
                            tint = TextPrimary
                        )
                    }

                    // Thumbnails toggle
                    IconButton(onClick = { showThumbnails = !showThumbnails }) {
                        Icon(
                            imageVector = Icons.Default.ViewCarousel,
                            contentDescription = "Thumbnails",
                            tint = if (showThumbnails) BrandAccent else TextPrimary
                        )
                    }

                    // Share
                    IconButton(onClick = {
                        sharePdfFile(context, document.filePath, document.name)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextPrimary
                        )
                    }

                    // Info
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
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
                    // Page Thumbnails Strip
                    AnimatedVisibility(visible = showThumbnails) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            items(totalPages) { pageIdx ->
                                val isSelected = pageIdx == currentPageIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) BrandAccentLight else SoftSurface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) BrandAccent else CardBorder
                                    ),
                                    modifier = Modifier
                                        .size(width = 54.dp, height = 72.dp)
                                        .clickable { currentPageIndex = pageIdx }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${pageIdx + 1}",
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) BrandAccent else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Navigation & Page Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                contentDescription = "Previous Page"
                            )
                        }

                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = { currentPageIndex = it.toInt() },
                            valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                            steps = (totalPages - 2).coerceAtLeast(0),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        IconButton(
                            onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                            enabled = currentPageIndex < totalPages - 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = "Next Page"
                            )
                        }
                    }
                }
            }
        },
        containerColor = when (readingMode) {
            PdfReadingMode.NORMAL -> Color(0xFFF8FAFC)
            PdfReadingMode.SEPIA -> Color(0xFFFBF0D9)
            PdfReadingMode.NIGHT_MODE -> Color(0xFF0F172A)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 4.0f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = BrandAccent)
            } else if (currentBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = if (readingMode == PdfReadingMode.NIGHT_MODE) 0.dp else 4.dp,
                    color = PureWhite,
                    modifier = Modifier
                        .padding(16.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                            rotationZ = rotationAngle
                        )
                ) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = "Unable to render PDF page",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }

            // Zoom reset floating indicator
            if (scale != 1f || rotationAngle != 0f) {
                FloatingActionButton(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        rotationAngle = 0f
                    },
                    containerColor = PureWhite,
                    contentColor = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOutMap,
                        contentDescription = "Reset Zoom"
                    )
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("PDF Document Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File Name: ${document.name}")
                    Text("Pages: $totalPages")
                    Text("Size: ${document.formattedSize}")
                    Text("Engine: Android Native PdfRenderer (Hardware Accelerated)")
                    Text("Status: 100% Offline Sandboxed")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun sharePdfFile(context: Context, filePath: String, name: String) {
    try {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share $name"))
    } catch (e: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Sharing Document: $name")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
    }
}
