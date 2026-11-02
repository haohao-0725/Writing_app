package com.haohao.inkjournal

import com.haohao.inkjournal.data.NoteRepository
import com.haohao.inkjournal.data.NoteSettings
import com.haohao.inkjournal.data.RawStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun dot(x: Float) = RawStroke(0, -1, 0.5f, 0.015f, 3, 0f, floatArrayOf(x), floatArrayOf(1f), intArrayOf(0))

    @Test
    fun createSaveListAndDelete() {
        val repo = NoteRepository(tmp.newFolder("a"))
        val meta = repo.createNote("第一本", NoteSettings())
        repo.savePage(meta.id, meta.pageIds[0], listOf(dot(1f), dot(2f)))
        val listed = repo.listNotes()
        assertEquals(1, listed.size)
        assertEquals("第一本", listed[0].title)
        assertEquals(2, repo.loadPage(meta.id, meta.pageIds[0]).strokes.size)
        repo.deleteNote(meta.id)
        assertTrue(repo.listNotes().isEmpty())
    }

    @Test
    fun backupRoundTripAndNewerWins() {
        val a = NoteRepository(tmp.newFolder("a"))
        val m = a.createNote("日記", NoteSettings(), now = 1000L)
        a.savePage(m.id, m.pageIds[0], listOf(dot(3f)))
        val zip = ByteArrayOutputStream().also { a.exportBackup(it, "{}", "test") }.toByteArray()

        val b = NoteRepository(tmp.newFolder("b"))
        val r1 = b.importBackup(ByteArrayInputStream(zip))
        assertEquals(1, r1.imported)
        assertEquals(1, b.loadPage(m.id, m.pageIds[0]).strokes.size)

        // b 這邊比較新 → 再匯入舊備份會略過
        b.saveMeta(b.loadMeta(m.id)!!.copy(updatedAt = 5000L, title = "改過"))
        val r2 = b.importBackup(ByteArrayInputStream(zip))
        assertEquals(0, r2.imported)
        assertEquals(1, r2.skipped)
        assertEquals("改過", b.loadMeta(m.id)!!.title)
    }

    @Test
    fun importIgnoresPathTricks() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry(NoteRepository.BACKUP_MANIFEST)); z.write("{}".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("../../evil.txt")); z.write("x".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("notes/../x/note.json")); z.write("{}".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val root = tmp.newFolder("c")
        val r = NoteRepository(root).importBackup(ByteArrayInputStream(bytes))
        assertEquals(0, r.imported)
        assertFalse(java.io.File(root.parentFile, "evil.txt").exists())
    }
}
