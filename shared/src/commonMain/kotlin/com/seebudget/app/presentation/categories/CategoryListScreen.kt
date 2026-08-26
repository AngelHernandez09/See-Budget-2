package com.seebudget.app.presentation.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.Category
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.CategoryIconBadge
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.CategoryListViewModel
import com.seebudget.app.generated.resources.Res
import com.seebudget.app.generated.resources.category_list_back_button
import com.seebudget.app.generated.resources.category_list_button_add
import com.seebudget.app.generated.resources.category_list_button_cancel
import com.seebudget.app.generated.resources.category_list_button_delete
import com.seebudget.app.generated.resources.category_list_button_edit
import com.seebudget.app.generated.resources.category_list_button_save
import com.seebudget.app.generated.resources.category_list_default_badge
import com.seebudget.app.generated.resources.category_list_dialog_title_create
import com.seebudget.app.generated.resources.category_list_dialog_title_edit
import com.seebudget.app.generated.resources.category_list_field_color
import com.seebudget.app.generated.resources.category_list_field_icon
import com.seebudget.app.generated.resources.category_list_field_name
import com.seebudget.app.generated.resources.category_list_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-02 — Gestión de categorías. No es una de las 11 pantallas del
 * documento por sí sola: forma parte de "Ajustes generales > Categorías"
 * (sección 8.4 #11) — alcanzable desde ahí (ver SettingsScreen/App.kt),
 * agregado en el chat junto con la reorganización de secciones (antes
 * era un tab propio del shell mínimo de Fase 1, sin `onBack`).
 */
@Composable
fun CategoryListScreen(onBack: () -> Unit = {}, viewModel: CategoryListViewModel = koinViewModel()) {
    val categories by viewModel.categories.collectAsState()
    val colors = LocalSeeBudgetColors.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.category_list_back_button)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.category_list_title), style = MaterialTheme.typography.headlineMedium)
            BrutalButton(onClick = { showCreateDialog = true }) { Text(stringResource(Res.string.category_list_button_add)) }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            items(categories, key = { it.id }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(2.dp, colors.textAndBorder))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIconBadge(name = category.name, colorHex = category.color)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                            if (category.isDefault) {
                                Text(stringResource(Res.string.category_list_default_badge), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { editingCategory = category }) { Text(stringResource(Res.string.category_list_button_edit)) }
                        if (!category.isDefault) {
                            TextButton(onClick = { viewModel.delete(category.id) }) { Text(stringResource(Res.string.category_list_button_delete)) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showCreateDialog) {
        CategoryEditDialog(
            initial = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, icon, color ->
                viewModel.create(name, icon, color)
                showCreateDialog = false
            },
        )
    }
    editingCategory?.let { category ->
        CategoryEditDialog(
            initial = category,
            onDismiss = { editingCategory = null },
            onConfirm = { name, icon, color ->
                viewModel.update(category.copy(name = name, icon = icon, color = color))
                editingCategory = null
            },
        )
    }
}

/**
 * `icon` se edita como texto libre a propósito: todavía no hay un set de
 * íconos real definido (ver CategoryIconBadge.kt / seed de Category.sq).
 */
@Composable
private fun CategoryEditDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, color: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: "#1B5E3F") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(Res.string.category_list_dialog_title_create) else stringResource(Res.string.category_list_dialog_title_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.category_list_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text(stringResource(Res.string.category_list_field_icon)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text(stringResource(Res.string.category_list_field_color)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, icon, color) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.category_list_button_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.category_list_button_cancel)) }
        },
    )
}
