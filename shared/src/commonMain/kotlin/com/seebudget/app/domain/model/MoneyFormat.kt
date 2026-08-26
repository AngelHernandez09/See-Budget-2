package com.seebudget.app.domain.model

import kotlin.math.roundToLong

/**
 * Utilidades para trabajar con montos guardados en unidades menores.
 *
 * Decisión de Fase 1: los campos de monto (`amount`, `initialBalance`) se
 * guardan como `Long` en unidades menores de su moneda (ej. 1050 = $10.50
 * en una moneda de 2 decimales), en vez de `Double`, para evitar errores de
 * redondeo de punto flotante — la exactitud del ProjectionEngine (RF-09,
 * "nunca por promedios") y la conversión multi-moneda (RF-04) lo necesitan.
 *
 * El número de decimales depende de la moneda, no es fijo: la mayoría usa 2
 * (USD, EUR...), el yen japonés usa 0, algunas monedas del Golfo usan 3.
 */
object MoneyFormat {
    private val ZERO_DECIMAL_CURRENCIES = setOf("JPY", "KRW", "VND", "CLP", "ISK")
    private val THREE_DECIMAL_CURRENCIES = setOf("BHD", "KWD", "OMR", "JOD", "TND")

    fun minorUnitDigits(currencyCode: String): Int = when (currencyCode.uppercase()) {
        in ZERO_DECIMAL_CURRENCIES -> 0
        in THREE_DECIMAL_CURRENCIES -> 3
        else -> 2
    }

    /** Ej. minorUnits=1050, currency="USD" -> "10.50". */
    fun toDecimalString(minorUnits: Long, currency: String): String {
        val digits = minorUnitDigits(currency)
        if (digits == 0) return minorUnits.toString()
        var divisor = 1L
        repeat(digits) { divisor *= 10 }
        val whole = minorUnits / divisor
        val fraction = kotlin.math.abs(minorUnits % divisor)
        return "$whole.${fraction.toString().padStart(digits, '0')}"
    }

    /**
     * Convierte un monto (en unidades menores de [from]) a unidades menores
     * de [to], usando [rate] tal como lo devuelve ExchangeRateRepository
     * (1 unidad de `from` = `rate` unidades de `to`).
     *
     * RF-04 (Fase 3): esta es la ÚNICA operación de este módulo que pasa
     * por `Double` — a diferencia de `amount`, acá es inevitable (la tasa
     * en sí es fraccionaria) y de bajo riesgo: se multiplica y se redondea
     * una sola vez al final, no hay acumulación de error por sumas
     * repetidas (eso es lo que `amount: Long` evita en el resto de la
     * app). Ver nota completa en ExchangeRate.kt.
     */
    fun convert(amountMinorUnits: Long, from: String, to: String, rate: Double): Long {
        if (from == to) return amountMinorUnits
        val fromDigits = minorUnitDigits(from)
        val toDigits = minorUnitDigits(to)
        val amountMajor = amountMinorUnits.toDouble() / tenPow(fromDigits)
        val convertedMajor = amountMajor * rate
        return (convertedMajor * tenPow(toDigits)).roundToLong()
    }

    private fun tenPow(digits: Int): Double {
        var result = 1.0
        repeat(digits) { result *= 10.0 }
        return result
    }

    /**
     * Entrada de monto "estilo caja registradora" (agregado en el chat):
     * molestia reportada por el usuario con el campo de texto libre
     * anterior — al escribir en un campo que autocompletaba ceros a la
     * derecha, entrar "0.10" a mano era incómodo. Con este esquema, cada
     * dígito nuevo entra por la derecha y empuja los existentes hacia la
     * izquierda, nunca se autocompletan ceros: "" -> 0.00, "1" -> 0.01,
     * "2" -> 0.12, "3" -> 1.23, "4" -> 12.34, "5" -> 123.45. Usado por
     * AmountField (presentation/components) — compartido por todos los
     * campos de monto de la app en vez de duplicar esta lógica por
     * pantalla (a diferencia de otros helpers de UI más simples como
     * ChipRow/ExpenseTypePicker, que sí se duplican a propósito por
     * archivo — acá el corrimiento de dígitos/cursor tiene casos borde
     * suficientes como para que duplicarlo 3 veces fuera un riesgo real
     * de que se desincronicen con el tiempo).
     *
     * maxDigits (10 por defecto) es una salvaguarda de UI, no una regla
     * de negocio: a partir de esa cantidad de dígitos totales, ignora
     * dígitos adicionales en vez de dejar crecer el monto sin límite
     * (hasta 99.999.999,99 en una moneda de 2 decimales).
     */
    fun appendAmountDigit(currentMinorUnits: Long, digit: Int, maxDigits: Int = 10): Long {
        require(digit in 0..9) { "digit debe estar entre 0 y 9, fue $digit" }
        if (currentMinorUnits.toString().length >= maxDigits) return currentMinorUnits
        return currentMinorUnits * 10 + digit
    }

    /** Inverso de [appendAmountDigit] — "borra" el último dígito ingresado (equivalente a backspace). */
    fun dropLastAmountDigit(currentMinorUnits: Long): Long = currentMinorUnits / 10
}
