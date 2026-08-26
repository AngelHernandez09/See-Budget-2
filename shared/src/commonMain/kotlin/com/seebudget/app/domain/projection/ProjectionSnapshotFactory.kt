package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.Budget
import com.seebudget.app.domain.model.MoneyFormat
import com.seebudget.app.domain.model.PlannedItem
import com.seebudget.app.domain.model.ProjectionSnapshotItem
import com.seebudget.app.domain.repository.ExchangeRateRepository
import kotlinx.datetime.LocalDate

/**
 * RF-09 — congela una lista de `PlannedItem` activos a la moneda base de
 * un presupuesto, en el formato que necesita `ProjectionSnapshot` (ver
 * KDoc de esa clase sobre por qué la conversión se hace UNA sola vez acá
 * y no se vuelve a tocar después).
 *
 * Extraído de `BudgetDashboardViewModel` (donde vivía como
 * `ensureInitialSnapshot`/`toFrozenSnapshotItem`) porque ahora
 * `BudgetFormViewModel` también necesita la misma lógica exacta para el
 * diálogo "¿corrección de error o cambio real?" (RF-09) y el botón
 * manual "Crear nueva línea de referencia desde hoy" — duplicar la
 * conversión de moneda en dos ViewModels distintos era el riesgo real
 * (dos implementaciones que podrían divergir con el tiempo), así que
 * queda acá, en `commonMain`, sin dependencias de plataforma.
 */
class ProjectionSnapshotFactory(
    private val exchangeRateRepository: ExchangeRateRepository,
) {
    /**
     * [today]: fecha a la que se convierte cada ítem (no hay tasa
     * "futura", ver `ExchangeRateRepositoryImpl`) — la pasa quien llama,
     * no la calcula acá, para que ViewModels con distinto `today` (no
     * debería pasar en la práctica, pero por las dudas) sean explícitos.
     */
    suspend fun freeze(
        budget: Budget,
        activePlannedItems: List<PlannedItem>,
        today: LocalDate,
    ): FrozenSnapshot {
        var unconverted = 0
        val items = activePlannedItems.mapNotNull { item ->
            val convertedAmount = convertAtRateOf(today, item.amount, item.currency, budget.baseCurrency)
            if (convertedAmount == null) {
                unconverted++
                null
            } else {
                ProjectionSnapshotItem(
                    plannedItemId = item.id,
                    name = item.name,
                    type = item.type,
                    amount = convertedAmount,
                    currency = budget.baseCurrency,
                    frequency = item.frequency,
                    billingDay = item.billingDay,
                    specificDate = item.specificDate,
                    dayOfWeek = item.dayOfWeek,
                    isActive = item.isActive,
                )
            }
        }
        return FrozenSnapshot(items = items, unconvertedCount = unconverted)
    }

    private suspend fun convertAtRateOf(date: LocalDate, amount: Long, from: String, to: String): Long? {
        val rate = exchangeRateRepository.getRate(date, from, to) ?: return null
        return MoneyFormat.convert(amount, from, to, rate)
    }
}

/** `null` (excluido) en vez de convertir 1:1 para los ítems sin tasa disponible — ver KDoc de la clase y de BudgetDashboardViewModel. */
data class FrozenSnapshot(
    val items: List<ProjectionSnapshotItem>,
    val unconvertedCount: Int,
)
