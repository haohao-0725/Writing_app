package com.haohao.inkjournal.ui

import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.haohao.inkjournal.InkJournalApp
import com.haohao.inkjournal.data.PageData
import com.haohao.inkjournal.data.PenPalette
import com.haohao.inkjournal.editor.NoteCanvasView
import com.haohao.inkjournal.editor.NoteSession
import com.haohao.inkjournal.editor.PagePainter
import kotlinx.coroutines.delay

private val BAR_HEIGHT = 56.dp
private val BAR_MARGIN = 12.dp
private val CapsuleShape = RoundedCornerShape(28.dp)

@Composable
fun EditorScreen(app: InkJournalApp, noteId: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<NoteSession?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(noteId) {
        session = NoteSession.open(app.notes, noteId, scope, app.writer)
        if (session == null) failed = true
    }
    val s = session
    if (s == null) {
        BackHandler(onBack = onClose)
        Box(Modifier.fillMaxSize().background(Ink.Bg), contentAlignment = Alignment.Center) {
            if (failed) Text("打不開這本筆記", color = Ink.Muted) else CircularProgressIndicator(color = Ink.Accent)
        }
        return
    }
    EditorContent(app, s, onClose)
}

@Composable
private fun EditorContent(app: InkJournalApp, session: NoteSession, onClose: () -> Unit) {
    val settings by app.settings.state.collectAsState()
    val fingerDrawing by rememberUpdatedState(settings.fingerDrawing)
    var canvas by remember { mutableStateOf<NoteCanvasView?>(null) }
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    var showNoteSettings by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var quickColorSlot by remember { mutableIntStateOf(-1) }
    var quickSizeSlot by remember { mutableIntStateOf(-1) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    // 紙面可以捲到工具列底下，但最頂端時第一頁停在工具列下面
    val topInsetPx = with(density) { WindowInsets.statusBars.getTop(this) + (BAR_MARGIN * 2 + BAR_HEIGHT).toPx() }
    val bottomInsetPx = with(density) { WindowInsets.navigationBars.getBottom(this) + 12.dp.toPx() }

    /** 存檔；第一頁有變就重畫主頁預覽圖。 */
    fun persist(leaving: Boolean) {
        canvas?.collectPendingStrokes()
        session.flushBlocking()
        val id = session.meta.id
        if (session.thumbDirty || !app.notes.thumbFile(id).exists()) {
            session.clearThumbDirty()
            val first = session.pages.first()
            val copy = PageData(first.id, first.strokes.toMutableList())
            val st = session.settings
            app.writer.execute {
                runCatching { PagePainter.saveThumbnail(app.notes.thumbFile(id), copy, st) }
                app.notifyDataChanged()
            }
        } else if (leaving) {
            app.notifyDataChanged()
        }
    }

    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) persist(leaving = false) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persist(leaving = true)
            canvas?.release()
        }
    }

    BackHandler(onBack = onClose)

    /** 回報浮動元件在畫布上的位置，讓正在寫的筆跡經過時被藏起來。 */
    fun Modifier.overlay(key: String): Modifier = onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        canvas?.setOverlay(
            key,
            RectF(b.left - canvasOrigin.x, b.top - canvasOrigin.y, b.right - canvasOrigin.x, b.bottom - canvasOrigin.y),
        )
    }

    Box(Modifier.fillMaxSize().background(Ink.Bg)) {
        AndroidView(
            factory = { ctx -> NoteCanvasView(ctx, session) { fingerDrawing }.also { canvas = it } },
            update = { it.setInsets(topInsetPx, bottomInsetPx) },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { canvasOrigin = it.positionInWindow() },
        )

        // 頂端漸層：捲到工具列底下的紙面會自然淡出，不會從膠囊之間的縫露出來
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusTop + BAR_MARGIN * 2 + BAR_HEIGHT)
                .overlay("scrim")
                .background(
                    Brush.verticalGradient(
                        0f to Ink.Bg.copy(alpha = 0.97f),
                        0.7f to Ink.Bg.copy(alpha = 0.85f),
                        1f to Color.Transparent,
                    ),
                ),
        )

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = BAR_MARGIN, vertical = BAR_MARGIN),
        ) {
            val showTitle = maxWidth >= 860.dp
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 左：返回 + 標題
                Capsule(Modifier.overlay("left").widthIn(max = if (showTitle) 300.dp else 56.dp)) {
                    ToolIcon(InkIcons.Back, "回主頁", onClick = onClose)
                    if (showTitle) {
                        Text(
                            session.meta.title.ifBlank { "未命名" },
                            color = Ink.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showRename = true }
                                .padding(start = 2.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // 中：筆
                PenCapsule(
                    session = session,
                    modifier = Modifier.overlay("pen"),
                    onEditColor = { quickColorSlot = it },
                    onEditSize = { quickSizeSlot = it },
                )
                Spacer(Modifier.weight(1f))
                // 右：復原、重做、頁面、設定
                Capsule(Modifier.overlay("right")) {
                    ToolIcon(InkIcons.Undo, "復原", enabled = session.canUndo) { session.undo() }
                    ToolIcon(InkIcons.Redo, "重做", enabled = session.canRedo) { session.redo() }
                    CapsuleDivider()
                    ToolIcon(InkIcons.AddPage, "新增一頁") { canvas?.addPageAndScroll() }
                    ToolIcon(InkIcons.Tune, "這本筆記的設定") { showNoteSettings = true }
                }
            }
        }

        PageChip(
            session = session,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            overlay = { Modifier.overlay("chip") },
            onHidden = { canvas?.setOverlay("chip", null) },
        )
    }

    if (showRename) {
        TextInputDialog(
            title = "筆記名稱",
            initial = session.meta.title,
            onDismiss = { showRename = false },
            onConfirm = { session.rename(it); showRename = false },
        )
    }
    if (showNoteSettings) {
        NoteSettingsSheet(
            app = app,
            session = session,
            onDismiss = { showNoteSettings = false },
            onRename = { showRename = true },
            onDeletePage = { index ->
                session.deletePage(index)
                canvas?.scrollToPage(index.coerceAtMost(session.pages.size - 1))
            },
        )
    }
    if (quickColorSlot >= 0) {
        val slot = quickColorSlot
        ColorPickerDialog(
            initial = session.settings.palette.colors[slot],
            onDismiss = { quickColorSlot = -1 },
            onPick = { c ->
                session.updateSettings { it.copy(palette = it.palette.withColor(slot, c)) }
                session.selectColor(slot)
                quickColorSlot = -1
            },
        )
    }
    if (quickSizeSlot >= 0) {
        val slot = quickSizeSlot
        SizePickerDialog(
            initial = session.settings.palette.sizesMm[slot],
            color = session.settings.penColor,
            onDismiss = { quickSizeSlot = -1 },
            onPick = { v ->
                session.updateSettings { it.copy(palette = it.palette.withSize(slot, v)) }
                session.selectSize(slot)
                quickSizeSlot = -1
            },
        )
    }
}

// ---------------- 浮動膠囊 ----------------

@Composable
private fun Capsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .height(BAR_HEIGHT)
            .shadow(14.dp, CapsuleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CapsuleShape)
            .background(Ink.Glass)
            .border(1.dp, Ink.Hairline, CapsuleShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CapsuleDivider() {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(24.dp)
            .background(Color(0x1FFFFFFF)),
    )
}

@Composable
private fun ToolIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (active) Ink.AccentSoft else Color.Transparent, label = "toolBg")
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = bg),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> Ink.Faint.copy(alpha = 0.6f)
                active -> Ink.Accent
                else -> Ink.Text
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PenCapsule(
    session: NoteSession,
    modifier: Modifier,
    onEditColor: (Int) -> Unit,
    onEditSize: (Int) -> Unit,
) {
    val st = session.meta.settings
    val eraser = session.eraserMode
    Capsule(modifier) {
        // 三種粗細（圓點用目前的顏色）
        for (i in 0 until PenPalette.SLOTS) {
            SizeDot(
                sizeMm = st.palette.sizesMm[i],
                color = st.penColor,
                selected = !eraser && st.selectedSize == i,
                onClick = { session.selectSize(i) },
                onLongClick = { onEditSize(i) },
            )
        }
        CapsuleDivider()
        // 三個顏色
        for (i in 0 until PenPalette.SLOTS) {
            ColorDot(
                color = st.palette.colors[i],
                selected = !eraser && st.selectedColor == i,
                size = 26.dp,
                onClick = { session.selectColor(i) },
                onLongClick = { onEditColor(i) },
            )
        }
        CapsuleDivider()
        ToolIcon(InkIcons.Eraser, "橡皮擦", active = eraser) { session.eraserMode = !eraser }
    }
}

/** 底部的頁碼：換頁時浮出來，停一下就淡出。 */
@Composable
private fun PageChip(
    session: NoteSession,
    modifier: Modifier,
    overlay: () -> Modifier,
    onHidden: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(session.currentPage, session.pageCount) {
        visible = true
        delay(1600)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
        DisposableEffect(Unit) { onDispose { onHidden() } }
        Text(
            "${session.currentPage + 1} / ${session.pageCount}",
            color = Ink.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = overlay()
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(Ink.Glass)
                .border(1.dp, Ink.Hairline, CircleShape)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

// ---------------- 這本筆記的設定 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSettingsSheet(
    app: InkJournalApp,
    session: NoteSession,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDeletePage: (Int) -> Unit,
) {
    val st = session.meta.settings
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.Surface2,
        scrimColor = Color(0x66000000),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("這本筆記", style = MaterialTheme.typography.headlineSmall, color = Ink.Text)
            Hint("整本每一頁都用同一組設定；其他筆記不受影響。", Modifier.padding(top = 4.dp))

            SectionTitle("名稱")
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.meta.title.ifBlank { "未命名" }, color = Ink.Text, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRename) { Text("重新命名") }
                }
            }

            SectionTitle("背景")
            SheetCard {
                BackgroundEditor(st.background, st.lineSpacingMm, st.gridSpacingMm) { t, line, grid ->
                    session.updateSettings { it.copy(background = t, lineSpacingMm = line, gridSpacingMm = grid) }
                }
            }

            SectionTitle("筆的顏色與粗細")
            SheetCard {
                PaletteEditor(st.palette) { p -> session.updateSettings { it.copy(palette = p) } }
                TextButton(
                    onClick = {
                        val d = app.settings.state.value.defaultPalette
                        session.updateSettings { it.copy(palette = d) }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("改回齒輪設定裡的預設") }
                Hint("小技巧：在上方工具列長按某個顏色或粗細，也可以直接調整。")
            }

            SectionTitle("頁面")
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "目前在第 ${session.currentPage + 1} 頁，共 ${session.pageCount} 頁",
                        color = Ink.Text,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { confirmDelete = true }, enabled = session.pageCount > 1) {
                        Icon(InkIcons.Trash, null, modifier = Modifier.size(18.dp), tint = if (session.pageCount > 1) Ink.Danger else Ink.Faint)
                        Spacer(Modifier.width(6.dp))
                        Text("刪除這一頁", color = if (session.pageCount > 1) Ink.Danger else Ink.Faint)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("完成") }
            }
        }
    }
    if (confirmDelete) {
        val index = session.currentPage
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("刪除第 ${index + 1} 頁？") },
            text = { Text("這一頁的內容會被刪除，沒有辦法復原。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeletePage(index) }) { Text("刪除", color = Ink.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SheetCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Card)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) { content() }
}
