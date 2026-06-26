package com.meiloon.mlcontrolcore_aos.extension

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 用於 LifecycleOwner (如 Activity, Fragment) 的 Flow 收集。
 * 預設在 STARTED 狀態下開始收集，並在 STOPPED 時暫停。
 */
fun <T> Flow<T>.collectWithLifecycle(
    owner: LifecycleOwner,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit
) {
    flowWithLifecycle(owner.lifecycle, state)
        .onEach { action(it) }
        .launchIn(owner.lifecycleScope)
}

/**
 * 用於 CoroutineScope (如 ViewModel 的 viewModelScope) 的 Flow 收集。
 */
fun <T> Flow<T>.collectIn(
    scope: CoroutineScope,
    action: suspend (T) -> Unit
) {
    onEach { action(it) }
        .launchIn(scope)
}
