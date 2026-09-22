package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocumentItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    document: DocumentItem,
    onBack: () -> Unit,
    onSaveContent: (String) -> Unit
) {
    val context = LocalContext.current
    var textContent by remember { mutableStateOf("") }
    var undoStack by remember { mutableStateOf(listOf<String>()) }
    var redoStack by remember { mutableStateOf(listOf<String>()) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showFindReplaceDialog by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var messageBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(document.filePath) {
        withContext(Dispatchers.IO) {
            val file = File(document.filePath)
            val initial = if (file.exists()) file.readText() else ""
            withContext(Dispatchers.Main) {
                textContent = initial
                undoStack = listOf(initial)
            }
        }
    }

    val lines = remember(textContent) { textContent.lines() }
    val lineCount = lines.size
    val charCount = textContent.length
    val wordCount = remember(textContent) {
        textContent.split("\\s+".toRegex()).count { it.isNotBlank() }
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
                            text = "$lineCount lines • $wordCount words • $charCount chars",
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
                    // Undo
                    IconButton(
                        onClick = {
                            if (undoStack.size > 1) {
                                val current = undoStack.last()
                                val previous = undoStack[undoStack.size - 2]
                                redoStack = redoStack + listOf(current)
                                undoStack = undoStack.dropLast(1)
                                textContent = previous
                                hasUnsavedChanges = true
                            }
                        },
                        enabled = undoStack.size > 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (undoStack.size > 1) TextPrimary else TextMuted)
                    }

                    // Redo
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.last()
                                redoStack = redoStack.dropLast(1)
                                undoStack = undoStack + listOf(next)
                                textContent = next
                                hasUnsavedChanges = true
                            }
                        },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (redoStack.isNotEmpty()) TextPrimary else TextMuted)
                    }

                    // Format JSON (if json file)
                    if (document.extension == "json") {
                        IconButton(
                            onClick = {
                                try {
                                    val formatted = if (textContent.trim().startsWith("[")) {
                                        JSONArray(textContent).toString(2)
                                    } else {
                                        JSONObject(textContent).toString(2)
                                    }
                                    textContent = formatted
                                    hasUnsavedChanges = true
                                    messageBanner = "JSON Formatted"
                                } catch (e: Exception) {
                                    messageBanner = "Invalid JSON"
                                }
                            }
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Format JSON", tint = CodePurple)
                        }
                    }

                    // Find and replace
                    IconButton(onClick = { showFindReplaceDialog = true }) {
                        Icon(Icons.Default.FindReplace, contentDescription = "Find & Replace", tint = TextPrimary)
                    }

                    // Save
                    IconButton(
                        onClick = {
                            onSaveContent(textContent)
                            hasUnsavedChanges = false
                            messageBanner = "File Saved Successfully"
                        },
                        enabled = hasUnsavedChanges
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (hasUnsavedChanges) BrandAccent else TextMuted
                        )
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
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasUnsavedChanges) "● Unsaved Changes" else "✓ Saved to Storage",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasUnsavedChanges) Color(0xFFE11D48) else ExcelGreen
                    )
                    Text(
                        text = "UTF-8 • ${document.extension.uppercase()}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
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
            messageBanner?.let { msg ->
                Surface(
                    color = BrandAccentLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = msg, fontSize = 12.sp, color = BrandAccent)
                        IconButton(onClick = { messageBanner = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Editor with line numbers
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Line numbers gutter
                Column(
                    modifier = Modifier
                        .background(OffWhite)
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                        .border(
                            width = 1.dp,
                            color = CardBorderSubtle,
                            shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
                        ),
                    horizontalAlignment = Alignment.End
                ) {
                    val maxLineDisplay = lineCount.coerceIn(1, 2000)
                    for (i in 1..maxLineDisplay) {
                        Text(
                            text = "$i",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            lineHeight = 20.sp
                        )
                    }
                    if (lineCount > 2000) {
                        Text(
                            text = "...",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Code Input Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    BasicTextField(
                        value = textContent,
                        onValueChange = { newText ->
                            textContent = newText
                            hasUnsavedChanges = true
                            if (undoStack.isEmpty() || undoStack.last() != newText) {
                                undoStack = (undoStack + listOf(newText)).takeLast(50)
                                redoStack = emptyList()
                            }
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(BrandAccent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showFindReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showFindReplaceDialog = false },
            title = { Text("Find & Replace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it },
                        label = { Text("Find") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = { replaceQuery = it },
                        label = { Text("Replace With") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (findQuery.isNotEmpty()) {
                            val count = textContent.split(findQuery).size - 1
                            textContent = textContent.replace(findQuery, replaceQuery)
                            hasUnsavedChanges = true
                            messageBanner = "Replaced $count occurrences"
                        }
                        showFindReplaceDialog = false
                    }
                ) {
                    Text("Replace All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFindReplaceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
