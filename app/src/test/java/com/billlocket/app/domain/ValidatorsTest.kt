package com.billlocket.app.domain

import com.billlocket.app.domain.model.AccountId
import com.billlocket.app.domain.model.AccountRef
import com.billlocket.app.domain.model.AccountType
import com.billlocket.app.domain.model.CategoryId
import com.billlocket.app.domain.model.CategoryRef
import com.billlocket.app.domain.model.CreateTransactionCommand
import com.billlocket.app.domain.model.CreateTransferCommand
import com.billlocket.app.domain.model.CurrencyCode
import com.billlocket.app.domain.model.ImageInput
import com.billlocket.app.domain.model.InstantAtZone
import com.billlocket.app.domain.model.Money
import com.billlocket.app.domain.model.SaveBudgetCommand
import com.billlocket.app.domain.model.TransactionImageId
import com.billlocket.app.domain.model.TransactionType
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.result.ValidationField
import com.billlocket.app.domain.result.ValidationRule
import com.billlocket.app.domain.validation.ValidateBudget
import com.billlocket.app.domain.validation.ValidateCreateTransaction
import com.billlocket.app.domain.validation.ValidateCreateTransfer
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    private val vnd = CurrencyCode.of("VND")
    private val occurred = InstantAtZone(1_700_000_000_000, 19_675, "Asia/Ho_Chi_Minh")
    private val activeAccount = AccountRef(AccountId("cash"), AccountType.CASH, true, vnd)
    private val otherAccount = AccountRef(AccountId("bank"), AccountType.BANK, true, vnd)
    private val expenseCategory = CategoryRef(CategoryId("food"), TransactionType.EXPENSE, true)

    @Test
    fun transactionValidator_acceptsValidImageLessExpense() {
        val result = ValidateCreateTransaction()(validTransaction(), vnd)

        assertEquals(Outcome.Success(Unit), result)
    }

    @Test
    fun transactionValidator_rejectsNonPositiveAmount() {
        val result = ValidateCreateTransaction()(validTransaction().copy(amount = Money(0, vnd)), vnd)

        assertEquals(
            Outcome.Failure(DomainFailure.Validation(ValidationField.AMOUNT, ValidationRule.MUST_BE_POSITIVE)),
            result,
        )
    }

    @Test
    fun transactionValidator_rejectsCategoryTypeMismatch() {
        val result = ValidateCreateTransaction()(
            validTransaction().copy(type = TransactionType.INCOME),
            vnd,
        )

        assertEquals(
            Outcome.Failure(DomainFailure.Validation(ValidationField.CATEGORY, ValidationRule.TYPE_MISMATCH)),
            result,
        )
    }

    @Test
    fun transactionValidator_rejectsArchivedAccount() {
        val result = ValidateCreateTransaction()(
            validTransaction().copy(account = activeAccount.copy(isActive = false)),
            vnd,
        )

        assertEquals(
            Outcome.Failure(DomainFailure.InactiveReference("account", "cash")),
            result,
        )
    }

    @Test
    fun transactionValidator_rejectsDuplicateImages() {
        val image = ImageInput(
            TransactionImageId("image-1"),
            "content://com.billlocket.app.media/images/image-1",
        )
        val result = ValidateCreateTransaction()(validTransaction().copy(images = listOf(image, image)), vnd)

        assertEquals(
            Outcome.Failure(DomainFailure.Validation(ValidationField.IMAGES, ValidationRule.DUPLICATE)),
            result,
        )
    }

    @Test
    fun transferValidator_acceptsDistinctActiveAccounts() {
        val result = ValidateCreateTransfer()(
            CreateTransferCommand(activeAccount, otherAccount, Money(50_000, vnd), null, occurred),
            vnd,
        )

        assertEquals(Outcome.Success(Unit), result)
    }

    @Test
    fun transferValidator_rejectsSameAccount() {
        val result = ValidateCreateTransfer()(
            CreateTransferCommand(activeAccount, activeAccount, Money(50_000, vnd), null, occurred),
            vnd,
        )

        assertEquals(
            Outcome.Failure(DomainFailure.Validation(ValidationField.TO_ACCOUNT, ValidationRule.MUST_BE_DISTINCT)),
            result,
        )
    }

    @Test
    fun budgetValidator_rejectsIncomeCategory() {
        val income = CategoryRef(CategoryId("salary"), TransactionType.INCOME, true)
        val result = ValidateBudget()(
            SaveBudgetCommand(LocalDate.of(2026, 9, 1).toEpochDay(), income, Money(1_000_000, vnd)),
            vnd,
        )

        assertEquals(
            Outcome.Failure(DomainFailure.Validation(ValidationField.CATEGORY, ValidationRule.MUST_BE_EXPENSE)),
            result,
        )
    }

    @Test
    fun budgetValidator_rejectsNonFirstDay() {
        val result = ValidateBudget()(
            SaveBudgetCommand(LocalDate.of(2026, 9, 2).toEpochDay(), null, Money(1_000_000, vnd)),
            vnd,
        )

        assertTrue(result is Outcome.Failure)
        assertEquals(
            DomainFailure.Validation(ValidationField.BUDGET_MONTH, ValidationRule.MUST_BE_MONTH_START),
            (result as Outcome.Failure).reason,
        )
    }

    @Test
    fun transactionValidator_acceptsTodayAndPastDate() {
        val validator = ValidateCreateTransaction()
        val zone = java.time.ZoneId.of(occurred.zoneId)
        val todayDate = LocalDate.ofEpochDay(occurred.localDateEpochDay)
        val nowMillis = todayDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val todayResult = validator(validTransaction(), vnd, nowMillis)
        assertEquals(Outcome.Success(Unit), todayResult)

        val pastOccurred = occurred.copy(
            localDateEpochDay = occurred.localDateEpochDay - 1,
            epochMillis = occurred.epochMillis - 86_400_000L,
        )
        val pastResult = validator(validTransaction().copy(occurred = pastOccurred), vnd, nowMillis)
        assertEquals(Outcome.Success(Unit), pastResult)
    }

    @Test
    fun transactionValidator_rejectsFutureDate() {
        val validator = ValidateCreateTransaction()
        val zone = java.time.ZoneId.of(occurred.zoneId)
        val todayDate = LocalDate.ofEpochDay(occurred.localDateEpochDay)
        val nowMillis = todayDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val futureOccurred = occurred.copy(
            localDateEpochDay = occurred.localDateEpochDay + 1,
            epochMillis = occurred.epochMillis + 86_400_000L,
        )
        val result = validator(validTransaction().copy(occurred = futureOccurred), vnd, nowMillis)

        assertEquals(
            Outcome.Failure(DomainFailure.Validation(ValidationField.DATE, ValidationRule.FUTURE_NOT_ALLOWED)),
            result,
        )
    }

    private fun validTransaction() = CreateTransactionCommand(
        type = TransactionType.EXPENSE,
        amount = Money(45_000, vnd),
        category = expenseCategory,
        account = activeAccount,
        note = "Lunch",
        occurred = occurred,
        images = emptyList(),
    )
}
