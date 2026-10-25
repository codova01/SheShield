package com.example.sheshield.data

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import java.io.File

object ComplaintPdf {

    private const val PAGE_W = 595   // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 48

    /** Writes the text to a PDF in the app cache and returns it, or null if something failed. */
    fun create(context: Context, text: String): File? {
        val dir = File(context.cacheDir, "complaints").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // keep only the latest copy
        val file = File(dir, "SheShield_complaint_${System.currentTimeMillis()}.pdf")

        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val footer = TextPaint().apply {
            color = Color.GRAY
            textSize = 9f
            isAntiAlias = true
        }
        val width = PAGE_W - 2 * MARGIN
        val usable = PAGE_H - 2 * MARGIN - 12
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setLineSpacing(2f, 1f)
            .build()

        val doc = PdfDocument()
        return try {
            var line = 0
            var pageNo = 1
            while (line < layout.lineCount) {
                val top = layout.getLineTop(line)
                var end = line
                while (end + 1 < layout.lineCount && layout.getLineBottom(end + 1) - top <= usable) end++
                val bottom = layout.getLineBottom(end)

                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                val canvas = page.canvas
                canvas.save()
                canvas.translate(MARGIN.toFloat(), MARGIN.toFloat() - top)
                canvas.clipRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat())
                layout.draw(canvas)
                canvas.restore()
                canvas.drawText("SheShield complaint draft  -  page $pageNo", MARGIN.toFloat(), (PAGE_H - 24).toFloat(), footer)
                doc.finishPage(page)

                line = end + 1
                pageNo++
            }
            file.outputStream().use { doc.writeTo(it) }
            file
        } catch (e: Exception) {
            Log.e("SheShield", "Could not create complaint PDF", e)
            null
        } finally {
            doc.close()
        }
    }
}