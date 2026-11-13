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

<img width="250" height="550" alt="5456128382291616006_119" src="https://github.com/user-attachments/assets/0d266764-c34c-49f0-87d3-4b02b7383449" />
<img width="250" height="550" alt="5456128382291616005_119" src="https://github.com/user-attachments/assets/e6627e6f-b0e6-4132-af1c-93a8221fd38d" />
<img width="250" height="550" alt="5456128382291616004_119" src="https://github.com/user-attachments/assets/607de120-8e5f-4da6-be13-7a761edf404c" />
<img width="250" height="550" alt="5456128382291616001_119" src="https://github.com/user-attachments/assets/401b7b2c-e2c2-4312-b8a7-9094442599aa" />
<img width="250" height="550" alt="5456128382291616002_119" src="https://github.com/user-attachments/assets/58f2e5e7-4897-4fdc-abf8-f7137fc23dda" />
<img width="250" height="550" alt="5456128382291616003_119" src="https://github.com/user-attachments/assets/fd4a4cf0-cece-46ff-ad86-33ed532ad555" />






## Шрифт
Используется Cascadia Code (Copyright Microsoft, github.com/microsoft/cascadia-code), 
лицензия SIL Open Font License 1.1, текст в app/src/main/assets/licenses/OFL.txt.   
