package com.billlocket.app.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.billlocket.app.widget.BillLocketWidget
import com.billlocket.app.widget.LatestTransactionWidget
import com.billlocket.app.widget.TodayRecapWidget
import com.billlocket.app.widget.WeeklyRecapWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BL-045 — Android implementation of [AppLocaleController].
 *
 * ## Architecture:
 * - Single source of truth is [AppCompatDelegate.getApplicationLocales] / [AppCompatDelegate.setApplicationLocales].
 * - On API 33+, Android framework manages per-app language settings via android:localeConfig.
 * - On API 26–32, AndroidX [AppLocalesMetadataHolderService] with autoStoreLocales=true persists
 *   the selected locale list in an internal XML file and restores it upon Activity start.
 * - MainActivity extends [androidx.appcompat.app.AppCompatActivity] with [Theme.AppCompat.DayNight.NoActionBar],
 *   which is the officially documented requirement for backward-compatible per-app locales.
 * - No duplicate SharedPreferences language key or manual Application.onCreate() restoration is used.
 */
@Singleton
class AppLocaleControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLocaleController {

    private val _current = MutableStateFlow(readCurrentLanguage())
    override val current: StateFlow<AppLanguage> = _current.asStateFlow()

    /**
     * Synchronize the StateFlow with the current [AppCompatDelegate.getApplicationLocales],
     * useful when the app returns to the foreground after the user changed languages in
     * Android 13+ system settings.
     */
    override fun syncFromSystem() {
        val effective = readCurrentLanguage()
        if (_current.value != effective) {
            _current.value = effective
        }
    }

    override fun apply(language: AppLanguage) {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val targetLocales = language.toLocaleListCompat()
        // Avoid redundant recreation if locales already match
        if (currentLocales.toLanguageTags() == targetLocales.toLanguageTags() && _current.value == language) {
            return
        }
        _current.value = language
        AppCompatDelegate.setApplicationLocales(targetLocales)

        // Trigger asynchronous widget updates so pinned Glance widgets reflect the new locale
        context?.let { ctx ->
            runCatching {
                CoroutineScope(Dispatchers.Default).launch {
                    runCatching {
                        LatestTransactionWidget().updateAll(ctx)
                        TodayRecapWidget().updateAll(ctx)
                        WeeklyRecapWidget().updateAll(ctx)
                        BillLocketWidget().updateAll(ctx)
                    }
                }
            }
        }
    }


    private fun readCurrentLanguage(): AppLanguage =
        mapLocaleListToAppLanguage(AppCompatDelegate.getApplicationLocales())

    companion object {
        /** Approved locale tag for Vietnamese. */
        const val TAG_VIETNAMESE = "vi-VN"

        /** Approved locale tag for English. */
        const val TAG_ENGLISH = "en-US"

        /**
         * Maps an [AppLanguage] selection to the corresponding [LocaleListCompat].
         * - SYSTEM: empty list, clearing app-specific locale to follow system.
         * - VIETNAMESE: "vi-VN".
         * - ENGLISH: "en-US".
         */
        fun AppLanguage.toLocaleListCompat(): LocaleListCompat = when (this) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.VIETNAMESE -> LocaleListCompat.forLanguageTags(TAG_VIETNAMESE)
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags(TAG_ENGLISH)
        }

        /**
         * Maps a [LocaleListCompat] to the corresponding [AppLanguage].
         * Empty list or unsupported language defaults to [AppLanguage.SYSTEM].
         */
        fun mapLocaleListToAppLanguage(locales: LocaleListCompat): AppLanguage {
            if (locales.isEmpty) return AppLanguage.SYSTEM
            val first = locales[0] ?: return AppLanguage.SYSTEM
            return when (first.language.lowercase()) {
                "vi" -> AppLanguage.VIETNAMESE
                "en" -> AppLanguage.ENGLISH
                else -> AppLanguage.SYSTEM
            }
        }
    }
}
