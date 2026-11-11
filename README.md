# WikarM@il

Почтовый клиент для Android: каждый ящик — отдельная вкладка, письма разных ящиков никогда не смешиваются.

Стек: Kotlin, Jetpack Compose, Material 3, Room, DataStore, JavaMail for Android 1.6.7 (javax.mail, IMAP/SMTP), Hilt, WorkManager. minSdk 26. Без рекламы и аналитики.

## WikarM@il 1.2

## WikarM@il — почтовый клиент для Android, в котором каждый ящик живёт в своей вкладке.

Что нового в 1.2
Исправлены пустые папки. Письма теперь корректно загружаются во всех IMAP-папках, включая вложенные и архивные. Ошибка в одном письме больше не прерывает загрузку всей папки.
Вложения. К новому письму можно прикрепить один или несколько файлов, без доступа ко всему хранилищу. Под текстом показывается список файлов с размером и кнопкой удаления, есть предупреждение, если вложения больше 20 МБ. Имена файлов на кириллице передаются правильно. Вложения сохраняются в черновике, а при пересылке исходные файлы прикрепляются автоматически.
Новая иконка — металлический конверт с буквой W. Поддерживаются тематические иконки Android 13+.
Экран запуска с логотипом WikarM@il вместо белой вспышки при открытии.
Основные возможности
Каждый почтовый ящик — отдельная вкладка в шапке, входящие разных ящиков не смешиваются.
Поддержка папок, включая созданные вами.
Свайп справа налево удаляет письмо, слева направо отмечает его прочитанным.
В меню вкладки: «Пометить все прочитанными» и «Удалить все».
Мгновенное получение новых писем через IMAP IDLE.
Настройки: тема оформления, язык приложения (русский, английский, немецкий), уведомления.
🇬🇧 WikarM@il 1.2

## WikarM@il is an Android mail client where every mailbox lives in its own tab.

## What’s new in 1.2
Empty folders fixed. Messages now load correctly in all IMAP folders, including nested and archive folders. A single malformed message no longer stops the whole folder from loading.
Attachments. Attach one or more files to a new message, with no storage permission required. The files appear below the message body with their size and a remove button, and you get a warning if the total exceeds 20 MB. Non-Latin file names are sent correctly. Attachments are saved with drafts, and original attachments are included automatically when forwarding.
New icon: a metallic envelope with a W. Themed icons on Android 13+ are supported.
Launch screen with the WikarM@il logo instead of a white flash on startup.
Key features
Each mailbox is a separate tab in the header, so inboxes are never mixed.
Folder support, including your own custom folders.
Swipe right-to-left to delete, left-to-right to mark as read.
Tab menu actions: “Mark all as read” and “Delete all”.
Instant delivery of new mail via IMAP IDLE.
Settings: app theme, app language (English, Russian, German), notifications.

## Возможности (версия 1.1)

- Несколько ящиков вручную (IMAP/SMTP, SSL/STARTTLS), пресеты IONOS и Яндекс (`imap.ya.ru:993`, `smtp.ya.ru:465`).
  Пароли шифруются AES-GCM ключом из Android Keystore.
- Вкладки ящиков со счётчиком непрочитанных во «Входящих»; переключение — только тапом по шапке.
- Меню вкладки (долгое нажатие или «⋮»): «Только важные», «Пометить все прочитанными», «Удалить все письма»,
  «Управление ящиками», «Настройки».
- Боковое меню с папками текущего ящика (IMAP LIST, вложенные, счётчики), создание/переименование/удаление своих папок.
- Список писем: аватар, имя, тема, превью, время, звёздочка «Важное» (IMAP-флаг `\Flagged`, синхронизация в обе стороны).
  Свайп справа налево — в Корзину (Snackbar «Отменить»), слева направо — прочитано/непрочитано.
  Порог ~38 % ширины, фон проявляется по мере свайпа, иконка растёт у порога, вибрация при срабатывании.
  Долгое нажатие — переместить в папку.
- Все действия применяются локально сразу и уходят на сервер через офлайн-очередь (WorkManager).
- Просмотр письма (WebView, внешние картинки по кнопке), вложения в «Загрузки», ответ, ответ всем,
  пересылка (с вложениями), новое письмо с выбором ящика-отправителя.
- Настройки: тема (светлая / тёмная / как в системе), язык (как в системе / Русский / English / Deutsch,
  per-app language API), уведомления (общий переключатель и для каждого ящика), режим получения
  («Мгновенно» — IMAP IDLE в foreground-сервисе, «Каждые 15 минут» — WorkManager), «О приложении».
- Уведомления: тихий канал «Фоновая синхронизация» для сервиса и канал «Новые письма» с группировкой по ящику;
  у письма кнопки «Прочитано» и «Удалить», у группы — «Прочитать все»; тап открывает письмо в нужной вкладке.

## Сборка

```
./gradlew assembleDebug
```

Debug-сборки подписываются постоянным ключом `app/debug.keystore`, поэтому новые версии ставятся поверх старых.

GitHub Actions собирает APK на каждый push. Сборка ветки `main` публикуется в GitHub Releases
с тегами `v<versionName>` и `latest`; файл называется `WikarM@il.v<versionName>.apk`.

Автор: Wikar · https://github.com/Wikaar/mail-client
