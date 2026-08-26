package com.seebudget.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seebudget.app.domain.model.Category
import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItemType
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.BrutalCard
import com.seebudget.app.presentation.components.CategoryIconBadge
import com.seebudget.app.presentation.components.TrendLineChart
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.HomeBudgetSection
import com.seebudget.app.presentation.viewmodel.HomeUiState
import com.seebudget.app.presentation.viewmodel.HomeViewModel
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pantalla 1 — Inicio (sección 8.4 #1). Ver KDoc de `HomeViewModel` para
 * las decisiones de alcance tomadas en el chat (mini-gráfico de una sola
 * línea, sin banner de "sugerí un presupuesto", acceso a notificaciones
 * del header apunta directo a Ajustes).
 *
 * `onOpenCheckIn`/`onOpenBudgetDashboard` solo se usan cuando hay
 * presupuesto activo (`state.budgetSection != null`) — mismo criterio de
 * navegación condicional que el resto de la app.
 */
@Composable
fun HomeScreen(
    onAddExpense: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExpenseList: () -> Unit,
    onOpenCheckIn: (budgetId: String, date: LocalDate) -> Unit,
    onOpenBudgetDashboard: (budgetId: String) -> Unit,
    onEditExpense: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalSeeBudgetColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        HomeHeader(onOpenSettings = onOpenSettings)
        Spacer(Modifier.height(20.dp))

        when (val state = uiState) {
            HomeUiState.Loading -> Text(stringResource(Res.string.home_loading), style = MaterialTheme.typography.bodyMedium)
            is HomeUiState.Loaded -> HomeContent(
                state = state,
                onAddExpense = onAddExpense,
                onOpenExpenseList = onOpenExpenseList,
                onOpenCheckIn = onOpenCheckIn,
                onOpenBudgetDashboard = onOpenBudgetDashboard,
                onEditExpense = onEditExpense,
            )
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(greetingFor(now.hour), style = MaterialTheme.typography.headlineMedium)
            Text(dayLabelFull(now.date), style = MaterialTheme.typography.bodySmall)
        }
        // "Acceso a Ajustes/notificaciones" del header (documento) — no hay
        // pantalla de notificaciones propia, ver KDoc de HomeViewModel.
        TextButton(onClick = onOpenSettings) { Text(stringResource(Res.string.home_settings_button)) }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Loaded,
    onAddExpense: () -> Unit,
    onOpenExpenseList: () -> Unit,
    onOpenCheckIn: (budgetId: String, date: LocalDate) -> Unit,
    onOpenBudgetDashboard: (budgetId: String) -> Unit,
    onEditExpense: (String) -> Unit,
) {
    val colors = LocalSeeBudgetColors.current

    BrutalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.home_spent_today_label), style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${MoneyFormat.toDecimalString(state.todayExpenseTotal, state.baseCurrency)} ${state.baseCurrency}",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.home_spent_month_label), style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${MoneyFormat.toDecimalString(state.monthExpenseTotal, state.baseCurrency)} ${state.baseCurrency}",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    BrutalButton(onClick = onAddExpense, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.home_add_expense_button))
    }
    Spacer(Modifier.height(24.dp))

    val budgetSection = state.budgetSection
    if (budgetSection != null) {
        BudgetSectionCards(
            section = budgetSection,
            today = state.today,
            onOpenCheckIn = onOpenCheckIn,
            onOpenBudgetDashboard = onOpenBudgetDashboard,
        )
        Spacer(Modifier.height(24.dp))
    }
    // Sin presupuesto activo: a pedido explícito del usuario en el chat,
    // esta sección queda vacía por ahora (sin banner sugiriendo crear uno).

    Text(stringResource(Res.string.home_recent_expenses_label), style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    if (state.recentExpenses.isEmpty()) {
        Text(stringResource(Res.string.home_no_expenses_empty), style = MaterialTheme.typography.bodyMedium)
    } else {
        state.recentExpenses.forEach { expense ->
            RecentExpenseRow(
                expense = expense,
                category = state.categoriesById[expense.categoryId],
                onClick = { onEditExpense(expense.id) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onOpenExpenseList) { Text(stringResource(Res.string.home_view_all_expenses_button)) }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun BudgetSectionCards(
    section: HomeBudgetSection,
    today: LocalDate,
    onOpenCheckIn: (budgetId: String, date: LocalDate) -> Unit,
    onOpenBudgetDashboard: (budgetId: String) -> Unit,
) {
    val colors = LocalSeeBudgetColors.current
    val budget = section.budget

    BrutalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenCheckIn(budget.id, today) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.home_checkin_today_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${section.checkInConfirmedCount}/${section.checkInTotalCount}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.accentAction,
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    BrutalCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenBudgetDashboard(budget.id) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(budget.name, style = MaterialTheme.typography.labelMedium)
            Text(
                text = stringResource(
                    Res.string.home_current_balance_label,
                    "${MoneyFormat.toDecimalString(section.todayBalance, budget.baseCurrency)} ${budget.baseCurrency}",
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = breakEvenLabel(section.breakEvenDate),
                style = MaterialTheme.typography.bodySmall,
                color = if (section.breakEvenDate != null) colors.accentError else colors.accentPositive,
            )
            Spacer(Modifier.height(8.dp))
            if (section.miniChartWindow.isNotEmpty()) {
                TrendLineChart(
                    entries = section.miniChartWindow.map { day ->
                        dayLabel(day.date) to minorUnitsToFloat(day.balance, budget.baseCurrency)
                    },
                    lineColor = colors.accentAction,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentExpenseRow(expense: Expense, category: Category?, onClick: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    val isIncome = expense.type == PlannedItemType.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(
                name = category?.name ?: "?",
                colorHex = category?.color ?: "#141410",
                size = 28.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(category?.name ?: stringResource(Res.string.home_no_category_label), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = (if (isIncome) "+" else "") + MoneyFormat.toDecimalString(expense.amount, expense.currency),
            style = MaterialTheme.typography.labelMedium,
            color = if (isIncome) colors.accentPositive else androidx.compose.ui.graphics.Color.Unspecified,
        )
    }
}

@Composable
private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> stringResource(Res.string.home_greeting_morning)
    in 12..18 -> stringResource(Res.string.home_greeting_afternoon)
    else -> stringResource(Res.string.home_greeting_evening)
}

@Composable
private fun breakEvenLabel(breakEvenDate: LocalDate?): String =
    if (breakEvenDate == null) {
        stringResource(Res.string.home_break_even_sufficient)
    } else {
        stringResource(Res.string.home_break_even_date, dayLabelFull(breakEvenDate))
    }

/** Mismo formato compacto que usa BudgetDashboardScreen para el eje X del gráfico — no se comparte, duplicado a propósito (ver convención del proyecto). */
private fun dayLabel(date: LocalDate): String = "${date.dayOfMonth}/${date.monthNumber}"

@Composable
private fun dayLabelFull(date: LocalDate): String {
    val monthName = stringResource(HOME_MONTH_KEYS[date.monthNumber - 1])
    return stringResource(Res.string.home_date_full_format, date.dayOfMonth, monthName, date.year)
}

/**
 * RNF-07 (i18n, agregado en el chat): reemplaza el SPANISH_MONTHS_FULL
 * hardcodeado — ver strings.xml (home_month_1..12). El orden día/mes/año
 * cambia según idioma (ver home_date_full_format en values-en).
 */
private val HOME_MONTH_KEYS = listOf(
    Res.string.home_month_1, Res.string.home_month_2, Res.string.home_month_3,
    Res.string.home_month_4, Res.string.home_month_5, Res.string.home_month_6,
    Res.string.home_month_7, Res.string.home_month_8, Res.string.home_month_9,
    Res.string.home_month_10, Res.string.home_month_11, Res.string.home_month_12,
)

/** Ver KDoc de BudgetDashboardScreen.minorUnitsToFloat — misma conversión, duplicada a propósito. */
private fun minorUnitsToFloat(minorUnits: Long, currency: String): Float {
    val digits = MoneyFormat.minorUnitDigits(currency)
    var divisor = 1.0
    repeat(digits) { divisor *= 10.0 }
    return (minorUnits / divisor).toFloat()
}
