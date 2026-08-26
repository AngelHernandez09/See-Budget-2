package com.seebudget.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * TODO Fase 9 (pulido/accesibilidad): hoy siempre `false`. La guía de
 * diseño pide respetar la preferencia "reduce motion" del sistema en el
 * press effect (sección 10.4), pero eso requiere expect/actual por
 * plataforma (Android: AccessibilityManager/Settings.Global; iOS:
 * UIAccessibility.isReduceMotionEnabled; sin equivalente directo en
 * Desktop/Web) — se implementa cuando se pula accesibilidad, no bloquea la
 * UI básica de Fase 1.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun SeeBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val seeBudgetColors = if (darkTheme) SeeBudgetDarkColors else SeeBudgetLightColors

    // Mapeo a ColorScheme de Material SOLO para que los componentes
    // estándar (OutlinedTextField, AlertDialog, DatePicker...) tengan
    // colores coherentes con la paleta. Los componentes propios
    // (BrutalButton/BrutalCard/etc.) leen de LocalSeeBudgetColors directo.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = seeBudgetColors.background,
            onBackground = seeBudgetColors.textAndBorder,
            surface = seeBudgetColors.surface,
            onSurface = seeBudgetColors.textAndBorder,
            primary = seeBudgetColors.accentAction,
            onPrimary = seeBudgetColors.background,
            secondary = seeBudgetColors.accentPositive,
            onSecondary = seeBudgetColors.background,
            error = seeBudgetColors.accentError,
            onError = seeBudgetColors.background,
            outline = seeBudgetColors.textAndBorder,
        )
    } else {
        lightColorScheme(
            background = seeBudgetColors.background,
            onBackground = seeBudgetColors.textAndBorder,
            surface = seeBudgetColors.surface,
            onSurface = seeBudgetColors.textAndBorder,
            primary = seeBudgetColors.accentPositive,
            onPrimary = seeBudgetColors.background,
            secondary = seeBudgetColors.accentAction,
            onSecondary = seeBudgetColors.textAndBorder,
            error = seeBudgetColors.accentError,
            onError = seeBudgetColors.background,
            outline = seeBudgetColors.textAndBorder,
        )
    }

    CompositionLocalProvider(LocalSeeBudgetColors provides seeBudgetColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = seeBudgetTypography(),
            shapes = SeeBudgetShapes,
            content = content,
        )
    }
}
