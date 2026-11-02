package com.haohao.inkjournal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.haohao.inkjournal.ui.EditorScreen
import com.haohao.inkjournal.ui.HomeScreen
import com.haohao.inkjournal.ui.Ink
import com.haohao.inkjournal.ui.InkJournalTheme
import com.haohao.inkjournal.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val app = application as InkJournalApp
        setContent {
            InkJournalTheme {
                // 三個畫面：主頁 / 筆記 / 設定。不用導覽函式庫，一個字串就夠。
                var route by rememberSaveable { mutableStateOf(ROUTE_HOME) }
                Surface(color = Ink.Bg, modifier = Modifier.fillMaxSize()) {
                    key(route) {
                        when {
                            route == ROUTE_SETTINGS -> SettingsScreen(app, onBack = { route = ROUTE_HOME })
                            route.startsWith(ROUTE_EDITOR) -> EditorScreen(
                                app,
                                noteId = route.removePrefix(ROUTE_EDITOR),
                                onClose = { route = ROUTE_HOME },
                            )
                            else -> HomeScreen(
                                app,
                                onOpen = { route = ROUTE_EDITOR + it },
                                onSettings = { route = ROUTE_SETTINGS },
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val ROUTE_HOME = "home"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_EDITOR = "editor:"
    }
}
