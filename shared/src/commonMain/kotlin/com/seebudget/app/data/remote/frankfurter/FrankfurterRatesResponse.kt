package com.seebudget.app.data.remote.frankfurter

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Respuesta de `GET https://api.frankfurter.dev/v1/{date}?base=X&symbols=Y`
 * (también sirve para `/v1/latest`, mismo shape). Verificado en vivo
 * contra la API real antes de escribir esto (ver chat, Fase 3).
 *
 * Ej.: `{"amount":1.0,"base":"EUR","date":"2024-06-14","rates":{"USD":1.0686}}`
 *
 * Nota: si `date` cae en fin de semana/feriado (sin publicación ese día),
 * Frankfurter devuelve la tasa del último día hábil anterior y lo refleja
 * en este mismo campo `date` (puede no coincidir con la fecha pedida) —
 * ExchangeRateRepositoryImpl cachea igual bajo la fecha *pedida* (la del
 * gasto), no bajo esta, para que la búsqueda futura por esa fecha
 * encuentre el valor sin tener que resolver el corrimiento cada vez.
 */
@Serializable
data class FrankfurterRatesResponse(
    val amount: Double,
    val base: String,
    val date: LocalDate,
    val rates: Map<String, Double>,
)
