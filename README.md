# Lentitudroid

Клиент [lentitud](https://github.com/meltude/lentitud) для Android.
## Сборка
1. Откройте папку в Android Studio, дождитесь синхронизации проекта;
2. (Опционально) положите шрифт в `app/src/main/assets/fonts/CascadiaCode.ttf`,
   Без него используется системный monospace;
3. Запустите сборку.

## Локальный сервер
Кнопка "Up local server" запускает бинарник `liblentitud.so` из
`app/src/main/jniLibs/arm64-v8a/` (Go-сервер, собранный под android/arm64)
и подключается к `ws://127.0.0.1:8080/ws`. Проверьте флаг адреса в `LocalServer.kt`.

# Шрифт
Используется Cascadia Code (Copyright Microsoft, github.com/microsoft/casca), 
лицензия SIL Open Font License 1.1, текст в app/src/main/assets/licenses/OFL.txt.   
