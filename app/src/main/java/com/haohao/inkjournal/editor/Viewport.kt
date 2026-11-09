package com.haohao.inkjournal.editor

import com.haohao.inkjournal.data.PageSpec
import kotlin.math.floor

/**
 * 「文件座標（公釐）↔ 螢幕像素」的換算。頁面由上往下排，中間留一點間隔，最後一頁下面是「新增一頁」按鈕。
 *
 *   螢幕 x = 文件 x × scale + tx
 *   螢幕 y = 文件 y × scale + ty
 *
 * zoom = 1 時頁寬剛好塞滿畫面寬（左右留 sideMarginPx）。純計算，不碰 Android，方便測試。
 */
class Viewport(private val sideMarginPx: Float) {
    var viewW = 0f; private set
    var viewH = 0f; private set
    var zoom = 1f; private set
    var tx = 0f; private set
    var ty = 0f; private set

    /** 上下被浮動工具列、系統列蓋住的高度（px）。頁面可以捲到底下，但捲到最頂/最底時會停在這些範圍外。 */
    var insetTop = 0f; private set
    var insetBottom = 0f; private set

    var pageCount = 1
        set(value) {
            field = value.coerceAtLeast(1)
            clamp()
        }

    val hasSize get() = viewW > 0f && viewH > 0f
    val fitScale: Float get() = ((viewW - 2 * sideMarginPx) / PageSpec.WIDTH_MM).coerceAtLeast(0.05f)
    val scale: Float get() = fitScale * zoom

    fun pageTopMm(i: Int) = TOP_MM + i * (PageSpec.HEIGHT_MM + GAP_MM)
    val docHeightMm: Float get() = pageTopMm(pageCount - 1) + PageSpec.HEIGHT_MM + BOTTOM_MM

    /** 「新增一頁」按鈕的位置（文件座標）。 */
    fun addPageButtonMm(): FloatArray {
        val top = pageTopMm(pageCount - 1) + PageSpec.HEIGHT_MM + 10f
        return floatArrayOf(PageSpec.WIDTH_MM * 0.25f, top, PageSpec.WIDTH_MM * 0.75f, top + 22f)
    }

    fun pageLeftPx() = tx
    fun pageTopPx(i: Int) = pageTopMm(i) * scale + ty

    fun setSize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val hadSize = hasSize
        val docTop = if (hadSize) docTopMm() else 0f
        viewW = w
        viewH = h
        ty = insetTop - docTop * scale
        clamp()
    }

    fun setInsets(top: Float, bottom: Float) {
        if (top == insetTop && bottom == insetBottom) return
        val docTop = docTopMm()
        insetTop = top
        insetBottom = bottom
        if (hasSize) ty = insetTop - docTop * scale
        clamp()
    }

    fun panBy(dx: Float, dy: Float) {
        tx += dx
        ty += dy
        clamp()
    }

    fun zoomBy(factor: Float, fx: Float, fy: Float) {
        val old = scale
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val k = scale / old
        tx = fx - (fx - tx) * k
        ty = fy - (fy - ty) * k
        clamp()
    }

    fun resetZoom(fx: Float, fy: Float) = zoomBy(1f / zoom, fx, fy)

    fun scrollToPage(i: Int) {
        ty = insetTop - (pageTopMm(i.coerceIn(0, pageCount - 1)) - TOP_MM) * scale
        clamp()
    }

    /** 沒被工具列蓋住的範圍中央那一頁。 */
    fun currentPage(): Int {
        if (!hasSize) return 0
        val docY = ((insetTop + viewH - insetBottom) / 2f - ty) / scale
        val stride = PageSpec.HEIGHT_MM + GAP_MM
        return floor((docY - TOP_MM + GAP_MM / 2f) / stride).toInt().coerceIn(0, pageCount - 1)
    }

    /** 目前看得到的頁（含部分可見）。 */
    fun visiblePages(): IntRange {
        if (!hasSize) return IntRange.EMPTY
        val stride = PageSpec.HEIGHT_MM + GAP_MM
        val top = -ty / scale
        val bottom = (viewH - ty) / scale
        val first = floor((top - TOP_MM - PageSpec.HEIGHT_MM) / stride).toInt() + 1
        val last = floor((bottom - TOP_MM) / stride).toInt()
        val a = first.coerceIn(0, pageCount - 1)
        val b = last.coerceIn(0, pageCount - 1)
        return if (b < a) IntRange.EMPTY else a..b
    }

    /**
     * 螢幕上的點落在哪一頁：命中時回傳 true，並把 [頁碼, 頁內 x mm, 頁內 y mm] 寫進 out。
     * 頁與頁之間的空隙算給比較近的那一頁；按到頁面左右外面則回傳 false。
     */
    fun hitPage(x: Float, y: Float, out: FloatArray): Boolean {
        val docX = (x - tx) / scale
        val docY = (y - ty) / scale
        if (docX < 0f || docX > PageSpec.WIDTH_MM) return false
        val stride = PageSpec.HEIGHT_MM + GAP_MM
        val i = floor((docY - TOP_MM + GAP_MM / 2f) / stride).toInt()
        if (i < 0 || i >= pageCount) return false
        val localY = docY - pageTopMm(i)
        if (localY < -GAP_MM / 2f || localY > PageSpec.HEIGHT_MM + GAP_MM / 2f) return false
        out[0] = i.toFloat()
        out[1] = docX
        out[2] = localY
        return true
    }

    fun isOnAddPageButton(x: Float, y: Float): Boolean {
        val r = addPageButtonMm()
        val docX = (x - tx) / scale
        val docY = (y - ty) / scale
        return docX in r[0]..r[2] && docY in r[1]..r[3]
    }

    /** 把 ty 設成某個值（還原上次位置用）。 */
    fun restore(zoom: Float, docTopMm: Float) {
        this.zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        ty = insetTop - docTopMm * scale
        clamp()
    }

    /** 工具列下緣對到的文件位置（mm）。 */
    fun docTopMm(): Float = if (hasSize) (insetTop - ty) / scale else 0f

    private fun clamp() {
        if (!hasSize) return
        val s = scale
        val contentW = PageSpec.WIDTH_MM * s
        tx = if (contentW + 2 * sideMarginPx <= viewW) {
            (viewW - contentW) / 2f
        } else {
            tx.coerceIn(viewW - contentW - sideMarginPx, sideMarginPx)
        }
        val contentH = docHeightMm * s
        val avail = viewH - insetTop - insetBottom
        ty = if (contentH <= avail) insetTop else ty.coerceIn(viewH - insetBottom - contentH, insetTop)
    }

    companion object {
        const val GAP_MM = 8f
        const val TOP_MM = 6f
        const val BOTTOM_MM = 44f
        const val MIN_ZOOM = 0.35f
        const val MAX_ZOOM = 5f
    }
}
