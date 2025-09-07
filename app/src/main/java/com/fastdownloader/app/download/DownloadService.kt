package com.fastdownloader.app.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.fastdownloader.app.MainActivity
import com.fastdownloader.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class DownloadService : Service() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_FILENAME = "filename"
        const val EXTRA_TREE_URI = "tree_uri"
        const val CHANNEL = MainActivity.DOWNLOADS_CHANNEL_ID

        fun start(ctx: Context, url: String, fileName: String, treeUri: String) {
            val i = Intent(ctx, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILENAME, fileName)
                putExtra(EXTRA_TREE_URI, treeUri)
            }
            // Use ContextCompat to avoid lint errors on older API levels
            ContextCompat.startForegroundService(ctx, i)
        }
    }

    private val client = OkHttpClient()
    private var job: Job? = null
    private lateinit var scope: CoroutineScope
    private val notifId: Int by lazy { (System.currentTimeMillis() % Int.MAX_VALUE).toInt() }

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.IO)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val fileName = intent?.getStringExtra(EXTRA_FILENAME) ?: "file.bin"
        val tree = intent?.getStringExtra(EXTRA_TREE_URI)

        if (url.isNullOrBlank() || tree.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Show an initial foreground notification
        startForeground(notifId, buildNotif(text = "Starting…", progress = 0, indeterminate = true))

        job?.cancel()
        job = scope.launch {
            runCatching {
                downloadToTree(url, fileName, Uri.parse(tree))
            }.onFailure {
                // Update final notification to failed
                notify(buildNotif(text = "Failed", progress = 0, indeterminate = false, done = true))
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun notify(n: Notification) {
        startForeground(notifId, n) // keep it foreground and update
    }

    private fun buildNotif(
        text: String,
        progress: Int,
        indeterminate: Boolean = false,
        done: Boolean = false
    ): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            // Use a built-in system icon so you don't need to add a drawable
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(text)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .setOngoing(!done)
            .setProgress(100, progress, indeterminate)
            .build()
    }

    @Throws(IOException::class)
    private fun downloadToTree(url: String, fileName: String, treeUri: Uri) {
        val tree = DocumentFile.fromTreeUri(this, treeUri)
            ?: throw IOException("Invalid folder")
        val outFile = tree.createFile("*/*", fileName)
            ?: throw IOException("Cannot create file")

        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty body")

            val total = body.contentLength().coerceAtLeast(0L)
            contentResolver.openOutputStream(outFile.uri)?.use { os ->
                body.byteStream().use { ins ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    var downloaded = 0L
                    var lastPct = -1

                    notify(buildNotif(text = "Downloading…", progress = 0, indeterminate = total <= 0))
                    while (ins.read(buf).also { read = it } != -1) {
                        os.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            if (pct != lastPct) {
                                notify(buildNotif(text = "$pct%", progress = pct, indeterminate = false))
                                lastPct = pct
                            }
                        }
                    }
                    os.flush()
                }
            }
        }

        // Completed
        notify(buildNotif(text = "Completed", progress = 100, indeterminate = false, done = true))
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
