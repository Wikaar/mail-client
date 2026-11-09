package com.mailtabs.app.ui.message

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ReplyAll
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.data.db.MessageEntity
import com.mailtabs.app.ui.main.Avatar
import com.mailtabs.app.ui.main.MoveToFolderDialog
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    onBack: () -> Unit,
    onReply: (accountId: Long, messageId: Long, all: Boolean) -> Unit,
    onForward: (accountId: Long, messageId: Long) -> Unit,
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
                    snackbar.showSnackbar("Сохранено в «Загрузки»: ${e.name}")
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    val m = message
                    if (m != null) {
                        IconButton(onClick = { onReply(m.accountId, m.id, false) }) {
                            Icon(Icons.Default.Reply, "Ответить")
                        }
                        IconButton(onClick = { onForward(m.accountId, m.id) }) {
                            Icon(Icons.Default.Forward, "Переслать")
                        }
                        IconButton(onClick = { showMove = true }) {
                            Icon(Icons.Default.DriveFileMove, "Переместить")
                        }
                        IconButton(onClick = vm::delete) {
                            Icon(Icons.Default.Delete, "Удалить")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Ещё") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Ответить всем") },
                                    leadingIcon = { Icon(Icons.Default.ReplyAll, null) },
                                    onClick = { menu = false; onReply(m.accountId, m.id, true) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Пометить непрочитанным") },
                                    leadingIcon = { Icon(Icons.Default.MarkEmailUnread, null) },
                                    onClick = { menu = false; vm.markUnreadAndClose() },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = message
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (m != null) {
                Header(m)
                if (attachments.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        attachments.forEach { a ->
                            AssistChip(
                                onClick = { vm.download(a) },
                                label = { Text("${a.fileName} · ${formatSize(a.size)}", maxLines = 1) },
                                leadingIcon = { Icon(Icons.Default.Attachment, null) },
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Body(m, Modifier.weight(1f))
            }
        }
    }

    if (showMove) {
        val m = message
        MoveToFolderDialog(
            folders = folders.filter { it.selectable && it.fullName != m?.folder },
            onPick = { showMove = false; vm.move(it) },
            onDismiss = { showMove = false },
        )
    }
}

@Composable
private fun Header(m: MessageEntity) {
    Column(Modifier.padding(16.dp)) {
        Text(m.subject.ifBlank { "(без темы)" }, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.padding(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.fromName.ifBlank { m.fromAddress }, m.fromAddress)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.fromName.ifBlank { m.fromAddress }, fontWeight = FontWeight.SemiBold)
                Text(
                    m.fromAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.date)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (m.toList.isNotBlank()) {
            Text(
                "Кому: ${m.toList}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (m.ccList.isNotBlank()) {
            Text(
                "Копия: ${m.ccList}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Body(m: MessageEntity, modifier: Modifier) {
    val html = m.bodyHtml
    val text = m.bodyText
    if (html == null && text == null) {
        Box(modifier.fillMaxWidth().padding(24.dp)) {
            Text("Загрузка письма…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // Внешние картинки по умолчанию не грузим (трекинг-пиксели).
    var showImages by remember(m.id) { mutableStateOf(false) }
    val hasRemote = html != null && Regex("(?i)<img[^>]+src=[\"']?https?://").containsMatchIn(html)
    val document = remember(html, text) { buildDocument(html, text) }

    Column(modifier) {
        if (hasRemote && !showImages) {
            TextButton(onClick = { showImages = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Показать изображения")
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
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            openExternal(view, request.url)
                            return true
                        }
                    }
                }
            },
            update = { wv ->
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

private fun buildDocument(html: String?, text: String?): String {
    val head = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>body{font-family:sans-serif;margin:12px;word-wrap:break-word;}img{max-width:100%;height:auto;}" +
        "pre{white-space:pre-wrap;font-family:sans-serif;}</style>"
    if (html != null) return "<html><head>$head</head><body>$html</body></html>"
    val escaped = (text ?: "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return "<html><head>$head</head><body><pre>$escaped</pre></body></html>"
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "?"
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format("%.1f МБ", bytes / 1024.0 / 1024.0)
}
