package com.haohao.inkjournal.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * 頁面向量檔（.hwik，HandWriting InK）。完整規格見 docs/FORMAT.md。
 *
 * 檔頭 12 bytes（不壓縮）：
 *   "HWIK" | u16 版本 | u16 旗標（bit0 = 內容用 Deflate 壓縮）| u32 保留
 * 內容（Deflate 壓縮，big-endian）：
 *   f32 頁寬mm | f32 頁高mm | i32 筆劃數 | 每一筆劃……
 */
object PageCodec {
    const val VERSION = 1
    private val MAGIC = byteArrayOf('H'.code.toByte(), 'W'.code.toByte(), 'I'.code.toByte(), 'K'.code.toByte())
    private const val FLAG_DEFLATE = 1

    private const val CH_PRESSURE = 1
    private const val CH_TILT = 2
    private const val CH_ORIENTATION = 4

    /** 防呆上限，避免壞檔讓 app 配置超大陣列。 */
    private const val MAX_STROKES = 1_000_000
    private const val MAX_POINTS = 1_000_000

    fun encode(strokes: List<RawStroke>, out: OutputStream) {
        val header = DataOutputStream(out)
        header.write(MAGIC)
        header.writeShort(VERSION)
        header.writeShort(FLAG_DEFLATE)
        header.writeInt(0)
        header.flush()

        val deflater = DeflaterOutputStream(out)
        val d = DataOutputStream(deflater)
        d.writeFloat(PageSpec.WIDTH_MM)
        d.writeFloat(PageSpec.HEIGHT_MM)
        d.writeInt(strokes.size)
        for (s in strokes) {
            var channels = 0
            if (s.pressures != null) channels = channels or CH_PRESSURE
            if (s.tilts != null) channels = channels or CH_TILT
            if (s.orientations != null) channels = channels or CH_ORIENTATION
            d.writeByte(s.brush)
            d.writeByte(s.toolType)
            d.writeByte(channels)
            d.writeByte(0)
            d.writeInt(s.color)
            d.writeFloat(s.sizeMm)
            d.writeFloat(s.epsilon)
            d.writeFloat(s.unitLengthCm)
            val n = s.size
            d.writeInt(n)
            // 欄位分開連續存（先全部 x、再全部 y……），壓縮效果比交錯存好
            for (i in 0 until n) d.writeFloat(s.xs[i])
            for (i in 0 until n) d.writeFloat(s.ys[i])
            for (i in 0 until n) d.writeInt(s.ts[i])
            s.pressures?.let { a -> for (i in 0 until n) d.writeFloat(a[i]) }
            s.tilts?.let { a -> for (i in 0 until n) d.writeFloat(a[i]) }
            s.orientations?.let { a -> for (i in 0 until n) d.writeFloat(a[i]) }
        }
        d.flush()
        deflater.finish()
        out.flush()
    }

    fun encode(strokes: List<RawStroke>): ByteArray =
        ByteArrayOutputStream().also { encode(strokes, it) }.toByteArray()

    fun decode(input: InputStream): MutableList<RawStroke> {
        val header = DataInputStream(input)
        val magic = ByteArray(4)
        header.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw IOException("不是 HWIK 檔")
        val version = header.readUnsignedShort()
        if (version > VERSION) throw IOException("檔案版本 $version 比 app 新，請先更新 app")
        val flags = header.readUnsignedShort()
        header.readInt() // 保留欄位

        val body: InputStream = if (flags and FLAG_DEFLATE != 0) InflaterInputStream(input) else input
        val d = DataInputStream(body)
        d.readFloat() // 頁寬（目前固定 A4）
        d.readFloat() // 頁高
        val count = d.readInt()
        if (count !in 0..MAX_STROKES) throw IOException("筆劃數不合理：$count")
        val list = ArrayList<RawStroke>(count)
        repeat(count) {
            val brush = d.readUnsignedByte()
            val tool = d.readUnsignedByte()
            val channels = d.readUnsignedByte()
            d.readUnsignedByte()
            val color = d.readInt()
            val size = d.readFloat()
            val eps = d.readFloat()
            val unit = d.readFloat()
            val n = d.readInt()
            if (n !in 0..MAX_POINTS) throw IOException("點數不合理：$n")
            val xs = FloatArray(n) { d.readFloat() }
            val ys = FloatArray(n) { d.readFloat() }
            val ts = IntArray(n) { d.readInt() }
            val ps = if (channels and CH_PRESSURE != 0) FloatArray(n) { d.readFloat() } else null
            val tl = if (channels and CH_TILT != 0) FloatArray(n) { d.readFloat() } else null
            val or = if (channels and CH_ORIENTATION != 0) FloatArray(n) { d.readFloat() } else null
            if (n > 0) list += RawStroke(brush, color, size, eps, tool, unit, xs, ys, ts, ps, tl, or)
        }
        return list
    }

    fun decode(bytes: ByteArray): MutableList<RawStroke> = decode(ByteArrayInputStream(bytes))
}

/** note.json：筆記的標題、時間、整本的設定、頁面順序。 */
object NoteJson {
    const val FORMAT = "inkjournal-note"
    const val VERSION = 1

    fun encode(meta: NoteMeta): String {
        val s = meta.settings
        val o = JSONObject()
        o.put("format", FORMAT)
        o.put("version", VERSION)
        o.put("id", meta.id)
        o.put("title", meta.title)
        o.put("createdAt", meta.createdAt)
        o.put("updatedAt", meta.updatedAt)
        o.put("page", JSONObject().put("widthMm", PageSpec.WIDTH_MM.toDouble()).put("heightMm", PageSpec.HEIGHT_MM.toDouble()))
        o.put(
            "background",
            JSONObject()
                .put("type", s.background.id)
                .put("lineSpacingMm", s.lineSpacingMm.toDouble())
                .put("gridSpacingMm", s.gridSpacingMm.toDouble()),
        )
        o.put("pens", encodePalette(s.palette).put("selectedColor", s.selectedColor).put("selectedSize", s.selectedSize))
        o.put("pages", JSONArray(meta.pageIds))
        o.put("lastPage", meta.lastPage)
        return o.toString(2)
    }

    fun decode(text: String): NoteMeta {
        val o = JSONObject(text)
        val bg = o.optJSONObject("background") ?: JSONObject()
        val pens = o.optJSONObject("pens") ?: JSONObject()
        val palette = decodePalette(pens, PenPalette.DEFAULT)
        val pagesArr = o.optJSONArray("pages") ?: JSONArray()
        val pageIds = (0 until pagesArr.length()).map { pagesArr.getString(it) }.filter { Ids.isSafe(it) }
        val settings = NoteSettings(
            background = BackgroundType.fromId(bg.optString("type")),
            lineSpacingMm = bg.optDouble("lineSpacingMm", NoteSettings.DEFAULT_LINE_SPACING.toDouble()).toFloat()
                .coerceIn(NoteSettings.MIN_LINE_SPACING, NoteSettings.MAX_LINE_SPACING),
            gridSpacingMm = bg.optDouble("gridSpacingMm", NoteSettings.DEFAULT_GRID_SPACING.toDouble()).toFloat()
                .coerceIn(NoteSettings.MIN_GRID_SPACING, NoteSettings.MAX_GRID_SPACING),
            palette = palette,
            selectedColor = pens.optInt("selectedColor", 0).coerceIn(0, PenPalette.SLOTS - 1),
            selectedSize = pens.optInt("selectedSize", 1).coerceIn(0, PenPalette.SLOTS - 1),
        )
        val id = o.getString("id")
        require(Ids.isSafe(id)) { "筆記 id 不合法" }
        return NoteMeta(
            id = id,
            title = o.optString("title", ""),
            createdAt = o.optLong("createdAt", 0L),
            updatedAt = o.optLong("updatedAt", 0L),
            settings = settings,
            pageIds = pageIds,
            lastPage = o.optInt("lastPage", 0),
        )
    }

    fun encodePalette(p: PenPalette): JSONObject =
        JSONObject()
            .put("colors", JSONArray(p.colors.map { colorToHex(it) }))
            .put("sizesMm", JSONArray(p.sizesMm.map { it.toDouble() }))

    fun decodePalette(o: JSONObject?, fallback: PenPalette): PenPalette {
        if (o == null) return fallback
        val c = o.optJSONArray("colors")
        val z = o.optJSONArray("sizesMm")
        val colors = (0 until PenPalette.SLOTS).map { i ->
            c?.optString(i)?.let { parseColor(it) } ?: fallback.colors[i]
        }
        val sizes = (0 until PenPalette.SLOTS).map { i ->
            val v = z?.optDouble(i, Double.NaN) ?: Double.NaN
            if (v.isNaN()) fallback.sizesMm[i] else PenPalette.clampSize(v.toFloat())
        }
        return PenPalette(colors, sizes)
    }

    fun colorToHex(argb: Int): String = "#%08X".format(argb)

    fun parseColor(s: String): Int? {
        val h = s.trim().removePrefix("#")
        return when (h.length) {
            6 -> h.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> h.toLongOrNull(16)?.toInt()
            else -> null
        }
    }
}

object Ids {
    private val SAFE = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val HEX = "0123456789abcdef"

    fun isSafe(id: String) = SAFE.matches(id)

    fun random(len: Int): String {
        val r = java.security.SecureRandom()
        return buildString { repeat(len) { append(HEX[r.nextInt(16)]) } }
    }

    fun newNoteId(now: Long = System.currentTimeMillis()): String =
        "n" + java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).format(java.util.Date(now)) + "_" + random(4)

    fun newPageId(): String = "p" + random(10)
}
