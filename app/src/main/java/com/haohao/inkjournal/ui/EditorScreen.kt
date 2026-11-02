package com.haohao.inkjournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.haohao.inkjournal.InkJournalApp
import com.haohao.inkjournal.data.PageData
import com.haohao.inkjournal.data.PenPalette
import com.haohao.inkjournal.editor.NoteCanvasView
import com.haohao.inkjournal.editor.NoteSession
import com.haohao.inkjournal.editor.PagePainter

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
    var showNoteSettings by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var quickColorSlot by remember { mutableIntStateOf(-1) }
    var quickSizeSlot by remember { mutableIntStateOf(-1) }
    val lifecycleOwner = LocalLifecycleOwner.current

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

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bar)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        EditorToolbar(
            session = session,
            onBack = onClose,
            onRename = { showRename = true },
            onAddPage = { canvas?.addPageAndScroll() },
            onNoteSettings = { showNoteSettings = true },
            onEditColor = { quickColorSlot = it },
            onEditSize = { quickSizeSlot = it },
        )
        AndroidView(
            factory = { ctx -> NoteCanvasView(ctx, session) { fingerDrawing }.also { canvas = it } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
        NoteSettingsDialog(
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

@Composable
private fun ToolDivider() {
    Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(28.dp).background(Ink.Outline))
}

@Composable
private fun ToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (active) Ink.Surface3 else Color.Transparent),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                !enabled -> Ink.Muted.copy(alpha = 0.35f)
                active -> Ink.Accent
                else -> Ink.Text
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun EditorToolbar(
    session: NoteSession,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onAddPage: () -> Unit,
    onNoteSettings: () -> Unit,
    onEditColor: (Int) -> Unit,
    onEditSize: (Int) -> Unit,
) {
    val st = session.meta.settings
    val eraser = session.eraserMode
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Ink.Bar)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "回主頁", tint = Ink.Text)
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRename)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                session.meta.title.ifBlank { "未命名" },
                color = Ink.Text,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("第 ${session.currentPage + 1} / ${session.pageCount} 頁", color = Ink.Muted, fontSize = 12.sp)
        }

        // 三種粗細（圓點顯示目前的顏色）
        for (i in 0 until PenPalette.SLOTS) {
            SizeDot(
                sizeMm = st.palette.sizesMm[i],
                color = st.penColor,
                selected = !eraser && st.selectedSize == i,
                onClick = { session.selectSize(i) },
                onLongClick = { onEditSize(i) },
            )
        }
        ToolDivider()
        // 三個顏色
        for (i in 0 until PenPalette.SLOTS) {
            ColorDot(
                color = st.palette.colors[i],
                selected = !eraser && st.selectedColor == i,
                size = 24.dp,
                onClick = { session.selectColor(i) },
                onLongClick = { onEditColor(i) },
            )
        }
        ToolDivider()
        ToolIcon(InkIcons.Eraser, "橡皮擦", active = eraser) { session.eraserMode = !eraser }
        ToolIcon(InkIcons.Undo, "復原", enabled = session.canUndo) { session.undo() }
        ToolIcon(InkIcons.Redo, "重做", enabled = session.canRedo) { session.redo() }
        ToolDivider()
        ToolIcon(InkIcons.AddPage, "新增一頁", onClick = onAddPage)
        ToolIcon(InkIcons.Tune, "這本筆記的設定", onClick = onNoteSettings)
    }
}

@Composable
private fun NoteSettingsDialog(
    app: InkJournalApp,
    session: NoteSession,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDeletePage: (Int) -> Unit,
) {
    val st = session.meta.settings
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Ink.Surface2,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.9f),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text("這本筆記的設定", style = MaterialTheme.typography.titleLarge, color = Ink.Text)
                Hint("整本筆記每一頁都用同一組設定；其他筆記不受影響。", Modifier.padding(top = 4.dp))

                SectionTitle("名稱")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.meta.title.ifBlank { "未命名" }, color = Ink.Text, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRename) { Text("重新命名") }
                }

                SectionTitle("背景")
                BackgroundEditor(st.background, st.lineSpacingMm, st.gridSpacingMm) { t, line, grid ->
                    session.updateSettings { it.copy(background = t, lineSpacingMm = line, gridSpacingMm = grid) }
                }

                SectionTitle("筆的顏色與粗細")
                PaletteEditor(st.palette) { p -> session.updateSettings { it.copy(palette = p) } }
                TextButton(
                    onClick = {
                        val d = app.settings.state.value.defaultPalette
                        session.updateSettings { it.copy(palette = d) }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("改回齒輪設定裡的預設") }
                Hint("小技巧：在上方工具列長按某個顏色或粗細，也可以直接調整。")

                SectionTitle("頁面")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "目前在第 ${session.currentPage + 1} 頁，共 ${session.pageCount} 頁",
                        color = Ink.Text,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { confirmDelete = true }, enabled = session.pageCount > 1) {
                        Text("刪除這一頁", color = if (session.pageCount > 1) Ink.Danger else Ink.Muted)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
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
