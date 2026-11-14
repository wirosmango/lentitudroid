package dev.lentitudroid

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    // Соединение живёт в ChatConnection (на уровне процесса) и не зависит от жизни Activity.
    val phase: StateFlow<Phase> = ChatConnection.phase
    val startError: StateFlow<String?> = ChatConnection.startError
    val lines: StateFlow<List<Line>> = ChatConnection.lines
    val nick: StateFlow<String> = ChatConnection.nick

    fun connect(address: String, nick: String) = ChatConnection.connect(getApplication(), address, nick)
    fun upLocalServer(nick: String) = ChatConnection.upLocalServer(getApplication(), nick)
    fun send(text: String) = ChatConnection.send(text)
    fun disconnect() {
        _showSettings.value = false
        ChatConnection.disconnect()
    }

    // ---- настройки (хранятся в SharedPreferences) ----
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(AppTheme.fromId(prefs.getString("theme", null)))
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _fontSize = MutableStateFlow(prefs.getInt("font_size", 15).coerceIn(MIN_FONT, MAX_FONT))
    val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

    private val _dynamicColors = MutableStateFlow(prefs.getBoolean("dynamic_colors", false) && dynamicColorsSupported)
    val dynamicColors: StateFlow<Boolean> = _dynamicColors.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    fun setDynamicColors(on: Boolean) {
        val v = on && dynamicColorsSupported
        _dynamicColors.value = v
        prefs.edit().putBoolean("dynamic_colors", v).apply()
    }

    fun setTheme(t: AppTheme) {
        _theme.value = t
        prefs.edit().putString("theme", t.id).apply()
    }

    fun cycleTheme() = setTheme(_theme.value.next())

    fun setFontSize(v: Int) {
        val n = v.coerceIn(MIN_FONT, MAX_FONT)
        _fontSize.value = n
        prefs.edit().putInt("font_size", n).apply()
    }

    fun openSettings() { _showSettings.value = true }
    fun closeSettings() { _showSettings.value = false }

    // ---- история чата (.md) ----
    init { HistoryStore.init(app) }

    private val _saveHistory = MutableStateFlow(HistoryStore.enabled)
    val saveHistory: StateFlow<Boolean> = _saveHistory.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    fun setSaveHistory(on: Boolean) {
        HistoryStore.setEnabled(on)
        _saveHistory.value = on
    }

    fun historyInfo(): String = HistoryStore.summary()

    /** Готовит .md и просит Activity показать диалог «Сохранить как…». */
    fun exportHistory(all: Boolean) {
        val data = HistoryStore.exportText(all)
        if (data == null) {
            _events.tryEmit(UiEvent.Toast(
                if (HistoryStore.enabled) "История пуста" else "История пуста (сохранение выключено)"
            ))
        } else {
            _events.tryEmit(UiEvent.Export(data.first, data.second))
        }
    }

    fun clearHistory() {
        HistoryStore.clear()
        _events.tryEmit(UiEvent.Toast("История удалена"))
    }

    // ---- ввод в чате и команды ----

    /** Обрабатывает ввод в чате: команды (/...) выполняются локально и в чат не уходят. */
    fun submit(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        when {
            t.startsWith("//") -> send(t.removePrefix("/")) // "//текст" отправит "/текст"
            t.startsWith("/") -> runCommand(t)
            else -> send(t)
        }
    }

    private fun note(text: String) = ChatConnection.addLine(Line.System(text))
    private fun fail(text: String) = ChatConnection.addLine(Line.Error(text))

    private fun runCommand(input: String) {
        val parts = input.drop(1).split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()
        when (cmd) {
            "settings" -> openSettings()
            "theme" -> {
                if (arg.isEmpty()) {
                    cycleTheme()
                } else {
                    val t = AppTheme.entries.firstOrNull { it.id == arg.lowercase() }
                    if (t == null) {
                        fail("нет такой темы: $arg (доступны: ${AppTheme.ids()})")
                        return
                    }
                    setTheme(t)
                }
                note("тема: ${_theme.value.id}")
            }
            "palette" -> {
                if (!dynamicColorsSupported) {
                    fail("динамические цвета недоступны: нужен Android 12+")
                } else {
                    setDynamicColors(!_dynamicColors.value)
                    val name = if (_dynamicColors.value) "динамическая (Monet)" else "фиксированная"
                    note("палитра: $name (действует в темах md3e)")
                }
            }
            "export" -> exportHistory(all = arg.equals("all", ignoreCase = true))
            "leave" -> disconnect()
            "help" -> note(
                "/settings — настройки, /theme [имя] — сменить тему, /palette — динамические цвета вкл/выкл, " +
                    "/export [all] — сохранить историю в .md, /leave — выйти на главный экран, //текст — отправить сообщение, начинающееся с /"
            )
            else -> fail("неизвестная команда: /$cmd (список: /help)")
        }
    }

    private companion object {
        const val MIN_FONT = 12
        const val MAX_FONT = 22
    }
}
