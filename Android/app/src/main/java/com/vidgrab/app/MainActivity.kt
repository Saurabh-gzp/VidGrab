package com.vidgrab.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.vidgrab.app.ui.AppRoot
import com.vidgrab.app.ui.theme.VidGrabTheme
import com.vidgrab.app.util.UrlUtils

class MainActivity : ComponentActivity() {

    private var incomingUrl by mutableStateOf<String?>(null)

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()
        handleIntent(intent)
        setContent {
            val themeFlow = remember { com.vidgrab.app.core.Prefs.themeFlow(applicationContext) }
            val theme by themeFlow.collectAsState(initial = com.vidgrab.app.core.Prefs.ThemeMode.System)
            val systemDark = isSystemInDarkTheme()
            SideEffect {
                val dark = when (theme) {
                    com.vidgrab.app.core.Prefs.ThemeMode.Dark -> true
                    com.vidgrab.app.core.Prefs.ThemeMode.Light -> false
                    else -> systemDark
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            VidGrabTheme(mode = theme) {
                AppRoot(
                    themeFlow = themeFlow,
                    incomingUrl = incomingUrl,
                    onUrlConsumed = { incomingUrl = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            incomingUrl = UrlUtils.extractUrl(text) ?: text.trim()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
