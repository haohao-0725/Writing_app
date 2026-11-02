package com.haohao.inkjournal.editor

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.haohao.inkjournal.data.RawStroke

/** 自訂格式（RawStroke）↔ Jetpack Ink 的 Stroke。 */
object InkConvert {
    /** 筆劃幾何的精細度（mm）。約等於預設縮放下 0.1 個像素，放大 5 倍仍然平滑。 */
    const val EPSILON_MM = 0.015f

    /** 原子筆：Ink 的 marker 是固定粗細的圓形筆尖，最接近實心原子筆的手感。 */
    val ballpoint: BrushFamily by lazy { StockBrushes.marker() }

    fun brush(color: Int, sizeMm: Float, epsilon: Float = EPSILON_MM): Brush =
        Brush.createWithColorIntArgb(ballpoint, color, sizeMm, epsilon)

    private fun toolToCode(t: InputToolType): Int = when (t) {
        InputToolType.STYLUS -> RawStroke.TOOL_STYLUS
        InputToolType.TOUCH -> RawStroke.TOOL_TOUCH
        InputToolType.MOUSE -> RawStroke.TOOL_MOUSE
        else -> RawStroke.TOOL_UNKNOWN
    }

    private fun codeToTool(c: Int): InputToolType = when (c) {
        RawStroke.TOOL_STYLUS -> InputToolType.STYLUS
        RawStroke.TOOL_TOUCH -> InputToolType.TOUCH
        RawStroke.TOOL_MOUSE -> InputToolType.MOUSE
        else -> InputToolType.UNKNOWN
    }

    fun fromInk(stroke: Stroke): RawStroke {
        val inputs = stroke.inputs
        val n = inputs.size
        val tmp = StrokeInput()
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val ts = IntArray(n)
        val ps = if (inputs.hasPressure()) FloatArray(n) else null
        val tl = if (inputs.hasTilt()) FloatArray(n) else null
        val or = if (inputs.hasOrientation()) FloatArray(n) else null
        var unit = StrokeInput.NO_STROKE_UNIT_LENGTH
        for (i in 0 until n) {
            inputs.populate(i, tmp)
            xs[i] = tmp.x
            ys[i] = tmp.y
            ts[i] = tmp.elapsedTimeMillis.toInt()
            ps?.set(i, tmp.pressure)
            tl?.set(i, tmp.tiltRadians)
            or?.set(i, tmp.orientationRadians)
            if (i == 0) unit = tmp.strokeUnitLengthCm
        }
        val tool = if (n > 0) toolToCode(inputs.getToolType()) else RawStroke.TOOL_UNKNOWN
        return RawStroke(
            brush = RawStroke.BRUSH_BALLPOINT,
            color = stroke.brush.colorIntArgb,
            sizeMm = stroke.brush.size,
            epsilon = stroke.brush.epsilon,
            toolType = tool,
            unitLengthCm = unit,
            xs = xs, ys = ys, ts = ts,
            pressures = ps, tilts = tl, orientations = or,
        ).also { it.renderCache = stroke }
    }

    /** 由存檔的資料重建 Ink 筆劃（會產生繪製用的網格，比較花時間，放背景執行緒做）。 */
    fun toInk(raw: RawStroke): Stroke? {
        if (raw.size == 0) return null
        val batch = MutableStrokeInputBatch()
        val tool = codeToTool(raw.toolType)
        for (i in 0 until raw.size) {
            try {
                batch.add(
                    tool,
                    raw.xs[i],
                    raw.ys[i],
                    raw.ts[i].toLong(),
                    raw.unitLengthCm,
                    raw.pressures?.get(i) ?: StrokeInput.NO_PRESSURE,
                    raw.tilts?.get(i) ?: StrokeInput.NO_TILT,
                    raw.orientations?.get(i) ?: StrokeInput.NO_ORIENTATION,
                )
            } catch (_: RuntimeException) {
                // 重複或順序不對的點直接略過
            }
        }
        if (batch.isEmpty()) return null
        val eps = if (raw.epsilon > 0f) raw.epsilon else EPSILON_MM
        return runCatching { Stroke(brush(raw.color, raw.sizeMm.coerceAtLeast(0.01f), eps), batch) }.getOrNull()
    }

    fun inkOf(raw: RawStroke): Stroke? = raw.renderCache as? Stroke

    /** 確保有 Ink 筆劃（沒有就現場建一個）。 */
    fun ensureInk(raw: RawStroke): Stroke? {
        inkOf(raw)?.let { return it }
        val s = toInk(raw)
        raw.renderCache = s
        return s
    }
}
