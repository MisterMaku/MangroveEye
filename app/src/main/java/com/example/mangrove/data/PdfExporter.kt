package com.example.mangrove.data

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Locale

/** "Save Log to PDF" -> Downloads/mangrove-assessment-log.pdf */
object PdfExporter {
    fun save(ctx: Context, list: List<Assessment>): Boolean {
        val doc = PdfDocument()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val fmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US)
        var n = 1
        fun newPage() = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n++).create())
        var page = newPage()
        var y = 56f

        p.textSize = 16f; p.isFakeBoldText = true; p.color = Color.rgb(28, 43, 36)
        page.canvas.drawText("MangroveEye — Assessment Log", 48f, y, p)
        y += 20f
        p.textSize = 10f; p.isFakeBoldText = false; p.color = Color.GRAY
        page.canvas.drawText(Config.SITE_NAME, 48f, y, p)
        y += 30f

        for (a in list) {
            if (y > 720f) { doc.finishPage(page); page = newPage(); y = 56f }
            val c = page.canvas
            val top = y
            p.color = Color.rgb(220, 214, 198); c.drawLine(48f, y, 548f, y, p); y += 18f
            // preview photo on the left, so the reader can tell which photo this row came from
            val tb = if (a.thumb.isEmpty()) null else BitmapFactory.decodeFile(Thumbs.file(ctx, a.thumb).path)
            if (tb != null) {
                val w = 64f
                val h = w * tb.height / tb.width
                c.drawBitmap(tb, null, RectF(48f, top + 8f, 48f + w, top + 8f + h), null)
            }
            val tx = 124f
            p.color = when (a.status) {
                Status.HEALTHY -> Color.rgb(74, 124, 89)
                Status.UNHEALTHY -> Color.rgb(184, 73, 58)
            }
            c.drawCircle(tx - 8f, y - 4f, 4f, p)
            p.color = Color.rgb(28, 43, 36); p.textSize = 12f; p.isFakeBoldText = true
            c.drawText(a.name, tx, y, p)
            p.textSize = 9.5f; p.isFakeBoldText = false; p.color = Color.GRAY
            c.drawText(fmt.format(a.timeMillis), 400f, y, p)
            y += 16f
            p.textSize = 9f; p.isFakeBoldText = true
            c.drawText("STATUS", tx, y, p); c.drawText("SUGGESTED ACTION", tx + 84f, y, p)
            y += 13f
            p.textSize = 10f; p.isFakeBoldText = false; p.color = Color.rgb(28, 43, 36)
            c.drawText("${a.status.label} (${a.healthy}% healthy)", tx, y, p)
            c.drawText(a.action, tx + 84f, y, p)
            y += 13f
            p.color = Color.GRAY; p.textSize = 9f
            c.drawText("GPS: %.6f, %.6f".format(Locale.US, a.lat, a.lon), tx, y, p)
            y += 13f
            c.drawText("Tiles: ${a.tilesText}", tx, y, p)
            a.note?.let { y += 13f; p.color = Color.rgb(138, 90, 30); c.drawText(it, tx, y, p) }
            y += 24f
        }
        doc.finishPage(page)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "mangrove-assessment-log.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        val ok = uri != null && ctx.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it); true } == true
        doc.close()
        return ok
    }
}