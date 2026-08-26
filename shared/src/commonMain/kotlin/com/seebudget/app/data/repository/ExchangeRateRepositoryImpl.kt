package com.seebudget.app.data.repository

import com.seebudget.app.data.remote.frankfurter.FrankfurterRatesResponse
import com.seebudget.app.db.AppDatabase
import com.seebudget.app.domain.repository.ExchangeRateRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Caché local + fetch a Frankfurter (ver ExchangeRate.kt/ExchangeRate.sq
 * para las decisiones de fondo: fecha exacta como clave, refresco 1 vez
 * por día, por qué `rate` es Double).
 *
 * Política de refresco: una tasa de una fecha PASADA, una vez cacheada, no
 * se vuelve a pedir nunca (una tasa histórica ya publicada no cambia). Una
 * tasa de HOY sí se puede repedir, pero como máximo una vez por día: si ya
 * se cacheó hoy, se reusa sin ir a la red hasta mañana.
 */
class ExchangeRateRepositoryImpl(
    private val db: AppDatabase,
    private val httpClient: HttpClient,
) : ExchangeRateRepository {
    private val queries = db.exchangeRateQueries

    override suspend fun getRate(date: LocalDate, from: String, to: String): Double? {
        if (from == to) return 1.0

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val cached = queries.selectRate(date, from, to).executeAsOneOrNull()
        if (cached != null && (date < today || cached.fetchedAt.isFromToday(today))) {
            return cached.rate
        }

        return try {
            val response = httpClient.get("https://api.frankfurter.dev/v1/$date") {
                parameter("base", from)
                parameter("symbols", to)
            }.body<FrankfurterRatesResponse>()
            val rate = response.rates[to] ?: return cached?.rate
            // Se cachea bajo la fecha PEDIDA (la del gasto), no bajo
            // `response.date` — ver nota en FrankfurterRatesResponse.
            queries.insertOrReplace(
                date = date,
                baseCurrency = from,
                targetCurrency = to,
                rate = rate,
                fetchedAt = Clock.System.now(),
            )
            rate
        } catch (e: Exception) {
            // Sin conexión u otro error: mejor una tasa cacheada un poco
            // vieja (de hoy, pero de más temprano) que ninguna. Si no había
            // nada cacheado, `cached` ya era null acá.
            cached?.rate
        }
    }

    private fun Instant.isFromToday(today: LocalDate): Boolean =
        toLocalDateTime(TimeZone.currentSystemDefault()).date == today
}
