package com.seebudget.app.presentation.checkin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.AmountField
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.BrutalCard
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.CheckInItemUiState
import com.seebudget.app.presentation.viewmodel.CheckInUiState
import com.seebudget.app.presentation.viewmodel.CheckInViewModel
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-10 — Check-in diario (documento, sección 8.4 #7): checklist de los
 * `PlannedItem` que corresponden a [date], con su monto editable "solo
 * para ese día", más los gastos adicionales ya registrados. Toda la
 * lógica de qué ítems corresponden, confirmar/desmarcar y editar montos
 * vive en `CheckInViewModel` — ver su KDoc para el detalle de cada regla
 * (esta pantalla solo renderiza ese estado y reenvía sus acciones).
 *
 * **Sin botón "Guardar"/"Confirmar día" propio** (a diferencia del mock
 * del documento): cada acción de esta pantalla (tocar el checkbox,
 * editar un monto) ya persiste de inmediato — mismo criterio reactivo
 * que el resto de la app (ej. `BudgetFormViewModel.addPlannedItem` en
 * modo editar). "← Volver" cumple el rol de cerrar la revisión del día;
 * no hay nada pendiente de guardar en batch.
 *
 * **"+ Agregar gasto"/editar un gasto adicional**: reutiliza
 * `ExpenseFormScreen` tal cual (ver `ExpenseFormViewModel.prefillForCheckIn`
 * y `App.kt`) en vez de duplicar un formulario acá — mismo criterio que
 * "Registrar/Editar gasto" ya reusado desde Inicio/Gastos.
 */
@Composable
fun CheckInScreen(
    budgetId: String,
    date: LocalDate,
    onBack: () -> Unit,
    onAddExpense: (budgetId: String, date: LocalDate) -> Unit,
    onEditExpense: (expenseId: String) -> Unit,
    viewModel: CheckInViewModel = koinViewModel(),
) {
    LaunchedEffect(budgetId, date) { viewModel.load(budgetId, date) }
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalSeeBudgetColors.current
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(Res.string.checkin_back_button)) }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.checkin_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        when (val state = uiState) {
            CheckInUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            CheckInUiState.NotFound -> Text(
                stringResource(Res.string.checkin_budget_not_found),
                style = MaterialTheme.typography.bodyLarge,
            )

            is CheckInUiState.Loaded -> LoadedCheckIn(
                state = state,
                today = today,
                onPreviousDay = viewModel::previousDay,
                onNextDay = viewModel::nextDay,
                onToggle = { item -> if (item.isConfirmed) viewModel.unconfirm(item) else viewModel.confirm(item) },
                onAmountChange = viewModel::onAmountChange,
                onAddExpense = { onAddExpense(budgetId, state.date) },
                onEditExpense = onEditExpense,
            )
        }
    }
}

@Composable
private fun LoadedCheckIn(
    state: CheckInUiState.Loaded,
    today: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToggle: (CheckInItemUiState) -> Unit,
    onAmountChange: (CheckInItemUiState, Long) -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        DateNavigationRow(date = state.date, today = today, onPrevious = onPreviousDay, onNext = onNextDay)
        Spacer(Modifier.height(16.dp))

        if (state.totalCount > 0) {
            Text(
                stringResource(Res.string.checkin_confirmed_count_label, state.confirmedCount, state.totalCount),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(12.dp))
            state.items.forEach { item ->
                CheckInItemRow(
                    item = item,
                    onToggle = { onToggle(item) },
                    onAmountChange = { amount -> onAmountChange(item, amount) },
                )
                Spacer(Modifier.height(8.dp))
            }
        } else {
            Text(
                stringResource(Res.string.checkin_no_items),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(24.dp))

        AdditionalExpensesSection(
            expenses = state.additionalExpenses,
            onEditExpense = onEditExpense,
            onAddExpense = onAddExpense,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DateNavigationRow(date: LocalDate, today: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious) { Text(stringResource(Res.string.checkin_previous_day_button)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dayLabelFull(date), style = MaterialTheme.typography.bodyLarge)
            if (date == today) {
                Text(stringResource(Res.string.checkin_today_label), style = MaterialTheme.typography.labelSmall, color = LocalSeeBudgetColors.current.accentAction)
            }
        }
        TextButton(onClick = onNext) { Text(stringResource(Res.string.checkin_next_day_button)) }
    }
}

@Composable
private fun CheckInItemRow(item: CheckInItemUiState, onToggle: () -> Unit, onAmountChange: (Long) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    val plannedItem = item.plannedItem

    BrutalCard(modifier = Modifier.fillMaxWidth(), backgroundColor = colors.surface) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SquareCheckbox(checked = item.isConfirmed, enabled = item.canConfirm, onCheckedChange = onToggle)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plannedItem.name.ifBlank { plannedItemTypeLabel(plannedItem.type) },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(
                        Res.string.checkin_expected_amount_label,
                        "${MoneyFormat.toDecimalString(plannedItem.amount, plannedItem.currency)} ${plannedItem.currency}",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                // Fase 7 — ver KDoc de CheckInViewModel.canConfirm: ítems de
                // antes de Fase 7 todavía sin categoría no se pueden confirmar.
                if (!item.canConfirm) {
                    Text(
                        stringResource(Res.string.checkin_missing_category_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accentError,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            CheckInAmountField(
                amountMinorUnits = item.editedAmount,
                currency = plannedItem.currency,
                differsFromBase = item.differsFromBase,
                onAmountChange = onAmountChange,
            )
        }
    }
}

/** "Checkbox grande y cuadrado (no el checkbox estándar)" — guía de diseño, sección 3, pantalla 7. */
@Composable
private fun SquareCheckbox(checked: Boolean, enabled: Boolean, onCheckedChange: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .border(BorderStroke(3.dp, colors.textAndBorder))
            .background(if (checked) colors.accentAction else colors.surface)
            .then(if (enabled) Modifier.clickable(onClick = onCheckedChange) else Modifier)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", style = MaterialTheme.typography.titleMedium, color = colors.textAndBorder)
    }
}

@Composable
private fun CheckInAmountField(
    amountMinorUnits: Long,
    currency: String,
    differsFromBase: Boolean,
    onAmountChange: (Long) -> Unit,
) {
    Column(horizontalAlignment = Alignment.End) {
        AmountField(
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            onAmountChange = onAmountChange,
            modifier = Modifier.width(110.dp),
        )
        // Indicador "si difiere del valor base" que pide el documento.
        if (differsFromBase) {
            Text(stringResource(Res.string.checkin_differs_from_base_label), style = MaterialTheme.typography.labelSmall, color = LocalSeeBudgetColors.current.accentAction)
        }
    }
}

@Composable
private fun AdditionalExpensesSection(
    expenses: List<Expense>,
    onEditExpense: (String) -> Unit,
    onAddExpense: () -> Unit,
) {
    Text(stringResource(Res.string.checkin_additional_expenses_label), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    if (expenses.isEmpty()) {
        Text(
            stringResource(Res.string.checkin_no_additional_expenses),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
    } else {
        expenses.forEach { expense ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditExpense(expense.id) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(expense.note?.ifBlank { null } ?: stringResource(Res.string.checkin_additional_expense_default_label), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${MoneyFormat.toDecimalString(expense.amount, expense.currency)} ${expense.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    BrutalButton(onClick = onAddExpense, backgroundColor = LocalSeeBudgetColors.current.surface) {
        Text(stringResource(Res.string.checkin_add_expense_button))
    }
}

@Composable
private fun plannedItemTypeLabel(type: PlannedItemType): String = when (type) {
    PlannedItemType.INCOME -> stringResource(Res.string.checkin_type_income)
    PlannedItemType.EXPENSE -> stringResource(Res.string.checkin_type_expense)
}

/** Formato largo en español ("1 de octubre de 2026") — mismo criterio que en ProjectionHistoryScreen/BudgetDashboardScreen. */
@Composable
private fun dayLabelFull(date: LocalDate): String {
    val monthName = stringResource(CHECKIN_MONTH_KEYS[date.monthNumber - 1])
    return stringResource(Res.string.checkin_date_full_format, date.dayOfMonth, monthName, date.year)
}

/**
 * RNF-07 (i18n, agregado en el chat): reemplaza el SPANISH_MONTHS_FULL
 * hardcodeado — ver strings.xml (checkin_month_1..12). El orden día/mes/año
 * cambia según idioma (ver checkin_date_full_format en values-en).
 */
private val CHECKIN_MONTH_KEYS = listOf(
    Res.string.checkin_month_1, Res.string.checkin_month_2, Res.string.checkin_month_3,
    Res.string.checkin_month_4, Res.string.checkin_month_5, Res.string.checkin_month_6,
    Res.string.checkin_month_7, Res.string.checkin_month_8, Res.string.checkin_month_9,
    Res.string.checkin_month_10, Res.string.checkin_month_11, Res.string.checkin_month_12,
)
