package com.haohao.inkjournal

import com.haohao.inkjournal.data.BackgroundType
import com.haohao.inkjournal.data.NoteJson
import com.haohao.inkjournal.data.NoteMeta
import com.haohao.inkjournal.data.NoteSettings
import com.haohao.inkjournal.data.PageCodec
import com.haohao.inkjournal.data.PenPalette
import com.haohao.inkjournal.data.RawStroke
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class FormatTest {

    private fun stroke(n: Int, withPressure: Boolean, seed: Float = 0f) = RawStroke(
        brush = RawStroke.BRUSH_BALLPOINT,
        color = 0xFF8DB8E8.toInt(),
        sizeMm = 0.7f,
        epsilon = 0.015f,
        toolType = RawStroke.TOOL_STYLUS,
        unitLengthCm = 0.1f,
        xs = FloatArray(n) { 10f + it * 0.37f + seed },
        ys = FloatArray(n) { 20f + it * 0.11f },
        ts = IntArray(n) { it * 8 },
        pressures = if (withPressure) FloatArray(n) { (it % 10) / 10f } else null,
    )

    @Test
    fun pageRoundTripKeepsEveryValue() {
        val src = listOf(stroke(50, true), stroke(1, false, 3f), stroke(300, false, 7.5f))
        val bytes = PageCodec.encode(src)
        val back = PageCodec.decode(bytes)
        assertEquals(src.size, back.size)
        for ((a, b) in src.zip(back)) {
            assertEquals(a.color, b.color)
            assertEquals(a.sizeMm, b.sizeMm, 0f)
            assertEquals(a.epsilon, b.epsilon, 0f)
            assertEquals(a.toolType, b.toolType)
            assertEquals(a.unitLengthCm, b.unitLengthCm, 0f)
            assertArrayEquals(a.xs, b.xs, 0f)
            assertArrayEquals(a.ys, b.ys, 0f)
            assertArrayEquals(a.ts, b.ts)
            if (a.pressures == null) assertNull(b.pressures) else assertArrayEquals(a.pressures, b.pressures!!, 0f)
        }
    }

    @Test
    fun emptyPageRoundTrip() {
        assertEquals(0, PageCodec.decode(PageCodec.encode(emptyList())).size)
    }

    @Test
    fun headerIsReadable() {
        val bytes = PageCodec.encode(listOf(stroke(5, false)))
        assertEquals("HWIK", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(PageCodec.VERSION, (bytes[4].toInt() shl 8) or bytes[5].toInt())
    }

    @Test
    fun compressionKeepsFilesSmall() {
        val many = List(500) { stroke(40, false, it.toFloat()) }
        val raw = 500 * 40 * 12 // x + y + t 不壓縮的大小
        assertTrue(PageCodec.encode(many).size < raw)
    }

    @Test
    fun rejectsGarbage() {
        try {
            PageCodec.decode("not an ink file".toByteArray())
            fail("should throw")
        } catch (_: IOException) {
        }
    }

    @Test
    fun noteJsonRoundTrip() {
        val meta = NoteMeta(
            id = "n20261102153000_ab12",
            title = "諮商後 · 11/2",
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000,
            settings = NoteSettings(
                background = BackgroundType.GRID,
                lineSpacingMm = 9.5f,
                gridSpacingMm = 5f,
                palette = PenPalette.DEFAULT.withColor(2, 0xFFF28B82.toInt()).withSize(0, 0.35f),
                selectedColor = 2,
                selectedSize = 0,
            ),
            pageIds = listOf("pabc", "pdef"),
            lastPage = 1,
        )
        val back = NoteJson.decode(NoteJson.encode(meta))
        assertEquals(meta, back)
    }

    @Test
    fun noteJsonClampsBadValues() {
        val text = """{"id":"n1","title":"x","background":{"type":"lined","lineSpacingMm":999},
            "pens":{"colors":["#123456","zz"],"sizesMm":[9],"selectedColor":7},"pages":["p1","../evil"]}"""
        val m = NoteJson.decode(text)
        assertEquals(NoteSettings.MAX_LINE_SPACING, m.settings.lineSpacingMm, 0f)
        assertEquals(0xFF123456.toInt(), m.settings.palette.colors[0])
        assertEquals(PenPalette.DEFAULT.colors[1], m.settings.palette.colors[1])
        assertEquals(PenPalette.MAX_SIZE_MM, m.settings.palette.sizesMm[0], 0f)
        assertEquals(2, m.settings.selectedColor)
        assertEquals(listOf("p1"), m.pageIds)
    }
}
