package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ColumnStatistics
import com.example.engine.SpreadsheetData
import com.example.engine.SpreadsheetEngine
import com.example.model.DocumentItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetViewerScreen(
    document: DocumentItem,
    onBack: () -> Unit,
    onSaveContent: (String) -> Unit
) {
    val context = LocalContext.current
    var spreadsheetData by remember { mutableStateOf<SpreadsheetData?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedColIndex by remember { mutableStateOf<Int?>(null) }
    var columnStats by remember { mutableStateOf<ColumnStatistics?>(null) }
    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (rowIdx, colIdx)
    var cellEditValue by remember { mutableStateOf("") }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var sortAscending by remember { mutableStateOf(true) }

    LaunchedEffect(document.filePath) {
        withContext(Dispatchers.IO) {
            val data = SpreadsheetEngine.loadSpreadsheet(context, document.filePath)
            spreadsheetData = data
        }
    }

    // Recalculate stats when column selection changes
    LaunchedEffect(selectedColIndex, spreadsheetData) {
        val data = spreadsheetData
        val col = selectedColIndex
        if (data != null && col != null) {
            columnStats = SpreadsheetEngine.computeColumnStats(data, col)
        } else {
            columnStats = null
        }
    }

    val horizontalScrollState = rememberScrollState()

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
                            text = "${spreadsheetData?.rowCount ?: 0} Rows • ${spreadsheetData?.colCount ?: 0} Columns • 100% Offline Grid",
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
                    // Search toggle
                    IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchVisible) BrandAccent else TextPrimary
                        )
                    }

                    // Add Row
                    IconButton(
                        onClick = {
                            val data = spreadsheetData ?: return@IconButton
                            val emptyRow = (0 until data.colCount).map { "" }
                            spreadsheetData = data.copy(rows = data.rows + listOf(emptyRow))
                            hasUnsavedChanges = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Row", tint = ExcelGreen)
                    }

                    // Save Changes
                    IconButton(
                        onClick = {
                            val data = spreadsheetData ?: return@IconButton
                            val csvStr = SpreadsheetEngine.exportToCsv(data)
                            onSaveContent(csvStr)
                            hasUnsavedChanges = false
                        },
                        enabled = hasUnsavedChanges
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (hasUnsavedChanges) ExcelGreen else TextMuted
                        )
                    }

                    // Share
                    IconButton(
                        onClick = {
                            val data = spreadsheetData ?: return@IconButton
                            val csvStr = SpreadsheetEngine.exportToCsv(data)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csvStr)
                                type = "text/csv"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Spreadsheet"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        bottomBar = {
            // Formula / Column Statistics Bar
            columnStats?.let { stats ->
                Surface(
                    color = PureWhite,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Formula Stats: ${stats.columnName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ExcelGreen
                            )
                            IconButton(
                                onClick = { selectedColIndex = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Stats", modifier = Modifier.size(16.dp))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatChip(label = "Count", value = "${stats.totalCount}")
                            stats.sum?.let { StatChip(label = "Sum", value = String.format("%.1f", it)) }
                            stats.average?.let { StatChip(label = "Avg", value = String.format("%.1f", it)) }
                            stats.min?.let { StatChip(label = "Min", value = String.format("%.1f", it)) }
                            stats.max?.let { StatChip(label = "Max", value = String.format("%.1f", it)) }
                        }
                    }
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
            AnimatedVisibility(visible = isSearchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter rows...", fontSize = 13.sp) },
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
                        focusedBorderColor = ExcelGreen,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = PureWhite,
                        unfocusedContainerColor = PureWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            val data = spreadsheetData
            if (data == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ExcelGreen)
                }
            } else {
                val filteredRows = remember(data.rows, searchQuery, selectedColIndex, sortAscending) {
                    var result = if (searchQuery.isBlank()) data.rows
                    else data.rows.filter { row ->
                        row.any { it.contains(searchQuery, ignoreCase = true) }
                    }
                    val col = selectedColIndex
                    if (col != null) {
                        result = result.sortedWith { rowA, rowB ->
                            val valA = rowA.getOrNull(col) ?: ""
                            val valB = rowB.getOrNull(col) ?: ""
                            val numA = valA.replace("$", "").replace(",", "").toDoubleOrNull()
                            val numB = valB.replace("$", "").replace(",", "").toDoubleOrNull()
                            val cmp = if (numA != null && numB != null) {
                                numA.compareTo(numB)
                            } else {
                                valA.compareTo(valB, ignoreCase = true)
                            }
                            if (sortAscending) cmp else -cmp
                        }
                    }
                    result
                }

                // Table Horizontal Scroller
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        // Header Row
                        item {
                            Row(
                                modifier = Modifier
                                    .background(OffWhite)
                                    .border(1.dp, CardBorder)
                            ) {
                                // Index header
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(44.dp)
                                        .background(Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("#", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                }

                                data.headers.forEachIndexed { colIdx, header ->
                                    val isColSelected = selectedColIndex == colIdx
                                    Surface(
                                        color = if (isColSelected) ExcelGreenLight else OffWhite,
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(44.dp)
                                            .border(0.5.dp, CardBorder)
                                            .clickable {
                                                if (isColSelected) {
                                                    sortAscending = !sortAscending
                                                } else {
                                                    selectedColIndex = colIdx
                                                    sortAscending = true
                                                }
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = header,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isColSelected) ExcelGreen else TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Icon(
                                                imageVector = if (isColSelected) {
                                                    if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                                                } else Icons.Default.Functions,
                                                contentDescription = "Sort or Calculate",
                                                tint = if (isColSelected) ExcelGreen else TextMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Data Rows
                        itemsIndexed(filteredRows) { rowIdx, row ->
                            Row(
                                modifier = Modifier
                                    .background(if (rowIdx % 2 == 0) PureWhite else Color(0xFFFAFAFA))
                                    .border(0.5.dp, CardBorderSubtle)
                            ) {
                                // Row Number
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(42.dp)
                                        .background(SoftSurface)
                                        .border(0.5.dp, CardBorderSubtle),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${rowIdx + 1}",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                // Cells
                                for (colIdx in 0 until data.colCount) {
                                    val cellText = if (colIdx < row.size) row[colIdx] else ""
                                    val isSelectedCol = selectedColIndex == colIdx

                                    Box(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(42.dp)
                                            .background(if (isSelectedCol) Color(0xFFF7FDF9) else Color.Transparent)
                                            .border(0.5.dp, CardBorderSubtle)
                                            .clickable {
                                                editingCell = Pair(rowIdx, colIdx)
                                                cellEditValue = cellText
                                            }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cellText,
                                            fontSize = 12.sp,
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
        }
    }

    // Cell Editor Dialog
    editingCell?.let { (rowIdx, colIdx) ->
        val headerName = spreadsheetData?.headers?.getOrNull(colIdx) ?: "Cell"
        AlertDialog(
            onDismissRequest = { editingCell = null },
            title = {
                Text(
                    text = "Edit $headerName (Row ${rowIdx + 1})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = cellEditValue,
                    onValueChange = { cellEditValue = it },
                    label = { Text("Cell Value") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExcelGreen,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentData = spreadsheetData
                        if (currentData != null && rowIdx in currentData.rows.indices) {
                            val row = currentData.rows[rowIdx].toMutableList()
                            while (row.size <= colIdx) row.add("")
                            row[colIdx] = cellEditValue

                            val newRows = currentData.rows.toMutableList()
                            newRows[rowIdx] = row
                            spreadsheetData = currentData.copy(rows = newRows)
                            hasUnsavedChanges = true
                        }
                        editingCell = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExcelGreen)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCell = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SoftSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = label, fontSize = 10.sp, color = TextMuted)
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}
