package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class CreateDocType {
    PDF,
    SPREADSHEET,
    PRESENTATION,
    TEXT_NOTE
}

@Composable
fun DocumentCreatorDialog(
    type: CreateDocType,
    onDismiss: () -> Unit,
    onCreatePdf: (title: String, body: String) -> Unit,
    onCreateSpreadsheet: (name: String, headers: List<String>, rows: List<List<String>>) -> Unit,
    onCreatePresentation: (name: String, title: String, subtitle: String, bullets: List<String>) -> Unit = { _, _, _, _ -> },
    onCreateText: (name: String, content: String, ext: String) -> Unit
) {
    var titleOrName by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var extension by remember { mutableStateOf("txt") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (type) {
                    CreateDocType.PDF -> "Create New PDF"
                    CreateDocType.SPREADSHEET -> "Create New Spreadsheet"
                    CreateDocType.PRESENTATION -> "Create PowerPoint Presentation (.pptx)"
                    CreateDocType.TEXT_NOTE -> "Create New Text File"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titleOrName,
                    onValueChange = { titleOrName = it },
                    label = {
                        Text(
                            when (type) {
                                CreateDocType.PDF -> "Document Title"
                                CreateDocType.PRESENTATION -> "Presentation Name (e.g. Sales_Deck)"
                                else -> "File Name"
                            }
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (type == CreateDocType.TEXT_NOTE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("txt", "md", "json", "csv", "kt").forEach { ext ->
                            FilterChip(
                                selected = extension == ext,
                                onClick = { extension = ext },
                                label = { Text(".$ext") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = {
                        Text(
                            when (type) {
                                CreateDocType.PDF -> "Initial Body Content"
                                CreateDocType.SPREADSHEET -> "Initial Column Headers (comma-separated)"
                                CreateDocType.PRESENTATION -> "Slide Bullet Points (one per line)"
                                CreateDocType.TEXT_NOTE -> "File Content"
                            }
                        )
                    },
                    placeholder = {
                        if (type == CreateDocType.PRESENTATION) {
                            Text("Point 1\nPoint 2\nPoint 3", fontSize = 13.sp)
                        }
                    },
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = titleOrName.ifBlank { "New_Document" }
                    when (type) {
                        CreateDocType.PDF -> onCreatePdf(finalName, contentText)
                        CreateDocType.SPREADSHEET -> {
                            val headers = if (contentText.isNotBlank()) {
                                contentText.split(",").map { it.trim() }
                            } else {
                                listOf("Item", "Category", "Quantity", "Price", "Total")
                            }
                            val sampleRow = headers.map { "" }
                            onCreateSpreadsheet(finalName, headers, listOf(sampleRow))
                        }
                        CreateDocType.PRESENTATION -> {
                            val bullets = contentText.lines().map { it.trim() }.filter { it.isNotBlank() }
                            onCreatePresentation(
                                finalName,
                                finalName.replace("_", " "),
                                "Created with Universal Slide Studio",
                                bullets
                            )
                        }
                        CreateDocType.TEXT_NOTE -> onCreateText(finalName, contentText, extension)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                Text("Create Document")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
