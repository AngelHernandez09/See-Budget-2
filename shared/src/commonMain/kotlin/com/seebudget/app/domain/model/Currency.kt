package com.seebudget.app.domain.model

/**
 * RF-04 — Multi-moneda. Lista curada de monedas soportadas por los
 * selectores (registrar gasto, moneda base, reportes).
 *
 * Decisión (chat, Fase 3): la lista NO es una curación arbitraria de
 * ~30-40 monedas "grandes" — es exactamente el set que soporta
 * Frankfurter (el proveedor de tasas elegido, ver ExchangeRate.kt),
 * verificado en vivo contra `https://api.frankfurter.dev/v1/currencies`.
 * Ofrecer en el selector una moneda que después no se puede convertir
 * rompería RF-04 ("conversión mediante tasas de cambio") para esa fila, así
 * que el selector y la cobertura real de conversión están garantizados
 * 1:1. Frankfurter no cubre monedas latinoamericanas más allá de BRL/MXN
 * (ARS, COP, PEN, CLP, UYU, BOB, PYG, VES quedaron afuera) — se confirmó
 * explícitamente con el usuario que esto no es un problema para el uso
 * real de la app antes de fijar esta lista.
 *
 * No incluye `decimalDigits` acá para no duplicar: para formateo/redondeo
 * de montos usar `MoneyFormat.minorUnitDigits(code)`, que ya es la fuente
 * única de verdad para eso (Fase 1).
 */
data class Currency(
    val code: String, // ISO 4217
    val name: String,
    val symbol: String,
)

object CurrencyCatalog {
    val all: List<Currency> = listOf(
        Currency("USD", "Dólar estadounidense", "$"),
        Currency("EUR", "Euro", "€"),
        Currency("GBP", "Libra esterlina", "£"),
        Currency("JPY", "Yen japonés", "¥"),
        Currency("AUD", "Dólar australiano", "$"),
        Currency("BRL", "Real brasileño", "R$"),
        Currency("CAD", "Dólar canadiense", "$"),
        Currency("CHF", "Franco suizo", "Fr."),
        Currency("CNY", "Yuan chino", "¥"),
        Currency("CZK", "Corona checa", "Kč"),
        Currency("DKK", "Corona danesa", "kr"),
        Currency("HKD", "Dólar de Hong Kong", "$"),
        Currency("HUF", "Florín húngaro", "Ft"),
        Currency("IDR", "Rupia indonesia", "Rp"),
        Currency("ILS", "Nuevo shéquel israelí", "₪"),
        Currency("INR", "Rupia india", "₹"),
        Currency("ISK", "Corona islandesa", "kr"),
        Currency("KRW", "Won surcoreano", "₩"),
        Currency("MXN", "Peso mexicano", "$"),
        Currency("MYR", "Ringgit malayo", "RM"),
        Currency("NOK", "Corona noruega", "kr"),
        Currency("NZD", "Dólar neozelandés", "$"),
        Currency("PHP", "Peso filipino", "₱"),
        Currency("PLN", "Zloty polaco", "zł"),
        Currency("RON", "Leu rumano", "lei"),
        Currency("SEK", "Corona sueca", "kr"),
        Currency("SGD", "Dólar de Singapur", "$"),
        Currency("THB", "Baht tailandés", "฿"),
        Currency("TRY", "Lira turca", "₺"),
        Currency("ZAR", "Rand sudafricano", "R"),
    )

    private val byCode: Map<String, Currency> = all.associateBy { it.code }

    fun find(code: String): Currency? = byCode[code.uppercase()]
}
