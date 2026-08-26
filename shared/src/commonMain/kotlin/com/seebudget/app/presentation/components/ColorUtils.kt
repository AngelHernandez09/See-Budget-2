package com.seebudget.app.presentation.components

import androidx.compose.ui.graphics.Color

/**
 * Parseo de colores hex (#RRGGBB) a `Color` de Compose. Centralizado acá
 * porque más de un componente lo necesita (CategoryIconBadge, y desde
 * Fase 4 también CategoryBarChart/ReportsScreen — colorea las barras del
 * gráfico de distribución con el mismo color que ya tiene esa categoría
 * en el resto de la app).
 */
fun parseHexColor(hex: String): Color? {
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return null
    return try {
        val r = cleaned.substring(0, 2).toInt(16)
        val g = cleaned.substring(2, 4).toInt(16)
        val b = cleaned.substring(4, 6).toInt(16)
        Color(red = r, green = g, blue = b)
    } catch (e: NumberFormatException) {
        null
    }
}
