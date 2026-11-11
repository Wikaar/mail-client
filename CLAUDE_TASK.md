# WikarM@il 1.2 — новая иконка

Ресурсы лежат в папке `res/` рядом с этим файлом. Выполни по шагам и в конце дай краткий отчёт.

## 1. Замена иконки
1. Скопируй содержимое `res/` в `app/src/main/res/` с заменой файлов.
2. Удали старые файлы иконки, иначе сборка упадёт с "Duplicate resources":
   - `mipmap-*/ic_launcher*.webp` во всех плотностях;
   - `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_background.xml`, `drawable-v24/ic_launcher_foreground.xml`;
   - старое объявление цвета `ic_launcher_background` в других файлах `values/` (теперь он в `values/wikar_brand_colors.xml`).
3. Проверь в `AndroidManifest.xml` у `<application>`:
   `android:icon="@mipmap/ic_launcher"` и `android:roundIcon="@mipmap/ic_launcher_round"`.
4. Поищи в проекте другие ссылки на старую иконку (например, `@drawable/ic_launcher*` в уведомлениях
   или экране «О приложении») и переведи их на `@mipmap/ic_launcher`. Для маленькой иконки уведомлений
   (`setSmallIcon`) цветную иконку не ставь — там должна остаться монохромная.

## 2. Версия
- `versionName = "1.2"`, `versionCode` увеличь на 1.
- Если версия захардкожена (например, в «О приложении»), бери её из `BuildConfig.VERSION_NAME`.

## 3. Проверка и коммит
1. Собери проект (`assembleRelease`, или debug, если release не настроен) и убедись, что ошибок нет.
2. Коммит `v1.2: новая иконка`, тег `v1.2`, push.
3. После выполнения удали из корня репозитория `CLAUDE_TASK.md` и папку `res/` этого пакета.
