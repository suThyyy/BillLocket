package com.billlocket.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface AsyncValue<out T> {
    data object Uninitialized : AsyncValue<Nothing>
    data object Loading : AsyncValue<Nothing>
    data class Data<T>(val value: T, val refreshing: Boolean = false) : AsyncValue<T>
    data class Error<T>(val previous: T?, val message: UiText) : AsyncValue<T>
}

sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText
}

@Composable
fun <T> StateFlow<T>.collectUiState(): State<T> = collectAsStateWithLifecycle()

@Composable
fun <E> CollectEffects(effects: Flow<E>, onEffect: suspend (E) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(effects, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect(onEffect)
        }
    }
}
