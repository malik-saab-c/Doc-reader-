package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream

enum class PdfReadingMode {
    NORMAL,
    SEPIA,
    NIGHT_MODE
}

object PdfEngine {
    private const val TAG = "PdfEngine"

    fun openRenderer(context: Context, filePathOrUri: String): Pair<PdfRenderer?, ParcelFileDescriptor?> {
        return try {
            val pfd: ParcelFileDescriptor? = if (filePathOrUri.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(filePathOrUri), "r")
            } else {
                val file = File(filePathOrUri)
                if (file.exists()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else null
            }

            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                Pair(renderer, pfd)
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening PDF: ${e.message}", e)
            Pair(null, null)
        }
    }

    fun renderPageToBitmap(
        renderer: PdfRenderer,
        pageIndex: Int,
        destWidth: Int = 1200,
        destHeight: Int = 1600,
        mode: PdfReadingMode = PdfReadingMode.NORMAL
    ): Bitmap? {
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
        var page: PdfRenderer.Page? = null
        return try {
            page = renderer.openPage(pageIndex)
            val pWidth = page.width.coerceAtLeast(1)
            val pHeight = page.height.coerceAtLeast(1)
            val aspect = pWidth.toFloat() / pHeight.toFloat()
            val finalWidth = destWidth.coerceIn(300, 2048)
            val calculatedHeight = if (aspect > 0.1f) (finalWidth / aspect).toInt() else 1200
            val finalHeight = calculatedHeight.coerceIn(300, 3200)

            val baseBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(baseBitmap)
            canvas.drawColor(Color.WHITE)

            page.render(baseBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            when (mode) {
                PdfReadingMode.NORMAL -> baseBitmap
                PdfReadingMode.SEPIA -> applySepia(baseBitmap)
                PdfReadingMode.NIGHT_MODE -> applyInverted(baseBitmap)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error rendering page safely: ${t.message}", t)
            null
        } finally {
            try {
                page?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing PDF page: ${e.message}")
            }
        }
    }

    private fun applySepia(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        val sepiaMatrix = ColorMatrix()
        sepiaMatrix.set(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 40f,
                0.349f, 0.686f, 0.168f, 0f, 25f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(sepiaMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    private fun applyInverted(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(invertMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    /**
     * Creates a real, offline vector PDF document with multi-page support using Android's native PdfDocument.
     */
    fun createPdfDocument(
        outputFile: File,
        title: String,
        author: String = "Sir Ghulam Mustafa",
        contentSections: List<Pair<String, String>>
    ): Boolean {
        val pdfDoc = PdfDocument()
        val pageWidth = 595 // A4 standard point width
        val pageHeight = 842 // A4 standard point height
        val margin = 50f

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 11f
            isAntiAlias = true
        }

        val headingPaint = Paint().apply {
            color = Color.rgb(37, 99, 235)
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 11f
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1.5f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDoc.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = margin + 30f

        // Draw Header
        canvas.drawText(title, margin, currentY, titlePaint)
        currentY += 20f
        canvas.drawText("Generated Offline • Architect: $author • Date: ${java.util.Date()}", margin, currentY, metaPaint)
        currentY += 15f
        canvas.drawLine(margin, currentY, pageWidth - margin, currentY, linePaint)
        currentY += 30f

        for ((heading, body) in contentSections) {
            // Check if we need a new page
            if (currentY > pageHeight - margin - 80f) {
                // Draw footer page number
                canvas.drawText("Page $pageNumber", pageWidth / 2f - 20f, pageHeight - 30f, metaPaint)
                pdfDoc.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDoc.startPage(pageInfo)
                canvas = page.canvas
                currentY = margin + 30f
            }

            if (heading.isNotBlank()) {
                canvas.drawText(heading, margin, currentY, headingPaint)
                currentY += 20f
            }

            // Word wrap body text
            val maxTextWidth = pageWidth - (2 * margin)
            val words = body.split(" ")
            var line = StringBuilder()

            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val measure = bodyPaint.measureText(testLine)
                if (measure < maxTextWidth) {
                    line = StringBuilder(testLine)
                } else {
                    canvas.drawText(line.toString(), margin, currentY, bodyPaint)
                    currentY += 16f
                    line = StringBuilder(word)

                    if (currentY > pageHeight - margin - 40f) {
                        canvas.drawText("Page $pageNumber", pageWidth / 2f - 20f, pageHeight - 30f, metaPaint)
                        pdfDoc.finishPage(page)

                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = pdfDoc.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = margin + 30f
                    }
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line.toString(), margin, currentY, bodyPaint)
                currentY += 24f
            }
        }

        // Draw last page footer
        canvas.drawText("Page $pageNumber", pageWidth / 2f - 20f, pageHeight - 30f, metaPaint)
        pdfDoc.finishPage(page)

        return try {
            outputFile.parentFile?.mkdirs()
            val fos = FileOutputStream(outputFile)
            pdfDoc.writeTo(fos)
            fos.close()
            pdfDoc.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving generated PDF: ${e.message}", e)
            pdfDoc.close()
            false
        }
    }
}
