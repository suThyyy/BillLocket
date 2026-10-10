package com.billlocket.app.locale

import kotlinx.coroutines.flow.StateFlow

/**
 * BL-045 — Narrow interface for app-language control.
 *
 * Presentation code calls [apply] to change the language.
 * [current] reflects the stored preference so the Settings UI can derive the current selection.
 *
 * The implementation is the only place that calls AppCompatDelegate.setApplicationLocales.
 * UI composables never call AppCompatDelegate directly.
 *
 * A fake implementation can be substituted in unit tests without any Android framework dependency.
 */
interface AppLocaleController {
    /** Currently persisted language preference. */
    val current: StateFlow<AppLanguage>

    /**
     * Persists [language] and applies it to the running process via the AndroidX per-app locale API.
     *
     * For SYSTEM: clears the app-specific locale list.
     * For VIETNAMESE: sets vi-VN.
     * For ENGLISH: sets en-US.
     *
     * On API 33+, this also cooperates with the system per-app language settings.
     * On API 26–32, AppCompatDelegate stores the choice in AutoStoreLocales mode and restores it on
     * the next Activity start, without requiring a hand-written global Context override.
     */
    fun apply(language: AppLanguage)

    /**
     * Re-reads the active platform locales and updates [current].
     */
    fun syncFromSystem()
}
