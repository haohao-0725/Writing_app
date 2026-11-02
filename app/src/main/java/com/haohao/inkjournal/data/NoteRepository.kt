package com.haohao.inkjournal.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 筆記存在 app 私有資料夾：
 *
 *   notes/<筆記id>/note.json          標題、時間、整本設定、頁面順序
 *   notes/<筆記id>/pages/<頁id>.hwik  每一頁的向量筆跡（PageCodec）
 *   notes/<筆記id>/thumb.png          主頁預覽圖（可以隨時重產生，不進備份）
 *
 * 這個類別只用 java.io，不碰 Android API，方便寫單元測試。
 */
class NoteRepository(root: File) {
    private val notesDir = File(root, "notes").apply { mkdirs() }
    private val lock = Any()

    private fun noteDir(id: String): File {
        require(Ids.isSafe(id)) { "id 不合法：$id" }
        return File(notesDir, id)
    }

    private fun metaFile(id: String) = File(noteDir(id), "note.json")
    private fun pageFile(noteId: String, pageId: String): File {
        require(Ids.isSafe(pageId)) { "頁 id 不合法：$pageId" }
        return File(File(noteDir(noteId), "pages"), "$pageId.hwik")
    }

    fun thumbFile(id: String) = File(noteDir(id), "thumb.png")

    fun listNotes(): List<NoteMeta> {
        val dirs = notesDir.listFiles { f -> f.isDirectory && Ids.isSafe(f.name) } ?: return emptyList()
        return dirs.mapNotNull { dir -> loadMeta(dir.name) }.sortedByDescending { it.updatedAt }
    }

    fun loadMeta(id: String): NoteMeta? = runCatching {
        NoteJson.decode(metaFile(id).readText(Charsets.UTF_8)).let { m ->
            if (m.pageIds.isEmpty()) m.copy(pageIds = listOf(Ids.newPageId())) else m
        }
    }.getOrNull()

    fun saveMeta(meta: NoteMeta) = synchronized(lock) {
        writeAtomic(metaFile(meta.id), NoteJson.encode(meta).toByteArray(Charsets.UTF_8))
    }

    fun createNote(title: String, settings: NoteSettings, now: Long = System.currentTimeMillis()): NoteMeta {
        val meta = NoteMeta(
            id = Ids.newNoteId(now),
            title = title,
            createdAt = now,
            updatedAt = now,
            settings = settings,
            pageIds = listOf(Ids.newPageId()),
            lastPage = 0,
        )
        saveMeta(meta)
        return meta
    }

    fun loadPage(noteId: String, pageId: String): PageData {
        val f = pageFile(noteId, pageId)
        if (!f.exists()) return PageData(pageId)
        return try {
            PageData(pageId, f.inputStream().buffered().use { PageCodec.decode(it) })
        } catch (e: IOException) {
            // 壞掉的頁面先另存一份，避免下一次存檔把它蓋掉
            f.copyTo(File(f.parentFile, "$pageId.broken-${System.currentTimeMillis()}"), overwrite = true)
            PageData(pageId)
        }
    }

    fun savePage(noteId: String, pageId: String, strokes: List<RawStroke>) = synchronized(lock) {
        writeAtomic(pageFile(noteId, pageId), PageCodec.encode(strokes))
    }

    fun deletePageFile(noteId: String, pageId: String) = synchronized(lock) {
        pageFile(noteId, pageId).delete()
    }

    fun deleteNote(id: String) = synchronized(lock) {
        noteDir(id).deleteRecursively()
    }

    // ---------------- 備份 ----------------

    /** 匯出成 zip：所有筆記（不含預覽圖）+ 設定。 */
    fun exportBackup(out: OutputStream, settingsJson: String?, appVersion: String): Int = synchronized(lock) {
        var count = 0
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(
                BACKUP_MANIFEST,
                JSONObject()
                    .put("format", BACKUP_FORMAT)
                    .put("version", 1)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("appVersion", appVersion)
                    .toString(2).toByteArray(Charsets.UTF_8),
            )
            if (settingsJson != null) put("settings.json", settingsJson.toByteArray(Charsets.UTF_8))
            for (meta in listNotes()) {
                put("notes/${meta.id}/note.json", metaFile(meta.id).readBytes())
                for (pid in meta.pageIds) {
                    val f = pageFile(meta.id, pid)
                    if (f.exists()) put("notes/${meta.id}/pages/$pid.hwik", f.readBytes())
                }
                count++
            }
        }
        count
    }

    data class ImportResult(val imported: Int, val skipped: Int, val settingsJson: String?)

    /**
     * 匯入 zip 備份。同一本筆記（同 id）以「最後修改時間」比較新的為準，本機比較新就略過。
     * 只接受符合格式的路徑（防止 zip 裡藏 ../ 之類的路徑寫到別的地方）。
     */
    fun importBackup(input: InputStream): ImportResult {
        val metas = HashMap<String, ByteArray>()
        val pages = HashMap<String, HashMap<String, ByteArray>>()
        var settingsJson: String? = null
        var sawManifest = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name
                val bytes = readLimited(zip, MAX_ENTRY_BYTES)
                when {
                    name == BACKUP_MANIFEST -> sawManifest = true
                    name == "settings.json" -> settingsJson = String(bytes, Charsets.UTF_8)
                    else -> {
                        NOTE_META_PATH.matchEntire(name)?.let { m -> metas[m.groupValues[1]] = bytes }
                        NOTE_PAGE_PATH.matchEntire(name)?.let { m ->
                            pages.getOrPut(m.groupValues[1]) { HashMap() }[m.groupValues[2]] = bytes
                        }
                    }
                }
            }
        }
        if (!sawManifest) throw IOException("這不是沉澱手記的備份檔")

        var imported = 0
        var skipped = 0
        for ((id, metaBytes) in metas) {
            val incoming = runCatching { NoteJson.decode(String(metaBytes, Charsets.UTF_8)) }.getOrNull()
            if (incoming == null || incoming.id != id) { skipped++; continue }
            val local = loadMeta(id)
            if (local != null && local.updatedAt >= incoming.updatedAt) { skipped++; continue }
            val notePages = pages[id].orEmpty()
            // 先檢查每一頁都讀得出來，再寫入
            val ok = notePages.values.all { runCatching { PageCodec.decode(it) }.isSuccess }
            if (!ok) { skipped++; continue }
            synchronized(lock) {
                val tmp = File(notesDir, "$id.importing")
                tmp.deleteRecursively()
                File(tmp, "pages").mkdirs()
                File(tmp, "note.json").writeBytes(metaBytes)
                for ((pid, b) in notePages) File(File(tmp, "pages"), "$pid.hwik").writeBytes(b)
                val dir = noteDir(id)
                val old = File(notesDir, "$id.old")
                old.deleteRecursively()
                if (dir.exists() && !dir.renameTo(old)) throw IOException("無法取代筆記 $id")
                if (!tmp.renameTo(dir)) {
                    old.renameTo(dir)
                    throw IOException("無法寫入筆記 $id")
                }
                old.deleteRecursively()
            }
            imported++
        }
        return ImportResult(imported, skipped, settingsJson)
    }

    companion object {
        const val BACKUP_FORMAT = "inkjournal-backup"
        const val BACKUP_MANIFEST = "backup.json"
        private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024
        private val NOTE_META_PATH = Regex("^notes/([A-Za-z0-9_-]{1,64})/note\\.json$")
        private val NOTE_PAGE_PATH = Regex("^notes/([A-Za-z0-9_-]{1,64})/pages/([A-Za-z0-9_-]{1,64})\\.hwik$")

        fun writeAtomic(target: File, bytes: ByteArray) {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            FileOutputStream(tmp).use { fos ->
                fos.write(bytes)
                fos.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                // 有些檔案系統不能直接覆蓋，先刪再改名
                target.delete()
                if (!tmp.renameTo(target)) throw IOException("無法寫入 ${target.name}")
            }
        }

        private fun readLimited(input: InputStream, limit: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) throw IOException("備份檔內容太大")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
