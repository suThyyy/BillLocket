package com.billlocket.app.domain.model

private fun requireIdentifier(value: String, type: String) {
    require(value.isNotBlank()) { "$type must not be blank" }
    require(value.length <= 128) { "$type is too long" }
}

@JvmInline
value class TransactionId(val value: String) {
    init { requireIdentifier(value, "TransactionId") }
}

@JvmInline
value class TransactionImageId(val value: String) {
    init { requireIdentifier(value, "TransactionImageId") }
}

@JvmInline
value class AccountId(val value: String) {
    init { requireIdentifier(value, "AccountId") }
}

@JvmInline
value class CategoryId(val value: String) {
    init { requireIdentifier(value, "CategoryId") }
}

@JvmInline
value class TransferId(val value: String) {
    init { requireIdentifier(value, "TransferId") }
}

@JvmInline
value class BudgetId(val value: String) {
    init { requireIdentifier(value, "BudgetId") }
}

@JvmInline
value class DraftId(val value: String) {
    init { requireIdentifier(value, "DraftId") }
}
