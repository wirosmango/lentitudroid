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

## Скриншоты

<img width="250" height="550" alt="Screenshot_2026-11-12-23-35-26-23_12357bf97180346bebda523c4919a338" src="https://github.com/user-attachments/assets/8de30c18-fdf5-4db3-82e9-0a9737f75e5d" />

<img width="250" height="550" alt="Screenshot_2026-11-12-23-37-08-12_12357bf97180346bebda523c4919a338" src="https://github.com/user-attachments/assets/f19b3925-71f6-472b-80d9-9c046828fdad" />




## Шрифт
Используется Cascadia Code (Copyright Microsoft, github.com/microsoft/cascadia-code), 
лицензия SIL Open Font License 1.1, текст в app/src/main/assets/licenses/OFL.txt.   
