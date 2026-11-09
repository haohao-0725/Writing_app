package com.haohao.inkjournal.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.haohao.inkjournal.editor.PagePainter

object Ink {
    val Bg = Color(PagePainter.APP_BG)
    val Page = Color(PagePainter.PAGE)
    /** 卡片、設定區塊 */
    val Card = Color(0xFF181B20)
    val Bar = Color(0xFF16191D)
    val Surface2 = Color(0xFF1F2328)
    val Surface3 = Color(0xFF2A2E35)
    /** 浮在紙面上的工具列（略透明） */
    val Glass = Color(0xF21D2026)
    /** 細邊框：白色 8% */
    val Hairline = Color(0x14FFFFFF)
    val Text = Color(0xFFEDE8DF)
    val Muted = Color(0xFF8D939C)
    val Faint = Color(0xFF5E646D)
    val Accent = Color(0xFFDDBB8E)
    val AccentSoft = Color(0x24DDBB8E)
    val Outline = Color(0xFF30353D)
    val Danger = Color(0xFFE88A80)
}

private val scheme = darkColorScheme(
    primary = Ink.Accent,
    onPrimary = Color(0xFF2A2116),
    primaryContainer = Color(0xFF3A3226),
    onPrimaryContainer = Ink.Accent,
    secondary = Color(0xFF9DB7D6),
    onSecondary = Color(0xFF13202E),
    secondaryContainer = Color(0xFF3A3226),
    onSecondaryContainer = Ink.Accent,
    background = Ink.Bg,
    onBackground = Ink.Text,
    surface = Ink.Bar,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Surface2,
    onSurfaceVariant = Ink.Muted,
    surfaceContainerLowest = Ink.Bg,
    surfaceContainerLow = Ink.Card,
    surfaceContainer = Ink.Surface2,
    surfaceContainerHigh = Ink.Surface2,
    surfaceContainerHighest = Ink.Surface3,
    outline = Ink.Outline,
    outlineVariant = Ink.Outline,
    error = Ink.Danger,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun InkJournalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}

/** 細線條、圓角收尾的圖示（24×24，線寬 1.8）。 */
object InkIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) {
                addPath(
                    pathData = addPathNodes(p),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Back = icon("Back", "M15 18 L9 12 L15 6")
    val Undo = icon(
        "Undo",
        "M9 14 L4 9 L9 4",
        "M4 9 H14.5 A5.5 5.5 0 0 1 20 14.5 A5.5 5.5 0 0 1 14.5 20 H11",
    )
    val Redo = icon(
        "Redo",
        "M15 14 L20 9 L15 4",
        "M20 9 H9.5 A5.5 5.5 0 0 0 4 14.5 A5.5 5.5 0 0 0 9.5 20 H13",
    )
    val Eraser = icon(
        "Eraser",
        "M7 21 L2.7 16.7 C1.7 15.7 1.7 14.2 2.7 13.3 L12.3 3.7 C13.3 2.7 14.8 2.7 15.7 3.7 L21.3 9.3 C22.3 10.3 22.3 11.8 21.3 12.7 L13 21",
        "M22 21 H7",
        "M5 11 L14 20",
    )
    val AddPage = icon(
        "AddPage",
        "M15 2 H6 A2 2 0 0 0 4 4 V20 A2 2 0 0 0 6 22 H18 A2 2 0 0 0 20 20 V7 Z",
        "M14 2 V6 A2 2 0 0 0 16 8 H20",
        "M9 15 H15",
        "M12 18 V12",
    )
    val Tune = icon(
        "Tune",
        "M20 7 H11",
        "M14 17 H5",
        "M20 17 A3 3 0 1 1 14 17 A3 3 0 1 1 20 17 Z",
        "M10 7 A3 3 0 1 1 4 7 A3 3 0 1 1 10 7 Z",
    )
    val Plus = icon("Plus", "M12 5 V19", "M5 12 H19")
    val Pencil = icon(
        "Pencil",
        "M16.5 3.5 A2.12 2.12 0 0 1 19.5 6.5 L7 19 L3 20 L4 16 Z",
        "M14.5 5.5 L18.5 9.5",
    )
    val Trash = icon(
        "Trash",
        "M3 6 H21",
        "M19 6 V20 A2 2 0 0 1 17 22 H7 A2 2 0 0 1 5 20 V6",
        "M8 6 V4 A2 2 0 0 1 10 2 H14 A2 2 0 0 1 16 4 V6",
    )
}
