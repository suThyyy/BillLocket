package com.billlocket.app.feature.transaction.voice

import com.billlocket.app.locale.AppLanguage
import java.util.Locale

object VoiceLocaleMapper {
    /**
     * Maps the selected [AppLanguage] and device system locale to a speech recognition BCP-47 locale tag:
     * - [AppLanguage.VIETNAMESE] -> "vi-VN"
     * - [AppLanguage.ENGLISH] -> "en-US"
     * - [AppLanguage.SYSTEM] -> resolves from [systemLocale]: if language starts with "en", returns "en-US", otherwise "vi-VN".
     */
    fun resolveLocaleTag(appLanguage: AppLanguage, systemLocale: Locale = Locale.getDefault()): String {
        return when (appLanguage) {
            AppLanguage.VIETNAMESE -> "vi-VN"
            AppLanguage.ENGLISH -> "en-US"
            AppLanguage.SYSTEM -> resolveLocaleTag(systemLocale)
        }
    }

    /**
     * Derives speech recognition locale tag directly from the active effective [Locale]:
     * - If [effectiveLocale.language] is "en" (case-insensitive), returns "en-US".
     * - Otherwise defaults to "vi-VN".
     */
    fun resolveLocaleTag(effectiveLocale: Locale): String {
        return if (effectiveLocale.language.equals("en", ignoreCase = true)) {
            "en-US"
        } else {
            "vi-VN"
        }
    }
}
