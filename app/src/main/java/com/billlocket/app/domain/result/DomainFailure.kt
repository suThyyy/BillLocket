package com.billlocket.app.domain.result

import com.billlocket.app.domain.model.CurrencyCode

enum class ValidationField {
    AMOUNT,
    TRANSACTION_TYPE,
    CATEGORY,
    ACCOUNT,
    FROM_ACCOUNT,
    TO_ACCOUNT,
    CURRENCY,
    IMAGES,
    BUDGET_MONTH,
    WARNING_PERCENT,
    DATE,
}

enum class ValidationRule {
    REQUIRED,
    MUST_BE_POSITIVE,
    TYPE_MISMATCH,
    MUST_BE_ACTIVE,
    MUST_BE_DISTINCT,
    DUPLICATE,
    MUST_BE_EXPENSE,
    MUST_BE_MONTH_START,
    OUT_OF_RANGE,
    FUTURE_NOT_ALLOWED,
}

sealed interface DomainFailure {
    data class Validation(
        val field: ValidationField,
        val rule: ValidationRule,
    ) : DomainFailure

    data class NotFound(val resource: String, val id: String) : DomainFailure

    data class Conflict(val reason: String) : DomainFailure

    data class InactiveReference(val resource: String, val id: String) : DomainFailure

    data class CurrencyMismatch(
        val expected: CurrencyCode,
        val actual: CurrencyCode,
    ) : DomainFailure

    data class StorageUnavailable(val operation: String) : DomainFailure

    data class DatabaseUnavailable(val operation: String) : DomainFailure

    data class PermissionDenied(
        val capability: String,
        val permanentlyDenied: Boolean,
    ) : DomainFailure

    data object NumericOverflow : DomainFailure

    data class Unexpected(val causeToken: String) : DomainFailure
}
