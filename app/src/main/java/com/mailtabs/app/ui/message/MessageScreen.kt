package com.mailtabs.app.ui.message

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.mailtabs.app.R
import com.mailtabs.app.data.db.AttachmentEntity
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.FolderRole
import com.mailtabs.app.data.db.MessageEntity
import com.mailtabs.app.ui.components.Avatar
import com.mailtabs.app.ui.components.StarToggle
import com.mailtabs.app.ui.main.MoveToFolderDialog
import com.mailtabs.app.ui.main.currentLocale
import com.mailtabs.app.ui.main.folderIcon
import com.mailtabs.app.ui.main.folderTitle
import com.mailtabs.app.ui.theme.CardShape
import com.mailtabs.app.ui.theme.PillShape
import com.mailtabs.app.ui.theme.Wikar
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    onBack: () -> Unit,
    onReply: (accountId: Long, messageId: Long, all: Boolean) -> Unit,
    onForward: (accountId: Long, messageId: Long) -> Unit,
    onEditDraft: (accountId: Long, messageId: Long) -> Unit,
    vm: MessageViewModel = hiltViewModel(),
) {
    val message by vm.message.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showMove by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                MessageEvent.Close -> onBack()
                is MessageEvent.Error -> snackbar.showSnackbar(e.text)
                is MessageEvent.OpenFile -> {
                    snackbar.showSnackbar(context.getString(R.string.saved_to_downloads, e.name))
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(e.uri, e.mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                    }
                }
            }
        }
    }

    val m = message
    val folder = m?.let { msg -> folders.firstOrNull { it.fullName == msg.folder } }
    val isDraft = folder?.role == FolderRole.DRAFTS
    // Из Корзины и Спама письмо можно вернуть во Входящие.
    val canRestore = folder?.role == FolderRole.TRASH || folder?.role == FolderRole.SPAM

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (m != null) {
                        if (isDraft) {
                            IconButton(onClick = { onEditDraft(m.accountId, m.id) }) {
                                Icon(Icons.Default.Edit, stringResource(R.string.edit_draft))
                            }
                        }
                        StarToggle(flagged = m.flagged, onToggle = vm::toggleFlagged)
                        IconButton(onClick = { showMove = true }) {
                            Icon(Icons.Default.DriveFileMove, stringResource(R.string.move))
                        }
                        IconButton(onClick = vm::delete) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (canRestore) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.restore_to_inbox)) },
                                        leadingIcon = { Icon(Icons.Default.MoveToInbox, null) },
                                        onClick = { menu = false; vm.move("INBOX") },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.mark_unread)) },
                                    leadingIcon = { Icon(Icons.Default.MarkEmailUnread, null) },
                                    onClick = { menu = false; vm.markUnreadAndClose() },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (m != null && !isDraft) {
                ReplyBar(
                    onReply = { onReply(m.accountId, m.id, false) },
                    onReplyAll = { onReply(m.accountId, m.id, true) },
                    onForward = { onForward(m.accountId, m.id) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = Wikar.dimens.maxContentWidth).fillMaxSize()) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (m != null) {
                    Header(m, folder)
                    if (attachments.isNotEmpty()) {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            attachments.forEach { a -> AttachmentCard(a, onClick = { vm.download(a) }) }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Body(m, Modifier.weight(1f))
                }
            }
        }
    }

    if (showMove) {
        MoveToFolderDialog(
            folders = folders.filter { it.selectable && it.fullName != m?.folder },
            onPick = { showMove = false; vm.move(it) },
            onDismiss = { showMove = false },
        )
    }
}

/** Нижняя панель ответа: главное действие — «Ответить». */
@Composable
private fun ReplyBar(onReply: () -> Unit, onReplyAll: () -> Unit, onForward: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onReply,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.reply), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(
                onClick = onReplyAll,
                shape = PillShape,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ReplyAll, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.reply_all), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(
                onClick = onForward,
                shape = PillShape,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Forward, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.forward), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Header(m: MessageEntity, folder: FolderEntity?) {
    var expanded by remember(m.id) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Text(
            m.subject.ifBlank { stringResource(R.string.no_subject) },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (folder != null) {
            Spacer(Modifier.size(8.dp))
            Surface(shape = PillShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(folderIcon(folder), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        folderTitle(folder, folder.fullName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Spacer(Modifier.size(16.dp))
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .animateContentSize()
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.fromName.ifBlank { m.fromAddress }, m.fromAddress)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.fromName.ifBlank { m.fromAddress },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            m.fromAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, currentLocale()).format(Date(m.date)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (m.toList.isNotBlank() || m.ccList.isNotBlank()) {
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = stringResource(if (expanded) R.string.hide_details else R.string.show_details),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (expanded) {
                    Spacer(Modifier.size(10.dp))
                    if (m.toList.isNotBlank()) {
                        Text(
                            stringResource(R.string.header_to, m.toList),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (m.ccList.isNotBlank()) {
                        Text(
                            stringResource(R.string.header_cc, m.ccList),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Карточка вложения: иконка по типу, имя, размер; тап — скачать и открыть. */
@Composable
private fun AttachmentCard(a: AttachmentEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(220.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CardShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(mimeIcon(a.mimeType, a.fileName), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.fileName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatSize(a.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.Download,
                contentDescription = stringResource(R.string.download),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Иконка по MIME-типу (или расширению). Используется и в окне написания письма. */
fun mimeIcon(mime: String, name: String): ImageVector {
    val m = mime.lowercase()
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        m.startsWith("image/") -> Icons.Outlined.Image
        m.startsWith("audio/") -> Icons.Outlined.Audiotrack
        m.startsWith("video/") -> Icons.Outlined.Movie
        m == "application/pdf" || ext == "pdf" -> Icons.Outlined.PictureAsPdf
        m.contains("zip") || m.contains("compressed") || ext in setOf("zip", "rar", "7z", "gz", "tar") -> Icons.Outlined.FolderZip
        m.startsWith("text/") || ext in setOf("doc", "docx", "odt", "txt", "rtf", "xls", "xlsx", "ods", "csv", "ppt", "pptx") ->
            Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Body(m: MessageEntity, modifier: Modifier) {
    val html = m.bodyHtml
    val text = m.bodyText
    if (html == null && text == null) {
        Box(modifier.fillMaxWidth().padding(24.dp)) {
            Text(stringResource(R.string.loading_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // Внешние картинки по умолчанию не грузим (трекинг-пиксели).
    var showImages by remember(m.id) { mutableStateOf(false) }
    val hasRemote = html != null && Regex("(?i)<img[^>]+src=[\"']?https?://").containsMatchIn(html)
    // Тёмная тема: простой текст красим цветами темы, HTML затемняет сам WebView (Android 13+).
    val dark = isSystemInDarkTheme()
    val darkening = remember { WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) }
    val surface = MaterialTheme.colorScheme.surface.toArgb()
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val textColors = if (dark && html == null) onSurface to surface else null
    val link = MaterialTheme.colorScheme.primary.toArgb()
    val document = remember(html, text, textColors, link) { buildDocument(html, text, textColors, link) }
    val background = if (html != null && dark && !darkening) android.graphics.Color.WHITE else surface

    Column(modifier) {
        if (hasRemote && !showImages) {
            Surface(
                onClick = { showImages = true },
                shape = CardShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.show_images_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.show_images),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    if (darkening) WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            openExternal(view, request.url)
                            return true
                        }
                    }
                }
            },
            update = { wv ->
                wv.setBackgroundColor(background)
                wv.settings.blockNetworkImage = !showImages
                wv.settings.blockNetworkLoads = !showImages
                val key = "${m.id}:${document.hashCode()}:$showImages"
                if (wv.tag != key) {
                    wv.tag = key
                    wv.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
                }
            },
        )
    }
}

private fun openExternal(view: WebView, uri: Uri) {
    try {
        view.context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

/** @param textColors цвета (текст, фон) ARGB для простого текста в тёмной теме. */
private fun buildDocument(html: String?, text: String?, textColors: Pair<Int, Int>?, link: Int): String {
    val colors = textColors?.let { (fg, bg) ->
        "html,body{color:${cssColor(fg)};background:${cssColor(bg)};}a{color:${cssColor(link)};}"
    } ?: ""
    val scheme = if (textColors != null) "<meta name=\"color-scheme\" content=\"dark\">" else ""
    val head = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">$scheme" +
        "<style>body{font-family:sans-serif;margin:12px;word-wrap:break-word;}img{max-width:100%;height:auto;}" +
        "pre{white-space:pre-wrap;font-family:sans-serif;}$colors</style>"
    if (html != null) return "<html><head>$head</head><body>$html</body></html>"
    val escaped = (text ?: "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return "<html><head>$head</head><body><pre>$escaped</pre></body></html>"
}

private fun cssColor(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

@Composable
private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "?"
    bytes < 1024 -> stringResource(R.string.size_bytes, bytes)
    bytes < 1024 * 1024 -> stringResource(R.string.size_kb, bytes / 1024)
    else -> stringResource(R.string.size_mb, String.format(currentLocale(), "%.1f", bytes / 1024.0 / 1024.0))
}
