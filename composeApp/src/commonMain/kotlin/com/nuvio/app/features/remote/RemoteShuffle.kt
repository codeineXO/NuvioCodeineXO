package com.nuvio.app.features.remote

import com.nuvio.app.features.randompicker.RandomPickerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shuffle for the phone remote: the PC's Shuffle model while its screen is showing, for the phone to drive. */
internal object RemoteShuffle {
    private val _model = MutableStateFlow<RandomPickerViewModel?>(null)
    val model: StateFlow<RandomPickerViewModel?> = _model.asStateFlow()

    fun publish(model: RandomPickerViewModel) {
        _model.value = model
    }

    fun clear(model: RandomPickerViewModel) {
        _model.compareAndSet(model, null)
    }
}
