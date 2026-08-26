package com.seebudget.app.domain.repository

import kotlinx.datetime.LocalDate

/**
 * RF-04 — tasas de cambio para convertir montos entre monedas (ver
 * ExchangeRate.kt para las decisiones de caché/proveedor).
 */
interface ExchangeRateRepository {
    /**
     * 1 unidad de [from] equivale a X unidades de [to] en la fecha [date].
     * `null` si no hay conexión y tampoco hay nada cacheado para ese
     * par/fecha (RNF-02: nunca lanza, el llamador decide cómo mostrar un
     * gasto sin tasa disponible).
     */
    suspend fun getRate(date: LocalDate, from: String, to: String): Double?
}
