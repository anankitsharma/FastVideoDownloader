package com.fastdownloader.app.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

// -------------------- DATA --------------------
private data class MediaCandidate(val url: String, val mime: String? = null)

/** JS bridge to receive media URLs from injected scripts. */
private class MediaBridge(private val onFound: (String, String?) -> Unit) {
    @JavascriptInterface fun onMedia(url: String?) {
        if (!url.isNullOrBlank()) onFound(url, null)
    }
    @JavascriptInterface fun onMediaWithMime(url: String?, mime: String?) {
        if (!url.isNullOrBlank()) onFound(url, mime)
    }
}

// -------------------- MAIN SCREEN --------------------
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(startUrl: String = "") {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(if (startUrl.isBlank()) "" else startUrl))
    }
    var pageTitle by rememberSaveable { mutableStateOf("Fast Browser") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Home overlay + recents
    var showHome by rememberSaveable { mutableStateOf(startUrl.isBlank()) }
    val recentSites = rememberSaveable { mutableStateListOf<String>() }

    // --- Sniffer state
    val candidates = remember { mutableStateListOf<MediaCandidate>() }
    var showPicker by remember { mutableStateOf(false) }
    fun addCandidate(url: String, mime: String? = null) {
        if (candidates.none { it.url == url }) candidates.add(0, MediaCandidate(url, mime))
    }

    // File chooser
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    fun navigateFromInput(raw: String) {
        val normalized = normalizeInput(raw)
        showHome = false
        webViewRef?.loadUrl(normalized)
        focusManager.clearFocus()
    }
    fun navigateTo(url: String) = navigateFromInput(url)

    BackHandler(enabled = canGoBack) { webViewRef?.goBack() }

    Scaffold(topBar = { TopAppBar(title = { Text(pageTitle, maxLines = 1) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = urlField,
                onValueChange = { urlField = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Search or type URL") },
                trailingIcon = { TextButton(onClick = { navigateFromInput(urlField.text) }) { Text("Go") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigateFromInput(urlField.text) })
            )

            if (loadingProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadingProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }

            Row(Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
                IconButton(onClick = { webViewRef?.stopLoading() }, enabled = loadingProgress in 1..99) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop")
                }
            }

            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // Settings
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.userAgentString = settings.userAgentString + " FastVideoDownloader/1.0"

                            // Cookies
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            // Add JS bridge for sniffing
                            addJavascriptInterface(MediaBridge { url, mime ->
                                post { addCandidate(url, mime) }
                            }, "AndroidBridge")

                            // Chrome client
                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    title?.let { pageTitle = it }
                                }
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadingProgress = newProgress
                                }
                                override fun onShowFileChooser(
                                    mWebView: WebView?,
                                    filePathCallback_: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    filePathCallback = filePathCallback_
                                    return try {
                                        val intent = fileChooserParams?.createIntent()
                                        if (intent != null) {
                                            filePicker.launch(intent); true
                                        } else {
                                            filePathCallback?.onReceiveValue(null)
                                            filePathCallback = null
                                            false
                                        }
                                    } catch (_: Exception) {
                                        filePathCallback?.onReceiveValue(null)
                                        filePathCallback = null
                                        false
                                    }
                                }
                            }

                            // Navigation + request interception
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?, request: WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    val scheme = uri.scheme ?: return false
                                    if (scheme == "http" || scheme == "https" || scheme == "data") return false
                                    return try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri)); true
                                    } catch (_: ActivityNotFoundException) { true }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    url?.let {
                                        if (!it.startsWith("data:text/html")) {
                                            urlField = TextFieldValue(it); showHome = false
                                        }
                                    }
                                    candidates.clear()
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    if (!url.isNullOrBlank()) {
                                        if (!url.startsWith("data:text/html")) {
                                            val host = runCatching { url.toUri().host ?: url }.getOrDefault(url)
                                            if (host.isNotBlank()) {
                                                recentSites.remove(host)
                                                recentSites.add(0, host)
                                                if (recentSites.size > 8) recentSites.removeAt(recentSites.lastIndex)
                                            }
                                        } else {
                                            urlField = TextFieldValue("")
                                        }
                                    }
                                    injectSniffer()
                                }

                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    val u = request?.url?.toString() ?: return null
                                    if (looksLikeMedia(u)) {
                                        view?.post { addCandidate(u, guessMimeFromUrl(u)) }
                                    }
                                    return null
                                }

                                override fun onReceivedError(
                                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                                ) { /* optional */ }
                            }

                            // Downloads
                            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                                scope.launch {
                                    val tree = com.fastdownloader.app.data.readTreeUri(context).firstOrNull()
                                    if (tree != null) {
                                        com.fastdownloader.app.download.DownloadService.start(
                                            context, url, fileName, tree
                                        )
                                    } else {
                                        enqueueDownload(context, url, userAgent, contentDisposition, mimeType)
                                    }
                                }
                            }

                            // Initial content
                            if (startUrl.isBlank()) {
                                loadDataWithBaseURL(null, HOME_HTML, "text/html", "utf-8", null)
                            } else {
                                loadUrl(normalizeInput(startUrl))
                            }

                            webViewRef = this
                        }
                    },
                    update = { wf ->
                        webViewRef = wf
                        canGoBack = wf.canGoBack()
                        canGoForward = wf.canGoForward()
                    }
                )

                if (candidates.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { showPicker = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) { Text("Download (${candidates.size})") }
                }

                if (showHome) {
                    HomeOverlay(
                        onQuickSiteClick = { navigateTo(it) },
                        recentSites = recentSites.toList(),
                        onHowToDownload = { navigateTo("https://example.com/how-to-download") }
                    )
                }
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            Text(
                "Detected media",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(candidates) { c ->
                    ListItem(
                        headlineContent = { Text(c.mime ?: guessMimeFromUrl(c.url) ?: "Unknown media") },
                        supportingContent = { Text(c.url, maxLines = 2) },
                        tonalElevation = 2.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Button(onClick = {
                            val fileName = URLUtil.guessFileName(c.url, null, c.mime)
                            val ctx = context
                            scope.launch {
                                val tree = com.fastdownloader.app.data.readTreeUri(ctx).firstOrNull()
                                if (tree != null) {
                                    com.fastdownloader.app.download.DownloadService.start(
                                        ctx, c.url, fileName, tree
                                    )
                                } else {
                                    enqueueDownload(ctx, c.url, null, null, c.mime)
                                }
                            }
                        }) { Text("Download") }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = {
                            candidates.remove(c)
                            if (candidates.isEmpty()) showPicker = false
                        }) { Text("Dismiss") }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// -------------------- HELPERS --------------------
/** Injects JS to discover <video> sources. */
private fun WebView.injectSniffer() {
    val js = """
        (function(){
          try {
            var videos = document.querySelectorAll('video');
            for (var i=0;i<videos.length;i++){
              var v = videos[i];
              if (v.currentSrc) AndroidBridge.onMedia(v.currentSrc);
              if (v.src) AndroidBridge.onMedia(v.src);
              var ss = v.querySelectorAll('source');
              for (var j=0;j<ss.length;j++){ if (ss[j].src) AndroidBridge.onMedia(ss[j].src); }
            }
          } catch(e){}
        })();
    """.trimIndent()
    if (Build.VERSION.SDK_INT >= 19) evaluateJavascript(js, null) else loadUrl("javascript:$js")
}

/** URL looks like media? */
private fun looksLikeMedia(u: String): Boolean {
    val lower = u.lowercase()
    return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".webm") ||
            lower.contains(".mpd") || lower.contains(".mkv") || lower.contains(".mov") ||
            lower.contains(".ts?") || lower.endsWith(".ts") || lower.contains("/hls/")
}

private fun guessMimeFromUrl(u: String): String? = when {
    u.endsWith(".m3u8", true) -> "application/vnd.apple.mpegurl"
    u.endsWith(".mpd", true) -> "application/dash+xml"
    u.endsWith(".mp4", true) -> "video/mp4"
    u.endsWith(".webm", true) -> "video/webm"
    u.endsWith(".mkv", true) -> "video/x-matroska"
    u.endsWith(".mov", true) -> "video/quicktime"
    u.endsWith(".ts", true) -> "video/mp2t"
    else -> null
}

/** Normalize input to URL or Google search */
private fun normalizeInput(inputRaw: String): String {
    val raw = inputRaw.trim()
    if (raw.isEmpty()) return "about:blank"
    val lower = raw.lowercase()
    val looksLikeUrl =
        lower.startsWith("http://") || lower.startsWith("https://") ||
                Regex("""^[\w\-]+(\.[\w\-]+)+(/.*)?$""").matches(raw)
    return if (looksLikeUrl) {
        if (lower.startsWith("http")) raw else "https://$raw"
    } else {
        val q = Uri.encode(raw)
        "https://www.google.com/search?q=$q"
    }
}

/** Build a DownloadManager.Request with headers + cookies. */
private fun buildDownloadRequest(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
): DownloadManager.Request {
    val cookies = CookieManager.getInstance().getCookie(url)
    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

    return DownloadManager.Request(Uri.parse(url)).apply {
        setTitle(fileName)
        setDescription("Downloading…")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setAllowedOverMetered(true)
        setAllowedOverRoaming(true)
        userAgent?.let { addRequestHeader("User-Agent", it) }
        if (!cookies.isNullOrBlank()) addRequestHeader("Cookie", cookies)
        setMimeType(mimeType ?: "application/octet-stream")
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    }
}

/** Updated enqueueDownload using buildDownloadRequest. */
private fun enqueueDownload(
    context: Context, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?
) {
    runCatching {
        val req = buildDownloadRequest(context, url, userAgent, contentDisposition, mimeType)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(req)
    }
}

// -------------------- HOME OVERLAY --------------------
@Composable
private fun HomeOverlay(
    onQuickSiteClick: (String) -> Unit,
    recentSites: List<String>,
    onHowToDownload: () -> Unit
) {
    val quickSites = listOf(
        QuickSite("YouTube", "https://www.youtube.com"),
        QuickSite("Facebook", "https://m.facebook.com"),
        QuickSite("Instagram", "https://www.instagram.com"),
        QuickSite("Reddit", "https://www.reddit.com"),
        QuickSite("Wikipedia", "https://www.wikipedia.org"),
        QuickSite("Twitter / X", "https://twitter.com"),
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(quickSites) { q -> QuickSiteChip(label = q.label) { onQuickSiteClick(q.url) } }
        }
        Spacer(Modifier.height(16.dp))
        if (recentSites.isNotEmpty()) {
            Text("Recent", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recentSites.take(8)) { host ->
                    QuickSiteChip(label = host) { onQuickSiteClick("https://$host") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onHowToDownload) { Text("How to download") }
    }
}

private data class QuickSite(val label: String, val url: String)

@Composable
private fun QuickSiteChip(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label, maxLines = 1) }
}

// A tiny welcome page when startUrl is blank
private const val HOME_HTML = """
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1"/>
    <title>Fast Video Downloader</title>
    <style>
      body { font-family: -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Ubuntu,'Helvetica Neue',Arial; margin: 24px; }
      h1 { font-size: 20px; margin-bottom: 12px; }
      p { color: #444; line-height: 1.5; }
      .hint { color: #777; font-size: 14px; margin-top: 16px; }
    </style>
  </head>
  <body>
    <h1>Welcome 👋</h1>
    <p>Type a URL above, or search anything. When a video is detected, tap the Download button.</p>
    <p class="hint">Tip: choose a download folder in Settings for best results.</p>
  </body>
</html>
"""
