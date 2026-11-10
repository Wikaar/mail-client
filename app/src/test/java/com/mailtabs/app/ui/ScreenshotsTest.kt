package com.mailtabs.app.ui

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.mailtabs.app.R
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.db.MessageEntity
import com.mailtabs.app.data.db.MessageListItem
import com.mailtabs.app.data.db.Security
import com.mailtabs.app.settings.SettingsState
import com.mailtabs.app.settings.SyncMode
import com.mailtabs.app.settings.ThemeMode
import com.mailtabs.app.ui.main.AccountMenuContent
import com.mailtabs.app.ui.main.AccountTab
import com.mailtabs.app.ui.main.AccountTabItem
import com.mailtabs.app.ui.main.NoAccounts
import com.mailtabs.app.ui.main.StarToggle
import com.mailtabs.app.ui.main.SwipeableMessageRow
import com.mailtabs.app.ui.message.Header
import com.mailtabs.app.ui.settings.SettingsActions
import com.mailtabs.app.ui.settings.SettingsContent
import com.mailtabs.app.ui.theme.MailTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Скриншоты экранов WikarM@il на демо-данных (без сети и реальных ящиков).
 * Запуск: ./gradlew recordRoborazziDebug → app/build/outputs/roborazzi/*.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], application = Application::class, qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    // ---------- Главный экран ----------

    @Test @Config(qualifiers = "+ru")
    fun inbox_light_ru() = render { DemoMain() }.also { shot("01_inbox_light_ru") }

    @Test @Config(qualifiers = "+ru-night")
    fun inbox_dark_ru() = render { DemoMain() }.also { shot("02_inbox_dark_ru") }

    @Test @Config(qualifiers = "+ru")
    fun swipe_read_ru() {
        render { DemoMain() }
        swipe("row_2", toRight = true)
        shot("03_swipe_read_ru")
    }

    @Test @Config(qualifiers = "+ru")
    fun swipe_delete_ru() {
        render { DemoMain() }
        swipe("row_3", toRight = false)
        shot("04_swipe_delete_ru")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test @Config(qualifiers = "+ru")
    fun menu_ru() {
        render { DemoMain(showMenu = true) }
        captureScreenRoboImage("build/outputs/roborazzi/05_tab_menu_ru.png")
    }

    @Test @Config(qualifiers = "+ru")
    fun only_important_ru() = render { DemoMain(onlyFlagged = true) }.also { shot("06_only_important_ru") }

    @Test @Config(qualifiers = "+en")
    fun inbox_en() = render { DemoMain() }.also { shot("07_inbox_light_en") }

    @Test @Config(qualifiers = "+de-night")
    fun inbox_dark_de() = render { DemoMain() }.also { shot("08_inbox_dark_de") }

    // ---------- Письмо ----------

    @Test @Config(qualifiers = "+ru")
    fun message_ru() = render { DemoMessage() }.also { shot("09_message_light_ru") }

    @Test @Config(qualifiers = "+ru-night")
    fun message_dark_ru() = render { DemoMessage() }.also { shot("10_message_dark_ru") }

    // ---------- Настройки (высокий экран, чтобы влезло всё) ----------

    @Test @Config(qualifiers = "+ru-h1500dp")
    fun settings_ru() = render { DemoSettings("ru", ThemeMode.SYSTEM) }.also { shot("11_settings_light_ru") }

    @Test @Config(qualifiers = "+ru-h1500dp-night")
    fun settings_dark_ru() = render { DemoSettings("ru", ThemeMode.DARK) }.also { shot("12_settings_dark_ru") }

    @Test @Config(qualifiers = "+en-h1500dp")
    fun settings_en() = render { DemoSettings("en", ThemeMode.LIGHT, periodic = true) }.also { shot("13_settings_en") }

    @Test @Config(qualifiers = "+de-h1500dp")
    fun settings_de() = render { DemoSettings("de", ThemeMode.SYSTEM) }.also { shot("14_settings_de") }

    // ---------- Пустое состояние ----------

    @Test @Config(qualifiers = "+ru")
    fun no_accounts_ru() = render {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { NoAccounts({}, {}) }
    }.also { shot("15_no_accounts_ru") }

    // ---------- helpers ----------

    private fun render(content: @Composable () -> Unit) {
        compose.setContent { MailTheme(content) }
        compose.waitForIdle()
    }

    /** Тянем строку дальше порога (~50% ширины) и не отпускаем — видно фон, иконку и состояние «порог пройден». */
    private fun swipe(tag: String, toRight: Boolean) {
        compose.onNodeWithTag(tag).performTouchInput {
            val start = if (toRight) centerLeft + Offset(20f, 0f) else centerRight - Offset(20f, 0f)
            down(start)
            val step = width * 0.05f * (if (toRight) 1 else -1)
            repeat(10) { moveBy(Offset(step, 0f)) }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }
}

// ================= Демо-данные =================

private val zone = ZoneId.systemDefault()
private fun at(daysAgo: Long, h: Int, m: Int): Long =
    LocalDate.now(zone).minusDays(daysAgo).atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

private val demoMessages = listOf(
    MessageListItem(1, 2, "INBOX", 101, "Анна Смирнова", "anna@example.com", "Встреча в четверг",
        "Привет! Давай перенесём созвон на 15:00, если тебе удобно. Повестку пришлю отдельно.", at(0, 9, 41),
        seen = false, flagged = true, hasAttachments = false),
    MessageListItem(2, 2, "INBOX", 102, "GitHub", "noreply@github.com", "[Wikaar/mail-client] Release v1.1",
        "Build debug APK succeeded on main. WikarM@il.v.1.1.apk is ready to download.", at(0, 8, 15),
        seen = false, flagged = false, hasAttachments = false),
    MessageListItem(3, 2, "INBOX", 103, "Deutsche Bahn", "buchung@bahn.de", "Ihre Fahrkarte nach Berlin",
        "Vielen Dank für Ihre Buchung. Ihr Online-Ticket finden Sie im Anhang.", at(1, 18, 2),
        seen = true, flagged = false, hasAttachments = true),
    MessageListItem(4, 2, "INBOX", 104, "Сергей Петров", "sergey@example.com", "Re: Отчёт за октябрь",
        "Посмотрел таблицу, всё ок. Пара комментариев ниже по тексту.", at(2, 14, 30),
        seen = true, flagged = true, hasAttachments = false),
    MessageListItem(5, 2, "INBOX", 105, "IONOS", "rechnung@ionos.de", "Ihre Rechnung November",
        "Ihre aktuelle Rechnung steht im Kundenkonto zum Download bereit.", at(5, 7, 0),
        seen = true, flagged = false, hasAttachments = true),
    MessageListItem(6, 2, "INBOX", 106, "Мама", "mama@example.com", "Фото с дачи",
        "Смотри, какие помидоры выросли! В выходные приезжайте.", at(8, 20, 12),
        seen = false, flagged = false, hasAttachments = true),
    MessageListItem(7, 2, "INBOX", 107, "Steam", "noreply@steampowered.com", "Распродажа выходных",
        "Скидки до 80% на игры из вашего списка желаемого.", at(12, 19, 0),
        seen = true, flagged = false, hasAttachments = false),
    MessageListItem(8, 2, "INBOX", 108, "Яндекс ID", "noreply@id.yandex.ru", "Вход в аккаунт",
        "Выполнен вход в ваш аккаунт с нового устройства.", at(20, 11, 5),
        seen = true, flagged = false, hasAttachments = false),
)

private val demoTabs = listOf(AccountTab(1, "IONOS", 3), AccountTab(2, "Яндекс", 12), AccountTab(3, "Работа", 0))

private fun demoAccount(id: Long, name: String, email: String, host: String) = AccountEntity(
    id = id, name = name, email = email, senderName = "Wikar",
    imapHost = "imap.$host", imapPort = 993, imapSecurity = Security.SSL,
    smtpHost = "smtp.$host", smtpPort = 465, smtpSecurity = Security.SSL,
    username = email, encryptedPassword = "",
)

private val demoAccounts = listOf(
    demoAccount(1, "IONOS", "wikar@example.de", "ionos.de"),
    demoAccount(2, "Яндекс", "wikar@yandex.ru", "ya.ru"),
    demoAccount(3, "Работа", "wikar@company.example", "company.example"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoMain(onlyFlagged: Boolean = false, showMenu: Boolean = false) {
    val list = if (onlyFlagged) demoMessages.filter { it.flagged } else demoMessages
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.folder_inbox))
                            Text(
                                if (onlyFlagged) stringResource(R.string.subtitle_only_important, "Яндекс") else "Яндекс",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = {}) { Icon(Icons.Default.Menu, null) } },
                    actions = {
                        Box {
                            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null) }
                            if (showMenu) {
                                AccountMenuContent(
                                    folderTitle = stringResource(R.string.folder_inbox),
                                    onlyFlagged = onlyFlagged,
                                    onDismiss = {}, onToggleOnlyFlagged = {}, onMarkAllRead = {},
                                    onDeleteAll = {}, onManageAccounts = {}, onSettings = {},
                                )
                            }
                        }
                    },
                )
                ScrollableTabRow(selectedTabIndex = 1, edgePadding = 8.dp) {
                    demoTabs.forEachIndexed { i, tab -> AccountTabItem(tab, i == 1, {}, {}) {} }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {},
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text(stringResource(R.string.compose)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(list, key = { it.id }) { m ->
                Box(Modifier.testTag("row_${m.id}")) {
                    SwipeableMessageRow(m = m, onClick = {}, onLongClick = {}, onToggleRead = {}, onToggleFlag = {}, onDelete = {})
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoMessage() {
    val m = MessageEntity(
        id = 1, accountId = 2, folder = "INBOX", uid = 101, messageIdHeader = null,
        fromName = "Анна Смирнова", fromAddress = "anna@example.com",
        toList = "Wikar <wikar@yandex.ru>", ccList = "Сергей Петров <sergey@example.com>",
        subject = "Встреча в четверг", preview = "", date = at(0, 9, 41),
        seen = true, flagged = true, hasAttachments = true,
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    StarToggle(flagged = true, onToggle = {}, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = {}) { Icon(Icons.Default.Reply, null) }
                    IconButton(onClick = {}) { Icon(Icons.Default.Forward, null) }
                    IconButton(onClick = {}) { Icon(Icons.Default.DriveFileMove, null) }
                    IconButton(onClick = {}) { Icon(Icons.Default.Delete, null) }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Header(m)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = {}, label = { Text("agenda.pdf · 248 KB") }, leadingIcon = { Icon(Icons.Default.Attachment, null) })
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            // В приложении тело письма показывает WebView; здесь — тот же текст обычным Text.
            Text(
                "Привет!\n\nДавай перенесём созвон на четверг, 15:00, если тебе удобно. " +
                    "Повестка во вложении — посмотри, пожалуйста, второй пункт.\n\nСпасибо!\nАнна",
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DemoSettings(language: String, theme: ThemeMode, periodic: Boolean = false) {
    SettingsContent(
        state = SettingsState(
            theme = theme,
            language = language,
            notificationsEnabled = true,
            mutedAccounts = setOf(3L),
            syncMode = if (periodic) SyncMode.PERIODIC else SyncMode.INSTANT,
        ),
        accounts = demoAccounts,
        currentLanguage = language,
        onBack = {},
        actions = NoopActions,
    )
}

private object NoopActions : SettingsActions {
    override fun setTheme(theme: ThemeMode) = Unit
    override fun setLanguage(tag: String) = Unit
    override fun setNotificationsEnabled(enabled: Boolean) = Unit
    override fun setAccountNotifications(accountId: Long, enabled: Boolean) = Unit
    override fun setSyncMode(mode: SyncMode) = Unit
}
