package com.haohao.inkjournal.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haohao.inkjournal.BuildConfig
import com.haohao.inkjournal.InkJournalApp
import com.haohao.inkjournal.data.PageCodec
import com.haohao.inkjournal.data.PenPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(app: InkJournalApp, onBack: () -> Unit) {
    val s by app.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val n = context.contentResolver.openOutputStream(uri)!!.use {
                        app.notes.exportBackup(it, app.settings.toJson(), BuildConfig.VERSION_NAME)
                    }
                    "已匯出 $n 本筆記"
                }.getOrElse { "匯出失敗：${it.message}" }
            }
            snackbar.showSnackbar(msg)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val r = context.contentResolver.openInputStream(uri)!!.use { app.notes.importBackup(it) }
                    if (r.skipped > 0) "已匯入 ${r.imported} 本，略過 ${r.skipped} 本（這台的版本比較新或檔案損壞）"
                    else "已匯入 ${r.imported} 本筆記"
                }.getOrElse { "匯入失敗：${it.message}" }
            }
            app.notifyDataChanged()
            snackbar.showSnackbar(msg)
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Ink.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Ink.Text)
                }
                Text("設定", fontSize = 22.sp, color = Ink.Text)
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
            ) {
                SectionTitle("筆的顏色與粗細")
                Card {
                    PaletteEditor(s.defaultPalette) { p -> app.settings.update { it.copy(defaultPalette = p) } }
                    Hint(
                        "這是「新筆記」的預設。已經寫過的筆記不會跟著變；想改某一本，打開它按右上角的調整圖示。",
                        Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { app.settings.update { it.copy(defaultPalette = PenPalette.DEFAULT) } }) {
                        Text("還原成原本的配色")
                    }
                }

                SectionTitle("新筆記的背景")
                Card {
                    BackgroundEditor(s.defaultBackground, s.defaultLineSpacingMm, s.defaultGridSpacingMm) { t, line, grid ->
                        app.settings.update {
                            it.copy(defaultBackground = t, defaultLineSpacingMm = line, defaultGridSpacingMm = grid)
                        }
                    }
                }

                SectionTitle("書寫")
                Card {
                    SwitchRow(
                        title = "手指也可以寫字",
                        hint = "關閉時只有 S Pen 會寫字，手指用來捲動和縮放，手掌碰到也不會畫出線（建議關閉）。",
                        checked = s.fingerDrawing,
                        onChange = { v -> app.settings.update { it.copy(fingerDrawing = v) } },
                    )
                }

                SectionTitle("備份")
                Card {
                    Text("筆記只存在這台平板裡。換機或重灌前，先匯出一份備份檔。", color = Ink.Text)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OutlinedButton(onClick = { exportLauncher.launch("沉澱手記備份_${LocalDate.now()}.zip") }) {
                            Text("匯出備份檔")
                        }
                        Spacer(Modifier.size(12.dp))
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                            Text("從備份檔匯入")
                        }
                    }
                    Hint("匯入時，同一本筆記以最後修改時間比較新的為準。", Modifier.padding(top = 8.dp))
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Ink.Outline)
                    SwitchRow(
                        title = "自動上傳備份到雲端",
                        hint = "之後的版本開放。",
                        checked = false,
                        enabled = false,
                        onChange = {},
                    )
                }

                SectionTitle("關於")
                Card {
                    Text("沉澱手記 ${BuildConfig.VERSION_NAME}", color = Ink.Text)
                    Hint(
                        "筆跡用向量格式（HWIK v${PageCodec.VERSION}）存在平板裡，放大多少倍都清楚。",
                        Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Bar)
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = if (enabled) Ink.Text else Ink.Muted)
            Hint(hint)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
