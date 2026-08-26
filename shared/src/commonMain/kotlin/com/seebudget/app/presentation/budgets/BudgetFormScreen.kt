package com.seebudget.app.presentation.budgets

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.domain.model.BudgetStatus
import com.seebudget.app.presentation.components.AmountField
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.CategoryIconBadge
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.CurrencyField
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.BudgetFormViewModel
import com.seebudget.app.presentation.viewmodel.PlannedItemDraft
import com.seebudget.app.presentation.viewmodel.PlannedItemRow
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-08/RF-09 — "Crear/Editar presupuesto" (sección 8.4 #9), con la
 * sección "Ítems planificados" embebida. En modo edición también hace de
 * "Ajustes del presupuesto" (#10): botón Activar/Pausar según el estado,
 * botón "Crear nueva línea de referencia desde hoy", y zona de peligro
 * (Completar/Eliminar, con confirmación).
 *
 * **Diálogo "¿corrección de error o cambio real?"** (ver KDoc de
 * `BudgetFormViewModel`): cuando `uiState.pendingSnapshotDecision` es
 * `true`, se muestra [ProjectionChangeDialog] — no es descartable
 * tocando afuera (`onDismissRequest = {}`), el usuario tiene que elegir
 * una de las 3 opciones para que el diálogo se cierre solo (el ViewModel
 * pone `pendingSnapshotDecision = false` al resolver).
 */
@Composable
fun BudgetFormScreen(
    budgetId: String?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: BudgetFormViewModel = koinViewModel(),
) {
    LaunchedEffect(budgetId) {
        if (budgetId != null) viewModel.loadForEdit(budgetId) else viewModel.reset()
    }
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val colors = LocalSeeBudgetColors.current
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showItemDialog by remember { mutableStateOf(false) }
    var editingItemRow by remember { mutableStateOf<PlannedItemRow?>(null) }
    var showNewLineDialog by remember { mutableStateOf(false) }

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
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.budget_form_back_button)) }
        Spacer(Modifier.height(8.dp))

        Text(
            text = if (uiState.editingId == null) {
                stringResource(Res.string.budget_form_title_create)
            } else {
                stringResource(Res.string.budget_form_title_edit)
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(Res.string.budget_form_field_name_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Text(stringResource(Res.string.budget_form_start_date_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        DateField(date = uiState.startDate, onDateChange = viewModel::onStartDateChange)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.budget_form_has_end_date_label), style = MaterialTheme.typography.labelMedium)
            Switch(checked = uiState.hasEndDate, onCheckedChange = viewModel::onHasEndDateChange)
        }
        if (uiState.hasEndDate) {
            Spacer(Modifier.height(8.dp))
            DateField(date = uiState.endDate, onDateChange = viewModel::onEndDateChange)
        }
        Spacer(Modifier.height(24.dp))

        AmountField(
            amountMinorUnits = uiState.initialBalance,
            currency = uiState.baseCurrency,
            onAmountChange = viewModel::onInitialBalanceChange,
            label = { Text(stringResource(Res.string.budget_form_initial_balance_label, uiState.baseCurrency)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        CurrencyField(
            selectedCode = uiState.baseCurrency,
            onCurrencyChange = viewModel::onBaseCurrencyChange,
            label = stringResource(Res.string.budget_form_base_currency_label),
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.budget_form_notification_label), style = MaterialTheme.typography.labelMedium)
            Switch(checked = uiState.notificationEnabled, onCheckedChange = viewModel::onNotificationEnabledChange)
        }
        if (uiState.notificationEnabled) {
            Spacer(Modifier.height(8.dp))
            TimeField(time = uiState.notificationTime, onTimeChange = viewModel::onNotificationTimeChange)
        }
        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.budget_form_planned_items_section_label), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { editingItemRow = null; showItemDialog = true }) { Text(stringResource(Res.string.budget_form_add_item_button)) }
        }
        Spacer(Modifier.height(8.dp))
        if (uiState.plannedItems.isEmpty()) {
            Text(
                stringResource(Res.string.budget_form_no_planned_items_message),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            uiState.plannedItems.forEach { row ->
                PlannedItemSummaryRow(
                    draft = row.asDraft(),
                    categories = categories,
                    onEdit = { editingItemRow = row; showItemDialog = true },
                    onDelete = { viewModel.deletePlannedItem(row) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrutalButton(
                onClick = { viewModel.save(onSaved) },
                enabled = uiState.name.isNotBlank(),
            ) { Text(stringResource(Res.string.budget_form_button_save)) }
            BrutalButton(onClick = onCancel, backgroundColor = colors.surface) { Text(stringResource(Res.string.budget_form_button_cancel)) }
        }

        if (uiState.editingId != null) {
            Spacer(Modifier.height(24.dp))
            if (uiState.status == BudgetStatus.PAUSED) {
                BrutalButton(onClick = viewModel::activate, backgroundColor = colors.accentPositive, contentColor = Color.White) {
                    Text(stringResource(Res.string.budget_form_button_activate))
                }
                Spacer(Modifier.height(16.dp))
            }
            if (uiState.status == BudgetStatus.ACTIVE) {
                BrutalButton(onClick = viewModel::pause, backgroundColor = colors.surface) {
                    Text(stringResource(Res.string.budget_form_button_pause))
                }
                Spacer(Modifier.height(16.dp))
            }
            // RF-09, documento sección 8.4 #10 — disponible en cualquier
            // momento, independiente del diálogo de corrección/cambio real.
            BrutalButton(onClick = { showNewLineDialog = true }, backgroundColor = colors.surface) {
                Text(stringResource(Res.string.budget_form_button_create_reference_line))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.budget_form_danger_zone_label), style = MaterialTheme.typography.labelMedium, color = colors.accentError)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uiState.status != BudgetStatus.COMPLETED) {
                    TextButton(onClick = { showCompleteConfirm = true }) { Text(stringResource(Res.string.budget_form_button_complete_budget)) }
                }
                TextButton(onClick = { showDeleteConfirm = true }) { Text(stringResource(Res.string.budget_form_button_delete_budget)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showItemDialog) {
        PlannedItemEditDialog(
            initial = editingItemRow?.asDraft(),
            categories = categories,
            onDismiss = { showItemDialog = false; editingItemRow = null },
            onConfirm = { draft ->
                val row = editingItemRow
                if (row == null) viewModel.addPlannedItem(draft) else viewModel.updatePlannedItem(row, draft)
                showItemDialog = false
                editingItemRow = null
            },
        )
    }

    if (showCompleteConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.budget_form_complete_confirm_title),
            message = stringResource(Res.string.budget_form_complete_confirm_message),
            confirmLabel = stringResource(Res.string.budget_form_label_complete),
            onConfirm = { showCompleteConfirm = false; viewModel.complete(onSaved) },
            onDismiss = { showCompleteConfirm = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.budget_form_delete_confirm_title),
            message = stringResource(Res.string.budget_form_delete_confirm_message),
            confirmLabel = stringResource(Res.string.budget_form_label_delete),
            onConfirm = { showDeleteConfirm = false; viewModel.delete(onDeleted) },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showNewLineDialog) {
        LabelInputDialog(
            title = stringResource(Res.string.budget_form_new_line_dialog_title),
            message = stringResource(Res.string.budget_form_new_line_dialog_message),
            onDismiss = { showNewLineDialog = false },
            onConfirm = { label ->
                viewModel.createNewReferenceLine(label)
                showNewLineDialog = false
            },
        )
    }

    if (uiState.pendingSnapshotDecision) {
        ProjectionChangeDialog(
            onCorrection = viewModel::confirmCorrection,
            onKeepCurrentLine = viewModel::confirmKeepCurrentLine,
            onCreateNewLine = viewModel::confirmNewLine,
        )
    }
}

/** Vista unificada de una fila (Draft o Saved) para pintarla/editarla igual. */
private fun PlannedItemRow.asDraft(): PlannedItemDraft = when (this) {
    is PlannedItemRow.Draft -> draft
    is PlannedItemRow.Saved -> item.toDraft()
}

private fun PlannedItem.toDraft(): PlannedItemDraft = PlannedItemDraft(
    name = name,
    categoryId = categoryId,
    type = type,
    amount = amount,
    currency = currency,
    frequency = frequency,
    billingDay = billingDay,
    specificDate = specificDate,
    dayOfWeek = dayOfWeek,
    isActive = isActive,
)

@Composable
private fun PlannedItemSummaryRow(
    draft: PlannedItemDraft,
    categories: List<Category>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalSeeBudgetColors.current
    val category = categories.firstOrNull { it.id == draft.categoryId }
    Row(
        modifier = Modifier
            .background(colors.surface)
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.name.ifBlank { typeLabel(draft.type) },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${typeLabel(draft.type)} · ${MoneyFormat.toDecimalString(draft.amount, draft.currency)} ${draft.currency}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = frequencyLabel(draft.frequency, draft.billingDay, draft.specificDate, draft.dayOfWeek) +
                    if (!draft.isActive) stringResource(Res.string.budget_form_inactive_suffix) else "",
                style = MaterialTheme.typography.labelSmall,
            )
            // Fase 7 (RF-10) — ver KDoc de PlannedItem.kt: sin categoría
            // significa que este ítem todavía no se puede confirmar desde
            // el Check-in diario hasta que se le asigne una acá.
            Text(
                text = category?.name ?: stringResource(Res.string.budget_form_no_category_label),
                style = MaterialTheme.typography.labelSmall,
                color = if (category == null) colors.accentError else colors.textAndBorder,
            )
        }
        TextButton(onClick = onEdit) { Text(stringResource(Res.string.budget_form_label_edit)) }
        TextButton(onClick = onDelete) { Text(stringResource(Res.string.budget_form_label_delete)) }
    }
}

@Composable
private fun typeLabel(type: PlannedItemType): String = when (type) {
    PlannedItemType.INCOME -> stringResource(Res.string.budget_form_type_income)
    PlannedItemType.EXPENSE -> stringResource(Res.string.budget_form_type_expense)
}

@Composable
private fun dayOfWeekLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> stringResource(Res.string.budget_form_day_mon)
    DayOfWeek.TUESDAY -> stringResource(Res.string.budget_form_day_tue)
    DayOfWeek.WEDNESDAY -> stringResource(Res.string.budget_form_day_wed)
    DayOfWeek.THURSDAY -> stringResource(Res.string.budget_form_day_thu)
    DayOfWeek.FRIDAY -> stringResource(Res.string.budget_form_day_fri)
    DayOfWeek.SATURDAY -> stringResource(Res.string.budget_form_day_sat)
    DayOfWeek.SUNDAY -> stringResource(Res.string.budget_form_day_sun)
    else -> dayOfWeek.name
}

@Composable
private fun frequencyLabel(
    frequency: PlannedItemFrequency,
    billingDay: Int?,
    specificDate: LocalDate?,
    dayOfWeek: DayOfWeek?,
): String = when (frequency) {
    PlannedItemFrequency.ONCE -> if (specificDate != null) {
        stringResource(Res.string.budget_form_frequency_once_with_date, specificDate.toString())
    } else {
        stringResource(Res.string.budget_form_frequency_once)
    }
    PlannedItemFrequency.DAILY -> stringResource(Res.string.budget_form_frequency_daily)
    PlannedItemFrequency.WEEKLY -> if (dayOfWeek != null) {
        stringResource(Res.string.budget_form_frequency_weekly_with_day, dayOfWeekLabel(dayOfWeek))
    } else {
        stringResource(Res.string.budget_form_frequency_weekly)
    }
    PlannedItemFrequency.WEEKDAYS -> stringResource(Res.string.budget_form_frequency_weekdays)
    PlannedItemFrequency.MONTHLY -> if (billingDay != null) {
        stringResource(Res.string.budget_form_frequency_monthly_with_day, billingDay)
    } else {
        stringResource(Res.string.budget_form_frequency_monthly)
    }
}

@Composable
private fun PlannedItemEditDialog(
    initial: PlannedItemDraft?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (PlannedItemDraft) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // Fase 7 (RF-10) — ver KDoc de PlannedItem.kt: obligatoria para poder
    // confirmar este ítem desde el Check-in diario. `null` solo es
    // posible al editar un ítem creado antes de Fase 7 hasta que se le
    // asigne una acá (ver validación de "Guardar" más abajo).
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var type by remember { mutableStateOf(initial?.type ?: PlannedItemType.EXPENSE) }
    var amount by remember { mutableStateOf(initial?.amount ?: 0L) }
    var currency by remember { mutableStateOf(initial?.currency ?: "USD") }
    var frequency by remember { mutableStateOf(initial?.frequency ?: PlannedItemFrequency.MONTHLY) }
    var billingDayText by remember { mutableStateOf(initial?.billingDay?.toString() ?: "") }
    var specificDate by remember {
        mutableStateOf(initial?.specificDate ?: Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }
    var dayOfWeek by remember { mutableStateOf(initial?.dayOfWeek ?: DayOfWeek.MONDAY) }
    var isActive by remember { mutableStateOf(initial?.isActive ?: true) }

    val billingDay = billingDayText.toIntOrNull()
    val billingDayValid = frequency != PlannedItemFrequency.MONTHLY || (billingDay != null && billingDay in 1..31)
    val nameValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) {
                    stringResource(Res.string.budget_form_item_dialog_title_new)
                } else {
                    stringResource(Res.string.budget_form_item_dialog_title_edit)
                }
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.budget_form_field_name_label)) },
                    isError = !nameValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.budget_form_category_label), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                PlannedItemCategoryPicker(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                )
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    options = PlannedItemType.entries,
                    selected = type,
                    label = ::typeLabel,
                    onSelect = { type = it },
                )
                Spacer(Modifier.height(12.dp))
                AmountField(
                    amountMinorUnits = amount,
                    currency = currency,
                    onAmountChange = { amount = it },
                    label = { Text(stringResource(Res.string.budget_form_item_amount_label, currency)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                CurrencyField(selectedCode = currency, onCurrencyChange = { currency = it })
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.budget_form_frequency_label), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                ChipRow(
                    options = PlannedItemFrequency.entries,
                    selected = frequency,
                    label = { frequencyLabel(it, null, null, null) },
                    onSelect = { frequency = it },
                )
                if (frequency == PlannedItemFrequency.MONTHLY) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = billingDayText,
                        onValueChange = { billingDayText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(Res.string.budget_form_billing_day_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !billingDayValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (frequency == PlannedItemFrequency.ONCE) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.budget_form_date_label), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    DateField(date = specificDate, onDateChange = { specificDate = it })
                }
                if (frequency == PlannedItemFrequency.WEEKLY) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.budget_form_day_of_week_label), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    ChipRow(
                        options = DayOfWeek.entries.toList(),
                        selected = dayOfWeek,
                        label = ::dayOfWeekLabel,
                        onSelect = { dayOfWeek = it },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.budget_form_active_label), style = MaterialTheme.typography.labelMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        PlannedItemDraft(
                            name = name,
                            categoryId = categoryId,
                            type = type,
                            amount = amount,
                            currency = currency,
                            frequency = frequency,
                            billingDay = if (frequency == PlannedItemFrequency.MONTHLY) billingDay else null,
                            specificDate = if (frequency == PlannedItemFrequency.ONCE) specificDate else null,
                            dayOfWeek = if (frequency == PlannedItemFrequency.WEEKLY) dayOfWeek else null,
                            isActive = isActive,
                        )
                    )
                },
                enabled = amount > 0 && billingDayValid && nameValid && categoryId != null,
            ) { Text(stringResource(Res.string.budget_form_button_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.budget_form_button_cancel)) }
        },
    )
}

/**
 * Selector de categoría del sub-formulario de ítem planificado (Fase 7,
 * RF-10) — chips igual que `CategoryPicker` de `ExpenseFormScreen`, pero
 * duplicado localmente (ver convención del proyecto de no compartir
 * helpers privados de UI entre archivos de pantalla) y con selección
 * NULLABLE: acá puede no haber ninguna categoría elegida todavía (ítem
 * recién creado, o uno de antes de Fase 7 que nunca tuvo una) — el botón
 * "Guardar" del diálogo exige `categoryId != null` antes de habilitarse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedItemCategoryPicker(categories: List<Category>, selectedId: String?, onSelect: (String) -> Unit) {
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
                    .clickable { onSelect(category.id) }
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

/**
 * RF-09 — el diálogo "¿corrección de error o cambio real?" (ver KDoc de
 * `BudgetFormViewModel`). No es descartable tocando afuera: el usuario
 * debe elegir "Corrección de error" o, dentro de "Cambio real", una de
 * las dos sub-opciones — así el diálogo siempre se cierra a través de
 * una decisión explícita, nunca por accidente.
 */
@Composable
private fun ProjectionChangeDialog(
    onCorrection: () -> Unit,
    onKeepCurrentLine: () -> Unit,
    onCreateNewLine: (String) -> Unit,
) {
    var step by remember { mutableStateOf(ProjectionChangeStep.CHOOSE) }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                if (step == ProjectionChangeStep.CHOOSE) {
                    stringResource(Res.string.budget_form_snapshot_dialog_title_choose)
                } else {
                    stringResource(Res.string.budget_form_snapshot_dialog_title_real_change)
                }
            )
        },
        text = {
            when (step) {
                ProjectionChangeStep.CHOOSE -> Text(
                    stringResource(Res.string.budget_form_snapshot_dialog_message_choose),
                )
                ProjectionChangeStep.REAL_CHANGE -> Column {
                    Text(
                        stringResource(Res.string.budget_form_snapshot_dialog_message_real_change),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(Res.string.budget_form_reference_line_label_field)) },
                        placeholder = { Text(stringResource(Res.string.budget_form_label_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                ProjectionChangeStep.CHOOSE -> TextButton(onClick = { step = ProjectionChangeStep.REAL_CHANGE }) {
                    Text(stringResource(Res.string.budget_form_snapshot_dialog_title_real_change))
                }
                ProjectionChangeStep.REAL_CHANGE -> TextButton(onClick = { onCreateNewLine(label) }) {
                    Text(stringResource(Res.string.budget_form_button_generate_new_line))
                }
            }
        },
        dismissButton = {
            when (step) {
                ProjectionChangeStep.CHOOSE -> TextButton(onClick = onCorrection) { Text(stringResource(Res.string.budget_form_button_correction)) }
                ProjectionChangeStep.REAL_CHANGE -> TextButton(onClick = onKeepCurrentLine) { Text(stringResource(Res.string.budget_form_button_keep_current_line)) }
            }
        },
    )
}

private enum class ProjectionChangeStep { CHOOSE, REAL_CHANGE }

/** Usado por el botón manual "Crear nueva línea de referencia desde hoy" — [ProjectionChangeDialog] pide su propio label inline, no reusa este composable. */
@Composable
private fun LabelInputDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(Res.string.budget_form_new_line_label_field)) },
                    placeholder = { Text(stringResource(Res.string.budget_form_label_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(label) }) { Text(stringResource(Res.string.budget_form_button_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.budget_form_button_cancel)) } },
    )
}

// FlowRow (no Row): con 5 frecuencias ("Diaria" agregada en el chat) más
// las 2 de tipo, un Row de una sola línea aplastaba los chips más largos
// (ej. "Mensual") en vez de dejarlos legibles — mismo fix que ya usa
// PlannedItemCategoryPicker más abajo.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(label(option), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalSeeBudgetColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = colors.accentError) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.budget_form_button_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val colors = LocalSeeBudgetColors.current

    Row(
        modifier = Modifier
            .background(colors.surface)
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .clickable { showPicker = true }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(date.toString(), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.budget_form_change_label), style = MaterialTheme.typography.labelMedium)
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
                }) { Text(stringResource(Res.string.budget_form_ok_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.budget_form_button_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(time: LocalTime, onTimeChange: (LocalTime) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val colors = LocalSeeBudgetColors.current

    Row(
        modifier = Modifier
            .background(colors.surface)
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .clickable { showPicker = true }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(time.toString(), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(Res.string.budget_form_change_label), style = MaterialTheme.typography.labelMedium)
    }

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(Res.string.budget_form_notification_time_dialog_title)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime(state.hour, state.minute))
                    showPicker = false
                }) { Text(stringResource(Res.string.budget_form_ok_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.budget_form_button_cancel)) }
            },
        )
    }
}
