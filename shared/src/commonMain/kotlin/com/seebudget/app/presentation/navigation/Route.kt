package com.seebudget.app.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Rutas de navegación (Fase 9, agregado en el chat) — reemplazan el
 * `sealed interface Screen` + estado local (`var screen by remember`)
 * que tenía `App.kt`. Se migró a Navigation 2
 * (`org.jetbrains.androidx.navigation:navigation-compose`), no
 * Navigation 3 — decisión tomada en el chat: Navigation 3 todavía no
 * confirma soporte oficial en Wasm y el historial del navegador en Web
 * depende de una librería de terceros marcada como prueba de concepto,
 * riesgo real para 2 de las 5 plataformas de esta app. Ver
 * `libs.versions.toml` para el detalle completo.
 *
 * No hace falta anotar esta interfaz con `@Serializable`: solo los tipos
 * hoja (los `data object`/`data class` de abajo) viajan como argumento de
 * `navigate()`/`composable<T>()`, la interfaz es puramente organizativa
 * (agrupa las rutas para que se puedan documentar/leer juntas, y habilita
 * un `when` exhaustivo si hiciera falta) — no dispara serialización
 * polimórfica.
 *
 * **Fechas viajan como `String` ISO-8601** (`LocalDate.toString()`/
 * `LocalDate.parse(...)`), no como `kotlinx.datetime.LocalDate` directo:
 * las rutas type-safe de Navigation Compose infieren su `NavType`
 * automáticamente solo para los tipos que kotlinx.serialization resuelve
 * como primitivos (String/Int/Long/Boolean/Float, y variantes nullable);
 * un tipo custom como `LocalDate` exigiría escribir un `NavType` a mano y
 * registrarlo en cada `composable<Route>(typeMap = ...)`. Se prefirió
 * mantenerlo simple — cada pantalla sigue recibiendo un `LocalDate` real,
 * la conversión ocurre en el borde (`App.kt`, dentro de `MainShell`).
 *
 * **Ninguna ruta lleva un campo `returnTo`** (a diferencia del `Screen`
 * anterior): con un back stack real, "volver" tras guardar/cancelar/
 * borrar es simplemente `navController.popBackStack()` — se verificó
 * cada callsite del `Screen` anterior al migrar, y en *todos* los casos
 * la pantalla a la que había que volver coincidía exactamente con la
 * pantalla de origen real. Las únicas 2 excepciones, donde el destino
 * debe ser siempre el mismo sin importar el origen, se resuelven con un
 * `navigate()` explícito en `App.kt`, no acá:
 * - El botón "atrás" del Check-in (pantalla 7): siempre vuelve al
 *   Dashboard del presupuesto, sin importar si se llegó desde Inicio, el
 *   propio Dashboard, o la notificación (deep link).
 * - Borrar un presupuesto desde sus Ajustes (pantalla 10): siempre
 *   vuelve a la Lista de presupuestos, porque el Dashboard del
 *   presupuesto recién borrado (si se venía de ahí) deja de tener
 *   sentido.
 */
sealed interface Route {
    /** Pantalla 1 — Inicio (sección 8.4 #1). Tab raíz de la bottom nav. */
    @Serializable
    data object Home : Route

    /** Pantalla 3 — Listado de gastos. Tab raíz de la bottom nav. */
    @Serializable
    data object ExpenseList : Route

    /**
     * Pantalla 2 — Registrar/Editar gasto (`expenseId == null` = alta).
     *
     * `prefillBudgetId`/`prefillDate` (Fase 7, RF-10): "+ Agregar gasto"
     * desde el Check-in diario — ver
     * `ExpenseFormViewModel.prefillForCheckIn`. `prefillDate` viaja como
     * ISO-8601 (ver KDoc de la interfaz).
     */
    @Serializable
    data class ExpenseForm(
        val expenseId: String? = null,
        val prefillBudgetId: String? = null,
        val prefillDate: String? = null,
    ) : Route

    /** Subsección de Ajustes generales (sección 8.4 #11) — ver KDoc de [Settings]. */
    @Serializable
    data object Categories : Route

    /** Pantalla 4 — Reportes. Tab raíz de la bottom nav. */
    @Serializable
    data object Reports : Route

    /** Pantalla 5 — Presupuestos, Lista. Tab raíz de la bottom nav. */
    @Serializable
    data object Budgets : Route

    /** Pantalla 6 — Presupuesto, Dashboard. */
    @Serializable
    data class BudgetDashboard(val budgetId: String) : Route

    /** Pantallas 9/10 — Crear presupuesto (`budgetId == null`) o Ajustes del presupuesto (`budgetId != null`). */
    @Serializable
    data class BudgetForm(val budgetId: String? = null) : Route

    /** Pantalla 8 — Historial de proyecciones. */
    @Serializable
    data class ProjectionHistory(val budgetId: String) : Route

    /** Pantalla 7 — Check-in diario. `date` viaja como ISO-8601 (ver KDoc de la interfaz). */
    @Serializable
    data class CheckIn(val budgetId: String, val date: String) : Route

    /** Pantalla 11 — Ajustes generales. Tab raíz de la bottom nav. */
    @Serializable
    data object Settings : Route

    /** RF-06 — alcanzable desde Ajustes (ver `SettingsScreen`). */
    @Serializable
    data object Export : Route
}
