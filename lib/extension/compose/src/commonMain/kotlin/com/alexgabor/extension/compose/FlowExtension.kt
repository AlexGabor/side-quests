package com.alexgabor.extension.compose

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun <T> StateFlow<T>.asState(coroutineScope: CoroutineScope): State<T> {
    val mutableState = mutableStateOf<T>(this.value)

    this.onEach {
        mutableState.value = it
    }.launchIn(coroutineScope)

    return mutableState
}

fun <T> Flow<T>.asState(initialValue: T, coroutineScope: CoroutineScope): State<T> {
    val mutableState = mutableStateOf(initialValue)

    this.onEach {
        mutableState.value = it
    }.launchIn(coroutineScope)

    return mutableState
}