package com.billlocket.app.ui.common

import java.util.concurrent.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RecoverableObservationTest {
    @Test fun `repository failure is emitted without ending outer filter collection`() = runTest {
        val filters = MutableSharedFlow<Int>()
        val results = async {
            filters.flatMapLatest { filter ->
                (if (filter == 1) {
                    flow<String> { throw IllegalStateException("database unavailable") }
                } else {
                    flowOf("loaded-$filter")
                }).asObservationResult()
            }.take(2).toList()
        }
        runCurrent()
        filters.emit(1)
        runCurrent()
        filters.emit(2)
        runCurrent()

        val collected = results.await()
        assertTrue(collected[0] is ObservationResult.Error)
        assertEquals("loaded-2", (collected[1] as ObservationResult.Value).value)
    }

    @Test fun `successful values remain successful values`() = runTest {
        assertEquals(listOf(ObservationResult.Value(42)), flowOf(42).asObservationResult().toList())
    }

    @Test fun `failed database subscription is recreated and can recover`() = runTest {
        var subscriptions = 0
        val results = flow {
            subscriptions += 1
            if (subscriptions == 1) throw IllegalStateException("temporary database failure")
            emit(7)
        }.asObservationResult(retryDelayMillis = 1).take(2).toList()

        assertTrue(results[0] is ObservationResult.Error)
        assertEquals(7, (results[1] as ObservationResult.Value).value)
        assertEquals(2, subscriptions)
    }

    @Test fun `cancellation is rethrown instead of becoming an error state`() = runTest {
        var cancelled = false
        try {
            flow<Int> { throw CancellationException("filter changed") }.asObservationResult().toList()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
