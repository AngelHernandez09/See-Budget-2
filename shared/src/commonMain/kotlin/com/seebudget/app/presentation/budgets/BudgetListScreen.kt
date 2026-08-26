package com.seebudget.app.presentation.budgets

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
import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.generated.resources.*
import com.seebudget.app.presentation.components.BrutalButton
import com.seebudget.app.presentation.components.BrutalCard
import com.seebudget.app.presentation.theme.LocalSeeBudgetColors
import com.seebudget.app.presentation.viewmodel.BudgetListViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * RF-08 — "Presupuestos — Lista" (sección 8.4 #5): card del ACTIVE
 * destacada, secciones "Pausados"/"Completados", botón "Crear nuevo
 * presupuesto", estado vacío con CTA.
 *
 * Tocar cualquier card navega al Dashboard del presupuesto (`onOpenBudget`
 * — pantalla #6 del documento, con el gráfico de dos líneas, ver
 * `BudgetDashboardScreen`; Fase 6). Desde ahí se accede a "Ajustes del
 * presupuesto" (#10 — `BudgetFormScreen` en modo editar, con el botón
 * Activar y la zona de peligro Completar/Eliminar).
 */
@Composable
fun BudgetListScreen(
    onCreateBudget: () -> Unit,
    onOpenBudget: (String) -> Unit,
    viewModel: BudgetListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalSeeBudgetColors.current

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.budget_list_title), style = MaterialTheme.typography.headlineMedium)
            BrutalButton(onClick = onCreateBudget) { Text(stringResource(Res.string.budget_list_button_new)) }
        }

        if (uiState.isEmpty) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                Text(stringResource(Res.string.budget_list_empty_message), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                BrutalButton(onClick = onCreateBudget) { Text(stringResource(Res.string.budget_list_button_create_first)) }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                uiState.active?.let { active ->
                    ActiveBudgetCard(budget = active, onClick = { onOpenBudget(active.id) })
                    Spacer(Modifier.height(24.dp))
                }

                if (uiState.paused.isNotEmpty()) {
                    Text(stringResource(Res.string.budget_list_paused_section_label), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    uiState.paused.forEach { budget ->
                        PausedBudgetRow(
                            budget = budget,
                            onClick = { onOpenBudget(budget.id) },
                            onActivate = { viewModel.activate(budget.id) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (uiState.completed.isNotEmpty()) {
                    Text(stringResource(Res.string.budget_list_completed_section_label), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    uiState.completed.forEach { budget ->
                        CompletedBudgetRow(budget = budget, onClick = { onOpenBudget(budget.id) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ActiveBudgetCard(budget: Budget, onClick: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    BrutalCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = colors.accentPositive,
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.budget_list_active_badge), style = MaterialTheme.typography.labelMedium, color = colors.background)
            Spacer(Modifier.height(4.dp))
            Text(budget.name, style = MaterialTheme.typography.headlineSmall, color = colors.background)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    Res.string.budget_list_initial_balance_label,
                    MoneyFormat.toDecimalString(budget.initialBalance, budget.baseCurrency),
                    budget.baseCurrency,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.background,
            )
            Text(
                text = budgetDateRangeLabel(budget),
                style = MaterialTheme.typography.bodySmall,
                color = colors.background,
            )
        }
    }
}

@Composable
private fun PausedBudgetRow(budget: Budget, onClick: () -> Unit, onActivate: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(budget.name, style = MaterialTheme.typography.bodyLarge)
            Text(budgetDateRangeLabel(budget), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onActivate) { Text(stringResource(Res.string.budget_list_button_activate)) }
        TextButton(onClick = onClick) { Text(stringResource(Res.string.budget_list_button_view)) }
    }
}

@Composable
private fun CompletedBudgetRow(budget: Budget, onClick: () -> Unit) {
    val colors = LocalSeeBudgetColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, colors.textAndBorder))
            .padding(12.dp)
            .then(Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(budget.name, style = MaterialTheme.typography.bodyLarge)
            Text(budgetDateRangeLabel(budget), style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onClick) { Text(stringResource(Res.string.budget_list_button_view)) }
    }
}

@Composable
private fun budgetDateRangeLabel(budget: Budget): String {
    val end = budget.endDate
    return if (end == null) {
        stringResource(Res.string.budget_list_date_range_no_end, budget.startDate.toString())
    } else {
        stringResource(Res.string.budget_list_date_range, budget.startDate.toString(), end.toString())
    }
}
