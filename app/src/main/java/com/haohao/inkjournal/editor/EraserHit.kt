package com.haohao.inkjournal.editor

import com.haohao.inkjournal.data.RawStroke
import kotlin.math.sqrt

/** 筆劃橡皮擦：橡皮擦劃過（一段線段 + 半徑）碰到的整條筆劃都擦掉。全部用頁內公釐座標。 */
object EraserHit {

    fun hits(strokes: List<RawStroke>, ax: Float, ay: Float, bx: Float, by: Float, radius: Float): List<Int> {
        val out = ArrayList<Int>()
        val minX = minOf(ax, bx) - radius
        val maxX = maxOf(ax, bx) + radius
        val minY = minOf(ay, by) - radius
        val maxY = maxOf(ay, by) + radius
        for (i in strokes.indices) {
            val s = strokes[i]
            val r = radius + s.sizeMm / 2f
            val half = s.sizeMm / 2f
            if (s.maxX + half < minX || s.minX - half > maxX || s.maxY + half < minY || s.minY - half > maxY) continue
            if (strokeHit(s, ax, ay, bx, by, r)) out += i
        }
        return out
    }

    private fun strokeHit(s: RawStroke, ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        val n = s.size
        if (n == 1) return pointSegDist(s.xs[0], s.ys[0], ax, ay, bx, by) <= r
        for (k in 0 until n - 1) {
            if (segSegDist(s.xs[k], s.ys[k], s.xs[k + 1], s.ys[k + 1], ax, ay, bx, by) <= r) return true
        }
        return false
    }

    fun pointSegDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 1e-12f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx - px
        val cy = ay + t * dy - py
        return sqrt(cx * cx + cy * cy)
    }

    fun segSegDist(
        p1x: Float, p1y: Float, p2x: Float, p2y: Float,
        q1x: Float, q1y: Float, q2x: Float, q2y: Float,
    ): Float {
        if (segmentsIntersect(p1x, p1y, p2x, p2y, q1x, q1y, q2x, q2y)) return 0f
        return minOf(
            minOf(pointSegDist(p1x, p1y, q1x, q1y, q2x, q2y), pointSegDist(p2x, p2y, q1x, q1y, q2x, q2y)),
            minOf(pointSegDist(q1x, q1y, p1x, p1y, p2x, p2y), pointSegDist(q2x, q2y, p1x, p1y, p2x, p2y)),
        )
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) =
        (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

    private fun segmentsIntersect(
        p1x: Float, p1y: Float, p2x: Float, p2y: Float,
        q1x: Float, q1y: Float, q2x: Float, q2y: Float,
    ): Boolean {
        val d1 = cross(q1x, q1y, q2x, q2y, p1x, p1y)
        val d2 = cross(q1x, q1y, q2x, q2y, p2x, p2y)
        val d3 = cross(p1x, p1y, p2x, p2y, q1x, q1y)
        val d4 = cross(p1x, p1y, p2x, p2y, q2x, q2y)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}
