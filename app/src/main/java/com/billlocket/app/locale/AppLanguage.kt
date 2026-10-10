package com.billlocket.app.locale

/**
 * BL-045 — Application language preference.
 *
 * SYSTEM: clear the per-app locale list; Android uses the device locale.
 * VIETNAMESE: apply vi-VN as the per-app locale.
 * ENGLISH: apply en-US as the per-app locale.
 *
 * This enum is a presentation/platform model. It must not appear in domain financial models or Room entities.
 */
enum class AppLanguage {
    SYSTEM,
    VIETNAMESE,
    ENGLISH,
}

fun AppLanguage.toLanguageTag(): String? = when (this) {
    AppLanguage.SYSTEM -> null
    AppLanguage.VIETNAMESE -> "vi-VN"
    AppLanguage.ENGLISH -> "en-US"
}
