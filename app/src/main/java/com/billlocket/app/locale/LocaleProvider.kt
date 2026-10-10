package com.billlocket.app.locale

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * BL-045 — Composable helper to obtain the current active application locale.
 *
 * Uses [LocalConfiguration] which Compose recomposes whenever the system configuration
 * (including locale) changes. This means composables calling [activeAppLocale] will
 * automatically recompose after a language change, even if the Activity is recreated.
 *
 * For non-Composable callers (widgets, ViewModels), use [Locale.getDefault()] directly,
 * or obtain the locale from Context.resources.configuration.locales[0].
 */
@Composable
fun activeAppLocale(): Locale {
    val configuration = LocalConfiguration.current
    // LocalConfiguration updates on Activity recreation after a language change,
    // so this correctly reflects the current per-app locale.
    @Suppress("DEPRECATION")
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        configuration.locales[0] ?: Locale.getDefault()
    } else {
        configuration.locale ?: Locale.getDefault()
    }
}
/**
 * Resolves the effective application locale from [androidx.appcompat.app.AppCompatDelegate.getApplicationLocales]
 * or falls back to the system locale from [context].
 */
fun resolveAppLocale(context: android.content.Context): Locale {
    val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    if (!locales.isEmpty) {
        val first = locales[0]
        if (first != null) return first
    }
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0] ?: Locale.getDefault()
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale ?: Locale.getDefault()
    }
}

/**
 * Creates a configuration context configured with the current effective application locale.
 * Useful for non-Activity contexts like Glance widgets, services, or background workers.
 */
fun android.content.Context.localizedContext(): android.content.Context {
    val locale = resolveAppLocale(this)
    val config = android.content.res.Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
