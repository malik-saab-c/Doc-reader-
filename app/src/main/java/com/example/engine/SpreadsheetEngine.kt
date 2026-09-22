package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class SpreadsheetData(
    val headers: List<String>,
    val rows: List<List<String>>,
    val title: String = "Spreadsheet"
) {
    val rowCount: Int get() = rows.size
    val colCount: Int get() = headers.size
}

data class ColumnStatistics(
    val columnName: String,
    val totalCount: Int,
    val numericCount: Int,
    val sum: Double?,
    val average: Double?,
    val min: Double?,
    val max: Double?
)

object SpreadsheetEngine {
    private const val TAG = "SpreadsheetEngine"

    fun loadSpreadsheet(context: Context, pathOrUri: String): SpreadsheetData {
        return try {
            val inputStream: InputStream? = if (pathOrUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))
            } else {
                val f = File(pathOrUri)
                if (f.exists()) f.inputStream() else null
            }

            if (inputStream == null) return emptySpreadsheet("File not found")

            val lower = pathOrUri.lowercase()
            when {
                lower.endsWith(".xlsx") || lower.endsWith(".xls") -> parseXlsx(inputStream)
                lower.endsWith(".tsv") -> parseCsv(inputStream, delimiter = '\t')
                else -> parseCsv(inputStream, delimiter = ',')
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading spreadsheet: ${e.message}", e)
            emptySpreadsheet("Error: ${e.message}")
        }
    }

    fun parseCsv(inputStream: InputStream, delimiter: Char = ','): SpreadsheetData {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val allRows = mutableListOf<List<String>>()

        var line = reader.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                allRows.add(parseCsvLine(line, delimiter))
            }
            line = reader.readLine()
        }
        reader.close()

        if (allRows.isEmpty()) {
            return emptySpreadsheet("Empty CSV")
        }

        val headers = allRows.first()
        val dataRows = if (allRows.size > 1) allRows.drop(1) else emptyList()

        return SpreadsheetData(
            headers = headers,
            rows = dataRows
        )
    }

    fun parseCsvString(csvContent: String, delimiter: Char = ','): SpreadsheetData {
        return parseCsv(csvContent.byteInputStream(), delimiter)
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val curVal = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val ch = line[i]
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == delimiter && !inQuotes) {
                result.add(curVal.toString().trim())
                curVal.setLength(0)
            } else {
                curVal.append(ch)
            }
        }
        result.add(curVal.toString().trim())
        return result
    }

    /**
     * Pure Kotlin XLSX parser without any heavy external libraries.
     * Uses ZipInputStream to read sharedStrings.xml and sheet1.xml.
     */
    fun parseXlsx(inputStream: InputStream): SpreadsheetData {
        val sharedStrings = mutableListOf<String>()
        val sheetXmlBytes = mutableMapOf<String, ByteArray>()

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedStrings.addAll(parseSharedStringsXml(zis.readBytes().inputStream()))
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheetXmlBytes[name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            zis.close()

            val primarySheet = sheetXmlBytes["xl/worksheets/sheet1.xml"]
                ?: sheetXmlBytes.values.firstOrNull()

            if (primarySheet != null) {
                return parseSheetXml(primarySheet.inputStream(), sharedStrings)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in pure XLSX parser: ${e.message}", e)
        }

        return emptySpreadsheet("Unable to parse XLSX")
    }

    private fun parseSharedStringsXml(stream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            var eventType = parser.eventType
            var inT = false
            val currentString = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "t") inT = true
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) currentString.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "t") inT = false
                        else if (tagName == "si") {
                            strings.add(currentString.toString())
                            currentString.setLength(0)
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sharedStrings: ${e.message}", e)
        }
        return strings
    }

    private fun parseSheetXml(stream: InputStream, sharedStrings: List<String>): SpreadsheetData {
        val table = mutableMapOf<Int, MutableMap<Int, String>>()
        var maxCol = 0

        try {
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            var eventType = parser.eventType

            var currentRow = 0
            var currentCol = 0
            var isSharedString = false
            var inValue = false
            var currentVal = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "row") {
                            val rStr = parser.getAttributeValue(null, "r")
                            currentRow = rStr?.toIntOrNull() ?: (currentRow + 1)
                            if (!table.containsKey(currentRow)) {
                                table[currentRow] = mutableMapOf()
                            }
                        } else if (tagName == "c") {
                            val cellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCol = parseColumnIndex(cellRef)
                            if (currentCol > maxCol) maxCol = currentCol
                            val type = parser.getAttributeValue(null, "t")
                            isSharedString = (type == "s")
                            currentVal.setLength(0)
                        } else if (tagName == "v") {
                            inValue = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inValue) {
                            currentVal.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "v") {
                            inValue = false
                            val text = currentVal.toString()
                            val finalVal = if (isSharedString) {
                                val idx = text.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else text
                            } else {
                                text
                            }
                            table[currentRow]?.put(currentCol, finalVal)
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sheet XML: ${e.message}", e)
        }

        if (table.isEmpty()) return emptySpreadsheet("Empty Sheet")

        val sortedRowKeys = table.keys.sorted()
        val numCols = (maxCol + 1).coerceAtLeast(1)

        val allRows = mutableListOf<List<String>>()
        for (r in sortedRowKeys) {
            val colMap = table[r] ?: emptyMap()
            val rowList = (0 until numCols).map { c -> colMap[c] ?: "" }
            allRows.add(rowList)
        }

        val headers = allRows.firstOrNull() ?: (0 until numCols).map { "Col ${it + 1}" }
        val dataRows = if (allRows.size > 1) allRows.drop(1) else emptyList()

        return SpreadsheetData(headers = headers, rows = dataRows)
    }

    private fun parseColumnIndex(cellRef: String): Int {
        var col = 0
        val upper = cellRef.uppercase()
        for (ch in upper) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else break
        }
        return (col - 1).coerceIn(0, 1024)
    }

    fun computeColumnStats(data: SpreadsheetData, colIndex: Int): ColumnStatistics {
        if (colIndex !in data.headers.indices) {
            return ColumnStatistics("Invalid", 0, 0, null, null, null, null)
        }

        val colName = data.headers[colIndex]
        val values = data.rows.mapNotNull { row ->
            if (colIndex < row.size) row[colIndex] else null
        }

        val numbers = values.mapNotNull {
            it.replace("$", "").replace(",", "").toDoubleOrNull()
        }

        val sum = if (numbers.isNotEmpty()) numbers.sum() else null
        val avg = if (numbers.isNotEmpty()) numbers.average() else null
        val min = if (numbers.isNotEmpty()) numbers.minOrNull() else null
        val max = if (numbers.isNotEmpty()) numbers.maxOrNull() else null

        return ColumnStatistics(
            columnName = colName,
            totalCount = values.size,
            numericCount = numbers.size,
            sum = sum,
            average = avg,
            min = min,
            max = max
        )
    }

    fun exportToCsv(data: SpreadsheetData): String {
        val sb = StringBuilder()
        sb.append(data.headers.joinToString(",") { escapeCsvCell(it) })
        sb.append("\n")
        for (row in data.rows) {
            sb.append(row.joinToString(",") { escapeCsvCell(it) })
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun escapeCsvCell(cell: String): String {
        return if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
    }

    fun emptySpreadsheet(message: String = "No Data"): SpreadsheetData {
        return SpreadsheetData(
            headers = listOf("A", "B", "C", "D"),
            rows = listOf(
                listOf(message, "", "", ""),
                listOf("", "", "", "")
            )
        )
    }
}
