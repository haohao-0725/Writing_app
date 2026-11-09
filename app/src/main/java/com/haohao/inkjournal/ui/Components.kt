package com.haohao.inkjournal.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haohao.inkjournal.data.BackgroundType
import com.haohao.inkjournal.data.NoteSettings
import com.haohao.inkjournal.data.PageSpec
import com.haohao.inkjournal.data.PenPalette
import com.haohao.inkjournal.editor.PagePainter
import kotlin.math.roundToInt

/** 適合深色紙面的色票。 */
val INK_SWATCHES = listOf(
    0xFFF5F1EA, 0xFFECE6DC, 0xFFC9C4BA, 0xFF9AA0A8,
    0xFF8DB8E8, 0xFF6FA3E0, 0xFFA9C7F2, 0xFF7FD1E0,
    0xFFA8D5A2, 0xFF7CC6A4, 0xFFD4E3A0, 0xFFF6D186,
    0xFFF0B46E, 0xFFF28B82, 0xFFF4A7B9, 0xFFE0A3D8,
    0xFFC3A6F0,
).map { it.toInt() }

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Ink.Muted,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.Muted, modifier = modifier)
}

/** 顏色圓點；選到的那個外面有一圈（中間留一點空隙）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorDot(color: Int, selected: Boolean, size: Dp = 26.dp, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val ring by animateColorAsState(if (selected) Ink.Text.copy(alpha = 0.9f) else Color.Transparent, label = "ring")
    val inner by animateDpAsState(if (selected) size - 6.dp else size, label = "inner")
    Box(
        Modifier
            .size(size + 14.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).border(2.dp, ring, CircleShape))
        Box(
            Modifier
                .size(inner)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, Color(0x1AFFFFFF), CircleShape),
        )
    }
}

/** 粗細按鈕：圓點大小跟筆的粗細成比例；選到的那個底下有一塊淡淡的圓。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SizeDot(sizeMm: Float, color: Int, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val d = (3f + sizeMm * 10f).coerceIn(4f, 24f).dp
    val bg by animateColorAsState(if (selected) Color(0x1FFFFFFF) else Color.Transparent, label = "sizeBg")
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(d).clip(CircleShape).background(Color(color).copy(alpha = if (selected) 1f else 0.75f)))
    }
}

/** 估計在編輯畫面（直向、符合頁寬）時 1 mm 大約幾 dp，用來畫預覽。 */
@Composable
fun mmToDpOnPage(): Float {
    val cfg = LocalConfiguration.current
    val shortSide = minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
    return ((shortSide - 28f) / PageSpec.WIDTH_MM).coerceIn(2f, 8f)
}

/** 在紙面色上畫一條示範筆跡。 */
@Composable
fun StrokePreview(color: Int, sizeMm: Float, modifier: Modifier = Modifier) {
    val mm = mmToDpOnPage()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Page),
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.62f)
            cubicTo(w * 0.25f, h * 0.05f, w * 0.38f, h * 0.95f, w * 0.52f, h * 0.45f)
            cubicTo(w * 0.62f, h * 0.15f, w * 0.75f, h * 0.85f, w * 0.92f, h * 0.38f)
        }
        drawPath(
            path,
            Color(color),
            style = Stroke(width = sizeMm * mm * density, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 漸層軌道的滑桿（色相、彩度、亮度用）。 */
@Composable
fun GradientSlider(value: Float, onChange: (Float) -> Unit, brush: Brush) {
    val change by rememberUpdatedState(onChange)
    Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) { detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { c, _ -> change((c.position.x / size.width).coerceIn(0f, 1f)) }
                },
        ) {
            val x = value.coerceIn(0f, 1f) * size.width
            val c = Offset(x, size.height / 2f)
            drawCircle(Color.White, radius = 10.dp.toPx(), center = c)
            drawCircle(Color(0xFF101215), radius = 10.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

    fun setColor(c: Int) {
        val t = FloatArray(3)
        android.graphics.Color.colorToHSV(c, t)
        h = t[0]; s = t[1]; v = t[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇顏色") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StrokePreview(color, 0.8f)
                Spacer(Modifier.height(14.dp))
                Hint("常用色（在深色紙面上都清楚）")
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (c in INK_SWATCHES) ColorDot(c, c == color, size = 28.dp, onClick = { setColor(c) })
                }
                Spacer(Modifier.height(14.dp))
                Hint("色相")
                GradientSlider(h / 360f, { h = it * 360f }, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.7f, 0.95f) }))
                Hint("鮮豔度")
                GradientSlider(s, { s = it }, Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v))))
                Hint("亮度")
                GradientSlider(v, { v = it }, Brush.horizontalGradient(listOf(Color.hsv(h, s, 0.25f), Color.hsv(h, s, 1f))))
                Spacer(Modifier.height(6.dp))
                Hint("#%06X".format(color and 0xFFFFFF))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color or 0xFF000000.toInt()) }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SizePickerDialog(initial: Float, color: Int, onDismiss: () -> Unit, onPick: (Float) -> Unit) {
    var v by remember { mutableFloatStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筆的粗細") },
        text = {
            Column {
                StrokePreview(color, v)
                Spacer(Modifier.height(12.dp))
                Text("%.2f mm".format(v), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = v,
                    onValueChange = { v = (it * 20f).roundToInt() / 20f },
                    valueRange = PenPalette.MIN_SIZE_MM..PenPalette.MAX_SIZE_MM,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (q in listOf(0.3f, 0.4f, 0.5f, 0.7f, 1.0f, 1.5f, 2.0f)) {
                        Text(
                            "%.1f".format(q),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (q == v) Ink.Surface3 else Color.Transparent)
                                .clickable { v = q }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            color = if (q == v) Ink.Accent else Ink.Muted,
                        )
                    }
                }
                Hint("單位是 A4 紙上的公釐；一般原子筆約 0.5 mm。")
            }
        },
        confirmButton = { TextButton(onClick = { onPick(v) }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 三種顏色 + 三種粗細的編輯區（全域設定和每本筆記的設定共用）。 */
@Composable
fun PaletteEditor(palette: PenPalette, onChange: (PenPalette) -> Unit) {
    var editColor by remember { mutableIntStateOf(-1) }
    var editSize by remember { mutableIntStateOf(-1) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("顏色", modifier = Modifier.width(56.dp), color = Ink.Muted)
            for (i in 0 until PenPalette.SLOTS) {
                ColorDot(palette.colors[i], selected = false, size = 30.dp, onClick = { editColor = i })
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("粗細", modifier = Modifier.width(56.dp), color = Ink.Muted)
            for (i in 0 until PenPalette.SLOTS) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { editSize = i }
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SizeDot(palette.sizesMm[i], palette.colors[0], selected = false, onClick = { editSize = i })
                    Text("%.2f mm".format(palette.sizesMm[i]), color = Ink.Text, fontSize = 13.sp)
                }
            }
        }
        Hint("點一下顏色或粗細就可以調整。", Modifier.padding(top = 6.dp))
    }
    if (editColor >= 0) {
        val slot = editColor
        ColorPickerDialog(
            initial = palette.colors[slot],
            onDismiss = { editColor = -1 },
            onPick = { onChange(palette.withColor(slot, it)); editColor = -1 },
        )
    }
    if (editSize >= 0) {
        val slot = editSize
        SizePickerDialog(
            initial = palette.sizesMm[slot],
            color = palette.colors[0],
            onDismiss = { editSize = -1 },
            onPick = { onChange(palette.withSize(slot, it)); editSize = -1 },
        )
    }
}

/** 小小的紙面預覽，顯示背景線的樣子。 */
@Composable
fun BackgroundPreview(settings: NoteSettings, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .aspectRatio(PageSpec.WIDTH_MM / PageSpec.HEIGHT_MM)
            .clip(RoundedCornerShape(6.dp))
            .background(Ink.Page)
            .border(1.dp, Ink.Outline, RoundedCornerShape(6.dp)),
    ) {
        val scale = size.width / PageSpec.WIDTH_MM
        drawIntoCanvas { PagePainter.drawBackground(it.nativeCanvas, 0f, 0f, scale, settings) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundEditor(
    type: BackgroundType,
    lineSpacing: Float,
    gridSpacing: Float,
    onChange: (BackgroundType, Float, Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BackgroundType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = t == type,
                        onClick = { onChange(t, lineSpacing, gridSpacing) },
                        shape = SegmentedButtonDefaults.itemShape(i, BackgroundType.entries.size),
                    ) { Text(t.label) }
                }
            }
            Spacer(Modifier.height(10.dp))
            when (type) {
                BackgroundType.BLANK -> Hint("空白紙面，沒有線。")
                BackgroundType.LINED -> {
                    Text("行距 %.1f mm".format(lineSpacing), color = Ink.Text)
                    Slider(
                        value = lineSpacing,
                        onValueChange = { onChange(type, (it * 2f).roundToInt() / 2f, gridSpacing) },
                        valueRange = NoteSettings.MIN_LINE_SPACING..NoteSettings.MAX_LINE_SPACING,
                    )
                }
                BackgroundType.GRID -> {
                    Text("格子 %.1f mm".format(gridSpacing), color = Ink.Text)
                    Slider(
                        value = gridSpacing,
                        onValueChange = { onChange(type, lineSpacing, (it * 2f).roundToInt() / 2f) },
                        valueRange = NoteSettings.MIN_GRID_SPACING..NoteSettings.MAX_GRID_SPACING,
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        BackgroundPreview(
            NoteSettings(background = type, lineSpacingMm = lineSpacing, gridSpacingMm = gridSpacing),
            Modifier.width(76.dp),
        )
    }
}

/** 簡單的輸入文字對話框（重新命名用）。 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String = "確定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

fun Color.argb(): Int = toArgb()
