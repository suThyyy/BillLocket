package com.billlocket.app.data.repository

import android.content.SharedPreferences
import com.billlocket.app.data.local.dao.ProjectionDao
import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.repository.SettingsRepository
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val preferences: SharedPreferences,
    private val projectionDao: ProjectionDao,
) : SettingsRepository {
    override val settings: Flow<UserSettings> =
        callbackFlow {
            fun emitCurrent() { trySend(read()) }
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> emitCurrent() }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            emitCurrent()
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()

    override suspend fun updateCurrency(code: String): Outcome<Unit> {
        val currency = try { CurrencyCode.of(code) } catch (_: IllegalArgumentException) {
            return Outcome.Failure(DomainFailure.Conflict("invalid_currency"))
        }
        val current = read().currency
        if (currency != current && projectionDao.financialRecordCount() > 0) {
            return Outcome.Failure(DomainFailure.Conflict("currency_has_financial_records"))
        }
        return edit("update_currency") { putString(KEY_CURRENCY, currency.value) }
    }

    override suspend fun updateTheme(mode: ThemeMode): Outcome<Unit> = edit("update_theme") { putString(KEY_THEME, mode.name) }

    override suspend fun updateAmountVisibility(value: AmountVisibility): Outcome<Unit> =
        edit("update_amount_visibility") { putString(KEY_AMOUNT_VISIBILITY, value.name) }

    override suspend fun updateWidgetSettings(value: WidgetSettings): Outcome<Unit> =
        edit("update_widget_settings") {
            putBoolean(KEY_WIDGET_AMOUNT, value.showAmount)
            putBoolean(KEY_WIDGET_CATEGORY, value.showCategory)
            putString(KEY_WIDGET_ACTION, value.openAction.name)
        }

    private fun read() =
        UserSettings(
            currency = enumOrDefault(KEY_CURRENCY, "VND", CurrencyCode::of),
            theme = enumOrDefault(KEY_THEME, ThemeMode.DARK.name, ThemeMode::valueOf),
            amountVisibility = enumOrDefault(KEY_AMOUNT_VISIBILITY, AmountVisibility.SHOWN.name, AmountVisibility::valueOf),
            widget =
                WidgetSettings(
                    showAmount = preferences.getBoolean(KEY_WIDGET_AMOUNT, false),
                    showCategory = preferences.getBoolean(KEY_WIDGET_CATEGORY, true),
                    openAction = enumOrDefault(KEY_WIDGET_ACTION, WidgetOpenAction.TRANSACTION_DETAIL.name, WidgetOpenAction::valueOf),
                ),
        )

    private fun <T> enumOrDefault(key: String, default: String, transform: (String) -> T): T =
        try { transform(preferences.getString(key, default) ?: default) } catch (_: IllegalArgumentException) { transform(default) }

    private fun edit(operation: String, block: SharedPreferences.Editor.() -> Unit): Outcome<Unit> {
        val success = preferences.edit().apply(block).commit()
        return if (success) Outcome.Success(Unit) else Outcome.Failure(DomainFailure.StorageUnavailable(operation))
    }

    private companion object {
        const val KEY_CURRENCY = "settings.currency"
        const val KEY_THEME = "settings.theme"
        const val KEY_AMOUNT_VISIBILITY = "settings.amount_visibility"
        const val KEY_WIDGET_AMOUNT = "settings.widget.show_amount"
        const val KEY_WIDGET_CATEGORY = "settings.widget.show_category"
        const val KEY_WIDGET_ACTION = "settings.widget.open_action"
    }
}
