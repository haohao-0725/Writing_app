package com.haohao.inkjournal.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.haohao.inkjournal.data.BackgroundType
import com.haohao.inkjournal.data.NoteSettings
import com.haohao.inkjournal.data.PageData
import com.haohao.inkjournal.data.PageSpec
import com.haohao.inkjournal.data.RawStroke
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.floor

/** 紙面顏色、橫線/方格背景，以及主頁預覽圖。 */
object PagePainter {
    const val APP_BG = 0xFF101215.toInt()
    const val PAGE = 0xFF1C1F24.toInt()
    const val PAGE_EDGE = 0xFF2B2F36.toInt()
    const val LINE = 0xFF343A44.toInt()
    const val GRID = 0xFF2C313A.toInt()
    const val MUTED = 0xFF8A9099.toInt()

    /** 橫線從離頁面頂端這個距離開始（上面留一塊寫日期/標題）。 */
    const val LINED_TOP_MM = 24f
    const val LINED_BOTTOM_MM = 12f

    private val linePaint = Paint().apply { style = Paint.Style.STROKE }

    /**
     * 在螢幕座標畫背景線。left/top 是頁面左上角（px），scale 是 px/mm。
     * visTop/visBottom 是可見範圍（px），只畫看得到的線。
     */
    fun drawBackground(
        c: Canvas, left: Float, top: Float, scale: Float, s: NoteSettings,
        visTop: Float = Float.NEGATIVE_INFINITY, visBottom: Float = Float.POSITIVE_INFINITY,
        linePaint: Paint = this.linePaint,
    ) {
        val right = left + PageSpec.WIDTH_MM * scale
        val bottom = top + PageSpec.HEIGHT_MM * scale
        linePaint.isAntiAlias = true
        when (s.background) {
            BackgroundType.BLANK -> Unit
            BackgroundType.LINED -> {
                linePaint.color = LINE
                linePaint.strokeWidth = (0.16f * scale).coerceAtLeast(1f)
                val sp = s.lineSpacingMm
                val last = PageSpec.HEIGHT_MM - LINED_BOTTOM_MM
                val k0 = ceil(((visTop - top) / scale - LINED_TOP_MM) / sp).toInt().coerceAtLeast(0)
                var k = k0
                while (true) {
                    val yMm = LINED_TOP_MM + k * sp
                    if (yMm > last) break
                    val y = top + yMm * scale
                    if (y > visBottom) break
                    c.drawLine(left, y, right, y, linePaint)
                    k++
                }
            }
            BackgroundType.GRID -> {
                linePaint.color = GRID
                linePaint.strokeWidth = (0.12f * scale).coerceAtLeast(1f)
                val sp = s.gridSpacingMm
                val offX = (PageSpec.WIDTH_MM % sp) / 2f
                val offY = (PageSpec.HEIGHT_MM % sp) / 2f
                val y0 = maxOf(top, visTop)
                val y1 = minOf(bottom, visBottom)
                if (y1 <= y0) return
                var x = offX
                while (x <= PageSpec.WIDTH_MM) {
                    val px = left + x * scale
                    c.drawLine(px, y0, px, y1, linePaint)
                    x += sp
                }
                val kStart = floor(((y0 - top) / scale - offY) / sp).toInt().coerceAtLeast(0)
                var k = kStart
                while (true) {
                    val yMm = offY + k * sp
                    if (yMm > PageSpec.HEIGHT_MM) break
                    val py = top + yMm * scale
                    if (py > y1) break
                    if (py >= y0) c.drawLine(left, py, right, py, linePaint)
                    k++
                }
            }
        }
    }

    // ---------------- 主頁預覽圖 ----------------

    /** 用簡單的折線把筆劃畫出來（預覽圖很小，看不出跟正式繪製的差別）。可在背景執行緒呼叫。 */
    fun drawStrokesSimple(c: Canvas, strokes: List<RawStroke>, scale: Float) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val path = Path()
        for (s in strokes) {
            val w = (s.sizeMm * scale).coerceAtLeast(0.6f)
            if (s.size == 1) {
                dotPaint.color = s.color
                c.drawCircle(s.xs[0] * scale, s.ys[0] * scale, w / 2f, dotPaint)
                continue
            }
            path.rewind()
            path.moveTo(s.xs[0] * scale, s.ys[0] * scale)
            for (i in 1 until s.size) path.lineTo(s.xs[i] * scale, s.ys[i] * scale)
            strokePaint.color = s.color
            strokePaint.strokeWidth = w
            c.drawPath(path, strokePaint)
        }
    }

    fun renderThumbnail(page: PageData?, settings: NoteSettings, widthPx: Int = THUMB_WIDTH): Bitmap {
        val scale = widthPx / PageSpec.WIDTH_MM
        val h = (PageSpec.HEIGHT_MM * scale).toInt()
        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(PAGE)
        drawBackground(c, 0f, 0f, scale, settings, linePaint = Paint().apply { style = Paint.Style.STROKE })
        if (page != null) drawStrokesSimple(c, page.strokes, scale)
        return bmp
    }

    fun saveThumbnail(file: File, page: PageData?, settings: NoteSettings) {
        val bmp = renderThumbnail(page, settings)
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    const val THUMB_WIDTH = 420
}
