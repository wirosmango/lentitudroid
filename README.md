# Lentitudroid

TUI-клиент lentitud для Android (Kotlin + Compose + OkHttp).

## Сборка
1. Откройте папку в Android Studio (Ladybug+), дождитесь Gradle sync.
2. (Опционально) положите шрифт в `app/src/main/assets/fonts/CascadiaCode.ttf`.
   Без него используется системный monospace.
3. Run.

## Local server
Кнопка "Up local server" запускает бинарник `liblentitud.so` из
`app/src/main/jniLibs/arm64-v8a/` (Go-сервер, собранный под android/arm64)
и подключается к `ws://127.0.0.1:8080/ws`. Проверьте флаг адреса в `LocalServer.kt`.

## Заметки
- `usesCleartextTraffic=true` разрешает ws:// без TLS; для публичных серверов лучше wss://.
- Адрес можно вводить как `host:port` — схема `ws://` и путь `/ws` добавятся сами.
