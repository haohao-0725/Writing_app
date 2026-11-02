package com.haohao.inkjournal.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WEEKDAYS = arrayOf("一", "二", "三", "四", "五", "六", "日")

fun defaultNoteTitle(date: LocalDate = LocalDate.now()): String =
    date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "（" + WEEKDAYS[date.dayOfWeek.value - 1] + "）"

private fun formatEdited(ms: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    val pattern = if (t.year == LocalDate.now().year) "M月d日 HH:mm" else "yyyy年M月d日"
    return t.format(DateTimeFormatter.ofPattern(pattern))
}

@OptIn(ExperimentalFoundationApi::class)
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

    Scaffold(
        containerColor = Ink.Bg,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 28.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("沉澱手記", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Ink.Text)
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日")) + " 星期" +
                            WEEKDAYS[LocalDate.now().dayOfWeek.value - 1],
                        color = Ink.Muted,
                        fontSize = 14.sp,
                    )
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "設定", tint = Ink.Muted)
                }
            }
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(176.dp),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
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
                    DropdownMenu(expanded = menuFor == meta.id, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text("重新命名") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuFor = null; renameFor = meta },
                        )
                        DropdownMenuItem(
                            text = { Text("刪除", color = Ink.Danger) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Ink.Danger) },
                            onClick = { menuFor = null; deleteFor = meta },
                        )
                    }
                }
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
                .clip(RoundedCornerShape(10.dp))
                .border(1.5.dp, Ink.Outline, RoundedCornerShape(10.dp))
                .combinedClickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Ink.Accent, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("新筆記", color = Ink.Accent, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
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
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PageSpec.WIDTH_MM / PageSpec.HEIGHT_MM)
                .clip(RoundedCornerShape(10.dp))
                .background(Ink.Page)
                .border(1.dp, Ink.Outline, RoundedCornerShape(10.dp)),
        ) {
            thumb?.let {
                Image(it, contentDescription = meta.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            meta.title.ifBlank { "未命名" },
            color = Ink.Text,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatEdited(meta.updatedAt) + " · " + meta.pageIds.size + " 頁",
            color = Ink.Muted,
            style = MaterialTheme.typography.bodySmall,
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
