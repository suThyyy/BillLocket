package com.billlocket.app.ui.common

import java.util.concurrent.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

sealed interface ObservationResult<out T> {
    data class Value<T>(val value: T) : ObservationResult<T>
    data class Error(val cause: Throwable) : ObservationResult<Nothing>
}

/**
 * Converts one repository subscription into values/errors without terminating
 * the outer filter flow that created it. A failed Room subscription is recreated
 * after a short delay, while filter changes and other cancellation still propagate.
 */
fun <T> Flow<T>.asObservationResult(retryDelayMillis: Long = 1_000L): Flow<ObservationResult<T>> = flow {
    require(retryDelayMillis >= 0L)
    while (currentCoroutineContext().isActive) {
        try {
            collect { emit(ObservationResult.Value(it)) }
            return@flow
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            emit(ObservationResult.Error(error))
            delay(retryDelayMillis)
        }
    }
}
