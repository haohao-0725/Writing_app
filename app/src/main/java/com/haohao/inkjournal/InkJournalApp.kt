package com.haohao.inkjournal

import android.app.Application
import com.haohao.inkjournal.data.NoteRepository
import com.haohao.inkjournal.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InkJournalApp : Application() {
    lateinit var notes: NoteRepository
        private set
    lateinit var settings: SettingsRepository
        private set

    /** 所有寫檔都排進這條執行緒，依照先後順序完成。 */
    val writer: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "note-writer") }

    private val _dataVersion = MutableStateFlow(0)
    /** 筆記清單或預覽圖有變動時 +1，主頁看到就重新整理。 */
    val dataVersion: StateFlow<Int> = _dataVersion

    override fun onCreate() {
        super.onCreate()
        notes = NoteRepository(filesDir)
        settings = SettingsRepository(filesDir)
    }

    fun notifyDataChanged() {
        _dataVersion.value = _dataVersion.value + 1
    }
}
