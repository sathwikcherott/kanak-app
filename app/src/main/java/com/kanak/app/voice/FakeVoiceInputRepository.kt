package com.kanak.app.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeVoiceInputRepository(
    override var isAvailable: Boolean = true,
    var mockTranscript: String = "15.50 for lunch",
    var mockError: VoiceInputError? = null
) : VoiceInputRepository {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    var wasStarted = false
    var wasStopped = false
    var wasDestroyed = false

    override fun startListening(
        onResult: (String) -> Unit,
        onError: (VoiceInputError) -> Unit
    ) {
        wasStarted = true
        if (!isAvailable) {
            _isListening.value = false
            onError(VoiceInputError.ServiceUnavailable)
            return
        }

        val error = mockError
        if (error != null) {
            _isListening.value = false
            onError(error)
        } else {
            _isListening.value = true
            onResult(mockTranscript)
            _isListening.value = false
        }
    }

    override fun stopListening() {
        wasStopped = true
        _isListening.value = false
    }

    override fun destroy() {
        wasDestroyed = true
        _isListening.value = false
    }
}
