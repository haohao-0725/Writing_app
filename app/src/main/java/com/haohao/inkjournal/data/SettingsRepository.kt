package com.haohao.inkjournal.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/** 齒輪裡的全域設定。筆的顏色與粗細是「新筆記的預設值」，每本筆記建立時複製一份，之後可以各自調整。 */
data class AppSettings(
    val defaultPalette: PenPalette = PenPalette.DEFAULT,
    val defaultBackground: BackgroundType = BackgroundType.LINED,
    val defaultLineSpacingMm: Float = NoteSettings.DEFAULT_LINE_SPACING,
    val defaultGridSpacingMm: Float = NoteSettings.DEFAULT_GRID_SPACING,
    /** 關閉（預設）時只有 S Pen 會寫字，手指負責捲動與縮放，手掌碰到也不會畫出線。 */
    val fingerDrawing: Boolean = false,
    /** 雲端備份：之後的版本才會開發，先保留設定位置。 */
    val cloudBackup: Boolean = false,
) {
    fun newNoteSettings() = NoteSettings(
        background = defaultBackground,
        lineSpacingMm = defaultLineSpacingMm,
        gridSpacingMm = defaultGridSpacingMm,
        palette = defaultPalette,
        selectedColor = 0,
        selectedSize = 1,
    )
}

object SettingsJson {
    fun encode(s: AppSettings): String = JSONObject()
        .put("format", "inkjournal-settings")
        .put("version", 1)
        .put("defaultPens", NoteJson.encodePalette(s.defaultPalette))
        .put("defaultBackground", s.defaultBackground.id)
        .put("defaultLineSpacingMm", s.defaultLineSpacingMm.toDouble())
        .put("defaultGridSpacingMm", s.defaultGridSpacingMm.toDouble())
        .put("fingerDrawing", s.fingerDrawing)
        .put("cloudBackup", s.cloudBackup)
        .toString(2)

    fun decode(text: String): AppSettings {
        val o = JSONObject(text)
        val d = AppSettings()
        return AppSettings(
            defaultPalette = NoteJson.decodePalette(o.optJSONObject("defaultPens"), d.defaultPalette),
            defaultBackground = BackgroundType.fromId(o.optString("defaultBackground", d.defaultBackground.id)),
            defaultLineSpacingMm = o.optDouble("defaultLineSpacingMm", d.defaultLineSpacingMm.toDouble()).toFloat()
                .coerceIn(NoteSettings.MIN_LINE_SPACING, NoteSettings.MAX_LINE_SPACING),
            defaultGridSpacingMm = o.optDouble("defaultGridSpacingMm", d.defaultGridSpacingMm.toDouble()).toFloat()
                .coerceIn(NoteSettings.MIN_GRID_SPACING, NoteSettings.MAX_GRID_SPACING),
            fingerDrawing = o.optBoolean("fingerDrawing", d.fingerDrawing),
            cloudBackup = o.optBoolean("cloudBackup", d.cloudBackup),
        )
    }
}

class SettingsRepository(root: File) {
    private val file = File(root, "settings.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load(): AppSettings =
        runCatching { SettingsJson.decode(file.readText(Charsets.UTF_8)) }.getOrDefault(AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        runCatching { NoteRepository.writeAtomic(file, SettingsJson.encode(next).toByteArray(Charsets.UTF_8)) }
    }

    fun toJson(): String = SettingsJson.encode(_state.value)
}
