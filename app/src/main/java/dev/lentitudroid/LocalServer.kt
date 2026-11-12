package dev.lentitudroid

import android.content.Context
import java.io.File

/**
 * Запуск локального сервера lentitud прямо на телефоне.
 *
 * Сервер написан на Go, поэтому нужен собранный под Android бинарник:
 *   GOOS=android GOARCH=arm64 CGO_ENABLED=1 CC=<ndk>/aarch64-linux-android24-clang go build -o liblentitud.so ./cmd/lentitud
 * и положить его в app/src/main/jniLibs/arm64-v8a/liblentitud.so
 * (имя lib*.so обязательно — только такие файлы Android распаковывает в nativeLibraryDir).
 */
object LocalServer {
    const val URL = "ws://127.0.0.1:8080/ws"

    // ВАЖНО: проверьте имя флага адреса в README сервера и поправьте при необходимости.
    private val ARGS = listOf("-addr", "127.0.0.1:8080")

    private var process: Process? = null

    /** Возвращает null при успехе, иначе текст ошибки. */
    @Synchronized
    fun start(context: Context): String? {
        if (process?.isAlive == true) return null
        val bin = File(context.applicationInfo.nativeLibraryDir, "liblentitud.so")
        if (!bin.exists()) return "бинарник сервера не найден (jniLibs/arm64-v8a/liblentitud.so)"
        return try {
            process = ProcessBuilder(listOf(bin.absolutePath) + ARGS)
                .redirectErrorStream(true)
                .start()
            null
        } catch (e: Exception) {
            "не удалось запустить сервер: ${e.message}"
        }
    }

    @Synchronized
    fun stop() {
        process?.destroy()
        process = null
    }
}
