package com.billlocket.app.domain.model

import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import java.util.Locale

@JvmInline
value class CurrencyCode private constructor(val value: String) {
    companion object {
        fun of(raw: String): CurrencyCode {
            val normalized = raw.trim().uppercase(Locale.ROOT)
            require(normalized.matches(Regex("[A-Z]{3}"))) {
                "Currency code must contain exactly three ASCII letters"
            }
            return CurrencyCode(normalized)
        }
    }

    override fun toString(): String = value
}

data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
)

object MoneyCalculator {
    fun add(left: Money, right: Money): Outcome<Money> = calculate(left, right, Math::addExact)

    fun subtract(left: Money, right: Money): Outcome<Money> = calculate(left, right, Math::subtractExact)

    private fun calculate(
        left: Money,
        right: Money,
        operation: (Long, Long) -> Long,
    ): Outcome<Money> {
        if (left.currency != right.currency) {
            return Outcome.Failure(
                DomainFailure.CurrencyMismatch(left.currency, right.currency),
            )
        }

        return try {
            Outcome.Success(Money(operation(left.minorUnits, right.minorUnits), left.currency))
        } catch (_: ArithmeticException) {
            Outcome.Failure(DomainFailure.NumericOverflow)
        }
    }
}
