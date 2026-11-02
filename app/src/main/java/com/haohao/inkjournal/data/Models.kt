package com.haohao.inkjournal.data

/** 頁面尺寸：A4，單位都是公釐（mm）。筆跡座標也用公釐存，所以放大多少倍都是向量、不會糊。 */
object PageSpec {
    const val WIDTH_MM = 210f
    const val HEIGHT_MM = 297f
}

enum class BackgroundType(val id: String, val label: String) {
    BLANK("blank", "空白"),
    LINED("lined", "橫線"),
    GRID("grid", "方格");

    companion object {
        fun fromId(id: String?): BackgroundType = entries.firstOrNull { it.id == id } ?: LINED
    }
}

/** 三種顏色 + 三種粗細。 */
data class PenPalette(
    val colors: List<Int>,
    val sizesMm: List<Float>,
) {
    init {
        require(colors.size == SLOTS && sizesMm.size == SLOTS)
    }

    fun withColor(slot: Int, color: Int) = copy(colors = colors.toMutableList().also { it[slot] = color })
    fun withSize(slot: Int, sizeMm: Float) =
        copy(sizesMm = sizesMm.toMutableList().also { it[slot] = clampSize(sizeMm) })

    companion object {
        const val SLOTS = 3
        const val MIN_SIZE_MM = 0.2f
        const val MAX_SIZE_MM = 3.0f

        fun clampSize(v: Float) = v.coerceIn(MIN_SIZE_MM, MAX_SIZE_MM)

        /** 深色紙面用的預設三色：米白（主要書寫）、霧藍、琥珀。 */
        val DEFAULT = PenPalette(
            colors = listOf(0xFFECE6DC.toInt(), 0xFF8DB8E8.toInt(), 0xFFF0B46E.toInt()),
            sizesMm = listOf(0.4f, 0.7f, 1.1f),
        )
    }
}

/** 一本筆記整份共用的設定（每一頁都一樣），不同筆記可以不同。 */
data class NoteSettings(
    val background: BackgroundType = BackgroundType.LINED,
    val lineSpacingMm: Float = DEFAULT_LINE_SPACING,
    val gridSpacingMm: Float = DEFAULT_GRID_SPACING,
    val palette: PenPalette = PenPalette.DEFAULT,
    val selectedColor: Int = 0,
    val selectedSize: Int = 1,
) {
    val penColor: Int get() = palette.colors[selectedColor.coerceIn(0, PenPalette.SLOTS - 1)]
    val penSizeMm: Float get() = palette.sizesMm[selectedSize.coerceIn(0, PenPalette.SLOTS - 1)]

    companion object {
        const val DEFAULT_LINE_SPACING = 8f
        const val DEFAULT_GRID_SPACING = 6f
        const val MIN_LINE_SPACING = 5f
        const val MAX_LINE_SPACING = 16f
        const val MIN_GRID_SPACING = 3f
        const val MAX_GRID_SPACING = 12f
    }
}

data class NoteMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val settings: NoteSettings,
    val pageIds: List<String>,
    /** 上次看到第幾頁（從 0 開始），下次打開回到這裡。 */
    val lastPage: Int = 0,
)

/**
 * 一筆筆劃的原始資料（向量）。座標是「頁面內」的公釐，時間是從筆劃開始算的毫秒。
 * 筆壓、傾斜、方向目前的原子筆用不到，但有就一起存，之後要做有筆壓的筆也不用改格式。
 */
class RawStroke(
    val brush: Int,
    val color: Int,
    val sizeMm: Float,
    val epsilon: Float,
    val toolType: Int,
    val unitLengthCm: Float,
    val xs: FloatArray,
    val ys: FloatArray,
    val ts: IntArray,
    val pressures: FloatArray? = null,
    val tilts: FloatArray? = null,
    val orientations: FloatArray? = null,
) {
    init {
        require(xs.size == ys.size && xs.size == ts.size)
        require(pressures == null || pressures.size == xs.size)
        require(tilts == null || tilts.size == xs.size)
        require(orientations == null || orientations.size == xs.size)
    }

    val size: Int get() = xs.size

    val minX: Float
    val minY: Float
    val maxX: Float
    val maxY: Float

    init {
        var a = Float.POSITIVE_INFINITY
        var b = Float.POSITIVE_INFINITY
        var c = Float.NEGATIVE_INFINITY
        var d = Float.NEGATIVE_INFINITY
        for (i in xs.indices) {
            if (xs[i] < a) a = xs[i]
            if (ys[i] < b) b = ys[i]
            if (xs[i] > c) c = xs[i]
            if (ys[i] > d) d = ys[i]
        }
        minX = a; minY = b; maxX = c; maxY = d
    }

    /** 給畫面用的 Jetpack Ink 筆劃（由原始資料產生的快取，不存檔）。型別寫 Any，讓這個檔案不依賴 Android。 */
    @Volatile
    var renderCache: Any? = null

    companion object {
        const val BRUSH_BALLPOINT = 0

        const val TOOL_UNKNOWN = 0
        const val TOOL_MOUSE = 1
        const val TOOL_TOUCH = 2
        const val TOOL_STYLUS = 3
    }
}

class PageData(val id: String, val strokes: MutableList<RawStroke> = mutableListOf())
