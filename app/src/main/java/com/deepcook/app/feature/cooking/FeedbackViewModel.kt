package com.deepcook.app.feature.cooking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.data.ai.NoteSuggester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Turns the feedback sentence into the stored note; the raw sentence is kept alongside it. */
class FeedbackViewModel(private val suggester: NoteSuggester) : ViewModel() {
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    fun submit(raw: String, onReady: (text: String) -> Unit) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _saving.value = true
            val text = runCatching { suggester.rewrite(trimmed) }.getOrNull() ?: trimmed.take(20)
            _saving.value = false
            onReady(text.ifBlank { trimmed.take(20) })
        }
    }
}
