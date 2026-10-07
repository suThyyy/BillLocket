package com.billlocket.app.domain.validation

import com.billlocket.app.domain.model.CreateTransactionCommand
import com.billlocket.app.domain.model.CreateTransferCommand
import com.billlocket.app.domain.model.CurrencyCode
import com.billlocket.app.domain.model.SaveBudgetCommand
import com.billlocket.app.domain.model.TransactionType
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.result.ValidationField
import com.billlocket.app.domain.result.ValidationRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ValidateCreateTransaction {
    operator fun invoke(
        command: CreateTransactionCommand,
        configuredCurrency: CurrencyCode,
        nowEpochMillis: Long? = null,
    ): Outcome<Unit> {
        if (command.amount.minorUnits <= 0) return invalid(ValidationField.AMOUNT, ValidationRule.MUST_BE_POSITIVE)
        if (command.amount.currency != configuredCurrency) {
            return Outcome.Failure(DomainFailure.CurrencyMismatch(configuredCurrency, command.amount.currency))
        }
        if (!command.account.isActive) {
            return Outcome.Failure(DomainFailure.InactiveReference("account", command.account.id.value))
        }
        if (command.account.currency != configuredCurrency) {
            return Outcome.Failure(DomainFailure.CurrencyMismatch(configuredCurrency, command.account.currency))
        }
        if (!command.category.isActive) {
            return Outcome.Failure(DomainFailure.InactiveReference("category", command.category.id.value))
        }
        if (command.category.type != command.type) {
            return invalid(ValidationField.CATEGORY, ValidationRule.TYPE_MISMATCH)
        }
        if (command.images.map { it.id }.toSet().size != command.images.size ||
            command.images.map { it.mediaUri }.toSet().size != command.images.size
        ) {
            return invalid(ValidationField.IMAGES, ValidationRule.DUPLICATE)
        }
        if (nowEpochMillis != null) {
            val zone = ZoneId.of(command.occurred.zoneId)
            val todayEpochDay = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate().toEpochDay()
            if (command.occurred.localDateEpochDay > todayEpochDay) {
                return invalid(ValidationField.DATE, ValidationRule.FUTURE_NOT_ALLOWED)
            }
        }
        return Outcome.Success(Unit)
    }
}

class ValidateCreateTransfer {
    operator fun invoke(
        command: CreateTransferCommand,
        configuredCurrency: CurrencyCode,
    ): Outcome<Unit> {
        if (command.amount.minorUnits <= 0) return invalid(ValidationField.AMOUNT, ValidationRule.MUST_BE_POSITIVE)
        if (command.from.id == command.to.id) {
            return invalid(ValidationField.TO_ACCOUNT, ValidationRule.MUST_BE_DISTINCT)
        }
        if (!command.from.isActive) {
            return Outcome.Failure(DomainFailure.InactiveReference("account", command.from.id.value))
        }
        if (!command.to.isActive) {
            return Outcome.Failure(DomainFailure.InactiveReference("account", command.to.id.value))
        }
        val currencies = setOf(command.amount.currency, command.from.currency, command.to.currency)
        if (currencies.size != 1 || command.amount.currency != configuredCurrency) {
            val actual = currencies.firstOrNull { it != configuredCurrency } ?: command.amount.currency
            return Outcome.Failure(DomainFailure.CurrencyMismatch(configuredCurrency, actual))
        }
        return Outcome.Success(Unit)
    }
}

class ValidateBudget {
    operator fun invoke(
        command: SaveBudgetCommand,
        configuredCurrency: CurrencyCode,
    ): Outcome<Unit> {
        if (command.limit.minorUnits <= 0) return invalid(ValidationField.AMOUNT, ValidationRule.MUST_BE_POSITIVE)
        if (command.limit.currency != configuredCurrency) {
            return Outcome.Failure(DomainFailure.CurrencyMismatch(configuredCurrency, command.limit.currency))
        }
        if (command.warningPercent !in 1..99) {
            return invalid(ValidationField.WARNING_PERCENT, ValidationRule.OUT_OF_RANGE)
        }
        if (LocalDate.ofEpochDay(command.monthStartEpochDay).dayOfMonth != 1) {
            return invalid(ValidationField.BUDGET_MONTH, ValidationRule.MUST_BE_MONTH_START)
        }
        command.category?.let { category ->
            if (!category.isActive) {
                return Outcome.Failure(DomainFailure.InactiveReference("category", category.id.value))
            }
            if (category.type != TransactionType.EXPENSE) {
                return invalid(ValidationField.CATEGORY, ValidationRule.MUST_BE_EXPENSE)
            }
        }
        return Outcome.Success(Unit)
    }
}

private fun invalid(field: ValidationField, rule: ValidationRule): Outcome.Failure =
    Outcome.Failure(DomainFailure.Validation(field, rule))
