package com.seebudget.app.presentation.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.presentation.components.AmountField
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.CategoryIconBadge
import com.seebudget.app.presentation.components.CurrencyField
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.ExpenseFormViewModel
import com.seebudget.app.generated.resources.Res
import com.seebudget.app.generated.resources.expense_form_back_button
import com.seebudget.app.generated.resources.expense_form_button_cancel
import com.seebudget.app.generated.resources.expense_form_button_delete
import com.seebudget.app.generated.resources.expense_form_button_ok
import com.seebudget.app.generated.resources.expense_form_button_save
import com.seebudget.app.generated.resources.expense_form_category_locked_by_item
import com.seebudget.app.generated.resources.expense_form_date_change_button
import com.seebudget.app.generated.resources.expense_form_extra_expense_chip
import com.seebudget.app.generated.resources.expense_form_label_amount
import com.seebudget.app.generated.resources.expense_form_label_category
import com.seebudget.app.generated.resources.expense_form_label_date
import com.seebudget.app.generated.resources.expense_form_label_note
import com.seebudget.app.generated.resources.expense_form_label_payment_method
import com.seebudget.app.generated.resources.expense_form_label_type
import com.seebudget.app.generated.resources.expense_form_link_to_budget
import com.seebudget.app.generated.resources.expense_form_title_create
import com.seebudget.app.generated.resources.expense_form_title_edit
import com.seebudget.app.generated.resources.expense_form_type_expense
import com.seebudget.app.generated.resources.expense_form_type_income
import com.seebudget.app.generated.resources.expense_form_type_locked_by_item
import com.seebudget.app.generated.resources.expense_form_which_item_label
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-01 — Registrar/editar un gasto (sección 8.4 #2 del documento).
 * Monto, moneda (Fase 3), tipo ingreso/egreso (agregado en el chat —
 * antes `Expense.type` solo se fijaba desde el Check-in diario, ver
 * `ExpenseFormViewModel`), categoría, fecha, nota, método de pago,
 * vínculo a presupuesto (Fase 6). Adjuntar foto queda pendiente.
 */
@Composable
fun ExpenseFormScreen(
    expenseId: String?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    onDeleted: () -> Unit,
    // Fase 7 (RF-10) — "+ Agregar gasto" desde el Check-in diario: ver
    // ExpenseFormViewModel.prefillForCheckIn / App.kt. Ambos null (caso
    // normal, fuera del Check-in) o `expenseId` != null (editando):
    // comportamiento sin cambios.
    prefillBudgetId: String? = null,
    prefillDate: LocalDate? = null,
    viewModel: ExpenseFormViewModel = koinViewModel(),
) {
    LaunchedEffect(expenseId, prefillBudgetId, prefillDate) {
        when {
            expenseId != null -> viewModel.loadForEdit(expenseId)
            prefillBudgetId != null && prefillDate != null -> viewModel.prefillForCheckIn(prefillBudgetId, prefillDate)
            else -> viewModel.reset()
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activeBudget by viewModel.activeBudget.collectAsState()
    val activePlannedItems by viewModel.activePlannedItems.collectAsState()
    val colors = LocalSeeBudgetColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Notas de UI (Fase 6): botón de regreso arriba a la izquierda —
        // antes había que scrollear hasta el final y tocar "Cancelar".
        // Reusa el mismo callback `onCancel`, no descarta nada por su
        // cuenta (el guardado sigue siendo explícito con "Guardar").
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.expense_form_back_button)) }
        Spacer(Modifier.height(8.dp))

        Text(
            text = if (uiState.editingId == null) stringResource(Res.string.expense_form_title_create) else stringResource(Res.string.expense_form_title_edit),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(24.dp))

        // Monto — protagonista visual (guía, sección 4 "puntos de mayor
        // impacto visual": tratarlo como titular, no campo secundario).
        AmountField(
            amountMinorUnits = uiState.amount,
            currency = uiState.currency,
            onAmountChange = viewModel::onAmountChange,
            label = { Text(stringResource(Res.string.expense_form_label_amount, uiState.currency)) },
            textStyle = MaterialTheme.typography.displayMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // RF-04 (Fase 3): moneda del gasto, default = moneda base del
        // usuario, editable acá mismo (ver ExpenseFormViewModel.init).
        CurrencyField(
            selectedCode = uiState.currency,
            onCurrencyChange = viewModel::onCurrencyChange,
        )
        Spacer(Modifier.height(24.dp))

        // Fase 7 (RF-10) / agregado en el chat — ver KDoc de
        // ExpenseFormViewModel.onPlannedItemChange: vinculado a un
        // PlannedItem, el tipo se fuerza siempre (PlannedItem.type nunca
        // es null) y la categoría se fuerza si el ítem ya tiene una.
        val linkedPlannedItem = activePlannedItems.firstOrNull { it.id == uiState.plannedItemId }
        val categoryLocked = linkedPlannedItem?.categoryId != null
        val typeLocked = linkedPlannedItem != null

        Text(stringResource(Res.string.expense_form_label_type), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (typeLocked) {
            Text(
                stringResource(Res.string.expense_form_type_locked_by_item, linkedPlannedItem?.name.orEmpty()),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(4.dp))
        }
        ExpenseTypePicker(
            selected = uiState.type,
            onSelect = viewModel::onTypeChange,
            enabled = !typeLocked,
        )
        Spacer(Modifier.height(24.dp))

        Text(stringResource(Res.string.expense_form_label_category), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (categoryLocked) {
            Text(
                stringResource(Res.string.expense_form_category_locked_by_item, linkedPlannedItem?.name.orEmpty()),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(4.dp))
        }
        CategoryPicker(
            categories = categories,
            selectedId = uiState.categoryId,
            onSelect = viewModel::onCategoryChange,
            enabled = !categoryLocked,
        )
        Spacer(Modifier.height(24.dp))

        Text(stringResource(Res.string.expense_form_label_date), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        DateField(date = uiState.date, onDateChange = viewModel::onDateChange)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.note,
            onValueChange = viewModel::onNoteChange,
            label = { Text(stringResource(Res.string.expense_form_label_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.paymentMethod,
            onValueChange = viewModel::onPaymentMethodChange,
            label = { Text(stringResource(Res.string.expense_form_label_payment_method)) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Fase 6 (RF-08/RF-09) — "Selector de presupuesto (solo si hay uno
        // activo)": togglea el vínculo con el presupuesto ACTIVE actual y,
        // si aplica, a qué PlannedItem corresponde (o "gasto adicional").
        activeBudget?.let { budget ->
            Spacer(Modifier.height(24.dp))
            BudgetLinkSection(
                budgetName = budget.name,
                linked = uiState.budgetId == budget.id,
                plannedItemId = uiState.plannedItemId,
                plannedItems = activePlannedItems,
                onLinkedChange = viewModel::onLinkedToBudgetChange,
                onPlannedItemChange = viewModel::onPlannedItemChange,
            )
        }
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrutalButton(
                onClick = { viewModel.save(onSaved) },
                enabled = uiState.categoryId.isNotBlank() && uiState.amount > 0,
            ) { Text(stringResource(Res.string.expense_form_button_save)) }
            BrutalButton(
                onClick = onCancel,
                backgroundColor = colors.surface,
            ) { Text(stringResource(Res.string.expense_form_button_cancel)) }
            if (uiState.editingId != null) {
                BrutalButton(
                    onClick = { viewModel.delete(onDeleted) },
                    backgroundColor = colors.accentError,
                    contentColor = Color.White,
                ) { Text(stringResource(Res.string.expense_form_button_delete)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BudgetLinkSection(
    budgetName: String,
    linked: Boolean,
    plannedItemId: String?,
    plannedItems: List<PlannedItem>,
    onLinkedChange: (Boolean) -> Unit,
    onPlannedItemChange: (String?) -> Unit,
) {
    val colors = LocalSeeBudgetColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.expense_form_link_to_budget, budgetName), style = MaterialTheme.typography.labelMedium)
        Switch(checked = linked, onCheckedChange = onLinkedChange)
    }
    if (linked) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.expense_form_which_item_label), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        FlowRowChips {
            PlannedItemChip(
                label = stringResource(Res.string.expense_form_extra_expense_chip),
                isSelected = plannedItemId == null,
                onClick = { onPlannedItemChange(null) },
            )
            plannedItems.forEach { item ->
                PlannedItemChip(
                    // Antes solo mostraba "Ingreso"/"Egreso" — con varios
                    // ítems del mismo tipo eran indistinguibles (reportado
                    // en el chat). Fallback al tipo si el nombre viniera
                    // vacío (ítems muy viejos, previos al campo `name`).
                    label = "${item.name.ifBlank { plannedItemTypeLabel(item.type) }} · " +
                        "${MoneyFormat.toDecimalString(item.amount, item.currency)} ${item.currency}",
                    isSelected = plannedItemId == item.id,
                    onClick = { onPlannedItemChange(item.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
private fun PlannedItemChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(
        modifier = Modifier
            .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
            .background(if (isSelected) colors.accentAction else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun plannedItemTypeLabel(type: PlannedItemType): String = when (type) {
    PlannedItemType.INCOME -> stringResource(Res.string.expense_form_type_income)
    PlannedItemType.EXPENSE -> stringResource(Res.string.expense_form_type_expense)
}

/**
 * Ingreso/Egreso — agregado en el chat, ver KDoc de
 * `ExpenseFormViewModel.onTypeChange`/`onPlannedItemChange`. Mismo look
 * que el `ChipRow` de `BudgetFormScreen` (duplicado a propósito, ver
 * convención de la clase sobre componentes privados por pantalla).
 */
@Composable
private fun ExpenseTypePicker(
    selected: PlannedItemType,
    onSelect: (PlannedItemType) -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalSeeBudgetColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlannedItemType.entries.forEach { type ->
            val isSelected = type == selected
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .then(if (enabled) Modifier.clickable { onSelect(type) } else Modifier)
                    .alpha(if (enabled || isSelected) 1f else 0.5f)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(expenseTypeLabel(type), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun expenseTypeLabel(type: PlannedItemType): String = when (type) {
    PlannedItemType.INCOME -> stringResource(Res.string.expense_form_type_income)
    PlannedItemType.EXPENSE -> stringResource(Res.string.expense_form_type_expense)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    // Fase 7 (RF-10) — ver KDoc de ExpenseFormViewModel.onPlannedItemChange.
    enabled: Boolean = true,
) {
    val colors = LocalSeeBudgetColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val isSelected = category.id == selectedId
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .then(if (enabled) Modifier.clickable { onSelect(category.id) } else Modifier)
                    .alpha(if (enabled || isSelected) 1f else 0.5f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconBadge(name = category.name, colorHex = category.color, size = 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(category.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val colors = LocalSeeBudgetColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .clickable { showPicker = true }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(date.toString(), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.expense_form_date_change_button), style = MaterialTheme.typography.labelMedium)
    }

    if (showPicker) {
        val initialMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val selected = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                        onDateChange(selected)
                    }
                    showPicker = false
                }) { Text(stringResource(Res.string.expense_form_button_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.expense_form_button_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
