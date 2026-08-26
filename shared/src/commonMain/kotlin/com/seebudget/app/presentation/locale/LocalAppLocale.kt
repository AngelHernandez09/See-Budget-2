package com.seebudget.app.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Selector de idioma (agregado en el chat, RNF-07). Compose Multiplatform
 * todavía no expone una API pública para pisar el "resource environment"
 * de `stringResource()` directamente (ver issue #4197 del repo de
 * compose-multiplatform) — el mecanismo oficial documentado es forzar el
 * locale real de cada plataforma y dejar que Compose Resources lea de
 * ahí, ver
 * https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html.
 *
 * Cada `actual` (androidMain/iosMain/jvmMain/jsMain/wasmJsMain) cambia el
 * locale real de esa plataforma (Android: Configuration; iOS:
 * NSUserDefaults "AppleLanguages"; Desktop: Locale.setDefault; Web:
 * intercepta `navigator.languages`, ver `webApp/.../index.html`).
 * `value == null` restaura el idioma original del sistema — es lo que
 * usa AppLanguage.SYSTEM.
 */
expect object LocalAppLocale {
    val current: String @Composable get
    @Composable infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Envuelve el árbol de composables para que `AppPreferences.language`
 * tenga efecto real sobre `stringResource()`. `key(languageTag)` fuerza
 * una recomposición completa del contenido al cambiar de idioma (mismo
 * motivo que el ejemplo oficial de JetBrains): sin esto, los
 * `Text(stringResource(...))` ya compuestos no se refrescan solos, ya
 * que `stringResource()` no observa el locale por sí mismo.
 */
@Composable
fun AppLocaleEnvironment(languageTag: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides languageTag) {
        key(languageTag) {
            content()
        }
    }
}
