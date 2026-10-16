package com.deepcook.app.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcook.app.core.model.RecipeFilter
import com.deepcook.app.core.model.TimeFilter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class)
class SearchViewModel(private val saved: SavedStateHandle, initialCategory: String) : ViewModel() {
    val query = saved.getStateFlow("query", "")
    private val category = saved.getStateFlow("category", initialCategory)
    private val time = saved.getStateFlow("time", TimeFilter.ANY.name)
    private val difficulty = saved.getStateFlow("difficultyScore", 0)
    val filter = combine(query.debounce(250), category, time, difficulty) { q, c, t, d ->
        RecipeFilter(q, c, TimeFilter.valueOf(t), d.takeIf { it in 1..5 })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeFilter(category = initialCategory))

    fun query(value: String) {
        saved["query"] = value
    }
    fun category(value: String) {
        saved["category"] = value
    }
    fun time(value: TimeFilter) {
        saved["time"] = value.name
    }
    fun difficulty(value: Int?) {
        saved["difficultyScore"] = value ?: 0
    }
    fun clearFilters() {
        category("全部")
        time(TimeFilter.ANY)
        difficulty(null)
    }
}
