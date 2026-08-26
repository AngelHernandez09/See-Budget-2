package com.seebudget.app.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta "Bosque y ámbar" (guía de diseño, sección 10.2). Los roles NO se
 * mapean 1:1 al ColorScheme de Material — en el tema oscuro el ámbar pasa
 * a ser el acento primario y el verde el secundario (así lo define la guía
 * explícitamente en su tabla de colores), por eso se expone como un set de
 * colores con nombre propio en vez de forzarlo dentro de `primary`/
 * `secondary` de Material (ver SeeBudgetTheme.kt para el mapeo a
 * ColorScheme, usado solo para que los componentes estándar de Material3
 * — TextField, AlertDialog, etc. — tengan colores razonables).
 */
data class SeeBudgetColors(
    val background: Color,
    val textAndBorder: Color,
    val accentPositive: Color, // verde — positivo/planificado, ingresos, presupuesto activo
    val accentAction: Color, // ámbar — CTA, alertas suaves, check-in
    val accentError: Color, // terracota — SOLO excedido/eliminar, nunca decorativo
    val surface: Color,
)

val SeeBudgetLightColors = SeeBudgetColors(
    background = Color(0xFFF5F1E8),
    textAndBorder = Color(0xFF141410),
    accentPositive = Color(0xFF1B5E3F),
    accentAction = Color(0xFFE8A317),
    accentError = Color(0xFFC4432B),
    surface = Color(0xFFFFFFFF),
)

val SeeBudgetDarkColors = SeeBudgetColors(
    background = Color(0xFF0F1F16),
    textAndBorder = Color(0xFFF5F1E8),
    accentPositive = Color(0xFF2E8B57),
    accentAction = Color(0xFFE8A317),
    accentError = Color(0xFFD9532B),
    surface = Color(0xFF182D20),
)

val LocalSeeBudgetColors = staticCompositionLocalOf { SeeBudgetLightColors }
