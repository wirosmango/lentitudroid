package dev.lentitudroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    // Разрешение на уведомления (Android 13+): без него уведомление сервиса скрыто, но соединение работает
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var askedNotifications = false

    // Экспорт истории: пользователь выбирает, куда сохранить .md (Загрузки, Диск и т. д.)
    private var pendingExport: String? = null
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
            val text = pendingExport
            pendingExport = null
            if (uri != null && text != null) {
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                        } catch (e: Exception) {
                            false
                        }
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (ok) "История сохранена" else "Не удалось сохранить файл",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ChatViewModel = viewModel()
            val mono = remember { loadMono(applicationContext) }
            val phase by vm.phase.collectAsStateWithLifecycle()
            val theme by vm.theme.collectAsStateWithLifecycle()
            val settings by vm.showSettings.collectAsStateWithLifecycle()
            val dynamic by vm.dynamicColors.collectAsStateWithLifecycle()
            LaunchedEffect(phase) {
                if (phase == Phase.Connecting && !askedNotifications && Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    askedNotifications = true
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            LaunchedEffect(Unit) {
                vm.events.collect { e ->
                    when (e) {
                        is UiEvent.Export -> {
                            pendingExport = e.content
                            createDocument.launch(e.fileName)
                        }
                        is UiEvent.Toast ->
                            Toast.makeText(this@MainActivity, e.text, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val pal = remember(theme, dynamic) { resolvePalette(this@MainActivity, theme, dynamic) }

            // статус-бар и навигационная панель в цвет текущей темы
            LaunchedEffect(pal) {
                val bar = if (pal.dark) SystemBarStyle.dark(pal.bg.toArgb())
                else SystemBarStyle.light(pal.bg.toArgb(), pal.bg.toArgb())
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }

            CompositionLocalProvider(LocalMono provides mono, LocalPalette provides pal) {
                when {
                    settings -> SettingsScreen(vm)
                    phase == Phase.Connected || phase == Phase.Reconnecting -> ChatScreen(vm)
                    else -> StartScreen(vm)
                }
            }
        }
    }
}
