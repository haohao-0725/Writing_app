package com.haohao.inkjournal

import com.haohao.inkjournal.data.PageSpec
import com.haohao.inkjournal.data.RawStroke
import com.haohao.inkjournal.editor.EraserHit
import com.haohao.inkjournal.editor.Viewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, size: Float = 0.5f) = RawStroke(
        RawStroke.BRUSH_BALLPOINT, -1, size, 0.015f, RawStroke.TOOL_STYLUS, 0f,
        floatArrayOf(x0, x1), floatArrayOf(y0, y1), intArrayOf(0, 10),
    )

    @Test
    fun eraserHitsCrossingAndNearStrokesOnly() {
        val strokes = listOf(
            line(0f, 10f, 100f, 10f), // 被橡皮擦垂直劃過
            line(0f, 50f, 100f, 50f), // 很遠
            line(52f, 0f, 52f, 5f),   // 末端離橡皮擦 2mm 內
        )
        val hits = EraserHit.hits(strokes, 50f, 0f, 50f, 20f, radius = 2f)
        assertEquals(listOf(0, 2), hits)
    }

    @Test
    fun eraserTapOnDot() {
        val dot = RawStroke(RawStroke.BRUSH_BALLPOINT, -1, 1f, 0.015f, 3, 0f, floatArrayOf(5f), floatArrayOf(5f), intArrayOf(0))
        assertEquals(listOf(0), EraserHit.hits(listOf(dot), 6f, 5f, 6f, 5f, 0.6f))
        assertEquals(emptyList<Int>(), EraserHit.hits(listOf(dot), 9f, 5f, 9f, 5f, 0.6f))
    }

    @Test
    fun segmentDistance() {
        assertEquals(0f, EraserHit.segSegDist(0f, 0f, 10f, 10f, 0f, 10f, 10f, 0f), 1e-5f)
        assertEquals(5f, EraserHit.segSegDist(0f, 0f, 10f, 0f, 0f, 5f, 10f, 5f), 1e-5f)
    }

    @Test
    fun viewportFitsPageWidth() {
        val vp = Viewport(sideMarginPx = 20f)
        vp.setSize(1620f, 2400f)
        assertEquals((1620f - 40f) / PageSpec.WIDTH_MM, vp.scale, 1e-3f)
        assertEquals(20f, vp.pageLeftPx(), 1e-3f)
        assertEquals(0, vp.currentPage())
    }

    @Test
    fun viewportHitTestMapsToPageMillimetres() {
        val vp = Viewport(sideMarginPx = 20f)
        vp.pageCount = 3
        vp.setSize(1620f, 2400f)
        val out = FloatArray(3)
        val s = vp.scale
        // 第 2 頁（index 1）左上角往內 10mm
        val x = vp.pageLeftPx() + 10f * s
        val y = vp.pageTopPx(1) + 10f * s
        assertTrue(vp.hitPage(x, y, out))
        assertEquals(1f, out[0], 0f)
        assertEquals(10f, out[1], 1e-3f)
        assertEquals(10f, out[2], 1e-3f)
        // 頁面左邊外面
        assertFalse(vp.hitPage(5f, y, out))
    }

    @Test
    fun zoomKeepsFocalPointStill() {
        val vp = Viewport(sideMarginPx = 20f)
        vp.pageCount = 2
        vp.setSize(1600f, 2400f)
        val fx = 800f
        val fy = 1200f
        val docX = (fx - vp.tx) / vp.scale
        val docY = (fy - vp.ty) / vp.scale
        vp.zoomBy(2f, fx, fy)
        assertEquals(2f, vp.zoom, 1e-4f)
        assertEquals(fx, docX * vp.scale + vp.tx, 0.5f)
        assertEquals(fy, docY * vp.scale + vp.ty, 0.5f)
    }

    @Test
    fun scrollingIsClampedToDocument() {
        val vp = Viewport(sideMarginPx = 20f)
        vp.pageCount = 2
        vp.setSize(1600f, 2400f)
        vp.panBy(0f, 99999f)
        assertEquals(0f, vp.ty, 0f)
        vp.panBy(0f, -999999f)
        assertEquals(1, vp.currentPage())
        assertTrue(vp.isOnAddPageButton(800f, vp.ty + (vp.addPageButtonMm()[1] + 5f) * vp.scale))
    }

    @Test
    fun rotationKeepsTopOfView() {
        val vp = Viewport(sideMarginPx = 20f)
        vp.pageCount = 4
        vp.setSize(1600f, 2400f)
        vp.scrollToPage(2)
        assertEquals(2, vp.currentPage())
        vp.setSize(2400f, 1600f)
        val top = vp.docTopMm()
        assertEquals(vp.pageTopMm(2) - Viewport.TOP_MM, top, 0.5f)
    }
}
