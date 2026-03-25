package com.zentra

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceUiState(
    val status: String = "Starting...",
    val lastHeard: String = "-",
    val message: String? = null
)

object VoiceStateStore {
    private val mutableState = MutableStateFlow(VoiceUiState())
    val state = mutableState.asStateFlow()

    fun updateStatus(status: String) {
        mutableState.value = mutableState.value.copy(status = status)
    }

    fun updateHeard(text: String) {
        mutableState.value = mutableState.value.copy(lastHeard = text)
    }

    fun updateMessage(message: String?) {
        mutableState.value = mutableState.value.copy(message = message)
    }
}
