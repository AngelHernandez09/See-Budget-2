package com.seebudget.app.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

/**
 * Ver KDoc de LocalAppLocale (commonMain) y de la variante jsMain — mismo
 * mecanismo (`window.__customLocale`), duplicado acá porque js/wasmJs no
 * comparten un source set intermedio en este proyecto.
 */
external object window {
    var __customLocale: String?
}

actual object LocalAppLocale {
    private val localComposition = staticCompositionLocalOf { Locale.current }

    actual val current: String
        @Composable get() = localComposition.current.toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        window.__customLocale = value?.replace('_', '-')
        return localComposition.provides(Locale.current)
    }
}
