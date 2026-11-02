package com.haohao.inkjournal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.haohao.inkjournal.editor.PagePainter

object Ink {
    val Bg = Color(PagePainter.APP_BG)
    val Page = Color(PagePainter.PAGE)
    val Bar = Color(0xFF16191D)
    val Surface2 = Color(0xFF1F2328)
    val Surface3 = Color(0xFF272B31)
    val Text = Color(0xFFE7E2D9)
    val Muted = Color(PagePainter.MUTED)
    val Accent = Color(0xFFD9B88F)
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
    background = Ink.Bg,
    onBackground = Ink.Text,
    surface = Ink.Bar,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Surface2,
    onSurfaceVariant = Ink.Muted,
    surfaceContainerLowest = Ink.Bg,
    surfaceContainerLow = Ink.Bar,
    surfaceContainer = Ink.Surface2,
    surfaceContainerHigh = Ink.Surface2,
    surfaceContainerHighest = Ink.Surface3,
    outline = Ink.Outline,
    outlineVariant = Ink.Outline,
    error = Ink.Danger,
)

@Composable
fun InkJournalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 幾個 material-icons-core 沒有的圖示。 */
object InkIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
        }.build()

    val Undo = icon(
        "Undo",
        "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
    )
    val Redo = icon(
        "Redo",
        "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z",
    )
    val Tune = icon(
        "Tune",
        "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z",
    )
    val Eraser = icon(
        "Eraser",
        "M16.24,3.56L21.19,8.5C21.97,9.29 21.97,10.55 21.19,11.34L12,20.53C10.44,22.09 7.91,22.09 6.34,20.53L2.81,17C2.03,16.21 2.03,14.95 2.81,14.16L13.41,3.56C14.2,2.78 15.46,2.78 16.24,3.56M4.22,15.58L7.76,19.11C8.54,19.9 9.8,19.9 10.59,19.11L14.12,15.58L9.17,10.63L4.22,15.58Z",
    )
    val AddPage = icon(
        "AddPage",
        "M14,2H6C4.9,2 4,2.9 4,4v16c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM18,20H6V4h7v5h5v11zM11,11v3H8v2h3v3h2v-3h3v-2h-3v-3h-2z",
    )
}
