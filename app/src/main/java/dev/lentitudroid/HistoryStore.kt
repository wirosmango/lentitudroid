package dev.lentitudroid

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * История чата в Markdown: одна сессия (подключение) — один .md-файл в filesDir/history.
 * Файлы приватны для приложения; наружу они попадают через экспорт (/export или «Настройки»).
 */
object HistoryStore {
    private lateinit var dir: File
    private lateinit var prefs: SharedPreferences
    private val lock = Any()

    @Volatile var enabled = true
        private set

    private var sessionActive = false
    private var sessionServer = ""
    private var sessionNick = ""
    private var current: File? = null
    private var header = ""

    private val timeFmt get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateTimeFmt get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileFmt get() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun init(ctx: Context) = synchronized(lock) {
        if (::dir.isInitialized) return@synchronized
        val app = ctx.applicationContext
        dir = File(app.filesDir, "history").apply { mkdirs() }
        prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        enabled = prefs.getBoolean("save_history", true)
    }

    fun setEnabled(v: Boolean) = synchronized(lock) {
        enabled = v
        prefs.edit().putBoolean("save_history", v).apply()
    }

    /** Экранирует служебные символы Markdown, чтобы текст сообщения отображался как есть. */
    private fun md(s: String) = s.replace(Regex("""([\\`*_\[\]<>#~|])"""), "\\\\$1")

    fun startSession(server: String, nick: String) = synchronized(lock) {
        sessionActive = true
        sessionServer = server
        sessionNick = nick
        current = null
        ensureFile()
    }

    private fun ensureFile(): File? {
        if (!enabled || !sessionActive || !::dir.isInitialized) return null
        val now = Date()
        var f = current
        if (f == null) {
            val host = sessionServer.substringAfter("://").substringBefore('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
            f = File(dir, "${fileFmt.format(now)}_$host.md")
            current = f
            header = "# Чат lentitud\n\n" +
                "- Сервер: `$sessionServer`\n" +
                "- Ник: ${md(sessionNick)}\n" +
                "- Начало: ${dateTimeFmt.format(now)}\n\n---\n\n"
        }
        if (!f.exists()) {
            dir.mkdirs()
            f.writeText(header)
        }
        return f
    }

    private fun append(line: String) = synchronized(lock) {
        try {
            ensureFile()?.appendText(line)
        } catch (_: Exception) {
            // запись истории не должна ломать чат
        }
    }

    fun message(nick: String, text: String) =
        append("- **${timeFmt.format(Date())} ${md(nick)}:** ${md(text)}\n")

    fun system(text: String) = append("- *${timeFmt.format(Date())} — ${md(text)}*\n")

    fun endSession() = synchronized(lock) {
        if (sessionActive) append("\n*Сессия завершена ${dateTimeFmt.format(Date())}*\n")
        sessionActive = false
        current = null
    }

    private fun files(): List<File> =
        if (!::dir.isInitialized) emptyList()
        else dir.listFiles { f -> f.isFile && f.extension == "md" }?.sortedBy { it.name }.orEmpty()

    /** Имя файла и текст для экспорта: текущая/последняя сессия или вся история; null — нечего экспортировать. */
    fun exportText(all: Boolean): Pair<String, String>? = synchronized(lock) {
        val list = files()
        if (list.isEmpty()) return@synchronized null
        if (all) {
            val name = "lentitud-history-${fileFmt.format(Date())}.md"
            name to list.joinToString("\n\n---\n\n") { it.readText() }
        } else {
            val f = current?.takeIf { it.exists() } ?: list.last()
            f.name to f.readText()
        }
    }

    fun summary(): String = synchronized(lock) {
        val list = files()
        if (list.isEmpty()) "история пуста"
        else "сессий: ${list.size} · ${maxOf(1L, list.sumOf { it.length() } / 1024)} КБ"
    }

    fun clear() = synchronized(lock) {
        files().forEach { it.delete() }
        // если сессия идёт — запись продолжится в новом файле
        current = null
    }
}
