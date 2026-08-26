package com.seebudget.app.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Ver KDoc de LocalAppLocale (commonMain) para el porqué de este mecanismo. */
actual object LocalAppLocale {
    private var default: Locale? = null
    private val localComposition = staticCompositionLocalOf { Locale.getDefault().toString() }

    actual val current: String
        @Composable get() = localComposition.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (default == null) {
            default = Locale.getDefault()
        }
        val new = when (value) {
            null -> default!!
            else -> Locale(value)
        }
        Locale.setDefault(new)
        return localComposition.provides(new.toString())
    }
}
