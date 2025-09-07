package com.fastdownloader.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.getSystemService
import com.fastdownloader.app.ui.BrowserScreen
import com.fastdownloader.app.ui.theme.FastVideoDownloaderTheme
import com.fastdownloader.app.ui.AppNav

class MainActivity : ComponentActivity() {

    companion object {
        const val DOWNLOADS_CHANNEL_ID = "downloads"
        const val DOWNLOADS_CHANNEL_NAME = "Downloads"
    }

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* you can no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create channel upfront (legacy style apps often do this in Activity.onCreate)
        createDownloadsChannel()

        // Ask for POST_NOTIFICATIONS on Android 13+ (optional but recommended for download toasts)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            maybeRequestPostNotifications()
        }

        setContent {
            FastVideoDownloaderTheme {
                // Keep it legacy-simple: just show the BrowserScreen with empty startUrl (custom home)
            //BrowserScreen(startUrl = "")
                AppNav()
            }
        }
    }

    private fun createDownloadsChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService<NotificationManager>() ?: return
            val existing = mgr.getNotificationChannel(DOWNLOADS_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    DOWNLOADS_CHANNEL_ID,
                    DOWNLOADS_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW // progress notifications shouldn’t be noisy
                ).apply {
                    description = "Download progress and completion"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun maybeRequestPostNotifications() {
        // Only request if not already granted
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val granted = checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPostNotifications.launch(permission)
        }
    }
}
