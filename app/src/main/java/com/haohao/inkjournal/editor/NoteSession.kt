package com.haohao.inkjournal.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.haohao.inkjournal.data.Ids
import com.haohao.inkjournal.data.NoteMeta
import com.haohao.inkjournal.data.NoteRepository
import com.haohao.inkjournal.data.NoteSettings
import com.haohao.inkjournal.data.PageData
import com.haohao.inkjournal.data.RawStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * 一本打開中的筆記：頁面資料、復原/重做、自動存檔。所有方法都在主執行緒呼叫。
 * 畫面（Compose）讀 [meta]、[canUndo] 這些 state；畫布（NoteCanvasView）透過 [listener] 收到變動。
 */
class NoteSession(
    private val repo: NoteRepository,
    initialMeta: NoteMeta,
    val pages: MutableList<PageData>,
    private val scope: CoroutineScope,
    /** 單一執行緒：所有寫檔依照先後順序執行，舊的快照不會蓋掉新的。 */
    private val writer: ExecutorService,
) {
    interface Listener {
        /** 某一頁從第 fromStroke 筆之後的內容變了（新增時 fromStroke = 最後一筆）。 */
        fun onPageContentChanged(pageIndex: Int, fromStroke: Int)
        fun onPageListChanged()
        fun onSettingsChanged()
    }

    var listener: Listener? = null

    var meta by mutableStateOf(initialMeta); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var eraserMode by mutableStateOf(false)
    var currentPage by mutableIntStateOf(initialMeta.lastPage.coerceIn(0, pages.size - 1)); private set
    var pageCount by mutableIntStateOf(pages.size); private set

    val settings: NoteSettings get() = meta.settings

    // ---------------- 復原 / 重做 ----------------

    private sealed class Action(val pageId: String) {
        class Add(pageId: String, val stroke: RawStroke) : Action(pageId)
        /** 依照擦掉的先後順序記錄（擦掉當下的位置, 筆劃）。 */
        class Erase(pageId: String, val removed: List<Pair<Int, RawStroke>>) : Action(pageId)
    }

    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()

    private fun push(a: Action) {
        undoStack.addLast(a)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        refreshUndoState()
    }

    private fun refreshUndoState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun indexOfPage(pageId: String) = pages.indexOfFirst { it.id == pageId }

    fun addStroke(pageIndex: Int, raw: RawStroke) {
        val page = pages[pageIndex]
        page.strokes.add(raw)
        push(Action.Add(page.id, raw))
        contentChanged(page)
        listener?.onPageContentChanged(pageIndex, page.strokes.size - 1)
    }

    // 一次橡皮擦手勢（筆按下到抬起）算一個復原步驟
    private var erasePageId: String? = null
    private var eraseRemoved: MutableList<Pair<Int, RawStroke>>? = null

    fun beginErase(pageIndex: Int) {
        erasePageId = pages[pageIndex].id
        eraseRemoved = ArrayList()
    }

    /** indices：目前清單中的位置（由小到大）。 */
    fun eraseStrokes(pageIndex: Int, indices: List<Int>) {
        if (indices.isEmpty()) return
        val page = pages[pageIndex]
        val log = if (page.id == erasePageId) eraseRemoved else null
        val single = if (log == null) ArrayList<Pair<Int, RawStroke>>() else null
        for (i in indices.sortedDescending()) {
            if (i !in page.strokes.indices) continue
            val s = page.strokes.removeAt(i)
            (log ?: single)!!.add(i to s)
        }
        if (single != null && single.isNotEmpty()) push(Action.Erase(page.id, single))
        contentChanged(page)
        listener?.onPageContentChanged(pageIndex, indices.min())
    }

    fun endErase() {
        val id = erasePageId
        val log = eraseRemoved
        erasePageId = null
        eraseRemoved = null
        if (id != null && !log.isNullOrEmpty()) push(Action.Erase(id, log))
    }

    fun undo() {
        val a = undoStack.removeLastOrNull() ?: return
        val pi = indexOfPage(a.pageId)
        if (pi >= 0) {
            val list = pages[pi].strokes
            val from = when (a) {
                is Action.Add -> {
                    val i = list.indexOfLast { it === a.stroke }
                    if (i >= 0) list.removeAt(i)
                    i.coerceAtLeast(0)
                }
                is Action.Erase -> {
                    for (k in a.removed.indices.reversed()) {
                        val (i, s) = a.removed[k]
                        list.add(i.coerceIn(0, list.size), s)
                    }
                    a.removed.minOf { it.first }
                }
            }
            contentChanged(pages[pi])
            listener?.onPageContentChanged(pi, from)
        }
        redoStack.addLast(a)
        refreshUndoState()
    }

    fun redo() {
        val a = redoStack.removeLastOrNull() ?: return
        val pi = indexOfPage(a.pageId)
        if (pi >= 0) {
            val list = pages[pi].strokes
            val from = when (a) {
                is Action.Add -> {
                    list.add(a.stroke)
                    list.size - 1
                }
                is Action.Erase -> {
                    for ((i, s) in a.removed) {
                        if (i in list.indices && list[i] === s) list.removeAt(i) else list.remove(s)
                    }
                    a.removed.minOf { it.first }
                }
            }
            contentChanged(pages[pi])
            listener?.onPageContentChanged(pi, from)
        }
        undoStack.addLast(a)
        refreshUndoState()
    }

    // ---------------- 頁面 ----------------

    fun addPage(): Int {
        val p = PageData(Ids.newPageId())
        pages.add(p)
        pageCount = pages.size
        updateMeta(bump = true) { it.copy(pageIds = pages.map { pg -> pg.id }) }
        dirtyPages.add(p.id)
        listener?.onPageListChanged()
        return pages.size - 1
    }

    fun deletePage(index: Int) {
        if (pages.size <= 1 || index !in pages.indices) return
        val removed = pages.removeAt(index)
        pageCount = pages.size
        undoStack.removeAll { it.pageId == removed.id }
        redoStack.removeAll { it.pageId == removed.id }
        refreshUndoState()
        dirtyPages.remove(removed.id)
        deletedPages.add(removed.id)
        if (index == 0) thumbDirty = true
        updateMeta(bump = true) { it.copy(pageIds = pages.map { pg -> pg.id }) }
        listener?.onPageListChanged()
    }

    fun updateCurrentPage(i: Int) {
        currentPage = i.coerceIn(0, pages.size - 1)
    }

    // ---------------- 設定 ----------------

    fun rename(title: String) = updateMeta(bump = true) { it.copy(title = title.trim()) }

    /** 換背景、改這本筆記的筆色粗細。 */
    fun updateSettings(transform: (NoteSettings) -> NoteSettings) {
        val before = meta.settings
        updateMeta(bump = true) { it.copy(settings = transform(it.settings)) }
        if (before.background != meta.settings.background ||
            before.lineSpacingMm != meta.settings.lineSpacingMm ||
            before.gridSpacingMm != meta.settings.gridSpacingMm
        ) thumbDirty = true
        listener?.onSettingsChanged()
    }

    /** 切換目前用的顏色/粗細（不算修改內容，不更新「最後修改時間」）。 */
    fun selectColor(slot: Int) {
        eraserMode = false
        updateMeta(bump = false) { it.copy(settings = it.settings.copy(selectedColor = slot)) }
    }

    fun selectSize(slot: Int) {
        eraserMode = false
        updateMeta(bump = false) { it.copy(settings = it.settings.copy(selectedSize = slot)) }
    }

    // ---------------- 存檔 ----------------

    private val dirtyPages = LinkedHashSet<String>()
    private val deletedPages = LinkedHashSet<String>()
    private var metaDirty = false
    private var saveJob: Job? = null

    /** 第一頁有變動 → 離開時要重畫主頁預覽圖。 */
    var thumbDirty = false
        private set

    private fun contentChanged(page: PageData) {
        dirtyPages.add(page.id)
        if (pages.firstOrNull() === page) thumbDirty = true
        updateMeta(bump = true) { it }
    }

    private fun updateMeta(bump: Boolean, transform: (NoteMeta) -> NoteMeta) {
        var m = transform(meta)
        if (bump) m = m.copy(updatedAt = System.currentTimeMillis())
        meta = m
        metaDirty = true
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    private class Snapshot(
        val meta: NoteMeta?,
        val pages: List<Pair<String, List<RawStroke>>>,
        val deleted: List<String>,
    )

    private fun takeSnapshot(): Snapshot? {
        if (!metaDirty && dirtyPages.isEmpty() && deletedPages.isEmpty()) return null
        val m = if (metaDirty) meta.copy(lastPage = currentPage) else null
        val ps = dirtyPages.mapNotNull { id -> pages.firstOrNull { it.id == id }?.let { id to it.strokes.toList() } }
        val del = deletedPages.toList()
        metaDirty = false
        dirtyPages.clear()
        deletedPages.clear()
        return Snapshot(m, ps, del)
    }

    private fun write(s: Snapshot) {
        val id = meta.id
        for ((pid, strokes) in s.pages) repo.savePage(id, pid, strokes)
        for (pid in s.deleted) repo.deletePageFile(id, pid)
        s.meta?.let { repo.saveMeta(it) }
    }

    fun saveNow() {
        val snap = takeSnapshot() ?: return
        writer.execute { runCatching { write(snap) } }
    }

    /** 離開畫面或 app 進背景時呼叫：等所有寫檔完成才返回（資料量小，很快）。 */
    fun flushBlocking() {
        saveJob?.cancel()
        if (meta.lastPage != currentPage) {
            meta = meta.copy(lastPage = currentPage)
            metaDirty = true
        }
        val snap = takeSnapshot()
        runCatching {
            writer.submit { if (snap != null) write(snap) }.get(5, TimeUnit.SECONDS)
        }
    }

    fun clearThumbDirty() {
        thumbDirty = false
    }

    companion object {
        private const val MAX_UNDO = 300
        private const val SAVE_DELAY_MS = 1200L

        /** 在背景讀出整本筆記。 */
        suspend fun open(
            repo: NoteRepository,
            noteId: String,
            scope: CoroutineScope,
            writer: ExecutorService,
        ): NoteSession? {
            val loaded = withContext(Dispatchers.IO) {
                val meta = repo.loadMeta(noteId) ?: return@withContext null
                val pages = meta.pageIds.map { repo.loadPage(noteId, it) }.toMutableList()
                meta to pages
            } ?: return null
            return NoteSession(repo, loaded.first, loaded.second, scope, writer)
        }
    }
}
