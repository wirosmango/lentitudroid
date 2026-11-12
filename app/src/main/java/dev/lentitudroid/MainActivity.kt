package dev.lentitudroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = SystemBarStyle.dark(Bg.toArgb())
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        setContent {
            val vm: ChatViewModel = viewModel()
            val mono = remember { loadMono(applicationContext) }
            val phase by vm.phase.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalMono provides mono) {
                if (phase == Phase.Connected) ChatScreen(vm, mono) else StartScreen(vm, mono)
            }
        }
    }
}
