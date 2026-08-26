package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.Expense
import com.seebudget.app.domain.model.PlannedItem
import kotlinx.datetime.LocalDate

/**
 * RF-10 — Check-in diario: arma, para un día puntual, la lista de
 * `PlannedItem` que corresponden a ese día (misma regla de ocurrencia
 * que usa el Dashboard para proyectar, ver [PlannedItemOccurrence] — "qué
 * corresponde hoy" en el Check-in nunca debe divergir de "qué se
 * proyecta hoy"), cada uno emparejado con el `Expense` ya confirmado ese
 * día si existe, más los "gastos adicionales" del día (los que no están
 * vinculados a ningún `PlannedItem`, `plannedItemId == null`).
 *
 * Puro y currency-agnostic — no convierte monedas ni calcula totales
 * (eso es responsabilidad de la capa ViewModel, mismo criterio que
 * [ProjectionEngine], ver su KDoc). Tampoco filtra por `isActive`:
 * mismo criterio que [ProjectionEngine.project] con `template` — quien
 * arma [activePlannedItems] (ViewModel/UseCase) es responsable de
 * filtrar `isActive == true` antes de llamar acá, este objeto no vuelve
 * a chequearlo.
 *
 * Confirmado en el chat (Fase 7): los checkboxes de ítems planificados
 * arrancan SIN marcar por defecto — no se asume que un ítem planificado
 * ocurrió solo porque estaba programado. Acá eso se traduce en
 * [CheckInItem.isConfirmed], que es `true` únicamente si ya existe un
 * `Expense` confirmado (`confirmedExpense != null`) para ese
 * `plannedItemId` ese día — nunca por defecto.
 */
object CheckInDayBuilder {
    fun build(
        date: LocalDate,
        activePlannedItems: List<PlannedItem>,
        expensesForDay: List<Expense>,
    ): CheckInDay {
        val occurringItems = activePlannedItems.filter { item ->
            PlannedItemOccurrence.occursOn(
                frequency = item.frequency,
                billingDay = item.billingDay,
                specificDate = item.specificDate,
                dayOfWeek = item.dayOfWeek,
                date = date,
            )
        }

        // Primer Expense confirmado que matchee cada plannedItemId. En el
        // flujo normal hay a lo sumo uno (el Check-in solo crea uno por
        // ítem por día), pero no se garantiza acá — si llegara a haber más
        // de uno, se toma el primero según el orden de expensesForDay (ver
        // ExpenseRepository.observeByBudgetAndDate, ordenado por
        // createdAt ASC).
        val expenseByPlannedItemId = HashMap<String, Expense>()
        for (expense in expensesForDay) {
            val plannedItemId = expense.plannedItemId ?: continue
            if (!expenseByPlannedItemId.containsKey(plannedItemId)) {
                expenseByPlannedItemId[plannedItemId] = expense
            }
        }

        val items = occurringItems.map { item ->
            CheckInItem(plannedItem = item, confirmedExpense = expenseByPlannedItemId[item.id])
        }

        val additionalExpenses = expensesForDay.filter { it.plannedItemId == null }

        return CheckInDay(date = date, items = items, additionalExpenses = additionalExpenses)
    }
}

/**
 * Resultado de armar el Check-in de un día: los ítems planificados que
 * corresponden ([items]) más los gastos adicionales ya registrados ese
 * día sin vincular a ningún `PlannedItem` ([additionalExpenses]).
 */
data class CheckInDay(
    val date: LocalDate,
    val items: List<CheckInItem>,
    val additionalExpenses: List<Expense>,
)

/**
 * Un `PlannedItem` que ocurre este día, emparejado con su `Expense`
 * confirmado si ya se hizo el check-in. [isConfirmed] es `true`
 * únicamente cuando [confirmedExpense] no es `null` — ver KDoc de
 * [CheckInDayBuilder] sobre por qué nunca arranca marcado por defecto.
 */
data class CheckInItem(
    val plannedItem: PlannedItem,
    val confirmedExpense: Expense?,
) {
    val isConfirmed: Boolean get() = confirmedExpense != null
}
