package com.billlocket.app.feature.insights

import com.billlocket.app.domain.model.*
import com.billlocket.app.domain.port.Clock
import com.billlocket.app.domain.port.NotificationPort
import com.billlocket.app.domain.repository.BudgetRepository
import com.billlocket.app.domain.result.DomainFailure
import com.billlocket.app.domain.result.Outcome
import com.billlocket.app.domain.usecase.EvaluateBudgetThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetAlertUseCaseTest {
    private val vnd = CurrencyCode.of("VND")

    @Test fun `successful notification is marked exactly once`() = runTest {
        val repository = FakeBudgets()
        val notifications = FakeNotifications(succeeds = true)
        EvaluateBudgetThresholds(repository, notifications, Clock { 42 })(1)
        assertEquals(listOf(80), notifications.published)
        assertEquals(listOf(80), repository.marked)
    }

    @Test fun `permission denial never marks threshold delivered`() = runTest {
        val repository = FakeBudgets()
        val notifications = FakeNotifications(succeeds = false)
        EvaluateBudgetThresholds(repository, notifications, Clock { 42 })(1)
        assertEquals(emptyList<Int>(), repository.marked)
    }

    private inner class FakeBudgets : BudgetRepository {
        val id = BudgetId("budget")
        val marked = mutableListOf<Int>()
        override fun observeMonth(monthStartEpochDay: Long): Flow<List<BudgetProgress>> = flowOf(listOf(BudgetProgress(id, monthStartEpochDay, null, Money(100, vnd), Money(80, vnd), 80)))
        override suspend fun upsert(command: SaveBudgetCommand) = Outcome.Success(id)
        override suspend fun delete(id: BudgetId) = Outcome.Success(Unit)
        override suspend fun pendingThresholds(monthStartEpochDay: Long) = Outcome.Success(listOf(ThresholdCrossing(id, 80)))
        override suspend fun markThresholdDelivered(id: BudgetId, percent: Int, at: Long): Outcome<Unit> { marked += percent; return Outcome.Success(Unit) }
        override suspend fun resetThresholdIfBelow(id: BudgetId, percent: Int) = Outcome.Success(Unit)
    }

    private class FakeNotifications(private val succeeds: Boolean) : NotificationPort {
        val published = mutableListOf<Int>()
        override suspend fun publishBudgetThreshold(budgetId: String, percent: Int): Outcome<Unit> {
            published += percent
            return if (succeeds) Outcome.Success(Unit) else Outcome.Failure(DomainFailure.PermissionDenied("notifications", false))
        }
    }
}
