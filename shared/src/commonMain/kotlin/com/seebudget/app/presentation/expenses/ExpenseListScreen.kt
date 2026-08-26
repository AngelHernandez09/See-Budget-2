package com.seebudget.app.presentation.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.presentation.components.AmountField
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.BrutalCard
import com.seebudget.app.presentation.components.CategoryIconBadge
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.BudgetLinkFilter
import com.seebudget.app.presentation.viewmodel.ExpenseListViewModel
import com.seebudget.app.presentation.viewmodel.ExpenseSortOption
import com.seebudget.app.generated.resources.Res
import com.seebudget.app.generated.resources.expense_list_amount_hint
import com.seebudget.app.generated.resources.expense_list_budget_link_all
import com.seebudget.app.generated.resources.expense_list_budget_link_linked
import com.seebudget.app.generated.resources.expense_list_budget_link_unlinked
import com.seebudget.app.generated.resources.expense_list_budget_linked_badge
import com.seebudget.app.generated.resources.expense_list_button_add
import com.seebudget.app.generated.resources.expense_list_clear_filters_button
import com.seebudget.app.generated.resources.expense_list_date_cancel_button
import com.seebudget.app.generated.resources.expense_list_date_clear_button
import com.seebudget.app.generated.resources.expense_list_date_confirm_button
import com.seebudget.app.generated.resources.expense_list_date_today
import com.seebudget.app.generated.resources.expense_list_date_undefined
import com.seebudget.app.generated.resources.expense_list_date_yesterday
import com.seebudget.app.generated.resources.expense_list_empty_state
import com.seebudget.app.generated.resources.expense_list_filter_amount_label
import com.seebudget.app.generated.resources.expense_list_filter_amount_max
import com.seebudget.app.generated.resources.expense_list_filter_amount_min
import com.seebudget.app.generated.resources.expense_list_filter_budget_link_label
import com.seebudget.app.generated.resources.expense_list_filter_category_label
import com.seebudget.app.generated.resources.expense_list_filter_currency_label
import com.seebudget.app.generated.resources.expense_list_filter_date_from
import com.seebudget.app.generated.resources.expense_list_filter_date_range_label
import com.seebudget.app.generated.resources.expense_list_filter_date_to
import com.seebudget.app.generated.resources.expense_list_filters_button
import com.seebudget.app.generated.resources.expense_list_filters_button_with_count
import com.seebudget.app.generated.resources.expense_list_no_categories
import com.seebudget.app.generated.resources.expense_list_no_category_label
import com.seebudget.app.generated.resources.expense_list_no_currencies
import com.seebudget.app.generated.resources.expense_list_no_matches
import com.seebudget.app.generated.resources.expense_list_search_label
import com.seebudget.app.generated.resources.expense_list_sort_amount_asc
import com.seebudget.app.generated.resources.expense_list_sort_amount_desc
import com.seebudget.app.generated.resources.expense_list_sort_label
import com.seebudget.app.generated.resources.expense_list_sort_none
import com.seebudget.app.generated.resources.expense_list_summary_expenses
import com.seebudget.app.generated.resources.expense_list_summary_income
import com.seebudget.app.generated.resources.expense_list_summary_net
import com.seebudget.app.generated.resources.expense_list_title
import com.seebudget.app.generated.resources.expense_list_unconverted_amount_warning
import com.seebudget.app.generated.resources.expense_list_unconverted_total_warning
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-01 — Listado de gastos (sección 8.4 #3 del documento). Selector de
 * moneda ya resuelto en Fase 3 (ExpenseFormScreen); el total de este
 * listado también, ver ExpenseListViewModel. Indicador visual de vínculo
 * a presupuesto (chip, ver `BudgetLinkBadge` más abajo) agregado en el
 * chat una vez que budgetId ya podía ser distinto de null (presupuestos,
 * Fase 5+).
 *
 * **Filtros/búsqueda/orden (agregado en el chat — ver KDoc completo de
 * `ExpenseListViewModel` sobre las reglas de negocio):** búsqueda por
 * nota siempre visible, selector de orden por monto siempre visible (son
 * solo 3 opciones, no ocupan espacio significativo), y un panel de
 * filtros (categoría, rango de fechas, moneda, monto, vínculo a
 * presupuesto) que se puede desplegar/ocultar con el botón "Filtros" —
 * decisión tomada en el chat para no ocupar espacio permanente en mobile
 * con esos 5 controles. Cuando el orden por monto está activo, la lista
 * deja de agruparse por fecha y pasa a ser una lista plana (también
 * decidido en el chat) — ver el `if (sortOption == ExpenseSortOption.NONE)`
 * más abajo.
 *
 * El bloque Ingresos/Gastos/Neto (agregado en el chat) va envuelto en un
 * `BrutalCard` — mismo tratamiento visual que el resto de los resúmenes
 * numéricos destacados de la app (ej. `SpendSummaryCard` del Dashboard de
 * presupuesto).
 */
@Composable
fun ExpenseListScreen(
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    viewModel: ExpenseListViewModel = koinViewModel(),
) {
    val filtered by viewModel.filtered.collectAsState()
    val categoriesById by viewModel.categoriesById.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val total by viewModel.total.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val hasAnyExpenses by viewModel.hasAnyExpenses.collectAsState()
    val selectedCategoryIds by viewModel.selectedCategoryIds.collectAsState()
    val minAmountFilter by viewModel.minAmountFilter.collectAsState()
    val maxAmountFilter by viewModel.maxAmountFilter.collectAsState()
    val budgetLinkFilter by viewModel.budgetLinkFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val usedCurrencies by viewModel.usedCurrencies.collectAsState()
    val selectedCurrencies by viewModel.selectedCurrencies.collectAsState()
    val dateFromFilter by viewModel.dateFromFilter.collectAsState()
    val dateToFilter by viewModel.dateToFilter.collectAsState()
    val colors = LocalSeeBudgetColors.current
    var filtersExpanded by remember { mutableStateOf(false) }

    // Cantidad de controles del panel desplegable con un valor distinto
    // del neutro — para el badge del botón "Filtros" (no incluye
    // búsqueda ni orden, que ya son siempre visibles).
    val activePanelFilterCount = (if (selectedCategoryIds.isNotEmpty()) 1 else 0) +
        (if (minAmountFilter > 0L || maxAmountFilter > 0L) 1 else 0) +
        (if (budgetLinkFilter != BudgetLinkFilter.ALL) 1 else 0) +
        (if (selectedCurrencies.isNotEmpty()) 1 else 0) +
        (if (dateFromFilter != null || dateToFilter != null) 1 else 0)
    val hasAnyActiveRefinement = activePanelFilterCount > 0 || searchQuery.isNotBlank() || sortOption != ExpenseSortOption.NONE

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.expense_list_title), style = MaterialTheme.typography.headlineMedium)
            BrutalButton(onClick = onAddExpense) {
                Text(stringResource(Res.string.expense_list_button_add))
            }
        }

        if (!hasAnyExpenses) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.expense_list_empty_state),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val yesterday = today.minus(1, DateTimeUnit.DAY)

            // Un solo LazyColumn para TODA la pantalla (agregado en el chat):
            // antes, buscador/orden/filtros vivían en un Column normal
            // arriba de este LazyColumn — cuando el panel de filtros crecía
            // (categoría + monto + presupuesto), la suma de ambos superaba
            // el alto de pantalla y, como ese Column de arriba no scrollea,
            // lo que quedaba por debajo del borde de la pantalla se volvía
            // inalcanzable (cada hijo de un Column recibe el alto completo
            // como límite, no "lo que sobra" — Compose no reparte el
            // espacio salvo con `weight`). Con todo dentro de un único
            // LazyColumn, la pantalla entera scrollea como una unidad —
            // desplegar el panel solo empuja el resto más abajo, nunca lo
            // deja fuera de alcance. El header ("Gastos" + "+ Agregar")
            // arriba se queda fijo a propósito, fuera de este LazyColumn.
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        label = { Text(stringResource(Res.string.expense_list_search_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.expense_list_sort_label), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    SortOptionChips(selected = sortOption, onSelect = viewModel::setSortOption)
                    Spacer(Modifier.height(12.dp))
                    FiltersToggleRow(
                        expanded = filtersExpanded,
                        activeCount = activePanelFilterCount,
                        onToggle = { filtersExpanded = !filtersExpanded },
                    )
                    if (filtersExpanded) {
                        Spacer(Modifier.height(12.dp))
                        FilterPanel(
                            categories = categories,
                            selectedCategoryIds = selectedCategoryIds,
                            onToggleCategory = viewModel::toggleCategoryFilter,
                            dateFrom = dateFromFilter,
                            dateTo = dateToFilter,
                            onDateRangeChange = viewModel::setDateRangeFilter,
                            usedCurrencies = usedCurrencies,
                            selectedCurrencies = selectedCurrencies,
                            onToggleCurrency = viewModel::toggleCurrencyFilter,
                            minAmount = minAmountFilter,
                            maxAmount = maxAmountFilter,
                            baseCurrency = baseCurrency,
                            onAmountRangeChange = viewModel::setAmountRangeFilter,
                            budgetLinkFilter = budgetLinkFilter,
                            onBudgetLinkChange = viewModel::setBudgetLinkFilter,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (filtered.expenses.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                stringResource(Res.string.expense_list_no_matches),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (hasAnyActiveRefinement) {
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = viewModel::clearAllFilters) {
                                    Text(stringResource(Res.string.expense_list_clear_filters_button))
                                }
                            }
                        }
                    }
                } else {
                    item {
                        // `total` puede ser null por un instante mientras se
                        // resuelve la primera conversión (StateFlow arranca en
                        // null, ver ExpenseListViewModel) — no hay nada que
                        // mostrar todavía en vez de un total a medio calcular.
                        val currentTotal = total
                        if (currentTotal != null) {
                            // Envuelto en BrutalCard (agregado en el chat) —
                            // ver KDoc de la clase.
                            BrutalCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(
                                            Res.string.expense_list_summary_income,
                                            MoneyFormat.toDecimalString(currentTotal.incomeMinorUnits, currentTotal.currency),
                                            currentTotal.currency,
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.accentPositive,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(
                                            Res.string.expense_list_summary_expenses,
                                            MoneyFormat.toDecimalString(currentTotal.expenseMinorUnits, currentTotal.currency),
                                            currentTotal.currency,
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(
                                            Res.string.expense_list_summary_net,
                                            MoneyFormat.toDecimalString(currentTotal.netMinorUnits, currentTotal.currency),
                                            currentTotal.currency,
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    if (currentTotal.unconvertedCount > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(
                                                Res.string.expense_list_unconverted_total_warning,
                                                currentTotal.unconvertedCount,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accentError,
                                        )
                                    }
                                    if (filtered.unconvertedForAmountCount > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(
                                                Res.string.expense_list_unconverted_amount_warning,
                                                filtered.unconvertedForAmountCount,
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accentError,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (sortOption == ExpenseSortOption.NONE) {
                        val grouped = filtered.expenses.groupBy { it.date }.toSortedMap(compareByDescending { it })
                        grouped.forEach { (date, dayExpenses) ->
                            item {
                                Text(
                                    text = dateHeaderLabel(date, today, yesterday),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            items(dayExpenses, key = { it.id }) { expense ->
                                ExpenseRow(
                                    expense = expense,
                                    category = categoriesById[expense.categoryId],
                                    onClick = { onEditExpense(expense.id) },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    } else {
                        // Orden por monto activo: lista plana (sin headers de
                        // fecha) — ver KDoc de la clase sobre por qué.
                        items(filtered.expenses, key = { it.id }) { expense ->
                            ExpenseRow(
                                expense = expense,
                                category = categoriesById[expense.categoryId],
                                onClick = { onEditExpense(expense.id) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun dateHeaderLabel(date: LocalDate, today: LocalDate, yesterday: LocalDate): String = when (date) {
    today -> stringResource(Res.string.expense_list_date_today)
    yesterday -> stringResource(Res.string.expense_list_date_yesterday)
    // TODO: formatear localizado cuando exista User.locale wiring (Fase 3+).
    else -> date.toString()
}

/**
 * Botón "Filtros" que despliega/oculta [FilterPanel] — decisión del chat
 * de no dejar categoría/rango de fechas/moneda/monto/vínculo a
 * presupuesto siempre visibles (a diferencia de búsqueda y orden). El
 * badge numérico muestra cuántos de esos 5 controles tienen un valor
 * activo.
 */
@Composable
private fun FiltersToggleRow(expanded: Boolean, activeCount: Int, onToggle: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(
        modifier = Modifier
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .background(if (activeCount > 0) colors.accentAction else colors.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = if (activeCount > 0) {
            stringResource(Res.string.expense_list_filters_button_with_count, activeCount)
        } else {
            stringResource(Res.string.expense_list_filters_button)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FilterPanel(
    categories: List<Category>,
    selectedCategoryIds: Set<String>,
    onToggleCategory: (String) -> Unit,
    dateFrom: LocalDate?,
    dateTo: LocalDate?,
    onDateRangeChange: (LocalDate?, LocalDate?) -> Unit,
    usedCurrencies: List<String>,
    selectedCurrencies: Set<String>,
    onToggleCurrency: (String) -> Unit,
    minAmount: Long,
    maxAmount: Long,
    baseCurrency: String,
    onAmountRangeChange: (Long, Long) -> Unit,
    budgetLinkFilter: BudgetLinkFilter,
    onBudgetLinkChange: (BudgetLinkFilter) -> Unit,
) {
    val colors = LocalSeeBudgetColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .padding(16.dp),
    ) {
        Text(stringResource(Res.string.expense_list_filter_category_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (categories.isEmpty()) {
            Text(stringResource(Res.string.expense_list_no_categories), style = MaterialTheme.typography.bodySmall)
        } else {
            CategoryFilterChips(categories = categories, selectedIds = selectedCategoryIds, onToggle = onToggleCategory)
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.expense_list_filter_date_range_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OptionalDateFilterField(
                label = stringResource(Res.string.expense_list_filter_date_from),
                date = dateFrom,
                onDateChange = { newFrom -> onDateRangeChange(newFrom, dateTo) },
                modifier = Modifier.weight(1f),
            )
            OptionalDateFilterField(
                label = stringResource(Res.string.expense_list_filter_date_to),
                date = dateTo,
                onDateChange = { newTo -> onDateRangeChange(dateFrom, newTo) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.expense_list_filter_currency_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        if (usedCurrencies.isEmpty()) {
            Text(stringResource(Res.string.expense_list_no_currencies), style = MaterialTheme.typography.bodySmall)
        } else {
            CurrencyFilterChips(currencies = usedCurrencies, selected = selectedCurrencies, onToggle = onToggleCurrency)
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.expense_list_filter_amount_label, baseCurrency), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AmountField(
                amountMinorUnits = minAmount,
                currency = baseCurrency,
                onAmountChange = { newMin -> onAmountRangeChange(newMin, maxAmount) },
                label = { Text(stringResource(Res.string.expense_list_filter_amount_min)) },
                modifier = Modifier.weight(1f),
            )
            AmountField(
                amountMinorUnits = maxAmount,
                currency = baseCurrency,
                onAmountChange = { newMax -> onAmountRangeChange(minAmount, newMax) },
                label = { Text(stringResource(Res.string.expense_list_filter_amount_max)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.expense_list_amount_hint),
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.expense_list_filter_budget_link_label), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        BudgetLinkChips(selected = budgetLinkFilter, onSelect = onBudgetLinkChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterChips(categories: List<Category>, selectedIds: Set<String>, onToggle: (String) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            val isSelected = category.id in selectedIds
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .clickable { onToggle(category.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIconBadge(name = category.name, colorHex = category.color, size = 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(category.name, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyFilterChips(currencies: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        currencies.forEach { code ->
            val isSelected = code in selected
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .clickable { onToggle(code) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(code, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Campo de fecha OPCIONAL para el filtro de rango (a diferencia de
 * `DateField` en ExpenseFormScreen, que siempre tiene un valor) — mismo
 * mecanismo de `DatePickerDialog`, pero acá `date == null` es un estado
 * válido ("sin límite en ese extremo", mismo criterio que el filtro de
 * monto) y hay una forma de volver a él ("Limpiar").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionalDateFilterField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val colors = LocalSeeBudgetColors.current

    Column(
        modifier = modifier
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .clickable { showPicker = true }
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(date?.toString() ?: stringResource(Res.string.expense_list_date_undefined), style = MaterialTheme.typography.bodyMedium)
            if (date != null) {
                TextButton(onClick = { onDateChange(null) }) { Text(stringResource(Res.string.expense_list_date_clear_button)) }
            }
        }
    }

    if (showPicker) {
        val initialMillis = (date ?: Clock.System.todayIn(TimeZone.currentSystemDefault()))
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onDateChange(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                    }
                    showPicker = false
                }) { Text(stringResource(Res.string.expense_list_date_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.expense_list_date_cancel_button)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun BudgetLinkChips(selected: BudgetLinkFilter, onSelect: (BudgetLinkFilter) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BudgetLinkFilter.entries.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(budgetLinkFilterLabel(option), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun budgetLinkFilterLabel(filter: BudgetLinkFilter): String = when (filter) {
    BudgetLinkFilter.ALL -> stringResource(Res.string.expense_list_budget_link_all)
    BudgetLinkFilter.LINKED -> stringResource(Res.string.expense_list_budget_link_linked)
    BudgetLinkFilter.UNLINKED -> stringResource(Res.string.expense_list_budget_link_unlinked)
}

@Composable
private fun SortOptionChips(selected: ExpenseSortOption, onSelect: (ExpenseSortOption) -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseSortOption.entries.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .border(BorderStroke(if (isSelected) 3.dp else 2.dp, colors.textAndBorder))
                    .background(if (isSelected) colors.accentAction else colors.surface)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(sortOptionLabel(option), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun sortOptionLabel(option: ExpenseSortOption): String = when (option) {
    ExpenseSortOption.NONE -> stringResource(Res.string.expense_list_sort_none)
    ExpenseSortOption.AMOUNT_DESC -> stringResource(Res.string.expense_list_sort_amount_desc)
    ExpenseSortOption.AMOUNT_ASC -> stringResource(Res.string.expense_list_sort_amount_asc)
}

/**
 * Fila de gasto SIN BrutalCard/press effect a propósito — la guía de
 * diseño pide evitar ese componente en filas de listas largas por ruido
 * visual/rendimiento (sección 10.4); acá solo un borde simple.
 */
@Composable
private fun ExpenseRow(expense: Expense, category: Category?, onClick: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    val isIncome = expense.type == PlannedItemType.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(
                name = category?.name ?: "?",
                colorHex = category?.color ?: "#141410",
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(category?.name ?: stringResource(Res.string.expense_list_no_category_label), style = MaterialTheme.typography.bodyLarge)
                    // Indicador de vinculo a presupuesto (agregado en el chat): un
                    // gasto generado o asociado a un PlannedItem de un presupuesto
                    // tiene expense.budgetId != null (ver KDoc de Expense.budgetId).
                    // No se muestra CUAL presupuesto (el ViewModel no carga ese
                    // lookup, y el pedido fue solo "que esten vinculados", no un
                    // nombre) - mismo color accentAction que ya usa toda la
                    // seccion de Presupuestos (BudgetDashboardScreen/
                    // BudgetFormScreen) para que se lea como "esto es de
                    // presupuestos" de forma consistente.
                    if (expense.budgetId != null) {
                        Spacer(Modifier.width(6.dp))
                        BudgetLinkBadge()
                    }
                }
                val note = expense.note
                if (!note.isNullOrBlank()) {
                    Text(note, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        // Ingreso/Egreso (agregado en el chat junto con el selector de
        // ExpenseFormScreen): "+" y color accentPositive solo para
        // ingresos — un egreso se sigue mostrando igual que siempre (sin
        // signo "-" agregado, MoneyFormat.toDecimalString ya no antepone
        // nada a un monto positivo).
        Text(
            text = (if (isIncome) "+" else "") + MoneyFormat.toDecimalString(expense.amount, expense.currency),
            style = MaterialTheme.typography.labelMedium,
            color = if (isIncome) colors.accentPositive else Color.Unspecified,
        )
    }
}

/**
 * Chip chico "Vinculado" (Presupuesto) para ExpenseRow — sin depender de
 * un set de iconos (el proyecto no tiene uno definido todavia, ver KDoc
 * de CategoryIconBadge), consistente con el resto de la UI neobrutalista:
 * borde grueso, esquinas rectas, sin blur.
 */
@Composable
private fun BudgetLinkBadge() {
    val colors = LocalSeeBudgetColors.current
    Box(
        modifier = Modifier
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .background(colors.accentAction)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.expense_list_budget_linked_badge),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textAndBorder,
        )
    }
}
