/**
 * Copyright (c) 2025 lcaohoanq. All rights reserved.
 * This software is the confidential and proprietary information of lcaohoanq.
 * You shall not disclose such confidential information and shall use it only in
 * accordance with the terms of the license agreement you entered into with lcaohoanq.
 */
package com.billlocket.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.billlocket.app.extensions.edgeToEdgeWithStyle
import com.billlocket.app.ui.theme.AppTheme
import com.billlocket.app.domain.repository.SettingsRepository
import com.billlocket.app.domain.model.UserSettings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import com.billlocket.app.widget.WIDGET_ACTION_EXTRA
import com.billlocket.app.widget.WIDGET_ID_EXTRA

data class WidgetLaunch(val action: String, val stableId: String?, val token: Long)

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appLocaleController: com.billlocket.app.locale.AppLocaleController
    private val widgetLaunch = mutableStateOf<WidgetLaunch?>(null)

    override fun onStart() {
        super.onStart()
        appLocaleController.syncFromSystem()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        edgeToEdgeWithStyle()

        consumeWidgetIntent(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(UserSettings())
            BillLocketApp(widgetLaunch.value, settings.theme)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWidgetIntent(intent)
    }

    private fun consumeWidgetIntent(intent: Intent?) {
        val action = intent?.getStringExtra(WIDGET_ACTION_EXTRA) ?: return
        widgetLaunch.value = WidgetLaunch(action, intent.getStringExtra(WIDGET_ID_EXTRA), System.nanoTime())
        intent.removeExtra(WIDGET_ACTION_EXTRA)
        intent.removeExtra(WIDGET_ID_EXTRA)
    }

}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BillLocketApp(widgetLaunch: WidgetLaunch? = null, themeMode: com.billlocket.app.domain.model.ThemeMode = com.billlocket.app.domain.model.ThemeMode.DARK) {
    AppTheme(mode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Navigation(widgetLaunch = widgetLaunch)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, showSystemUi = true)
@Composable
@RestrictTo(RestrictTo.Scope.TESTS)
fun BillLocketAppPreview() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Navigation()
        }
    }
}
