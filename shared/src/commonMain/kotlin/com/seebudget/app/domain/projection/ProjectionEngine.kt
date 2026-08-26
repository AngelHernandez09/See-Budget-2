package com.seebudget.app.domain.projection

import com.seebudget.app.domain.model.PlannedItemFrequency
import com.seebudget.app.domain.model.PlannedItemType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * RF-09 — Motor de simulación día a día del presupuesto ("nunca por
 * promedios", ver documento). Vive en `commonMain`, sin dependencias de
 * plataforma ni de red — recibe todo ya resuelto (montos ya convertidos a
 * la moneda base, gastos reales ya agregados por día) y hace pura
 * aritmética de fechas/saldo, para poder testearlo exhaustivamente
 * (RNF-06) sin mockear nada.
 *
 * **Diseño de las dos líneas del gráfico (sección 8 del documento,
 * pantalla 6):** ambas se calculan llamando a [project] — no son dos
 * motores distintos, son dos INVOCACIONES con distintos parámetros:
 * - **Línea de referencia** (`ProjectionSnapshot` congelado): se llama
 *   con `today = null`. Todo el rango (incluso días ya pasados) se
 *   calcula desde la plantilla congelada — es intencional: es "lo que se
 *   proyectaba en ese momento", nunca debe mezclarse con datos reales
 *   posteriores, si no dejaría de servir como referencia fija para
 *   comparar contra la línea real.
 * - **Línea de estado real** (dinámica): se llama con `today` = hoy y
 *   `actualDeltasByDate` = gastos reales ya registrados. Días `<= today`
 *   usan esos datos reales; días `> today` usan la plantilla VIGENTE
 *   (los `PlannedItem` activos actuales, no los congelados) — así
 *   "recalculada con cada cambio" como pide el documento. `today` cuenta
 *   como pasado (decisión tomada en el chat: usa gastos reales, no la
 *   plantilla).
 *
 * **Conversión de moneda:** ver `ExchangeRateRepositoryImpl` — no hay
 * tasa "futura", así que toda conversión de la plantilla usa la tasa
 * vigente HOY, una sola vez por ítem (no por ocurrencia). Por eso acá
 * [ProjectionTemplateItem.amount] y las entradas de `actualDeltasByDate`
 * ya vienen convertidos a la moneda base del presupuesto — este motor no
 * sabe nada de monedas ni de tasas de cambio, esa conversión es
 * responsabilidad de quien arma los parámetros (capa ViewModel/UseCase).
 *
 * **Horizonte sin `endDate`:** el documento permite presupuestos "sin
 * límite" (`endDate == null`, "muestra hasta qué día alcanza el saldo").
 * Simular sin ningún límite es imposible en la práctica, así que se
 * acota a [horizonDays] desde `today` (o desde `startDate` si `today` es
 * null, caso de la línea de referencia) — decisión de implementación, no
 * de negocio: si el saldo no se agota dentro de ese horizonte,
 * [ProjectionResult.breakEvenDate] queda `null` ("no se agota dentro del
 * horizonte analizado", a criterio de la UI cómo comunicarlo).
 *
 * **`ProjectionDay.incomeDelta`/`expenseDelta`** (agregado para RF-09 —
 * "gasto total por mes/año" del Dashboard): además del `delta` neto, cada
 * día expone por separado cuánto de ese delta es ingreso y cuánto es
 * egreso, para que la capa ViewModel pueda sumar "gasto total" sin
 * reimplementar la lógica de ocurrencias (`occursOn`, que desde Fase 7
 * delega en `PlannedItemOccurrence`) por fuera de este motor. Para días
 * `isProjected == false` (reales): `actualDeltasByDate` ya viene con signo
 * (Fase 7 — `Expense.type` cerró el gap de RF-01 "Expense siempre es un
 * egreso"; quien arma el mapa, ver `BudgetDashboardViewModel`, resta para
 * EXPENSE y suma para INCOME) — acá simplemente se separa en
 * `maxOf(0, actual)` / `maxOf(0, -actual)`.
 */
object ProjectionEngine {

    /** Ver KDoc de la clase — horizonte de simulación cuando `endDate == null`. */
    const val DEFAULT_HORIZON_DAYS: Int = 365

    fun project(
        startDate: LocalDate,
        endDate: LocalDate?,
        initialBalance: Long,
        template: List<ProjectionTemplateItem>,
        today: LocalDate? = null,
        actualDeltasByDate: Map<LocalDate, Long> = emptyMap(),
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
    ): ProjectionResult {
        require(horizonDays > 0) { "horizonDays debe ser positivo" }

        val horizonAnchor = today ?: startDate
        val lastDate = endDate ?: horizonAnchor.plus(horizonDays, DateTimeUnit.DAY)
        if (lastDate < startDate) {
            // Rango inválido/vacío (ej. endDate anterior a startDate) — no
            // debería llegar hasta acá desde la UI, pero no reventamos: se
            // devuelve una proyección vacía en vez de lanzar.
            return ProjectionResult(days = emptyList(), breakEvenDate = null)
        }

        val days = ArrayList<ProjectionDay>(1)
        var balance = initialBalance
        // El saldo ya puede nacer agotado (ej. initialBalance <= 0 desde el
        // día 0, antes de simular ningún día) — se marca aparte de la
        // pasada día a día de más abajo.
        var breakEvenDate: LocalDate? = if (initialBalance <= 0L) startDate else null

        var date = startDate
        while (date <= lastDate) {
            val isProjected = today == null || date > today
            val (delta, incomeDelta, expenseDelta) = if (isProjected) {
                var income = 0L
                var expense = 0L
                for (item in template) {
                    if (!occursOn(item, date)) continue
                    when (item.type) {
                        PlannedItemType.INCOME -> income += item.amount
                        PlannedItemType.EXPENSE -> expense += item.amount
                    }
                }
                Triple(income - expense, income, expense)
            } else {
                val actual = actualDeltasByDate[date] ?: 0L
                Triple(actual, maxOf(0L, actual), maxOf(0L, -actual))
            }
            balance += delta
            if (breakEvenDate == null && balance <= 0L) breakEvenDate = date
            days += ProjectionDay(
                date = date,
                delta = delta,
                incomeDelta = incomeDelta,
                expenseDelta = expenseDelta,
                balance = balance,
                isProjected = isProjected,
            )
            date = date.plus(1, DateTimeUnit.DAY)
        }

        return ProjectionResult(days = days, breakEvenDate = breakEvenDate)
    }

    /** Fase 7: delega en `PlannedItemOccurrence` — ver KDoc ahí (misma regla, ahora compartida con el Check-in diario, RF-10). */
    private fun occursOn(item: ProjectionTemplateItem, date: LocalDate): Boolean =
        PlannedItemOccurrence.occursOn(
            frequency = item.frequency,
            billingDay = item.billingDay,
            specificDate = item.specificDate,
            dayOfWeek = item.dayOfWeek,
            date = date,
        )
}

/**
 * Copia currency-agnostic de un ítem planificado, ya lista para simular:
 * [amount] ya está convertido a la moneda base del presupuesto (ver KDoc
 * de [ProjectionEngine] sobre por qué la conversión pasa por afuera).
 * Quien arma esta lista (ViewModel/UseCase) es responsable de filtrar
 * `isActive == true` antes de llamar a `project()` — este motor no
 * vuelve a chequearlo.
 */
data class ProjectionTemplateItem(
    val plannedItemId: String,
    val type: PlannedItemType,
    val amount: Long,
    val frequency: PlannedItemFrequency,
    val billingDay: Int? = null,
    val specificDate: LocalDate? = null,
    val dayOfWeek: DayOfWeek? = null,
)

/**
 * Un día simulado: su delta neto (+ingresos/-egresos) y el saldo
 * acumulado al final de ese día. [incomeDelta]/[expenseDelta] son el
 * desglose sin signo del mismo delta (`delta == incomeDelta -
 * expenseDelta`) — ver KDoc de [ProjectionEngine] sobre por qué existen.
 */
data class ProjectionDay(
    val date: LocalDate,
    val delta: Long,
    val incomeDelta: Long,
    val expenseDelta: Long,
    val balance: Long,
    val isProjected: Boolean,
)

/**
 * [breakEvenDate]: primer día en que `balance <= 0` ("la fecha exacta en
 * que se agota", RF-09). `null` = el saldo no se agota dentro del rango
 * simulado (`endDate`, o el horizonte por defecto si el presupuesto no
 * tiene límite — ver KDoc de [ProjectionEngine]).
 */
data class ProjectionResult(
    val days: List<ProjectionDay>,
    val breakEvenDate: LocalDate?,
)
