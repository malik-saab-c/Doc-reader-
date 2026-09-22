package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReaderScreen(
    document: DocumentItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var docData by remember { mutableStateOf<WordDocumentData?>(null) }
    var fontSizeSp by remember { mutableFloatStateOf(16f) }
    var selectedFontFamily by remember { mutableStateOf(FontFamily.SansSerif) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var showFormattingMenu by remember { mutableStateOf(false) }

    // Offline Text to Speech
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

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

    LaunchedEffect(document.filePath) {
        withContext(Dispatchers.IO) {
            val data = OfficeDocumentEngine.loadWordDocument(context, document.filePath)
            docData = data
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
                            text = "${docData?.wordCount ?: 0} words • ~${docData?.readTimeMinutes ?: 1} min read",
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
                    // Search
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) BrandAccent else TextPrimary
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
                            tint = if (isSpeaking) BrandAccent else TextPrimary
                        )
                    }

                    // Formatting dialog
                    IconButton(onClick = { showFormattingMenu = true }) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Font settings", tint = TextPrimary)
                    }

                    // Share Text
                    IconButton(
                        onClick = {
                            val text = docData?.fullText ?: ""
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Document"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        bottomBar = {
            Surface(
                color = PureWhite,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Native DOCX/Text Engine",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${docData?.characterCount ?: 0} Characters",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        },
        containerColor = PureWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Row
            AnimatedVisibility(visible = isSearchActive) {
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
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WordBlue,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            val data = docData
            if (data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WordBlue)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(data.sections) { section ->
                        RenderDocxSection(
                            section = section,
                            fontSizeSp = fontSizeSp,
                            fontFamily = selectedFontFamily,
                            searchHighlight = searchQuery
                        )
                    }
                }
            }
        }
    }

    if (showFormattingMenu) {
        AlertDialog(
            onDismissRequest = { showFormattingMenu = false },
            title = { Text("Reading Typography") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Font Size: ${fontSizeSp.toInt()} sp", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 12f..26f,
                        steps = 7,
                        colors = SliderDefaults.colors(thumbColor = WordBlue, activeTrackColor = WordBlue)
                    )

                    Text("Font Family", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormattingMenu = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun RenderDocxSection(
    section: DocxSection,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    searchHighlight: String
) {
    val isHighlighted = searchHighlight.isNotBlank() && section.text.contains(searchHighlight, ignoreCase = true)

    val backgroundModifier = if (isHighlighted) {
        Modifier.background(Color(0xFFFEF08A), shape = RoundedCornerShape(4.dp))
    } else Modifier

    when (section.type) {
        SectionType.TITLE -> {
            Text(
                text = section.text,
                fontSize = (fontSizeSp + 8).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                color = TextPrimary,
                lineHeight = (fontSizeSp + 14).sp,
                modifier = backgroundModifier.padding(vertical = 4.dp)
            )
        }
        SectionType.HEADING1 -> {
            Text(
                text = section.text,
                fontSize = (fontSizeSp + 4).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                color = WordBlue,
                lineHeight = (fontSizeSp + 10).sp,
                modifier = backgroundModifier.padding(top = 10.dp, bottom = 4.dp)
            )
        }
        SectionType.HEADING2 -> {
            Text(
                text = section.text,
                fontSize = (fontSizeSp + 2).sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = fontFamily,
                color = TextPrimary,
                modifier = backgroundModifier.padding(top = 6.dp)
            )
        }
        SectionType.BULLET_ITEM -> {
            Row(modifier = backgroundModifier.padding(start = 8.dp)) {
                Text(
                    text = "•",
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    color = WordBlue,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = section.text,
                    fontSize = fontSizeSp.sp,
                    fontFamily = fontFamily,
                    color = TextPrimary,
                    lineHeight = (fontSizeSp * 1.5).sp
                )
            }
        }
        SectionType.CODE_BLOCK -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SoftSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = section.text,
                    fontSize = (fontSizeSp - 2).sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        SectionType.PARAGRAPH -> {
            Text(
                text = section.text,
                fontSize = fontSizeSp.sp,
                fontFamily = fontFamily,
                fontWeight = if (section.isBold) FontWeight.Bold else FontWeight.Normal,
                color = TextPrimary,
                lineHeight = (fontSizeSp * 1.55).sp,
                modifier = backgroundModifier
            )
        }
    }
}
