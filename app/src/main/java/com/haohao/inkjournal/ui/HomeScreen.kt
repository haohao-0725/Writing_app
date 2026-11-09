package com.haohao.inkjournal.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haohao.inkjournal.InkJournalApp
import com.haohao.inkjournal.data.NoteMeta
import com.haohao.inkjournal.data.PageSpec
import com.haohao.inkjournal.editor.PagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WEEKDAYS = arrayOf("一", "二", "三", "四", "五", "六", "日")
private val CardShape = RoundedCornerShape(16.dp)

fun defaultNoteTitle(date: LocalDate = LocalDate.now()): String =
    date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "（" + WEEKDAYS[date.dayOfWeek.value - 1] + "）"

private fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..10 -> "早安"
    in 11..13 -> "午安"
    in 14..17 -> "午後好"
    in 18..23 -> "晚上好"
    else -> "夜深了，慢慢來"
}

private fun formatEdited(ms: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (t.toLocalDate()) {
        today -> "今天 " + t.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> "昨天 " + t.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> t.format(DateTimeFormatter.ofPattern(if (t.year == today.year) "M月d日" else "yyyy年M月d日"))
    }
}

@Composable
fun HomeScreen(app: InkJournalApp, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val version by app.dataVersion.collectAsState()
    val scope = rememberCoroutineScope()
    val notes by produceState<List<NoteMeta>?>(null, version) {
        value = withContext(Dispatchers.IO) { app.notes.listNotes() }
    }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<NoteMeta?>(null) }
    var deleteFor by remember { mutableStateOf<NoteMeta?>(null) }

    fun createNote() {
        scope.launch {
            val meta = withContext(Dispatchers.IO) {
                app.notes.createNote(defaultNoteTitle(), app.settings.state.value.newNoteSettings())
            }
            onOpen(meta.id)
        }
    }

    val today = LocalDate.now()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(184.dp),
        contentPadding = PaddingValues(start = 36.dp, end = 36.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.fillMaxSize().background(Ink.Bg),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 28.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), color = Ink.Accent, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("沉澱手記", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        today.format(DateTimeFormatter.ofPattern("M月d日")) + " 星期" + WEEKDAYS[today.dayOfWeek.value - 1] +
                            (notes?.let { "  ·  ${it.size} 本筆記" } ?: ""),
                        color = Ink.Muted,
                        fontSize = 14.sp,
                    )
                }
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Ink.Card)
                        .border(1.dp, Ink.Hairline, CircleShape)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "設定", tint = Ink.Text, modifier = Modifier.size(22.dp))
                }
            }
        }
        item(key = "new") { NewNoteCard(onClick = ::createNote) }
        items(notes.orEmpty(), key = { it.id }) { meta ->
            Box {
                NoteCard(
                    app = app,
                    meta = meta,
                    version = version,
                    onClick = { onOpen(meta.id) },
                    onLongClick = { menuFor = meta.id },
                )
                DropdownMenu(
                    expanded = menuFor == meta.id,
                    onDismissRequest = { menuFor = null },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Ink.Surface3,
                ) {
                    DropdownMenuItem(
                        text = { Text("重新命名") },
                        leadingIcon = { Icon(InkIcons.Pencil, null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuFor = null; renameFor = meta },
                    )
                    DropdownMenuItem(
                        text = { Text("刪除", color = Ink.Danger) },
                        leadingIcon = { Icon(InkIcons.Trash, null, tint = Ink.Danger, modifier = Modifier.size(20.dp)) },
                        onClick = { menuFor = null; deleteFor = meta },
                    )
                }
            }
        }
        if (notes?.isEmpty() == true) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text("還沒有筆記。點「新筆記」，寫下今天的心情。", color = Ink.Muted, fontSize = 14.sp)
            }
        }
    }

    renameFor?.let { meta ->
        TextInputDialog(
            title = "筆記名稱",
            initial = meta.title,
            onDismiss = { renameFor = null },
            onConfirm = { name ->
                renameFor = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        app.notes.loadMeta(meta.id)?.let {
                            app.notes.saveMeta(it.copy(title = name.trim(), updatedAt = System.currentTimeMillis()))
                        }
                    }
                    app.notifyDataChanged()
                }
            },
        )
    }

    deleteFor?.let { meta ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("刪除這本筆記？") },
            text = { Text("「${meta.title.ifBlank { "未命名" }}」整本（${meta.pageIds.size} 頁）會從這台平板刪除，沒有辦法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteFor = null
                    scope.launch {
                        withContext(Dispatchers.IO) { app.notes.deleteNote(meta.id) }
                        app.notifyDataChanged()
                    }
                }) { Text("刪除", color = Ink.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NewNoteCard(onClick: () -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PageSpec.WIDTH_MM / PageSpec.HEIGHT_MM)
                .clip(CardShape)
                .background(Brush.verticalGradient(listOf(Color(0x26DDBB8E), Color(0x0ADDBB8E))))
                .border(1.dp, Color(0x40DDBB8E), CardShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Ink.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(InkIcons.Plus, contentDescription = null, tint = Color(0xFF2A2116), modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("新筆記", color = Ink.Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(" ", fontSize = 15.sp)
        Text(" ", fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    app: InkJournalApp,
    meta: NoteMeta,
    version: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val thumb by produceState<ImageBitmap?>(null, meta.id, meta.updatedAt, version) {
        value = withContext(Dispatchers.IO) { loadOrMakeThumbnail(app, meta) }
    }
    // 不在外層裁切，預覽圖的陰影才不會被切掉
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PageSpec.WIDTH_MM / PageSpec.HEIGHT_MM)
                .shadow(12.dp, CardShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CardShape)
                .background(Ink.Page)
                .border(1.dp, Ink.Hairline, CardShape),
        ) {
            thumb?.let {
                Image(it, contentDescription = meta.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            meta.title.ifBlank { "未命名" },
            color = Ink.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            formatEdited(meta.updatedAt) + "  ·  " + meta.pageIds.size + " 頁",
            color = Ink.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        )
    }
}

/** 讀預覽圖；沒有的話（例如剛匯入）就從第一頁畫一張存起來。 */
private fun loadOrMakeThumbnail(app: InkJournalApp, meta: NoteMeta): ImageBitmap? = runCatching {
    val f = app.notes.thumbFile(meta.id)
    if (!f.exists()) {
        val first = meta.pageIds.firstOrNull()?.let { app.notes.loadPage(meta.id, it) }
        PagePainter.saveThumbnail(f, first, meta.settings)
    }
    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
}.getOrNull()
