# FLY Optimizer — ADB без ПК и без root (Android 16)

## Возможности и границы

Доступ без root: Android shell-разрешения через локальную Wireless Debugging, Shizuku, диагностические команды, некоторые параметры `settings`/`device_config`, Game Mode. **Недоступны**: изменение частот CPU/GPU/RAM напрямую, аппаратных лимитов напряжения и температуры, произвольная запись `/sys` или файлов других приложений, остановка защищённых сервисов. Точная совместимость зависит от прошивки.

На скриншоте пользователя: `Build.DEVICE=peridot`, `Build.SOC_MODEL=SM8635`, `Build.MODEL=24069PC21G` → ПО/железо определяется как POCO F6. Портированная прошивка может показывать изменённые строковые свойства.

## Метод: Termux ADB loopback — без ПК и уведомления Shizuku

1. Установить Termux из официальных источников [F-Droid](https://f-droid.org/packages/com.termux/) / [GitHub](https://github.com/termux/termux-app). Разрешить работу в фоне на время настройки.
2. На телефоне включить Wi-Fi, «Для разработчиков» → «Беспроводная отладка».
3. В Termux:
   ```bash
   pkg update
   pkg install android-tools
   ```
4. Открыть Termux и «Настройки → Для разработчиков → Беспроводная отладка → Подключить устройство с помощью кода» в разделённом экране. Скопировать **порт сопряжения** и шестизначный **код** из системного диалога. Порт сопряжения НЕ совпадает с портом подключения для команд.
5. В Termux выполнить:
   ```bash
   adb pair 127.0.0.1:ПОРТ_СОПРЯЖЕНИЯ
   ```
   Ввести шестизначный код. Адрес `127.0.0.1` обозначает тот же телефон.
6. Вернуться в экран беспроводной отладки. Под строкой «IP-адрес и порт» найти **другой порт**, предназначенный для подключения.
   ```bash
   adb connect 127.0.0.1:ПОРТ_ПОДКЛЮЧЕНИЯ
   adb devices
   ```
   При удаче список содержит устройство со статусом `device`. Некоторые прошивки блокируют loopback ADB — тогда способ не сработает.
7. Для запуска установленного Shizuku после подключения выполнить:
   ```bash
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```
   Теперь открыть Shizuku, проверить статус «Запущено», запустить FLY Optimizer → «Подключить Shizuku».

При перезагрузке может потребоваться повторный запуск ADB/Shizuku. По завершении можно закрыть соединение командой `adb disconnect` и выключить беспроводную отладку, если больше не нужен Shizuku.

## Безопасные команды диагностики

```bash
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.soc.model
adb shell settings get system peak_refresh_rate
adb shell settings get system min_refresh_rate
adb shell dumpsys thermalservice
adb shell dumpsys power
```

`dumpsys` на некоторых прошивках возвращает урезанные сведения — это ожидаемо.

## Тест ограничителя FPS без root + откат

Заменить `ПАКЕТ_ИГРЫ` на реальное имя пакета, например из FLY Optimizer; не вводить команды буквально с угловыми скобками.

1. **До изменений** прочитать и записать исходное значение:
   ```bash
   adb shell device_config get game_overlay ПАКЕТ_ИГРЫ
   ```
2. Пробный профиль для 120-Гц экрана:
   ```bash
   adb shell device_config put game_overlay ПАКЕТ_ИГРЫ 'mode=2,fps=120'
   adb shell device_config get game_overlay ПАКЕТ_ИГРЫ
   ```
3. Если Android поддерживает Game Mode для игры, активировать «Производительность», перезапустить игру и проверить FPS/время кадра. Если игра переопределяет режим, запись не даёт нужного эффекта. Для более холодного и потенциально стабильного теста использовать `mode=2,fps=60` вместо 120.
4. **Откат:** если исходное значение было `null`, удалить пробное:
   ```bash
   adb shell device_config delete game_overlay ПАКЕТ_ИГРЫ
   ```
   Если исходное значение было НЕ `null`, восстановить именно сохранённую строку:
   ```bash
   adb shell device_config put game_overlay ПАКЕТ_ИГРЫ 'СОХРАНЁННОЕ_ИСХОДНОЕ_ЗНАЧЕНИЕ'
   ```
   Убедиться повторным `get`.

Не использовать команд `stop thermalservice`, подмены температур, изменения `/sys/class/thermal` или отключения защиты батареи. Это не устраняет фризы и может привести к перегреву.

## Источники

- [Shizuku: официальный запуск через беспроводную отладку](https://shizuku.rikka.app/guide/setup/)
- [Android Developers: FPS throttling](https://developer.android.com/games/optimize/adpf/gamemode/fps-throttling)
- [AOSP: Thermal mitigation](https://source.android.com/docs/core/power/thermal-mitigation)
- [Community example: Termux self-ADB pairing](https://gist.github.com/kairusds/1d4e32d3cf0d6ca44dc126c1a383a48d)
