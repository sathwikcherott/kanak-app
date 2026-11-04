package com.kanak.app.voice

import kotlinx.coroutines.flow.StateFlow

sealed interface VoiceInputError {
    data object PermissionDenied : VoiceInputError
    data object ServiceUnavailable : VoiceInputError
    data object NoSpeechDetected : VoiceInputError
    data class Unknown(val message: String) : VoiceInputError
}

interface VoiceInputRepository {
    val isAvailable: Boolean
    val isListening: StateFlow<Boolean>

    fun startListening(
        onResult: (String) -> Unit,
        onError: (VoiceInputError) -> Unit
    )

    fun stopListening()
    fun destroy()
}
