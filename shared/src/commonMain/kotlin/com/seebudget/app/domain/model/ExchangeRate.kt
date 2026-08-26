package com.seebudget.app.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * RF-04 — Tasa de cambio cacheada localmente para convertir montos entre
 * monedas (usado por reportes, RF-03, y por moneda base de presupuestos).
 *
 * Decisiones tomadas en el chat (Fase 3):
 * - Proveedor: Frankfurter (gratis, sin API key, historial por fecha
 *   exacta gratis — ver CurrencyCatalog para el detalle de cobertura).
 * - Se cachea por **fecha exacta**, no solo "la más reciente": RF-03 pide
 *   convertir cada gasto con la tasa vigente el día en que se hizo (no
 *   con la tasa de hoy), así que `date` es parte de la clave junto con el
 *   par de monedas.
 * - Refresco: como máximo 1 vez por día — antes de pedirle a la API una
 *   tasa ya cacheada para esa fecha/par, se usa la que hay en SQLDelight;
 *   la política concreta de "cuándo repreguntar" vive en la capa de
 *   lógica (ExchangeRateRepository, próximo paso), no acá.
 * - `rate` es `Double` (no `Long` en unidades menores, a diferencia de
 *   `Expense.amount`/`Budget.initialBalance`): es un multiplicador, no un
 *   monto de dinero en sí — la fuente (Frankfurter) ya lo entrega como
 *   número de punto flotante con ~4-6 decimales, y solo se usa una vez por
 *   conversión (se multiplica y se redondea al final a unidades menores
 *   de la moneda destino vía MoneyFormat) — no hay la acumulación de error
 *   por sumas repetidas que sí justificaría evitar Double en `amount`.
 *
 * Es un caché de datos públicos, no le pertenece a ningún usuario: no
 * tiene equivalente remoto en Supabase ni participa del SyncManager (sin
 * `userId`/`syncStatus`/`deletedAt` — ver ExchangeRate.sq).
 */
data class ExchangeRate(
    val date: LocalDate,
    val baseCurrency: String, // ISO 4217 — 1 unidad de esta moneda...
    val targetCurrency: String, // ...equivale a `rate` unidades de esta
    val rate: Double,
    val fetchedAt: Instant, // cuándo se guardó este valor en caché
)
