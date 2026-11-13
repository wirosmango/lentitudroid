package dev.lentitudroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ChatViewModel = viewModel()
            val mono = remember { loadMono(applicationContext) }
            val phase by vm.phase.collectAsStateWithLifecycle()
            val theme by vm.theme.collectAsStateWithLifecycle()
            val settings by vm.showSettings.collectAsStateWithLifecycle()
            val pal = theme.palette

            // статус-бар и навигационная панель в цвет текущей темы
            LaunchedEffect(theme) {
                val bar = if (pal.dark) SystemBarStyle.dark(pal.bg.toArgb())
                else SystemBarStyle.light(pal.bg.toArgb(), pal.bg.toArgb())
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }

            CompositionLocalProvider(LocalMono provides mono, LocalPalette provides pal) {
                when {
                    settings -> SettingsScreen(vm)
                    phase == Phase.Connected -> ChatScreen(vm)
                    else -> StartScreen(vm)
                }
            }
        }
    }
}
