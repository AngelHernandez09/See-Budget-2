package com.seebudget.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * RF-02 — Gestión simple de categorías (predefinidas + personalizadas).
 * Fase 1: CRUD directo, sin validaciones de negocio adicionales (el
 * documento no define reglas más allá de "isDefault distingue el
 * origen" — ver Category.kt).
 */
class CategoryListViewModel(
    private val repository: CategoryRepository,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, icon: String, color: String) {
        viewModelScope.launch { repository.create(name, icon, color) }
    }

    fun update(category: Category) {
        viewModelScope.launch { repository.update(category) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
