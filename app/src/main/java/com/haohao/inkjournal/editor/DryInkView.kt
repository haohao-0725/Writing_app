package com.haohao.inkjournal.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderNode
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.haohao.inkjournal.data.PageData
import com.haohao.inkjournal.data.PageSpec
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * 「乾的」筆跡層：畫紙面、背景線、所有已完成的筆劃。
 *
 * 每一頁的筆劃分成每 [CHUNK] 筆一組，各錄成一個 RenderNode（GPU 顯示清單，內容是公釐座標的向量）。
 * 捲動、縮放時只要用新的矩陣重播，不用重新處理每一筆；新增一筆只需要重錄最後一組。
 * 縮放結束後，再分幾個畫格慢慢用新的倍率重錄，讓邊緣的抗鋸齒維持最清楚。
 */
@SuppressLint("ViewConstructor")
class DryInkView(
    context: Context,
    private val session: NoteSession,
    private val viewport: Viewport,
) : View(context) {

    private val renderer = CanvasStrokeRenderer.create()
    private val density = resources.displayMetrics.density

    private val builder = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ink-builder").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val building = HashSet<String>()
    private var released = false

    /** 手指縮放進行中：先不要為了倍率重錄（會卡）。 */
    var zooming = false

    var eraserVisible = false
    var eraserX = 0f
    var eraserY = 0f
    var eraserRadiusPx = 0f

    private val pagePaint = Paint().apply { color = PagePainter.PAGE }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = PagePainter.PAGE_EDGE
        strokeWidth = density
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0FFFFFFF }
    private val buttonEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x14FFFFFF
        strokeWidth = density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDDBB8E.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val eraserStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xB3FFFFFF.toInt()
        strokeWidth = 1.5f * density
    }
    private val eraserFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
    private val tmpMatrix = Matrix()

    private class PageRender {
        val nodes = ArrayList<RenderNode>()
        /** 每一組錄製時的倍率；NaN = 內容變了，下一格一定要重錄。 */
        val recordedScale = ArrayList<Float>()

        fun ensureCount(strokes: Int) {
            val need = (strokes + CHUNK - 1) / CHUNK
            while (nodes.size < need) {
                nodes += RenderNode("ink").apply {
                    setPosition(0, 0, PageSpec.WIDTH_MM.toInt(), PageSpec.HEIGHT_MM.toInt())
                    clipToBounds = false
                }
                recordedScale += Float.NaN
            }
            while (nodes.size > need) {
                nodes.removeAt(nodes.lastIndex).discardDisplayList()
                recordedScale.removeAt(recordedScale.lastIndex)
            }
        }

        fun dirtyFrom(strokeIndex: Int) {
            for (k in (strokeIndex / CHUNK).coerceAtLeast(0) until recordedScale.size) recordedScale[k] = Float.NaN
        }

        fun release() {
            nodes.forEach { it.discardDisplayList() }
            nodes.clear()
            recordedScale.clear()
        }
    }

    private val renders = HashMap<String, PageRender>()
    private var syncBuildBudget = 0
    private var lastVisible: IntRange = IntRange.EMPTY

    // ---------------- 外部通知 ----------------

    fun onPageContentChanged(pageIndex: Int, fromStroke: Int) {
        val page = session.pages.getOrNull(pageIndex) ?: return
        renders[page.id]?.dirtyFrom(fromStroke)
        invalidate()
    }

    fun onPageListChanged() {
        val ids = session.pages.mapTo(HashSet()) { it.id }
        val gone = renders.keys.filter { it !in ids }
        for (id in gone) renders.remove(id)?.release()
        lastVisible = IntRange.EMPTY
        invalidate()
    }

    fun release() {
        released = true
        builder.shutdownNow()
        renders.values.forEach { it.release() }
        renders.clear()
    }

    // ---------------- 繪製 ----------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(PagePainter.APP_BG)
        if (!viewport.hasSize || released) return
        val s = viewport.scale
        val settings = session.settings
        val vis = viewport.visiblePages()
        val deadline = System.nanoTime() + RERECORD_BUDGET_NS
        var moreStale = false
        syncBuildBudget = SYNC_BUILD_PER_FRAME
        val hw = canvas.isHardwareAccelerated
        val h = height.toFloat()

        for (i in vis) {
            val page = session.pages[i]
            val left = viewport.pageLeftPx()
            val top = viewport.pageTopPx(i)
            val right = left + PageSpec.WIDTH_MM * s
            val bottom = top + PageSpec.HEIGHT_MM * s
            val corner = 4f * density
            canvas.drawRoundRect(left, top, right, bottom, corner, corner, pagePaint)
            PagePainter.drawBackground(canvas, left, top, s, settings, visTop = 0f, visBottom = h)

            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            canvas.translate(left, top)
            canvas.scale(s, s)
            if (hw) {
                val pr = renders.getOrPut(page.id) { PageRender() }
                pr.ensureCount(page.strokes.size)
                for (k in pr.nodes.indices) {
                    val rs = pr.recordedScale[k]
                    if (rs.isNaN()) {
                        record(page, pr, k, s)
                    } else if (!zooming && abs(rs / s - 1f) > STALE_SCALE) {
                        if (System.nanoTime() < deadline) record(page, pr, k, s) else moreStale = true
                    }
                    canvas.drawRenderNode(pr.nodes[k])
                }
            } else {
                // 軟體繪製（例如截圖）時沒有 RenderNode 可用，直接畫
                tmpMatrix.setScale(s, s)
                for (r in page.strokes) InkConvert.inkOf(r)?.let { renderer.draw(canvas, it, tmpMatrix) }
            }
            canvas.restore()
            canvas.drawRoundRect(left, top, right, bottom, corner, corner, edgePaint)
        }

        drawAddPageButton(canvas, s)

        if (eraserVisible) {
            canvas.drawCircle(eraserX, eraserY, eraserRadiusPx, eraserFill)
            canvas.drawCircle(eraserX, eraserY, eraserRadiusPx, eraserStroke)
        }

        if (moreStale) postInvalidateOnAnimation()
        requestBuilds(vis)
        if (vis != lastVisible) {
            lastVisible = vis
            releaseFar(vis)
        }
    }

    private fun record(page: PageData, pr: PageRender, k: Int, scale: Float) {
        val strokes = page.strokes
        val from = k * CHUNK
        val to = minOf(from + CHUNK, strokes.size)
        val node = pr.nodes[k]
        val c = node.beginRecording()
        try {
            tmpMatrix.setScale(scale, scale)
            for (i in from until to) {
                val raw = strokes[i]
                var ink = InkConvert.inkOf(raw)
                if (ink == null && raw.renderCache == null && syncBuildBudget > 0) {
                    // 少量缺的（例如復原回來的筆劃）直接現場建，不要等背景
                    syncBuildBudget--
                    ink = InkConvert.toInk(raw)
                    raw.renderCache = ink ?: FAILED
                }
                if (ink != null) renderer.draw(c, ink, tmpMatrix)
            }
        } finally {
            node.endRecording()
        }
        pr.recordedScale[k] = scale
    }

    private fun drawAddPageButton(canvas: Canvas, s: Float) {
        val r = viewport.addPageButtonMm()
        val l = viewport.tx + r[0] * s
        val t = viewport.ty + r[1] * s
        val rr = viewport.tx + r[2] * s
        val b = viewport.ty + r[3] * s
        if (b < 0f || t > height) return
        val radius = (b - t) / 2f
        canvas.drawRoundRect(l, t, rr, b, radius, radius, buttonPaint)
        canvas.drawRoundRect(l, t, rr, b, radius, radius, buttonEdge)
        textPaint.textSize = (6f * s).coerceIn(13f * density, 30f * density)
        val cy = (t + b) / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("＋  新增一頁", (l + rr) / 2f, cy, textPaint)
    }

    // ---------------- 背景建立 Ink 筆劃 ----------------

    /** 看得到的頁優先，前後各多準備一頁，捲過去時就已經好了。 */
    private fun requestBuilds(vis: IntRange) {
        if (vis.isEmpty() || released) return
        val order = ArrayList<Int>()
        for (i in vis) order += i
        if (vis.first - 1 >= 0) order += vis.first - 1
        if (vis.last + 1 < session.pages.size) order += vis.last + 1
        for (i in order) {
            val page = session.pages[i]
            if (page.id in building) continue
            if (page.strokes.none { it.renderCache == null }) continue
            building += page.id
            val snapshot = page.strokes.toList()
            val pageId = page.id
            builder.execute {
                for (r in snapshot) {
                    if (r.renderCache == null) r.renderCache = InkConvert.toInk(r) ?: FAILED
                }
                post {
                    building -= pageId
                    if (!released) {
                        renders[pageId]?.dirtyFrom(0)
                        invalidate()
                    }
                }
            }
        }
    }

    /** 離畫面很遠的頁，釋放 GPU 顯示清單與 Ink 網格，省記憶體（原始資料還在，回來時再建）。 */
    private fun releaseFar(vis: IntRange) {
        if (vis.isEmpty()) return
        val keep = (vis.first - KEEP_PAGES)..(vis.last + KEEP_PAGES)
        for ((i, page) in session.pages.withIndex()) {
            if (i in keep || page.id in building) continue
            renders.remove(page.id)?.release()
            for (r in page.strokes) r.renderCache = null
        }
    }

    companion object {
        const val CHUNK = 64
        private const val STALE_SCALE = 0.04f
        private const val RERECORD_BUDGET_NS = 5_000_000L
        private const val SYNC_BUILD_PER_FRAME = 24
        private const val KEEP_PAGES = 3
        /** 建立失敗的筆劃做個記號，不要一直重試。 */
        private val FAILED = Any()
    }
}
